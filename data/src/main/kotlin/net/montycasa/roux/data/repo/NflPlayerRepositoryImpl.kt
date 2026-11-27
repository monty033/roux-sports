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
import net.montycasa.roux.data.nfl.NflPlayerAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflGameLog
import net.montycasa.roux.domain.model.NflPlayerId
import net.montycasa.roux.domain.model.NflPlayerProfile
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.NflPlayerRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first football player page.
 *
 * The order is the roster loader's: a fresh cache answers without a request, a stale one is refreshed and kept as the
 * fallback, and only a failure with nothing cached surfaces as an error. The profile and the game log have separate
 * entries, so one refresh can never evict or overwrite the other.
 *
 * The key is the NFL player id under the NFL player cache kinds. An NHL player with the same digits is a different
 * entry by construction, not by a naming convention.
 */
internal class NflPlayerRepositoryImpl(
    private val api: EspnApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : NflPlayerRepository {
    override suspend fun profile(playerId: NflPlayerId): Loadable<NflPlayerProfile?> =
        load(
            key = CacheKey(CacheKind.NflPlayer, playerId.value),
            maxAge = FreshnessPolicy.nflPlayerMaxAge(),
            fetch = { api.playerProfile(playerId.value) },
            parse = NflPlayerAdapter::parseProfile,
        )

    override suspend fun gameLog(playerId: NflPlayerId): Loadable<NflGameLog?> =
        load(
            key = CacheKey(CacheKind.NflPlayerGameLog, playerId.value),
            maxAge = FreshnessPolicy.nflPlayerGameLogMaxAge(),
            fetch = { api.playerGameLog(playerId.value) },
            parse = NflPlayerAdapter::parseGameLog,
        )

    private suspend fun <T> load(
        key: CacheKey,
        maxAge: Duration,
        fetch: suspend () -> ApiResult,
        parse: (String) -> ParseResult<T>,
    ): Loadable<T> =
        withContext(ioDispatcher) {
            val cached = readCached(key, parse)
            if (cached != null && !isStale(cached.fetchedAt, maxAge)) {
                return@withContext Loadable(cached.value, Freshness.Fresh, null)
            }

            val result =
                try {
                    fetch()
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: EspnHostNotAllowedException) {
                    // A refused host is not an offline device: retrying the same URL earns the same refusal.
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                // The bytes the server sent, so a later reader parses exactly what arrived.
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

    /** A cached body that no longer parses counts as absent: it is not evidence that the feed failed. */
    private suspend fun <T> readCached(
        key: CacheKey,
        parse: (String) -> ParseResult<T>,
    ): CachedValue<T>? {
        val entry =
            try {
                cache.get(key)
            } catch (_: IOException) {
                null
            } ?: return null
        val parsed = parse(entry.bytes.decodeToString())
        if (parsed !is ParseResult.Success) return null
        return CachedValue(parsed.value, entry.fetchedAt)
    }

    private fun isStale(
        fetchedAt: Instant,
        maxAge: Duration,
    ): Boolean = clock.now() - fetchedAt > maxAge

    private fun <T> fallBack(
        cached: CachedValue<T>?,
        error: LoadError,
    ): Loadable<T> =
        if (cached != null) {
            Loadable(cached.value, Freshness.Stale(cached.fetchedAt), error)
        } else {
            Loadable(null, Freshness.Missing, error)
        }

    private class CachedValue<T>(
        val value: T,
        val fetchedAt: Instant,
    )
}

/** Builds the football player repository. The implementation stays internal so the wire shape is not published. */
fun nflPlayerRepository(
    api: EspnApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): NflPlayerRepository = NflPlayerRepositoryImpl(api, cache, clock, ioDispatcher)
