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
import net.montycasa.roux.data.nfl.NflRosterAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.NflClubRosterRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first loader for one club's football roster.
 *
 * The order is the same one every loader here uses: a fresh cache answers without a request, a stale one is refreshed
 * and kept as the fallback, and only a failure with nothing cached surfaces as an error with no roster. Each club gets
 * its own entry, so two clubs' rosters can never be read as each other.
 *
 * The lifetime is the six hours the football schedule uses. A roster moves on the scale of trades and call-ups, and a
 * tab switch must not spend a request learning that nothing has changed.
 */
internal class NflClubRosterRepositoryImpl(
    private val api: EspnApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : NflClubRosterRepository {
    override suspend fun roster(club: TeamKey): Loadable<NflClubRoster?> =
        withContext(ioDispatcher) {
            val key = club.abbrev.uppercase()
            val cached = readCached(key)
            val maxAge = FreshnessPolicy.nflRosterMaxAge()
            if (cached != null && !isStale(cached.fetchedAt, maxAge)) {
                return@withContext Loadable(cached.roster, Freshness.Fresh, null)
            }

            val result =
                try {
                    api.teamRoster(club.abbrev)
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: EspnHostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = NflRosterAdapter.parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                // The bytes the server sent, so a later reader parses exactly what arrived.
                                cache.put(CacheKey(CacheKind.NflRoster, key), result.bytes, clock.now())
                                Loadable(parsed.value, Freshness.Fresh, null)
                            } catch (_: IOException) {
                                // The answer is good; the disk is what failed.
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

    /** A cached body that no longer parses counts as absent: it is not evidence that the feed failed. */
    private suspend fun readCached(key: String): CachedRoster? {
        val entry =
            try {
                cache.get(CacheKey(CacheKind.NflRoster, key))
            } catch (_: IOException) {
                null
            } ?: return null
        val parsed = NflRosterAdapter.parse(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        return CachedRoster(parsed.value, entry.fetchedAt)
    }

    private fun isStale(
        fetchedAt: Instant,
        maxAge: Duration,
    ): Boolean = clock.now() - fetchedAt > maxAge

    private fun fallBack(
        cached: CachedRoster?,
        error: LoadError,
    ): Loadable<NflClubRoster?> =
        if (cached != null) {
            Loadable(cached.roster, Freshness.Stale(cached.fetchedAt), error)
        } else {
            Loadable(null, Freshness.Missing, error)
        }

    private data class CachedRoster(
        val roster: NflClubRoster,
        val fetchedAt: Instant,
    )
}

/**
 * Builds the football roster loader.
 *
 * The implementation stays internal so the wire shape never becomes part of what `:data` publishes.
 */
fun nflClubRosterRepository(
    api: EspnApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): NflClubRosterRepository = NflClubRosterRepositoryImpl(api, cache, clock, ioDispatcher)
