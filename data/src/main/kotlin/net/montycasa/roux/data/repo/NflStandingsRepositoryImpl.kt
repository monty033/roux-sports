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
import net.montycasa.roux.data.nfl.NflStandingsAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflStandingTable
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.NflStandingsRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first football table loader.
 *
 * The order is the same one the scores loader uses: a fresh cache means no request at all, a stale cache means
 * request-then-fall-back-to-it, and only a failure with nothing cached is allowed to surface as an error. Each request
 * shape gets its own cache key, so the conference tables and a conference's divisions can never be read as each other
 * - which would put the wrong sixteen clubs under a heading.
 *
 * A blocked ESPN host and a 429 stay answers rather than faults: the first must not become a silent offline retry
 * loop, and the second carries the pause the poller needs.
 */
internal class NflStandingsRepositoryImpl(
    private val api: EspnApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    /** Where parsing and cache file work runs, so a poll tick never does that work on the caller's thread. */
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : NflStandingsRepository {
    override suspend fun conferences(anyGameLive: Boolean): Loadable<List<NflStandingTable>> =
        load(key = CONFERENCES, group = null, anyGameLive = anyGameLive)

    override suspend fun divisions(
        conference: NflConference,
        anyGameLive: Boolean,
    ): Loadable<List<NflStandingTable>> =
        load(key = "divisions-${conference.name.lowercase()}", group = conference.groupId, anyGameLive = anyGameLive)

    private suspend fun load(
        key: String,
        group: Int?,
        anyGameLive: Boolean,
    ): Loadable<List<NflStandingTable>> =
        withContext(ioDispatcher) {
            val cached = readCached(key)
            val maxAge = FreshnessPolicy.standingsMaxAge(anyGameLive)
            if (cached != null && !isStale(cached.fetchedAt, maxAge)) {
                return@withContext Loadable(cached.tables, Freshness.Fresh, null)
            }

            val result =
                try {
                    api.standings(group)
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: EspnHostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = NflStandingsAdapter.parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                // Write the bytes the server sent, not a re-encoding of the parsed model.
                                cache.put(CacheKey(CacheKind.NflStandings, key), result.bytes, clock.now())
                                Loadable(parsed.value, Freshness.Fresh, null)
                            } catch (_: IOException) {
                                // The feed answered and the answer is good; the disk is what failed.
                                Loadable(parsed.value, Freshness.Fresh, LoadError.Local)
                            }
                        }

                        is ParseResult.Failure -> fallBack(cached, LoadError.Parse)
                    }
                }

                is ApiResult.HttpError -> fallBack(cached, LoadError.Http(result.status))

                is ApiResult.NetworkError -> fallBack(cached, LoadError.Network)
            }
        }

    /** A cached body that no longer parses is treated as absent; it is not evidence of a failure. */
    private suspend fun readCached(key: String): CachedTables? {
        val entry =
            try {
                cache.get(CacheKey(CacheKind.NflStandings, key))
            } catch (_: IOException) {
                // An unreadable cache is the same as an empty one. It must not stop the network attempt or crash.
                null
            } ?: return null
        val parsed = NflStandingsAdapter.parse(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        return CachedTables(parsed.value, entry.fetchedAt)
    }

    private fun isStale(
        fetchedAt: Instant,
        maxAge: Duration,
    ): Boolean = clock.now() - fetchedAt > maxAge

    private fun fallBack(
        cached: CachedTables?,
        error: LoadError,
    ): Loadable<List<NflStandingTable>> =
        if (cached != null) {
            Loadable(cached.tables, Freshness.Stale(cached.fetchedAt), error)
        } else {
            // Nothing cached: the caller gets the error and no tables, and the screen decides what to say.
            Loadable(emptyList(), Freshness.Missing, error)
        }

    private data class CachedTables(
        val tables: List<NflStandingTable>,
        val fetchedAt: Instant,
    )

    private companion object {
        const val CONFERENCES = "conferences"

        /**
         * The group ids the feed publishes divisions under.
         *
         * They are not the conference numbering they resemble: the AFC answers 8 and the NFC 7, and 9 answers the
         * conference level with no rows at all. `docs/nfl-sources.md` records the probe.
         */
        val NflConference.groupId: Int
            get() =
                when (this) {
                    NflConference.AFC -> 8
                    NflConference.NFC -> 7
                }
    }
}
