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
import net.montycasa.roux.data.nfl.NflClubScheduleAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.NflClubScheduleRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first loader for one club's football season.
 *
 * The order is the same one every loader here uses: a fresh cache answers without a request, a stale one is refreshed
 * and kept as the fallback, and only a failure with nothing cached surfaces as an error with no season. Each club gets
 * its own entry, so two clubs' seasons can never be read as each other.
 *
 * The lifetime is the hockey schedule's six hours. A football season moves once a game ends, and a schedule that
 * refreshed on every visit would spend a request to learn that nothing has changed.
 */
internal class NflClubScheduleRepositoryImpl(
    private val api: EspnApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : NflClubScheduleRepository {
    override suspend fun schedule(
        club: TeamKey,
        anyGameLive: Boolean,
    ): Loadable<NflClubSchedule?> =
        withContext(ioDispatcher) {
            val key = club.abbrev.uppercase()
            val cached = readCached(key)
            val maxAge = FreshnessPolicy.clubScheduleMaxAge()
            if (cached != null && !isStale(cached.fetchedAt, maxAge)) {
                return@withContext Loadable(cached.schedule, Freshness.Fresh, null)
            }

            val result =
                try {
                    api.teamSchedule(club.abbrev)
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: EspnHostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = NflClubScheduleAdapter.parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                // The bytes the server sent, so a later reader parses exactly what arrived.
                                cache.put(CacheKey(CacheKind.NflSchedule, key), result.bytes, clock.now())
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
    private suspend fun readCached(key: String): CachedSchedule? {
        val entry =
            try {
                cache.get(CacheKey(CacheKind.NflSchedule, key))
            } catch (_: IOException) {
                null
            } ?: return null
        val parsed = NflClubScheduleAdapter.parse(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        return CachedSchedule(parsed.value, entry.fetchedAt)
    }

    private fun isStale(
        fetchedAt: Instant,
        maxAge: Duration,
    ): Boolean = clock.now() - fetchedAt > maxAge

    private fun fallBack(
        cached: CachedSchedule?,
        error: LoadError,
    ): Loadable<NflClubSchedule?> =
        if (cached != null) {
            Loadable(cached.schedule, Freshness.Stale(cached.fetchedAt), error)
        } else {
            Loadable(null, Freshness.Missing, error)
        }

    private data class CachedSchedule(
        val schedule: NflClubSchedule,
        val fetchedAt: Instant,
    )
}

/**
 * Builds the football schedule loader.
 *
 * The implementation stays internal so the wire shape never becomes part of what `:data` publishes.
 */
fun nflClubScheduleRepository(
    api: EspnApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): NflClubScheduleRepository = NflClubScheduleRepositoryImpl(api, cache, clock, ioDispatcher)
