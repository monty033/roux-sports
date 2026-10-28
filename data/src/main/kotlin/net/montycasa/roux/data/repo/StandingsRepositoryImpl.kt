package net.montycasa.roux.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.repo.StandingsRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first league table.
 *
 * The league day is the device's civil date, from [LeagueCalendar]: the same day the scores screen is showing, so the
 * table a fan reads and the games they are browsing always agree. Getting this wrong is a whole-table-wrong bug that
 * looks like a data problem, so the conversion happens in exactly one place and the tests pin both sides of a DST
 * change. The zone is injected rather than read from ambient state, which keeps this testable and :data honest.
 */
internal class StandingsRepositoryImpl(
    private val api: NhlApi,
    private val adapter: NhlAdapter,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    /** The device's zone, supplied by the caller; see the class note for why it is not read here. */
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    /** Where parsing and cache work runs; see ScoresRepositoryImpl for why this is not the caller's thread. */
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : StandingsRepository {
    override suspend fun standings(anyGameLive: Boolean): Loadable<List<StandingRow>> {
        val day = leagueDay(clock.now())
        return load(
            key = CacheKey(CacheKind.Standings, day),
            maxAge = FreshnessPolicy.standingsMaxAge(anyGameLive),
            fetch = { api.standings(day) },
            parse = { adapter.parseStandings(it) },
        )
    }

    override suspend fun seasons(): Loadable<List<Season>> =
        load(
            // One entry: the list itself is the cache, so there is nothing to key it by beyond
            // "the seasons".
            key = CacheKey(CacheKind.Seasons, SEASONS_KEY),
            maxAge = FreshnessPolicy.seasonsMaxAge(),
            fetch = { api.seasons() },
            parse = { adapter.parseSeasons(it) },
        )

    /**
     * The shared cache-first decision: fresh copy and no request, else fetch, else fall back.
     *
     * Both resources want identical behaviour, so it lives once rather than twice - two copies of
     * this ordering would drift and the drift would show up as one screen refreshing too eagerly.
     */
    private suspend fun <T> load(
        key: CacheKey,
        maxAge: Duration,
        fetch: suspend () -> ApiResult,
        parse: (String) -> ParseResult<T>,
    ): Loadable<T> =
        // Parsing and cache file work must not run on the caller's thread: the screens collect this from the main
        // thread, and a table-sized parse plus an access stamp there is a hitch.
        withContext(ioDispatcher) {
            val cached = readCached(key, parse)
            if (cached != null &&
                !FreshnessPolicy.isStale(fetchedAt = cached.fetchedAt, now = clock.now(), maxAge = maxAge)
            ) {
                return@withContext Loadable(cached.value, Freshness.Fresh, null)
            }

            val result =
                try {
                    fetch()
                } catch (limited: RateLimited) {
                    // A rate limit is an answer, not a fault: it carries the pause the server asked for, and letting it
                    // escape took the process down instead of falling back to the cached table.
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: HostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                // Stamped when the bytes arrive: a slow request must not install an entry
                                // that is already past its own lifetime.
                                cache.put(key, result.bytes, clock.now())
                                Loadable(parsed.value, Freshness.Fresh, null)
                            } catch (_: IOException) {
                                // The feed answered and the answer is good; the disk is what failed. Reporting
                                // that as a fetch failure would hide a usable table behind an error, so the data
                                // is returned and Local names the actual problem.
                                Loadable(parsed.value, Freshness.Fresh, LoadError.Local)
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

    /** A cached body that no longer parses is absent, not evidence that the network will fail. */
    private suspend fun <T> readCached(
        key: CacheKey,
        parse: (String) -> ParseResult<T>,
    ): CachedValue<T>? {
        // ResponseCache.get already swallows IOException and answers null, so an unreadable entry is simply absent,
        // and a defensive catch here would be unreachable. The behaviour is pinned by
        // aCacheThatCannotBeReadStillReachesTheNetwork instead.
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

    private class CachedValue<T>(
        val value: T,
        val fetchedAt: Instant,
    )

    private companion object {
        const val SEASONS_KEY = "season-list"
    }

    /** The day this install is browsing: the device's civil date for [now]. */
    internal fun leagueDay(now: Instant): String = LeagueCalendar.leagueDay(now, zone)
}

/**
 * Builds the league-table repository.
 *
 * The implementation and the adapter stay internal so the undocumented wire shape never becomes part
 * of what `:data` publishes; callers depend on [StandingsRepository].
 */
fun standingsRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    /** The device's zone: the standings day follows the same civil date the scores screen browses. */
    zone: TimeZone = TimeZone.currentSystemDefault(),
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): StandingsRepository = StandingsRepositoryImpl(api, NhlAdapter, cache, clock, zone, ioDispatcher)
