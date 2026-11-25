package net.montycasa.roux.ui.teams

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.TeamKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The picker over football's source: grouped, in the feed's order, and sharing every other rule with hockey's. */
@OptIn(ExperimentalCoroutinesApi::class)
class NflTeamsViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val stores = mutableListOf<ViewModelStore>()
    private val eagles = TeamKey(League.NFL, "PHI")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun viewModel(preferences: FakePreferences = FakePreferences()): TeamsViewModel =
        TeamsViewModel(
            source = NflTeamsFixtures.source,
            preferences = preferences,
            favoritesFirst = false,
        ).also { model -> stores += ViewModelStore().apply { put("teams", model) } }

    @Test
    fun theLoadedStateHoldsThirtyTwoClubsInEightGroups() =
        runTest {
            viewModel().uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()

                assertTrue(state.grouped)
                assertEquals(8, state.groups.size)
                assertEquals(32, state.rows.size)
                assertTrue(state.groups.all { group -> group.teams.all { it.key.league == League.NFL } })
                assertFalse(state.isRefreshing)
            }
        }

    @Test
    fun theFirstFrameAlreadyKnowsItIsGrouped() =
        runTest {
            viewModel().uiState.test { assertTrue(awaitItem().grouped) }
        }

    @Test
    fun aStarredClubStaysInItsDivisionInsteadOfMovingToTheTop() =
        runTest {
            val viewModel = viewModel(FakePreferences(setOf(eagles)))
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()

                assertEquals(eagles, state.rows.first { it.key in state.favourites }.key)
                assertEquals(
                    "the starred club is still under NFC East, not at the head of the list",
                    "NFC East",
                    state.groups.single { group -> group.teams.any { it.key == eagles } }.division,
                )
                assertFalse("PHI is not first", state.rows.first().key == eagles)
            }
        }

    @Test
    fun starringAFootballClubWritesTheQualifiedKeyAndLeavesTheHockeyClubUnstarred() =
        runTest {
            val preferences = FakePreferences()
            val viewModel = viewModel(preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.toggle(eagles)
                runCurrent()
                val state = expectMostRecentItem()

                assertEquals(setOf(eagles), state.favourites)
                assertEquals(listOf(setOf(eagles)), preferences.writes)
                assertFalse(TeamKey(League.NHL, "PHI") in state.favourites)
            }
        }

    @Test
    fun openingAClubsAlertsReadsThatClubsOwnSettings() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val club = expectMostRecentItem().rows.first { it.key == eagles }
                viewModel.openAlerts(club)
                runCurrent()

                assertEquals(club, expectMostRecentItem().alerts?.team)
            }
        }
}
