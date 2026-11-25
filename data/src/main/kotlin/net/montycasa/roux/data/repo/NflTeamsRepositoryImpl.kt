package net.montycasa.roux.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.EspnApi
import net.montycasa.roux.data.net.EspnHostNotAllowedException
import net.montycasa.roux.data.net.RateLimited
import net.montycasa.roux.data.nfl.NflTeamsAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflTeam
import net.montycasa.roux.domain.model.NflTeamGroup
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.NflStandingsRepository
import net.montycasa.roux.domain.repo.NflTeamsRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first football club loader.
 *
 * The order is the standings loader's: a fresh cache means no request at all, a stale cache means
 * request-then-fall-back-to-it, and only a failure with nothing cached is allowed to surface as an error. The clubs
 * are good for a day ([FreshnessPolicy.nflTeamsMaxAge]): a franchise's name or city changes on the scale of seasons, so
 * a tab switch and a relaunch should not cost a request.
 *
 * The grouping is composed here rather than read, because the teams payload carries no division. The division tables
 * are [standings]' to load and cache; this class only joins them to the clubs by abbreviation.
 */
internal class NflTeamsRepositoryImpl(
    private val api: EspnApi,
    private val cache: ResponseCache,
    private val standings: NflStandingsRepository,
    private val clock: Clock = Clock.System,
    /** Where parsing and cache file work runs, so a tab open never does that work on the caller's thread. */
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : NflTeamsRepository {
    override suspend fun teams(): Loadable<List<NflTeam>> =
        withContext(ioDispatcher) {
            val cached = readCached()
            if (cached != null && !isStale(cached.fetchedAt, FreshnessPolicy.nflTeamsMaxAge())) {
                return@withContext Loadable(cached.teams, Freshness.Fresh, null)
            }

            val result =
                try {
                    api.teams()
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: EspnHostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = NflTeamsAdapter.parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                // Write the bytes the server sent, not a re-encoding of the parsed model.
                                cache.put(CacheKey(CacheKind.NflTeams, KEY), result.bytes, clock.now())
                                Loadable(parsed.value, Freshness.Fresh, null)
                            } catch (_: IOException) {
                                // The feed answered and the answer is good; the disk is what failed.
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

    override suspend fun groupedByDivision(): Loadable<List<NflTeamGroup>> {
        val loaded = teams()
        val clubs = loaded.data.orEmpty()
        if (clubs.isEmpty()) return Loadable(emptyList(), loaded.freshness, loaded.error)

        // One after the other: both loads write the same cache, and a day-old list does not need the tables fast
        // enough to be worth two writers.
        val afc = standings.divisions(NflConference.AFC, anyGameLive = false)
        val nfc = standings.divisions(NflConference.NFC, anyGameLive = false)

        val placed = mutableSetOf<String>()
        val groups = mutableListOf<NflTeamGroup>()
        // The feed's order: AFC's four divisions, then NFC's four, each as the table states them.
        for (table in afc.data.orEmpty() + nfc.data.orEmpty()) {
            val named = table.rows.mapTo(mutableSetOf()) { it.team.abbrev }
            // The teams feed's own order inside a division. A division table is a ranking, and a ranking moves after
            // every game; a list a person scans for their club should not.
            val members = clubs.filter { it.team.abbrev in named && placed.add(it.team.abbrev) }
            if (members.isNotEmpty()) groups += NflTeamGroup(division = table.name, teams = members)
        }

        // A club no table named is still a club. It follows the divisions, under no heading.
        val unplaced = clubs.filter { it.team.abbrev !in placed }
        if (unplaced.isNotEmpty()) groups += NflTeamGroup(division = null, teams = unplaced)

        // The clubs decide freshness; a division table that failed leaves clubs ungrouped, which the error reports so
        // the screen can offer a retry, but it does not make a good list of clubs look stale.
        return Loadable(groups, loaded.freshness, loaded.error ?: afc.error ?: nfc.error)
    }

    /** A cached body that no longer parses is treated as absent; it is not evidence of a failure. */
    private suspend fun readCached(): CachedTeams? {
        val entry =
            try {
                cache.get(CacheKey(CacheKind.NflTeams, KEY))
            } catch (_: IOException) {
                // An unreadable cache is the same as an empty one. It must not stop the network attempt or crash.
                null
            } ?: return null
        val parsed = NflTeamsAdapter.parse(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        return CachedTeams(parsed.value, entry.fetchedAt)
    }

    private fun isStale(
        fetchedAt: Instant,
        maxAge: Duration,
    ): Boolean = clock.now() - fetchedAt > maxAge

    private fun fallBack(
        cached: CachedTeams?,
        error: LoadError,
    ): Loadable<List<NflTeam>> =
        if (cached != null) {
            Loadable(cached.teams, Freshness.Stale(cached.fetchedAt), error)
        } else {
            // Nothing cached: the caller gets the error and no clubs, and the screen decides what to say.
            Loadable(emptyList(), Freshness.Missing, error)
        }

    private data class CachedTeams(
        val teams: List<NflTeam>,
        val fetchedAt: Instant,
    )

    private companion object {
        const val KEY = "teams"
    }
}

/**
 * Builds the football teams repository.
 *
 * The implementation stays internal so the undocumented wire shape never becomes part of what `:data` publishes;
 * callers depend on [NflTeamsRepository]. [standings] is the repository the division tables are read through, so the
 * grouping shares its cache rather than asking the feed for the same tables twice.
 */
fun nflTeamsRepository(
    api: EspnApi,
    cache: ResponseCache,
    standings: NflStandingsRepository,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): NflTeamsRepository = NflTeamsRepositoryImpl(api, cache, standings, clock, ioDispatcher)
