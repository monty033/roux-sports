package net.montycasa.roux.ui.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.ShotTotals
import net.montycasa.roux.domain.repo.GameDetailRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * When the page re-reads itself, and how it behaves when it cannot.
 *
 * Time is virtual, so these assert the cadence rather than wait for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeDetails(
        private val answer: (attempt: Int) -> GameDetail,
    ) : GameDetailRepository {
        var loads = 0
        var refreshes = 0

        override suspend fun load(
            gameId: GameId,
            state: GameState,
        ): GameDetail {
            loads += 1
            return answer(loads)
        }

        override suspend fun refresh(
            gameId: GameId,
            state: GameState,
        ): GameDetail {
            refreshes += 1
            loads += 1
            return answer(loads)
        }
    }

    private fun loaded(): GameDetail =
        GameDetail(
            landing = Loadable(GameLanding(periodScoring = listOf(PeriodScoring(1, 2, 0))), Freshness.Fresh, null),
            boxscore = Loadable(GameBoxscore(goalies = listOf(GoalieLine(PlayerRef("A. Goalie", "TOR"), 10, 12))), Freshness.Fresh, null),
            rightRail = Loadable(GameRightRail(shots = ShotTotals(12, 8)), Freshness.Fresh, null),
        )

    private fun failed(error: LoadError): GameDetail =
        GameDetail(
            landing = Loadable(null, Freshness.Missing, error),
            boxscore = Loadable(null, Freshness.Missing, error),
            rightRail = Loadable(null, Freshness.Missing, error),
        )

    private fun viewModel(
        state: GameState,
        details: GameDetailRepository,
    ) = GameDetailViewModel(
        gameId = GameId("2026020188"),
        state = state,
        details = details,
        clock = Clock.System,
        jitter = { 0.5 },
    )

    private fun TestScope.collecting(viewModel: GameDetailViewModel) = backgroundScope.launch { viewModel.uiState.collect {} }

    @Test
    fun aLivePageReReadsEveryHalfMinute() =
        runTest(dispatcher) {
            val details = FakeDetails { loaded() }
            val job = collecting(viewModel(GameState.Live(), details))
            runCurrent()
            assertEquals(1, details.loads)

            advanceTimeBy(29.seconds)
            runCurrent()
            assertEquals("inside the interval nothing is re-read", 1, details.loads)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(2, details.loads)
            job.cancel()
        }

    @Test
    fun aFinalPageIsNeverPolled() =
        runTest(dispatcher) {
            val details = FakeDetails { loaded() }
            val job = collecting(viewModel(GameState.Final, details))
            runCurrent()
            assertEquals(1, details.loads)

            advanceTimeBy(30.minutes)
            runCurrent()

            assertEquals("a finished game has nothing to re-read", 1, details.loads)
            job.cancel()
        }

    /** Scheduled is neither live nor final: it goes quiet, but must not wait twenty minutes for the puck drop. */
    @Test
    fun aScheduledPageReReadsOnTheSlowerCadence() =
        runTest(dispatcher) {
            val details = FakeDetails { loaded() }
            val job = collecting(viewModel(GameState.Scheduled, details))
            runCurrent()

            advanceTimeBy(4.minutes)
            runCurrent()
            assertEquals(1, details.loads)

            advanceTimeBy(2.minutes)
            runCurrent()
            assertEquals(2, details.loads)
            job.cancel()
        }

    /**
     * A rotation inside the grace period must not restart the poll.
     *
     * Restarting would show up as an immediate extra request, which is the thing the grace period exists to prevent -
     * so this asserts on the request count, not on whether a loop happens to be alive.
     */
    @Test
    fun aRotationInsideTheGracePeriodDoesNotRestartThePoll() =
        runTest(dispatcher) {
            val details = FakeDetails { loaded() }
            val viewModel = viewModel(GameState.Live(), details)
            val first = collecting(viewModel)
            runCurrent()
            assertEquals(1, details.loads)

            advanceTimeBy(20.seconds)
            runCurrent()
            first.cancel()
            advanceTimeBy(2.seconds)
            val second = collecting(viewModel)
            runCurrent()

            assertEquals("inside the grace period the poll continues, it does not begin again", 1, details.loads)
            second.cancel()
        }

    @Test
    fun pollingStopsAfterTheSubscriberGoesAway() =
        runTest(dispatcher) {
            val details = FakeDetails { loaded() }
            val viewModel = viewModel(GameState.Live(), details)
            val job = collecting(viewModel)
            runCurrent()
            val before = details.loads

            job.cancel()
            advanceTimeBy(4.seconds)
            runCurrent()
            assertEquals("inside the grace period nothing new is requested", before, details.loads)

            advanceTimeBy(5.minutes)
            runCurrent()
            assertEquals("after the grace period polling is dead", before, details.loads)
        }

    @Test
    fun failuresFollowTheBackoffRatherThanTheLiveCadence() =
        runTest(dispatcher) {
            // Jitter pinned at 0.5, so attempt one backs off by exactly 25 s.
            val details = FakeDetails { failed(LoadError.Network) }
            val job = collecting(viewModel(GameState.Live(), details))
            runCurrent()
            assertEquals(1, details.loads)

            advanceTimeBy(24.seconds)
            runCurrent()
            assertEquals("a failure must not retry at the live cadence", 1, details.loads)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(2, details.loads)
            job.cancel()
        }

    /** A refusal is not a transport failure: asking again earns the same refusal, so the loop stops. */
    @Test
    fun aBlockedHostStopsPollingEntirely() =
        runTest(dispatcher) {
            val details = FakeDetails { failed(LoadError.Blocked("api-web.nhle.com")) }
            val job = collecting(viewModel(GameState.Live(), details))
            runCurrent()

            advanceTimeBy(30.minutes)
            runCurrent()

            assertEquals(1, details.loads)
            job.cancel()
        }

    /** A rate limit carries a pause; the page honours it instead of retrying at its own cadence. */
    @Test
    fun aRateLimitPauseIsHonoured() =
        runTest(dispatcher) {
            val details = FakeDetails { failed(LoadError.Http(429, retryAfter = 90.seconds)) }
            val job = collecting(viewModel(GameState.Live(), details))
            runCurrent()

            advanceTimeBy(80.seconds)
            runCurrent()
            assertEquals(1, details.loads)

            advanceTimeBy(11.seconds)
            runCurrent()
            assertEquals(2, details.loads)
            job.cancel()
        }

    /** A pull-to-refresh reaches past a fresh copy, and does not disturb the cadence that follows it. */
    @Test
    fun refreshForcesTheNetworkAndKeepsPolling() =
        runTest(dispatcher) {
            val details = FakeDetails { loaded() }
            val viewModel = viewModel(GameState.Final, details)
            val job = collecting(viewModel)
            runCurrent()

            viewModel.refresh()
            runCurrent()

            assertEquals(1, details.refreshes)
            assertEquals(2, details.loads)
            job.cancel()
        }

    /** A repository that throws is a bug, not a bad feed: show it, keep the last page, and do not crash. */
    @Test
    fun aThrowingRepositoryDoesNotTakeThePageDown() =
        runTest(dispatcher) {
            var throwNow = true
            val details =
                object : GameDetailRepository {
                    override suspend fun load(
                        gameId: GameId,
                        state: GameState,
                    ): GameDetail {
                        if (throwNow) error("loader exploded")
                        return loaded()
                    }

                    override suspend fun refresh(
                        gameId: GameId,
                        state: GameState,
                    ): GameDetail = load(gameId, state)
                }
            val viewModel = viewModel(GameState.Live(), details)
            val job = collecting(viewModel)
            runCurrent()

            assertTrue("the page should say something went wrong", viewModel.uiState.value.unexpectedFailure)
            assertTrue("and it should have nothing to draw", viewModel.uiState.value.isEmpty)

            throwNow = false
            advanceTimeBy(30.seconds)
            runCurrent()

            assertFalse(viewModel.uiState.value.unexpectedFailure)
            assertFalse(viewModel.uiState.value.isEmpty)
            job.cancel()
        }
}
