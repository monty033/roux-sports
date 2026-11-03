package net.montycasa.roux.ui.team

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
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.repo.ClubRosterRepository
import net.montycasa.roux.domain.repo.ClubScheduleRepository
import net.montycasa.roux.domain.repo.StandingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamDetailViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val stores = mutableListOf<ViewModelStore>()
    private val tor = TeamKey("TOR")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun aKnownRowIsShownWithItsRecordPointsAndRanks() =
        runTest {
            val repository = FakeStandings()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertFalse(state.isLoading)
                assertEquals(toronto, state.row)
                val row = requireNotNull(state.row)
                assertEquals("9-2-1", row.record)
                assertEquals(19, row.pts)
                assertEquals(3, row.conferenceSequence)
                assertEquals(7, row.leagueSequence)
                assertEquals(1, repository.calls)
            }
        }

    @Test
    fun anUnknownClubLeavesTheRowAbsentRatherThanInventingZeroes() =
        runTest {
            val repository = FakeStandings(result = Loadable(listOf(boston), Freshness.Fresh, null))
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertFalse(state.isLoading)
                assertNull("a club not in the table has no row at all", state.row)
                // The table answered, so a second read would be busywork rather than a cold-start recovery.
                assertEquals(1, repository.calls)
            }
        }

    @Test
    fun aColdStartWithAnEmptyCacheAsksTheTableOnceAndRenders() =
        runTest {
            val repository = FakeStandings(result = Loadable(listOf(toronto), Freshness.Fresh, null))
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                assertTrue("the page starts before the table has answered", awaitItem().isLoading)
                runCurrent()
                val state = expectMostRecentItem()
                assertFalse("the page must not stay on an empty header", state.isLoading)
                assertEquals(toronto, state.row)
                // `standings()` fetches when its cache is missing, so asking twice would be busywork against the same answer.
                assertEquals("a cold start asks the table once", 1, repository.calls)
            }
        }

    @Test
    fun aRetryAsksTheTableAgain() =
        runTest {
            val repository = FakeStandings()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                runCurrent()
                expectMostRecentItem()
                viewModel.retry()
                runCurrent()
                expectMostRecentItem()
                assertEquals("retry is the way back from a failure", 2, repository.calls)
            }
        }

    @Test
    fun theRosterLoadsOnceAndRosterRetryDoesNotAskTheScheduleAgain() =
        runTest {
            val standings = FakeStandings()
            val schedule = FakeSchedule()
            val roster = FakeRoster()
            val viewModel =
                TeamDetailViewModel(
                    teamKey = tor,
                    standings = standings,
                    schedule = schedule,
                    roster = roster,
                ).also { stores += ViewModelStore().apply { put("team", it) } }

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertEquals(1, schedule.calls)
                assertEquals(1, roster.calls)
                assertTrue(state.roster?.data != null)

                viewModel.retryRoster()
                runCurrent()
                expectMostRecentItem()

                assertEquals("roster retry must not re-read the season", 1, schedule.calls)
                assertEquals(2, roster.calls)
            }
        }

    private fun viewModel(repository: StandingsRepository): TeamDetailViewModel =
        TeamDetailViewModel(teamKey = tor, standings = repository).also { model ->
            stores += ViewModelStore().apply { put("team", model) }
        }
}

private class FakeStandings(
    var result: Loadable<List<StandingRow>> = Loadable(listOf(toronto), Freshness.Fresh, null),
) : StandingsRepository {
    var calls = 0

    override suspend fun standings(anyGameLive: Boolean): Loadable<List<StandingRow>> {
        calls++
        return result
    }

    override suspend fun seasons(): Loadable<List<Season>> = error("Not used by the team page")
}

private class FakeSchedule : ClubScheduleRepository {
    var calls = 0

    override suspend fun schedule(teamKey: TeamKey): Loadable<List<ClubGame>> {
        calls++
        return Loadable(emptyList(), Freshness.Fresh, null)
    }
}

private class FakeRoster : ClubRosterRepository {
    var calls = 0

    override suspend fun roster(teamKey: TeamKey): Loadable<ClubRoster> {
        calls++
        return Loadable(ClubRoster(), Freshness.Fresh, null)
    }
}

private val toronto =
    StandingRow(
        team = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs"),
        gp = 12,
        w = 9,
        l = 2,
        otl = 1,
        pts = 19,
        leagueSequence = 7,
        conferenceSequence = 3,
        divisionSequence = 2,
        wildcardSequence = 0,
        conference = Conference.Eastern,
        division = Division.Atlantic,
    )

private val boston =
    StandingRow(
        team = TeamRef(TeamKey("BOS"), name = "Boston Bruins", shortName = "Bruins"),
        gp = 12,
        w = 8,
        l = 3,
        otl = 1,
        pts = 17,
        leagueSequence = 4,
        conferenceSequence = 2,
        divisionSequence = 1,
        wildcardSequence = 0,
        conference = Conference.Eastern,
        division = Division.Atlantic,
    )
