package net.montycasa.roux.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.HostNotAllowedException
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.RateLimited
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.data.nhl.PlayerGameLogAdapter
import net.montycasa.roux.data.nhl.PlayerProfileAdapter
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.FetchOutcome
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.PlayerProfileRepository
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Cache-first player profile.
 *
 * The cache key is the player id alone. A profile does not change during a game, so a plain age-based freshness is
 * enough; there is no state stamp for it to compare.
 */
internal class PlayerProfileRepositoryImpl(
    private val api: NhlApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : PlayerProfileRepository {
    override suspend fun profile(playerId: Long): Loadable<PlayerProfile> =
        load(
            key = CacheKey(CacheKind.PlayerProfile, playerId.toString()),
            maxAge = FreshnessPolicy.playerProfileMaxAge(),
            fetch = { api.playerLanding(playerId) },
            parse = PlayerProfileAdapter::parse,
        )

    override suspend fun gameLog(
        playerId: Long,
        season: Int,
        isGoalie: Boolean,
        gameType: Int,
    ): Loadable<List<PlayerGame>> =
        load(
            // The game type is part of the key: a playoff log and a regular-season log for one season are two entries.
            key = CacheKey(CacheKind.PlayerGameLog, "$playerId-$season-$gameType"),
            maxAge = FreshnessPolicy.playerGameLogMaxAge(),
            fetch = { api.playerGameLog(playerId, season.toString(), gameType) },
            parse = { json -> PlayerGameLogAdapter.parse(json, isGoalie) },
        )

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
}

/** Builds the player-profile repository. */
fun playerProfileRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): PlayerProfileRepository = PlayerProfileRepositoryImpl(api, cache, clock, ioDispatcher)
