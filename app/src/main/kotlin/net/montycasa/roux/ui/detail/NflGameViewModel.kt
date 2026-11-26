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
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.NflGameDetail
import net.montycasa.roux.domain.policy.PollPolicy
import net.montycasa.roux.domain.repo.NflGameDetailRepository
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The football game page's clock.
 *
 * The cadence is the same contract as hockey's page: a live game re-reads every half minute, a final one stops, and a
 * scheduled one waits for the kickoff window. The route's state decides the cadence; the payload never does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NflGameViewModel(
    private val gameId: GameId,
    private val state: GameState,
    private val details: NflGameDetailRepository,
    private val clock: Clock,
    /** Jitter source for the failure backoff, injected so a test can pin the multiplier. */
    private val jitter: () -> Double = { Random.Default.nextDouble() },
    private val liveInterval: Duration = LIVE_INTERVAL,
) : ViewModel() {
    private val refreshTrigger = MutableStateFlow(Trigger())

    // Retained across a cancelled request or an upstream restart, so a rotation does not blank the page.
    private var retained: Loadable<NflGameDetail?>? = null

    val uiState: StateFlow<NflGameUiState> =
        refreshTrigger
            .flatMapLatest { trigger -> poll(trigger) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(GRACE_MILLIS), NflGameUiState())

    /** Loads immediately even when the cadence has stopped, and reaches past a fresh cache. */
    fun refresh() {
        refreshTrigger.update { Trigger(count = it.count + 1, force = true) }
    }

    private fun poll(trigger: Trigger): Flow<NflGameUiState> =
        flow {
            var detail = retained ?: missing()
            var announce = true
            var failedAttempts = 0
            var forceNext = trigger.force
            while (currentCoroutineContext().isActive) {
                if (announce) emit(NflGameUiState(detail = detail, refreshing = true))
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
                        emit(NflGameUiState(detail = detail, refreshing = false, unexpectedFailure = true))
                        failedAttempts += 1
                        delay(PollPolicy.backoff(failedAttempts, jitter))
                        continue
                    }
                detail = loaded
                retained = loaded
                emit(NflGameUiState(detail = detail, refreshing = false))
                announce = false

                val error = detail.error
                // A blocked host is a refusal, not a transport failure: re-asking earns the same refusal.
                if (error is LoadError.Blocked) break
                val wait =
                    if (error != null) {
                        failedAttempts += 1
                        val backoff = PollPolicy.backoff(failedAttempts, jitter)
                        val retryAfter = (error as? LoadError.Http)?.retryAfter
                        if (retryAfter != null && retryAfter > backoff) retryAfter else backoff
                    } else {
                        failedAttempts = 0
                        pollDelay() ?: break
                    }
                delay(wait)
            }
        }

    private fun pollDelay(): Duration? =
        when (state) {
            is GameState.Live -> liveInterval
            GameState.Final -> null
            else -> SCHEDULED_INTERVAL
        }

    private fun missing(): Loadable<NflGameDetail?> = Loadable(null, Freshness.Missing, null)

    private data class Trigger(
        val count: Long = 0,
        val force: Boolean = false,
    )

    private companion object {
        val LIVE_INTERVAL = 30.seconds
        val SCHEDULED_INTERVAL = 5.minutes
        const val GRACE_MILLIS = 5_000L
    }
}

/** What the football game page draws: one summary, with its freshness and failure together. */
data class NflGameUiState(
    val detail: Loadable<NflGameDetail?> = Loadable(null, Freshness.Missing, null),
    val refreshing: Boolean = false,
    /** The repository threw instead of answering. Distinct from a failed fetch: this is a bug, not a bad feed. */
    val unexpectedFailure: Boolean = false,
)
