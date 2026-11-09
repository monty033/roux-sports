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
import net.montycasa.roux.data.nhl.NhlAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.FetchOutcome
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubSchedule
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.repo.ClubScheduleRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first club season schedule.
 *
 * The season comes from the same device civil day the scores screen shows, so a page opened in
 * early July asks for the coming season rather than the one that just ended. The cache key carries
 * both the club and the season: a season boundary must never serve last year's fixtures under this
 * year's request.
 *
 * A season with no games for the club yet rolls back once, to the season before it, and the value
 * names the season its games came from. Only an empty reply rolls back; an error keeps its own handling.
 */
internal class ClubScheduleRepositoryImpl(
    private val api: NhlApi,
    private val adapter: NhlAdapter,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : ClubScheduleRepository {
    override suspend fun schedule(teamKey: TeamKey): Loadable<ClubSchedule> {
        val abbrev = teamKey.abbrev.uppercase()
        val season = LeagueCalendar.seasonId(clock.now(), zone)
        val requested = read(abbrev, season)
        if (!requested.emptySeason) return requested.loadable
        // The requested season has no games for this club yet. Roll back once; the season before is read
        // exactly like any other, and if it is empty or fails too, its own answer is what the page shows.
        return read(abbrev, LeagueCalendar.previousSeasonId(season)).loadable
    }

    private suspend fun read(
        abbrev: String,
        season: String,
    ): SeasonRead<ClubSchedule> =
        load(
            key = CacheKey(CacheKind.ClubSchedule, "$abbrev-$season"),
            maxAge = FreshnessPolicy.clubScheduleMaxAge(),
            fetch = { api.clubSchedule(abbrev, season) },
            parse = { parseSchedule(it, season) },
        )

    private fun parseSchedule(
        json: String,
        season: String,
    ): ParseResult<ClubSchedule> =
        when (val parsed = adapter.parseClubSchedule(json)) {
            is ParseResult.Success -> ParseResult.Success(ClubSchedule(season = season, games = parsed.value))
            is ParseResult.Failure -> parsed
        }

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
                                // The whole-season payload is stamped when it arrives, so a slow
                                // response cannot install an entry that is already past its lifetime.
                                cache.put(key, result.bytes, clock.now())
                                SeasonRead(Loadable(parsed.value, Freshness.Fresh, null))
                            } catch (_: IOException) {
                                // The feed answered; the disk is what failed. A usable list still
                                // renders, and Local names the actual problem.
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
     * One season's read. [emptySeason] is true only when the feed answered with a well-formed reply that had no games
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
}

/**
 * Builds the club-schedule repository. The implementation stays internal so the undocumented wire
 * shape is not part of what :data publishes.
 */
fun clubScheduleRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): ClubScheduleRepository = ClubScheduleRepositoryImpl(api, NhlAdapter, cache, clock, zone, ioDispatcher)

/** The shorter name some call sites use for [clubScheduleRepository]. */
fun scheduleRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): ClubScheduleRepository = clubScheduleRepository(api, cache, clock, zone, ioDispatcher)
