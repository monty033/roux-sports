package net.montycasa.roux.ui.teams

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.StandingsRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The alert sheet's view model: what it shows when opened, what it writes, and what it does when the write fails.
 *
 * The store is faked rather than exercised as DataStore, because what is under test is the order the view model
 * does things in - read before showing, write before publishing, reconcile only after a successful write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamsViewModelNotificationsTest {
    private val toronto = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs")

    @Before
    fun useTestMainDispatcher() {
        // viewModelScope runs on Main, which a unit test does not have: a standard test dispatcher makes its
        // launches something the test can step through instead of a thread it has to sleep on.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeStandings : StandingsRepository {
        override suspend fun standings(anyGameLive: Boolean): Loadable<List<StandingRow>> = Loadable(emptyList(), Freshness.Fresh, null)

        override suspend fun seasons(): Loadable<List<net.montycasa.roux.domain.model.Season>> =
            Loadable(emptyList(), Freshness.Fresh, null)
    }

    private class FakePreferences(
        private var start: Boolean = false,
        private var goals: Boolean = false,
        private val writable: Boolean = true,
        private val readGate: CompletableDeferred<Unit>? = null,
    ) : PreferencesRepository {
        override suspend fun getThemeMode() = ThemeMode.System

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun getUpcomingLeadMinutes() = 30

        override suspend fun setUpcomingLeadMinutes(minutes: Int) = Unit

        val writes = CopyOnWriteArrayList<Pair<TeamKey, TeamAlertPreferences>>()

        override suspend fun getFavourites(): Set<TeamKey> = setOf(TeamKey("TOR"))

        override suspend fun setFavourites(teams: Set<TeamKey>) = Unit

        override suspend fun getFavouritesOnly(): Boolean = false

        override suspend fun setFavouritesOnly(enabled: Boolean) = Unit

        override suspend fun getLeagueOrder(): List<String> = emptyList()

        override suspend fun setLeagueOrder(order: List<String>) = Unit

        override suspend fun isWritable(): Boolean = writable

        override suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences {
            readGate?.await()
            return TeamAlertPreferences(start = start, goals = goals)
        }

        override suspend fun setTeamAlertPreferences(
            team: TeamKey,
            preferences: TeamAlertPreferences,
        ) {
            if (!writable) throw IllegalStateException("read-only store")
            writes += team to preferences
            start = preferences.start
            goals = preferences.goals
        }

        override suspend fun getNotificationLedgerEntry(gameId: String) = null

        override suspend fun setNotificationLedgerEntry(
            gameId: String,
            entry: net.montycasa.roux.domain.repo.NotificationLedgerEntry,
        ) = Unit

        override suspend fun claimStart(
            gameId: String,
            goalBaseline: Int,
            at: kotlin.time.Instant,
        ) = true

        override suspend fun claimGoal(
            gameId: String,
            goalCount: Int,
            at: kotlin.time.Instant,
        ) = true

        override suspend fun claimBaseline(
            gameId: String,
            goalCount: Int,
            at: kotlin.time.Instant,
        ) = true

        override suspend fun claimFinal(
            gameId: String,
            at: kotlin.time.Instant,
        ) = true

        override suspend fun claimIntermission(
            gameId: String,
            period: Int,
            at: kotlin.time.Instant,
        ) = true

        override suspend fun claimUpcoming(
            gameId: String,
            at: kotlin.time.Instant,
        ) = true

        override suspend fun pruneNotificationLedger(
            olderThan: kotlin.time.Instant,
            keep: Set<String>,
        ) = Unit
    }

    private fun viewModel(
        preferences: FakePreferences,
        alertsChanged: suspend () -> Unit = {},
    ) = TeamsViewModel(FakeStandings(), preferences, alertsChanged)

    /**
     * `uiState` is shared with `WhileSubscribed`, so it stays at its initial value until something collects it.
     * A test that read `.value` without this would be asserting on a flow that was never started.
     */
    private fun TestScope.observe(viewModel: TeamsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun theSheetShowsWhatIsAlreadySaved() =
        runTest {
            val viewModel = viewModel(FakePreferences(start = true, goals = false))
            observe(viewModel)

            viewModel.openAlerts(toronto)
            advanceUntilIdle()

            val sheet = viewModel.uiState.value.alerts
            assertEquals("Toronto Maple Leafs", sheet?.team?.name)
            assertTrue("a saved start alert is on when the sheet opens", sheet?.start == true)
            assertFalse("and the unsaved goal alert is off", sheet?.goals == true)
        }

    @Test
    fun anUnconfiguredTeamsSheetOpensWithBothSwitchesOff() =
        runTest {
            val readGate = CompletableDeferred<Unit>()
            val viewModel = viewModel(FakePreferences(readGate = readGate))
            observe(viewModel)
            advanceUntilIdle()

            viewModel.openAlerts(toronto)
            runCurrent()

            // Before the saved answer arrives, the sheet must already be off. Starting it on and then flipping it
            // off would be a visible lie for however long the read takes.
            val opening = viewModel.uiState.value.alerts
            assertFalse("the sheet must not open with game-start alerts on", opening?.start == true)
            assertFalse("the sheet must not open with goal alerts on", opening?.goals == true)

            readGate.complete(Unit)
            advanceUntilIdle()

            val loaded = viewModel.uiState.value.alerts
            assertFalse("a team nobody configured must not have game-start alerts on", loaded?.start == true)
            assertFalse("a team nobody configured must not have goal alerts on", loaded?.goals == true)
        }

    @Test
    fun closingTheSheetRefreshesThatRowsAlertingState() =
        runTest {
            val viewModel = viewModel(FakePreferences())
            observe(viewModel)

            viewModel.openAlerts(toronto)
            advanceUntilIdle()
            viewModel.setAlert(goals = true)
            advanceUntilIdle()
            assertTrue(
                "the row is stale until the sheet closes; that is the refresh this test exercises",
                TeamKey("TOR") !in viewModel.uiState.value.alerting,
            )

            viewModel.closeAlerts()
            advanceUntilIdle()

            assertTrue(
                "the bell must follow the switch without a restart",
                TeamKey("TOR") in viewModel.uiState.value.alerting,
            )
        }

    @Test
    fun turningAGoalAlertOnSavesItAndRebuildsTheWatches() =
        runTest {
            val preferences = FakePreferences()
            var reconciled = 0
            val viewModel = viewModel(preferences) { reconciled += 1 }
            observe(viewModel)

            viewModel.openAlerts(toronto)
            advanceUntilIdle()
            viewModel.setAlert(goals = true)
            advanceUntilIdle()

            assertEquals(listOf(TeamKey("TOR") to TeamAlertPreferences(start = false, goals = true)), preferences.writes)
            assertTrue(
                "the sheet shows the saved value",
                viewModel.uiState.value.alerts
                    ?.goals == true,
            )
            assertEquals("and the watches were rebuilt once", 1, reconciled)
        }

    @Test
    fun aFailedWriteLeavesTheSwitchWhereItWas() =
        runTest {
            val viewModel = viewModel(FakePreferences(writable = false))
            observe(viewModel)

            viewModel.openAlerts(toronto)
            advanceUntilIdle()
            viewModel.setAlert(start = true)
            advanceUntilIdle()

            val sheet = viewModel.uiState.value.alerts
            assertFalse("a switch that was never stored must not show as on", sheet?.start == true)
            assertTrue("and the failure is reported", sheet?.saveFailed == true)
        }

    @Test
    fun closingTheSheetForgetsIt() =
        runTest {
            val viewModel = viewModel(FakePreferences())
            observe(viewModel)

            viewModel.openAlerts(toronto)
            advanceUntilIdle()
            viewModel.closeAlerts()
            // The state is shared and conflated: the clear is published when the collector next runs.
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.alerts)
        }

    @Test
    fun openingAnotherTeamDoesNotLeaveTheFirstOnesSwitchBehind() =
        runTest {
            val montreal = TeamRef(TeamKey("MTL"), name = "Montréal Canadiens", shortName = "Canadiens")
            val preferences = FakePreferences(start = true, goals = true)
            val viewModel = viewModel(preferences)
            observe(viewModel)

            viewModel.openAlerts(toronto)
            advanceUntilIdle()
            viewModel.openAlerts(montreal)
            advanceUntilIdle()

            assertEquals(
                "the sheet is for the team just opened",
                "MTL",
                viewModel.uiState.value.alerts
                    ?.team
                    ?.key
                    ?.abbrev,
            )
        }
}
