package net.montycasa.roux.data.repo

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.HostNotAllowedException
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.RateLimited
import net.montycasa.roux.data.nhl.BoxscoreAdapter
import net.montycasa.roux.data.nhl.LandingAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.data.nhl.RightRailAdapter
import net.montycasa.roux.domain.Cached
import net.montycasa.roux.domain.FetchOutcome
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.policy.FreshnessPolicy
import net.montycasa.roux.domain.repo.GameDetailRepository
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Loads a game page from three endpoints, each with its own cache entry and its own verdict.
 *
 * The three requests run together rather than one after another: they are independent, and a page that needs three
 * round trips in sequence is three times as slow on a bad connection for no reason. They also fail apart - a timed-out
 * boxscore leaves the goals on screen - which is why the result is three [Loadable]s rather than one.
 *
 * Cached bytes are parsed on every read and never stored pre-parsed, so a parser change invalidates nothing on disk. A
 * cached body that no longer parses counts as absent rather than as a failure: the network may still answer.
 */
internal class GameDetailRepositoryImpl(
    private val api: NhlApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.System,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : GameDetailRepository {
    override suspend fun load(
        gameId: GameId,
        state: GameState,
    ): GameDetail = sections(gameId, state, force = false)

    override suspend fun refresh(
        gameId: GameId,
        state: GameState,
    ): GameDetail = sections(gameId, state, force = true)

    private suspend fun sections(
        gameId: GameId,
        state: GameState,
        force: Boolean,
    ): GameDetail =
        withContext(ioDispatcher) {
            val maxAge =
                FreshnessPolicy.detailMaxAge(
                    isLive = state is GameState.Live,
                    isFinal = state is GameState.Final,
                )
            coroutineScope {
                val landing =
                    async(start = CoroutineStart.DEFAULT) {
                        fetch(CacheKind.DetailLanding, gameId, state, maxAge, force, api::gamecenterLanding, LandingAdapter::parse)
                    }
                val boxscore =
                    async {
                        fetch(CacheKind.DetailBoxscore, gameId, state, maxAge, force, api::gamecenterBoxscore, BoxscoreAdapter::parse)
                    }
                val rightRail =
                    async {
                        fetch(CacheKind.DetailRightRail, gameId, state, maxAge, force, api::gamecenterRightRail, RightRailAdapter::parse)
                    }
                GameDetail(
                    landing = landing.await(),
                    boxscore = boxscore.await(),
                    rightRail = rightRail.await(),
                )
            }
        }

    /**
     * One endpoint: fresh cache short-circuits, everything else tries the network and falls back to whatever is stored.
     *
     * A cached copy is shown with [Freshness.Stale] and the error that stopped the refresh, never as current data - a
     * failed fetch is not evidence that what we already had is still right.
     */
    private suspend fun <T> fetch(
        kind: CacheKind,
        gameId: GameId,
        state: GameState,
        maxAge: Duration?,
        force: Boolean,
        request: suspend (GameId) -> ApiResult,
        parse: (String) -> ParseResult<T>,
    ): Loadable<T> {
        val key = CacheKey(kind, gameId.value)
        val stateToken = state.cacheToken()
        val cached = readCached(key, stateToken, parse)

        if (!force && cached != null && !FreshnessPolicy.isStale(cached.fetchedAt, clock.now(), maxAge)) {
            return Loadable(cached.data, Freshness.Fresh, null)
        }

        // The two exceptions the HTTP boundary raises on purpose are answers rather than faults: a rate limit carries
        // the pause the server asked for, and a blocked host is a refusal worth showing. Letting either escape would
        // take the page down instead of degrading it.
        val result =
            try {
                request(gameId)
            } catch (limited: RateLimited) {
                return fallBack(cached, LoadError.Http(status = 429, retryAfter = limited.retryAfter))
            } catch (blocked: HostNotAllowedException) {
                return fallBack(cached, LoadError.Blocked(blocked.host))
            }

        return when (result) {
            is ApiResult.Success -> {
                when (val parsed = parse(result.bytes.decodeToString())) {
                    is ParseResult.Success -> {
                        try {
                            // The server's bytes, not a re-encoding of the parsed model, so an adapter change never
                            // invalidates the cache. Stamped on arrival, not on request start.
                            cache.put(key, result.bytes, clock.now(), stateToken)
                            Loadable(parsed.value, Freshness.Fresh, null)
                        } catch (_: IOException) {
                            // The feed answered and the answer is good; the disk is what failed. Showing that as a fetch
                            // failure would hide usable data behind an error, so the data is returned and Local names
                            // the real problem.
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
        stateToken: String,
        parse: (String) -> ParseResult<T>,
    ): Cached<T>? {
        val entry =
            try {
                cache.get(key)
            } catch (_: IOException) {
                // Reading may write an access stamp or evict; if that fails the cache is simply unavailable, which is
                // the same as empty. It must not stop the network attempt or crash the page.
                return null
            } ?: return null
        // A body belongs to the state it was captured in. Reading it after the game has moved on - or finding an entry
        // written before states were recorded - is a miss, not a stale copy: the payload is missing whole cards that the
        // current state has, so serving it would silently render a skeleton. The network must answer instead.
        if (entry.state != stateToken) return null
        return when (val parsed = parse(entry.bytes.decodeToString())) {
            is ParseResult.Success -> Cached(parsed.value, entry.fetchedAt)
            is ParseResult.Failure -> null
        }
    }

    /**
     * A stable label for the lifecycle state, written beside the cached body.
     *
     * Critical counts as its own state: it is a different feed moment, and moving into it is exactly when the page is
     * most likely to have new content. The unknown branch is encoded because the raw token is not ours to constrain.
     */
    private fun GameState.cacheToken(): String =
        when (this) {
            is GameState.Live -> if (critical) "live-critical" else "live"
            GameState.Scheduled -> "scheduled"
            GameState.Pre -> "pre"
            GameState.Final -> "final"
            is GameState.Unknown -> "unknown:" + raw.encodeUtf8().base64()
        }

    private fun <T> fallBack(
        cached: Cached<T>?,
        error: LoadError,
    ): Loadable<T> = Loadable.from(cached, FetchOutcome.Failed(error))
}

/**
 * Builds the game-page repository.
 *
 * The implementation and the three adapters stay internal so the undocumented wire shape never becomes part of what
 * `:data` publishes; callers depend on [GameDetailRepository].
 */
fun gameDetailRepository(
    api: NhlApi,
    cache: ResponseCache,
    clock: Clock = Clock.System,
    ioDispatcher: CoroutineContext = Dispatchers.IO,
): GameDetailRepository = GameDetailRepositoryImpl(api, cache, clock, ioDispatcher)
