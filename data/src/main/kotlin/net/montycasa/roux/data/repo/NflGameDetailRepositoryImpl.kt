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
import net.montycasa.roux.data.nfl.NflSummaryAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.NflGameDetail
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.NflGameDetailRepository
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Cache-first loader for one football game's summary.
 *
 * The order is the one every loader here uses: a fresh cache answers without a request, a stale one is refreshed and
 * kept as the fallback, and only a failure with nothing cached is allowed to surface as an error. The body belongs to
 * the state it was captured in as well as to its event id: a scheduled payload is missing the scoring and stat cards a
 * final one has, so it must not be served after kickoff even when the clock says it is fresh.
 *
 * The lifetime is [FreshnessPolicy.detailMaxAge], the same rule hockey's page uses, not a second window invented here.
 */
internal class NflGameDetailRepositoryImpl(
    private val api: EspnApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : NflGameDetailRepository {
    override suspend fun load(
        gameId: GameId,
        state: GameState,
    ): Loadable<NflGameDetail?> = load(gameId, state, force = false)

    override suspend fun refresh(
        gameId: GameId,
        state: GameState,
    ): Loadable<NflGameDetail?> = load(gameId, state, force = true)

    private suspend fun load(
        gameId: GameId,
        state: GameState,
        force: Boolean,
    ): Loadable<NflGameDetail?> =
        withContext(ioDispatcher) {
            val key = CacheKey(CacheKind.NflGameDetail, gameId.value)
            val stateToken = state.cacheToken()
            val cached = readCached(key, stateToken)
            val maxAge =
                FreshnessPolicy.detailMaxAge(
                    isLive = state is GameState.Live,
                    isFinal = state is GameState.Final,
                )

            if (!force && cached != null && !FreshnessPolicy.isStale(cached.fetchedAt, clock.now(), maxAge)) {
                return@withContext Loadable(cached.data, Freshness.Fresh, null)
            }

            // A rate limit carries the pause the server asked for, and a blocked host is a refusal worth showing.
            // Both are answers rather than faults: letting either escape would crash instead of falling back.
            val result =
                try {
                    api.gameSummary(gameId.value)
                } catch (limited: RateLimited) {
                    return@withContext fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
                } catch (blocked: EspnHostNotAllowedException) {
                    return@withContext fallBack(cached, LoadError.Blocked(blocked.host))
                }

            when (result) {
                is ApiResult.Success -> {
                    when (val parsed = NflSummaryAdapter.parse(result.bytes.decodeToString())) {
                        is ParseResult.Success -> {
                            try {
                                // The server's bytes, not a re-encoding of the model, and stamped on arrival.
                                cache.put(key, result.bytes, clock.now(), stateToken)
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

    /** A cached body that no longer parses, or belongs to another state, is a miss rather than a fallback. */
    private suspend fun readCached(
        key: CacheKey,
        stateToken: String,
    ): Cached<NflGameDetail>? {
        val entry =
            try {
                cache.get(key)
            } catch (_: IOException) {
                // An unreadable cache is the same as an empty one; it must not stop the network attempt.
                return null
            } ?: return null
        if (entry.state != stateToken) return null
        return when (val parsed = NflSummaryAdapter.parse(entry.bytes.decodeToString())) {
            is ParseResult.Success -> Cached(parsed.value, entry.fetchedAt)
            is ParseResult.Failure -> null
        }
    }

    private fun fallBack(
        cached: Cached<NflGameDetail>?,
        error: LoadError,
    ): Loadable<NflGameDetail?> =
        if (cached == null) {
            Loadable(null, Freshness.Missing, error)
        } else {
            Loadable(cached.data, Freshness.Stale(cached.fetchedAt), error)
        }

    /**
     * A stable label for the state a body was captured in. The unknown branch keeps the raw token, because it is not
     * ours to constrain.
     */
    private fun GameState.cacheToken(): String =
        when (this) {
            is GameState.Live -> if (critical) "live-critical" else "live"
            GameState.Scheduled -> "scheduled"
            GameState.Pre -> "pre"
            GameState.Final -> "final"
            is GameState.Unknown -> "unknown:" + raw.encodeUtf8().base64()
        }
}

/**
 * Builds the football game-page loader.
 *
 * The implementation and adapter stay internal so the wire shape never becomes part of what `:data` publishes.
 */
fun nflGameDetailRepository(
    api: EspnApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): NflGameDetailRepository = NflGameDetailRepositoryImpl(api, cache, clock, ioDispatcher)
