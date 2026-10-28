package net.montycasa.roux.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.policy.PollPolicy
import net.montycasa.roux.domain.repo.GameDetailRepository
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The game page's clock: collection owns polling, including the five-second grace period.
 *
 * The page is opened from a row, so the row's state comes in with it and decides the cadence. Nothing is inferred from
 * what arrived: a final game whose payload happens to contain no scoring is still final.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModel(
    private val gameId: GameId,
    private val state: GameState,
    private val details: GameDetailRepository,
    private val clock: Clock,
    /** Jitter source for the failure backoff, injected so a test can pin the multiplier. */
    private val jitter: () -> Double = { Random.Default.nextDouble() },
    /** How often a live page re-reads itself. The cache lifetime matches it, so a tick normally finds something new. */
    private val liveInterval: Duration = LIVE_INTERVAL,
) : ViewModel() {
    private val refreshTrigger = MutableStateFlow(Trigger())

    // Retained across a cancelled request or an upstream restart, so a rotation does not blank the page.
    private var retained: GameDetail? = null

    val uiState: StateFlow<DetailUiState> =
        refreshTrigger
            .flatMapLatest { trigger -> poll(trigger) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(GRACE_MILLIS), DetailUiState())

    /** Loads immediately even when the cadence has stopped, and reaches past a fresh cache. */
    fun refresh() {
        refreshTrigger.update { Trigger(count = it.count + 1, force = true) }
    }

    private fun poll(trigger: Trigger): Flow<DetailUiState> =
        flow {
            var detail = retained ?: emptyDetail()
            var announce = true
            var failedAttempts = 0
            // Only the load that starts this flow may be forced; a cadence tick keeps honouring the cache.
            var forceNext = trigger.force
            while (currentCoroutineContext().isActive) {
                if (announce) emit(DetailUiState(detail = detail, refreshing = true))
                // A repository that breaks its contract must not take the page - or the process - down. The last
                // usable page stays on screen and the cadence backs off, which is the same answer as a failed fetch.
                val loaded =
                    try {
                        if (forceNext) {
                            forceNext = false
                            details.refresh(gameId, state)
                        } else {
                            details.load(gameId, state)
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        emit(DetailUiState(detail = detail, refreshing = false, unexpectedFailure = true))
                        failedAttempts += 1
                        delay(PollPolicy.backoff(failedAttempts, jitter))
                        continue
                    }
                detail = loaded
                retained = loaded
                emit(DetailUiState(detail = detail, refreshing = false))
                announce = false

                val errors = detail.errors()
                // A blocked host is a refusal, not a transport failure: re-asking earns the same refusal.
                if (errors.any { it is LoadError.Blocked }) break
                val wait =
                    if (errors.isNotEmpty()) {
                        failedAttempts += 1
                        val backoff = PollPolicy.backoff(failedAttempts, jitter)
                        // A rate limit is an instruction: wait at least as long as it asked, whichever section carried it.
                        val retryAfter = errors.filterIsInstance<LoadError.Http>().mapNotNull { it.retryAfter }.maxOrNull()
                        if (retryAfter != null && retryAfter > backoff) retryAfter else backoff
                    } else {
                        failedAttempts = 0
                        pollDelay() ?: break
                    }
                delay(wait)
            }
        }

    /**
     * Live pages re-read every half minute; final ones never; anything else waits for the puck-drop window.
     *
     * Scheduled is neither: it cannot be final, and the feed is already stale the moment the game begins, which can be
     * minutes after the announced start while this page is open.
     */
    private fun pollDelay(): Duration? =
        when (state) {
            is GameState.Live -> liveInterval
            GameState.Final -> null
            else -> SCHEDULED_INTERVAL
        }

    private fun emptyDetail(): GameDetail =
        GameDetail(
            landing = Loadable(null, Freshness.Missing, null),
            boxscore = Loadable(null, Freshness.Missing, null),
            rightRail = Loadable(null, Freshness.Missing, null),
        )

    private fun GameDetail.errors(): List<LoadError> = listOf(landing.error, boxscore.error, rightRail.error).filterNotNull()

    private data class Trigger(
        val count: Long = 0,
        val force: Boolean = false,
    )

    private companion object {
        val LIVE_INTERVAL = 30.seconds
        val SCHEDULED_INTERVAL = 5.minutes

        /** Matches the screens' grace period: a rotation must not restart polling. */
        const val GRACE_MILLIS = 5_000L
    }
}

/** What the page draws: one game, three sections, each with its own freshness and failure. */
data class DetailUiState(
    val detail: GameDetail =
        GameDetail(
            landing = Loadable(null, Freshness.Missing, null),
            boxscore = Loadable(null, Freshness.Missing, null),
            rightRail = Loadable(null, Freshness.Missing, null),
        ),
    val refreshing: Boolean = false,
    /** The repository threw instead of answering. Distinct from a section failing: this is a bug, not a bad feed. */
    val unexpectedFailure: Boolean = false,
) {
    val isEmpty: Boolean
        get() = detail.landing.data == null && detail.boxscore.data == null && detail.rightRail.data == null
}
