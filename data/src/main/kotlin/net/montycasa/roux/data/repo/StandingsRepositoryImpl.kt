package net.montycasa.roux.data.repo

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.StandingsRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first league table.
 *
 * The league day is the date in New York, not the device's date and not UTC: a viewer in Tokyo at
 * 11:00 local is still on last night's slate in NHL terms, and the table is addressed by that date.
 * Getting this wrong is a whole-table-wrong bug that looks like a data problem, so the conversion
 * happens in exactly one place - [leagueDay] - and the tests pin both sides of a DST change.
 */
internal class StandingsRepositoryImpl(
    private val api: NhlApi,
    private val adapter: NhlAdapter,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
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
    ): Loadable<T> {
        val cached = readCached(key, parse)
        if (cached != null &&
            !FreshnessPolicy.isStale(fetchedAt = cached.fetchedAt, now = clock.now(), maxAge = maxAge)
        ) {
            return Loadable(cached.value, Freshness.Fresh, null)
        }

        return when (val result = fetch()) {
            is ApiResult.Success -> {
                when (val parsed = parse(result.bytes.decodeToString())) {
                    is ParseResult.Success -> {
                        // Stamped when the bytes arrive: a slow request must not install an entry
                        // that is already past its own lifetime.
                        cache.put(key, result.bytes, clock.now())
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

    /** A cached body that no longer parses is absent, not evidence that the network will fail. */
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

    private class CachedValue<T>(
        val value: T,
        val fetchedAt: Instant,
    )

    private companion object {
        const val SEASONS_KEY = "season-list"
        val LEAGUE_ZONE = TimeZone.of("America/New_York")
    }

    /** The NHL league day: the calendar date in New York for [now]. */
    internal fun leagueDay(now: Instant): String = now.toLocalDateTime(LEAGUE_ZONE).date.toString()
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
): StandingsRepository = StandingsRepositoryImpl(api, NhlAdapter, cache, clock)
