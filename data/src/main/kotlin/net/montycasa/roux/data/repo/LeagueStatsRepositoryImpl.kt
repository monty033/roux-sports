package net.montycasa.roux.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.HostNotAllowedException
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.RateLimited
import net.montycasa.roux.data.nhl.LeaguePenaltiesAdapter
import net.montycasa.roux.data.nhl.LeagueStatsAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.FetchOutcome
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubPenalties
import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeaguePenalties
import net.montycasa.roux.domain.model.LeagueSummary
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.repo.LeagueStatsRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first league table.
 *
 * The cache key is the season and the game type together, as the club stats are, so a season boundary
 * never serves last season's table under this season's request. The entry is its own kind, so reading
 * the league cannot evict a club's roster, schedule or stats.
 */
internal class LeagueStatsRepositoryImpl(
    private val api: NhlApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : LeagueStatsRepository {
    /**
     * The table and its penalty minutes are two requests, started together. The table alone decides freshness and
     * the error, so a penalties failure leaves the table fresh and shows only as a dash on the penalty row.
     */
    override suspend fun summary(): Loadable<LeagueSummary> =
        coroutineScope {
            val season = LeagueCalendar.seasonId(clock.now(), zone)
            val table =
                async {
                    load(
                        key = CacheKey(CacheKind.LeagueStats, "$season-$GAME_TYPE"),
                        maxAge = FreshnessPolicy.leagueStatsMaxAge(),
                        fetch = { api.leagueSummary(season, GAME_TYPE) },
                        parse = LeagueStatsAdapter::parse,
                    )
                }
            val penalties =
                async {
                    load(
                        key = CacheKey(CacheKind.LeaguePenaltyTable, "$season-$GAME_TYPE"),
                        maxAge = FreshnessPolicy.leagueStatsMaxAge(),
                        fetch = { api.leaguePenalties(season, GAME_TYPE) },
                        parse = LeaguePenaltiesAdapter::parse,
                    )
                }
            val summary = table.await()
            val penaltyTable = penalties.await()
            Loadable(summary.data?.let { joinPenalties(it, penaltyTable.data) }, summary.freshness, summary.error)
        }

    private suspend fun <T> load(
        key: CacheKey,
        maxAge: Duration,
        fetch: suspend () -> ApiResult,
        parse: (String) -> ParseResult<T>,
    ): Loadable<T> =
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
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: HostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                cache.put(key, result.bytes, clock.now())
                                Loadable(parsed.value, Freshness.Fresh, null)
                            } catch (_: IOException) {
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
        const val GAME_TYPE = 2
    }
}

/**
 * Puts each club's penalty minutes onto its summary line.
 *
 * A pair is matched by club id when both sides carry one, and by exact club name only when a side has no id. The
 * name is never normalised, so a spelling that differs between the two feeds is a miss rather than a silent match.
 * A club with no penalties row keeps null minutes, which prints as a dash and earns no rank.
 */
internal fun joinPenalties(
    summary: LeagueSummary,
    penalties: LeaguePenalties?,
): LeagueSummary {
    if (penalties == null) return summary
    return LeagueSummary(
        clubs =
            summary.clubs.map { club ->
                val match = penalties.clubs.firstOrNull { row -> sameClub(club, row) }
                club.copy(penaltyMinutes = match?.penaltyMinutes)
            },
    )
}

private fun sameClub(
    club: LeagueClub,
    row: ClubPenalties,
): Boolean =
    if (club.teamId != null && row.teamId != null) {
        club.teamId == row.teamId
    } else {
        club.teamName == row.teamName
    }

/** Builds the league-table repository. */
fun leagueStatsRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): LeagueStatsRepository = LeagueStatsRepositoryImpl(api, cache, clock, zone, ioDispatcher)
