package net.montycasa.roux.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.EspnApi
import net.montycasa.roux.data.net.EspnHostNotAllowedException
import net.montycasa.roux.data.net.RateLimited
import net.montycasa.roux.data.nfl.NflScoreboardAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.FetchOutcome
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
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
 * Cache-first NFL scoreboard loader, the NFL counterpart of the NHL scores repository.
 *
 * The same order applies: a fresh cache means no request at all, a stale cache means request-then-fall-back-to-it,
 * and only a failure with nothing cached is allowed to surface as an error. NFL days live under their own cache kind
 * so an NHL day and an NFL day can never evict or be read as each other.
 *
 * A blocked ESPN host and a 429 stay answers rather than faults: the first must not become a silent
 * offline retry loop, and the second carries the pause the poller needs.
 *
 * A live NFL game still maps to [GameState.Unknown] until step 3.2c, so [slateOf] counts nothing as live
 * and a game in progress takes the kickoff-bounded lifetime rather than the 25-second live one. That is
 * the state mapping's gap, not this loader's, and it closes when the adapter learns the live states.
 *
 * [ScoresRepository.openingNight] is left at the interface default (null) on purpose: the NFL opening night comes from
 * the week index in step 3.4, and answering null until then is honest rather than guessing from a single dated
 * scoreboard.
 */
internal class NflScoresRepositoryImpl(
    private val api: EspnApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    /** Where parsing and cache file work runs, so a poll tick never does that work on the caller's thread. */
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : ScoresRepository {
    override suspend fun loadDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay> = load(date, today, force = false)

    override suspend fun refreshDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay> = load(date, today, force = true)

    /**
     * [force] skips the fresh-cache short-circuit. Everything after that point is identical, which is why this is one
     * function with a flag rather than two copies that would drift.
     */
    private suspend fun load(
        date: String,
        today: String,
        force: Boolean,
    ): Loadable<ScoreDay> =
        withContext(ioDispatcher) {
            val cached = readCached(date, today)

            if (!force && cached != null && !isStale(cached, clock.now(), today)) {
                return@withContext Loadable(cached.day, Freshness.Fresh, null)
            }

            // A rate limit carries the pause the server asked for, and a blocked host is a refusal worth showing.
            // Both are answers rather than faults: letting either escape would crash instead of falling back to the
            // cached slate, and a blocked host must not become a silent offline retry loop.
            val result =
                try {
                    api.scoreboard(date)
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: EspnHostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = NflScoreboardAdapter.parseScoreboard(result.bytes.decodeToString(), date)) {
                        is ParseResult.Success -> {
                            if (isWrongDay(date, parsed.value, today)) {
                                // Caching this would file another day's slate under this key, and a finished slate
                                // never expires, so the wrong day would then be permanent.
                                fallBack(cached, LoadError.MismatchedDay(date, parsed.value.date))
                            } else {
                                // Write the bytes the server sent, not a re-encoding of the parsed model, and stamp
                                // them when they arrived rather than when the load began.
                                try {
                                    cache.put(CacheKey(CacheKind.NflScore, date), result.bytes, clock.now())
                                    Loadable(parsed.value, Freshness.Fresh, null)
                                } catch (_: IOException) {
                                    // The feed answered and the answer is good; the disk is what failed.
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

    override suspend fun cachedDay(
        date: String,
        today: String,
    ): ScoreDay? = withContext(ioDispatcher) { readCached(date, today)?.day }

    override suspend fun prefetchAdjacent(
        date: String,
        today: String,
    ): Unit =
        withContext(ioDispatcher) {
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
                    // Speculative work never surfaces. The date shift is inside the guard because it throws on
                    // impossible input, and that must not escape a method promising it will not.
                }
            }
        }

    private suspend fun fetchAndStore(
        date: String,
        today: String,
    ) {
        val result = api.scoreboard(date)
        if (result !is ApiResult.Success) return
        val parsed = NflScoreboardAdapter.parseScoreboard(result.bytes.decodeToString(), date)
        if (parsed !is ParseResult.Success) return
        if (isWrongDay(date, parsed.value, today)) return
        cache.put(CacheKey(CacheKind.NflScore, date), result.bytes, clock.now())
    }

    /** A cached body that no longer parses is treated as absent; it is not evidence of a failure. */
    private suspend fun readCached(
        date: String,
        today: String,
    ): CachedDay? {
        val entry =
            try {
                cache.get(CacheKey(CacheKind.NflScore, date))
            } catch (_: IOException) {
                // An unreadable cache is the same as an empty one. It must not stop the network attempt or crash.
                null
            } ?: return null
        val parsed = NflScoreboardAdapter.parseScoreboard(entry.bytes.decodeToString(), date)
        if (parsed !is ParseResult.Success) return null
        if (isWrongDay(date, parsed.value, today)) return null
        return CachedDay(parsed.value, entry.fetchedAt)
    }

    /**
     * Whether a slate belongs to a day other than the one asked for.
     *
     * This cannot fire against today's adapter: `parseScoreboard` stamps `date = requestedDate`, because the feed's
     * dated scoreboard has no single date field and the caller already knows the day it asked for. The guard is kept
     * as the boundary for an adapter that starts reporting the payload's own date, and it is the reason a mismatched
     * day can never be filed under this key.
     */
    private fun isWrongDay(
        requested: String,
        day: ScoreDay,
        today: String,
    ): Boolean =
        when {
            day.date == requested -> false

            // Rows for another day, including rows that were all dropped as unusable.
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

    /**
     * The same max-age helper and clock as the NHL path, including the kickoff bound.
     *
     * Without [nextStartOf] a day with games still to play takes the idle lifetime, and a copy stamped five minutes
     * before kickoff keeps showing a scheduled row for up to five minutes after it - exactly the window the policy
     * exists to close.
     */
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

    /**
     * The next kickoff still ahead of us, if any.
     *
     * Only future starts count: a start already in the past cannot bound a lifetime, and passing it through would
     * floor the lifetime at zero and refetch on every load. A delayed game therefore falls back to the idle lifetime.
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

    private fun slateOf(day: ScoreDay): DaySlate =
        DaySlate(
            size = day.games.size,
            live = day.games.count { !isTerminal(it) && it.state is GameState.Live },
            finished = day.games.count(::isTerminal),
        )

    /** A renamed terminal token would otherwise look like a game still in progress. */
    private fun isTerminal(game: Game): Boolean = game.state is GameState.Final || game.finalKind != null

    /** ISO dates sort lexicographically, so string comparison is the calendar order. */
    private fun relationOf(
        date: String,
        today: String,
    ): DayRelation =
        when {
            date < today -> DayRelation.Past
            date > today -> DayRelation.Future
            else -> DayRelation.Today
        }

    private fun fallBack(
        cached: CachedDay?,
        error: LoadError,
    ): Loadable<ScoreDay> = Loadable.from(cached?.let { Cached(it.day, it.fetchedAt) }, FetchOutcome.Failed(error))

    private class CachedDay(
        val day: ScoreDay,
        val fetchedAt: Instant,
    )
}

/**
 * Builds the NFL scoreboard repository.
 *
 * The implementation stays internal so the undocumented wire shape never becomes part of what `:data` publishes;
 * callers depend on [ScoresRepository].
 */
fun nflScoresRepository(
    api: EspnApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): ScoresRepository = NflScoresRepositoryImpl(api, cache, clock, ioDispatcher)
