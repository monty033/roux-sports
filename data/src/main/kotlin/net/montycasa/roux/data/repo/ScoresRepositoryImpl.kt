package net.montycasa.roux.data.repo

import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.NhlApi
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
) : ScoresRepository {
    override suspend fun loadDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay> {
        val now = clock.now()
        val cached = readCached(date)

        if (cached != null && !isStale(cached, now, today)) {
            return Loadable(cached.day, Freshness.Fresh, null)
        }

        return when (val result = api.score(date)) {
            is ApiResult.Success -> {
                when (val parsed = adapter.parseScore(result.bytes.decodeToString())) {
                    is ParseResult.Success -> {
                        // Write the bytes the server sent, not a re-encoding of the parsed model:
                        // that is what lets the cache survive an adapter change.
                        cache.put(CacheKey(CacheKind.Score, date), result.bytes, now)
                        Loadable(parsed.value, Freshness.Fresh, null)
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
    ) {
        // Paging is one day at a time, so warming both neighbours is the whole working set.
        for (neighbour in listOf(IsoDate.shift(date, -1), IsoDate.shift(date, 1))) {
            val now = clock.now()
            val cached = readCached(neighbour)
            if (cached != null && !isStale(cached, now, today)) continue
            // Deliberately ignores the outcome: this is speculative work and a failure here must
            // not surface as an error the user did not ask for.
            runCatching { fetchAndStore(neighbour, now) }
        }
    }

    private suspend fun fetchAndStore(
        date: String,
        now: Instant,
    ) {
        val result = api.score(date)
        if (result !is ApiResult.Success) return
        if (adapter.parseScore(result.bytes.decodeToString()) !is ParseResult.Success) return
        cache.put(CacheKey(CacheKind.Score, date), result.bytes, now)
    }

    /** A cached body that no longer parses is treated as absent; it is not evidence of a failure. */
    private suspend fun readCached(date: String): CachedDay? {
        val entry = cache.get(CacheKey(CacheKind.Score, date)) ?: return null
        val parsed = adapter.parseScore(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        return CachedDay(parsed.value, entry.fetchedAt)
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
): ScoresRepository = ScoresRepositoryImpl(api, NhlAdapter, cache, clock)
