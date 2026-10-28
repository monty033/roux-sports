package net.montycasa.roux.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.HostNotAllowedException
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.RateLimited
import net.montycasa.roux.data.nhl.NhlAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.FetchOutcome
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.policy.DayRelation
import net.montycasa.roux.domain.policy.DaySlate
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.ScoresRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first scoreboard loader.
 *
 * The order matters and is the whole point of this class: a fresh cache means no request at all,
 * a stale cache means request-then-fall-back-to-it, and only a failure with nothing cached is
 * allowed to surface as an error.
 *
 * Cached bytes are parsed on every read rather than being stored pre-parsed. That keeps the cache
 * independent of the adapter - a parser change invalidates nothing on disk - at the cost of a parse
 * per load, which is cheap next to the request it avoids. A cached body that no longer parses is
 * treated as absent rather than as a failure: the network may well still answer.
 */
internal class ScoresRepositoryImpl(
    private val api: NhlApi,
    private val adapter: NhlAdapter,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    /**
     * Where parsing and cache file work runs. Not the caller's thread: the poll collects this on the main thread,
     * and a ninety-kilobyte parse plus an access stamp and eviction sweep there is a visible hitch on every tick.
     * A `CoroutineContext` rather than a `CoroutineDispatcher` so a test can hand in a named single thread.
     */
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : ScoresRepository {
    override suspend fun loadDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay> =
        withContext(ioDispatcher) {
            val cached = readCached(date, today)

            if (cached != null && !isStale(cached, clock.now(), today)) {
                return@withContext Loadable(cached.day, Freshness.Fresh, null)
            }

            // The two exceptions the HTTP boundary raises on purpose are answers, not faults: a rate limit carries
            // the pause the server asked for, and a blocked host is a refusal worth showing. Letting either escape
            // took the process down instead of falling back to the cached slate.
            val result =
                try {
                    api.score(date)
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: HostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = adapter.parseScore(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            if (isWrongDay(date, parsed.value, today)) {
                                // Caching this would file another day's slate under this key, and a
                                // finished slate never expires - the wrong day would then be permanent.
                                fallBack(cached, LoadError.MismatchedDay(date, parsed.value.date))
                            } else {
                                // Write the bytes the server sent, not a re-encoding of the parsed
                                // model: that is what lets the cache survive an adapter change. The
                                // stamp is taken when the bytes arrived, not when the load began, so a
                                // slow request cannot install an entry that is already past its own
                                // lifetime and so refetch immediately.
                                try {
                                    cache.put(CacheKey(CacheKind.Score, date), result.bytes, clock.now())
                                    Loadable(parsed.value, Freshness.Fresh, null)
                                } catch (_: IOException) {
                                    // The feed answered and the answer is good; the disk is what failed. Reporting
                                    // that as a fetch failure would hide a perfectly usable slate behind an error,
                                    // so the data is returned and Local names the actual problem.
                                    Loadable(parsed.value, Freshness.Fresh, LoadError.Local)
                                }
                            }
                        }

                        is ParseResult.Failure -> {
                            fallBack(cached, LoadError.Parse)
                        }
                    }
                }

                is ApiResult.HttpError -> {
                    fallBack(cached, LoadError.Http(result.status))
                }

                is ApiResult.NetworkError -> {
                    fallBack(cached, LoadError.Network)
                }
            }
        }

    override suspend fun prefetchAdjacent(
        date: String,
        today: String,
    ) = withContext(ioDispatcher) {
        // Paging is one day at a time, so warming both neighbours is the whole working set.
        for (offset in intArrayOf(-1, 1)) {
            try {
                val neighbour = IsoDate.shift(date, offset)
                val cached = readCached(neighbour, today)
                if (cached != null && !isStale(cached, clock.now(), today)) continue
                fetchAndStore(neighbour, today)
            } catch (cancellation: CancellationException) {
                // Cancellation is the caller's decision, not a failure of speculative work.
                throw cancellation
            } catch (_: Exception) {
                // Speculative work never surfaces. The date shift is inside the guard because it
                // throws on impossible input, and that must not escape a method promising it will not.
            }
        }
    }

    private suspend fun fetchAndStore(
        date: String,
        today: String,
    ) {
        val result = api.score(date)
        if (result !is ApiResult.Success) return
        val parsed = adapter.parseScore(result.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return
        if (isWrongDay(date, parsed.value, today)) return
        cache.put(CacheKey(CacheKind.Score, date), result.bytes, clock.now())
    }

    /** A cached body that no longer parses is treated as absent; it is not evidence of a failure. */
    private suspend fun readCached(
        date: String,
        today: String,
    ): CachedDay? {
        val entry =
            try {
                cache.get(CacheKey(CacheKind.Score, date))
            } catch (_: IOException) {
                // Reading a cache may still write an access stamp or evict; a failure there means the cache is
                // unavailable, which is the same as empty. It must not stop the network attempt or crash.
                null
            } ?: return null
        val parsed = adapter.parseScore(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        if (isWrongDay(date, parsed.value, today)) return null
        return CachedDay(parsed.value, entry.fetchedAt)
    }

    /**
     * Whether a slate belongs to a day other than the one asked for.
     *
     * A quiet date is answered by echoing a date beside an empty list, so an empty slate cannot be
     * judged by its date alone - but it cannot be waved through either. A past, all-final, empty
     * slate never expires under the freshness policy, so an empty body dated 2026-10-24 filed under
     * 2026-11-01 would become permanently fresh: the wrong day, forever, with no error to notice.
     *
     * The exemption is therefore narrow. An empty slate is accepted only when it echoes *today*,
     * which is the documented shape of a quiet-day answer; any other date, or any body that carried
     * rows the adapter could not map, is a slate about a different day and is refused.
     */
    private fun isWrongDay(
        requested: String,
        day: ScoreDay,
        today: String,
    ): Boolean =
        when {
            day.date == requested -> false

            // Rows for another day - including rows that were all dropped as unusable.
            day.games.isNotEmpty() || day.droppedCount > 0 -> true

            else -> day.date != today
        }

    private fun isStale(
        cached: CachedDay,
        now: Instant,
        today: String,
    ): Boolean =
        FreshnessPolicy.isStale(
            fetchedAt = cached.fetchedAt,
            now = now,
            maxAge = maxAge(cached.day, now, today),
        )

    private fun maxAge(
        day: ScoreDay,
        now: Instant,
        today: String,
    ): Duration? =
        FreshnessPolicy.scoreDayMaxAge(
            now = now,
            slate = slateOf(day),
            day = relationOf(day.date, today),
            nextStartAt = nextStartOf(day, now),
        )

    /** Counted rather than flagged: "all games are final" is vacuously true for an empty day. */
    private fun slateOf(day: ScoreDay): DaySlate =
        DaySlate(
            size = day.games.size,
            live = day.games.count { !isTerminal(it) && it.state is GameState.Live },
            finished = day.games.count(::isTerminal),
        )

    private fun relationOf(
        date: String,
        today: String,
    ): DayRelation =
        when {
            // ISO dates sort lexicographically, so string comparison is the calendar order.
            date < today -> DayRelation.Past

            date > today -> DayRelation.Future

            else -> DayRelation.Today
        }

    /**
     * The next scheduled start still ahead of us, if any.
     *
     * Only future starts are considered: a start already in the past cannot bound a cache lifetime,
     * and passing it through would floor the lifetime at zero and refetch on every load. A delayed
     * game therefore falls back to the idle lifetime, which is bounded and cheap.
     */
    private fun nextStartOf(
        day: ScoreDay,
        now: Instant,
    ): Instant? =
        day.games
            .asSequence()
            .filter { !isTerminal(it) && it.state !is GameState.Live }
            .mapNotNull { it.startTimeUtc }
            .filter { it > now }
            .minOrNull()

    /**
     * A renamed terminal state token would otherwise look like a game still in progress.
     * `finalKind` survives a clock that has stopped, which the state token alone does not prove.
     */
    private fun isTerminal(game: Game): Boolean = game.state is GameState.Final || game.finalKind != null

    private class CachedDay(
        val day: ScoreDay,
        val fetchedAt: Instant,
    ) {
        val data: ScoreDay get() = day
    }

    private fun fallBack(
        cached: CachedDay?,
        error: LoadError,
    ): Loadable<ScoreDay> =
        Loadable.from(
            cached?.let { Cached(it.data, it.fetchedAt) },
            FetchOutcome.Failed(error),
        )
}

/**
 * Builds the scoreboard repository.
 *
 * The implementation and the adapter stay internal so the undocumented wire shape never becomes
 * part of what `:data` publishes; callers depend on [ScoresRepository].
 */
fun scoresRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): ScoresRepository = ScoresRepositoryImpl(api, NhlAdapter, cache, clock, ioDispatcher)
