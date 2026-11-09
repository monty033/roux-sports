package net.montycasa.roux.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.HostNotAllowedException
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.RateLimited
import net.montycasa.roux.data.nhl.ClubStatsAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.FetchOutcome
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.repo.ClubStatsRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first club statistics.
 *
 * The cache key carries both the club and the season, exactly like the schedule: a season boundary
 * must never serve last year's totals under this year's request. The entry is its own kind, so a
 * stats fetch cannot evict the roster or the schedule the reader already has.
 *
 * A season with no skater or goalie rows for the club yet rolls back once, to the season before it, and
 * the value says which season it came from. Only an empty reply rolls back; an error keeps its own handling.
 */
internal class ClubStatsRepositoryImpl(
    private val api: NhlApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : ClubStatsRepository {
    override suspend fun stats(teamKey: TeamKey): Loadable<ClubStats> {
        val abbrev = teamKey.abbrev.uppercase()
        val season = LeagueCalendar.seasonId(clock.now(), zone)
        val requested = read(abbrev, season)
        if (!requested.emptySeason) return requested.loadable
        // The requested season has nothing for this club yet. Roll back once: the season before is read
        // exactly like any other, and if it is empty or fails too, its own answer is what the page shows.
        return read(abbrev, LeagueCalendar.previousSeasonId(season)).loadable
    }

    private suspend fun read(
        abbrev: String,
        season: String,
    ): SeasonRead<ClubStats> =
        load(
            key = CacheKey(CacheKind.ClubStats, "$abbrev-$season"),
            maxAge = FreshnessPolicy.clubStatsMaxAge(),
            fetch = { api.clubStats(abbrev, season, GAME_TYPE) },
            parse = { ClubStatsAdapter.parse(it, season) },
        )

    private suspend fun <T> load(
        key: CacheKey,
        maxAge: Duration,
        fetch: suspend () -> ApiResult,
        parse: (String) -> ParseResult<T>,
    ): SeasonRead<T> =
        withContext(ioDispatcher) {
            val cached = readCached(key, parse)
            if (cached != null &&
                !FreshnessPolicy.isStale(fetchedAt = cached.fetchedAt, now = clock.now(), maxAge = maxAge)
            ) {
                return@withContext SeasonRead(Loadable(cached.value, Freshness.Fresh, null))
            }

            val result =
                try {
                    fetch()
                } catch (limited: RateLimited) {
                    return@withContext SeasonRead(fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter)))
                } catch (blocked: HostNotAllowedException) {
                    return@withContext SeasonRead(fallBack(cached, LoadError.Blocked(blocked.host)))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                cache.put(key, result.bytes, clock.now())
                                SeasonRead(Loadable(parsed.value, Freshness.Fresh, null))
                            } catch (_: IOException) {
                                SeasonRead(Loadable(parsed.value, Freshness.Fresh, LoadError.Local))
                            }
                        }

                        is ParseResult.Failure -> {
                            SeasonRead(fallBack(cached, LoadError.Parse), emptySeason = parsed.emptyPayload)
                        }
                    }
                }

                is ApiResult.HttpError -> {
                    SeasonRead(fallBack(cached, LoadError.Http(result.status)))
                }

                is ApiResult.NetworkError -> {
                    SeasonRead(fallBack(cached, LoadError.Network))
                }
            }
        }

    private suspend fun <T> readCached(
        key: CacheKey,
        parse: (String) -> ParseResult<T>,
    ): CachedValue<T>? {
        val entry = cache.get(key) ?: return null
        val parsed = parse(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        return CachedValue(parsed.value, entry.fetchedAt)
    }

    private fun <T> fallBack(
        cached: CachedValue<T>?,
        error: LoadError,
    ): Loadable<T> =
        Loadable.from(
            cached?.let { Cached(it.value, it.fetchedAt) },
            FetchOutcome.Failed(error),
        )

    /**
     * One season's read. [emptySeason] is true only when the feed answered with a well-formed reply that had no rows
     * for the club, the one outcome that justifies asking for the season before.
     */
    private class SeasonRead<T>(
        val loadable: Loadable<T>,
        val emptySeason: Boolean = false,
    )

    private class CachedValue<T>(
        val value: T,
        val fetchedAt: Instant,
    )

    private companion object {
        const val GAME_TYPE = 2
    }
}

/** Builds the club-stats repository. */
fun clubStatsRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): ClubStatsRepository = ClubStatsRepositoryImpl(api, cache, clock, zone, ioDispatcher)

/** The shorter name call sites use for [clubStatsRepository]. */
fun statsRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): ClubStatsRepository = clubStatsRepository(api, cache, clock, zone, ioDispatcher)
