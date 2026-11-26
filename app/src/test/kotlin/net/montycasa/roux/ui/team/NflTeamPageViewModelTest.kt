package net.montycasa.roux.ui.team

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflRosterPlayer
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.repo.NflClubRosterRepository
import net.montycasa.roux.domain.repo.NflClubScheduleRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The two football tabs load independently: one failing must not take the other's content away. */
@OptIn(ExperimentalCoroutinesApi::class)
class NflTeamPageViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val stores = mutableListOf<ViewModelStore>()
    private val bills = TeamKey(League.NFL, "BUF")

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun teardown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun bothTabsLoadOnOpen() =
        runTest {
            val schedule = FakeSchedule()
            val roster = FakeRoster()

            val vm = model(schedule, roster)
            runCurrent()

            assertEquals(1, schedule.calls)
            assertEquals(1, roster.calls)
            assertTrue("the schedule is on screen", vm.uiState.value.season != null)
            assertTrue("the roster is ready for its tab", vm.uiState.value.roster != null)
            assertEquals(false, vm.uiState.value.loading)
            assertEquals(false, vm.uiState.value.rosterLoading)
        }

    @Test
    fun aFailedRosterLeavesTheSeasonOnScreen() =
        runTest {
            val vm = model(FakeSchedule(), FakeRoster(failure = LoadError.Network))
            runCurrent()

            assertTrue(vm.uiState.value.season != null)
            assertNull(vm.uiState.value.roster)
            assertEquals(LoadError.Network, vm.uiState.value.rosterError)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun aFailedSeasonLeavesTheRosterOnScreen() =
        runTest {
            val vm = model(FakeSchedule(failure = LoadError.Network), FakeRoster())
            runCurrent()

            assertTrue(vm.uiState.value.roster != null)
            assertNull(vm.uiState.value.season)
            assertEquals(LoadError.Network, vm.uiState.value.error)
            assertNull(vm.uiState.value.rosterError)
        }

    @Test
    fun refreshingTheRosterDoesNotAskForTheSeasonAgain() =
        runTest {
            val schedule = FakeSchedule()
            val roster = FakeRoster()
            val vm = model(schedule, roster)
            runCurrent()

            vm.refreshRoster()
            runCurrent()

            assertEquals(1, schedule.calls)
            assertEquals(2, roster.calls)
        }

    private fun model(
        schedule: NflClubScheduleRepository,
        roster: NflClubRosterRepository,
    ): NflTeamPageViewModel {
        val store = ViewModelStore().also { stores += it }
        return NflTeamPageViewModel(schedule, roster, bills).also { store.put("vm", it) }
    }

    private class FakeSchedule(
        private val failure: LoadError? = null,
    ) : NflClubScheduleRepository {
        var calls = 0

        override suspend fun schedule(
            club: TeamKey,
            anyGameLive: Boolean,
        ): Loadable<NflClubSchedule?> {
            calls++
            val data =
                if (failure == null) {
                    NflClubSchedule(seasonYear = 2026, team = TeamRef(club, name = "Buffalo Bills"))
                } else {
                    null
                }
            return Loadable(data, if (failure == null) Freshness.Fresh else Freshness.Missing, failure)
        }
    }

    private class FakeRoster(
        private val failure: LoadError? = null,
    ) : NflClubRosterRepository {
        var calls = 0

        override suspend fun roster(club: TeamKey): Loadable<NflClubRoster?> {
            calls++
            val data =
                if (failure == null) {
                    NflClubRoster(
                        team = TeamRef(club, name = "Buffalo Bills"),
                        players =
                            listOf(
                                NflRosterPlayer(
                                    id = "1",
                                    name = "Josh Allen",
                                    jersey = "17",
                                    positionAbbreviation = "QB",
                                    positionName = "Quarterback",
                                ),
                            ),
                    )
                } else {
                    null
                }
            return Loadable(data, if (failure == null) Freshness.Fresh else Freshness.Missing, failure)
        }
    }
}
