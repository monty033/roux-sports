package net.montycasa.roux.ui.standings

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
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
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflStandingTable
import net.montycasa.roux.domain.repo.NflStandingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NflStandingsViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val stores = mutableListOf<ViewModelStore>()
    private val since = Instant.parse("2026-11-24T14:00:00Z")

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun teardown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun model(repo: FakeNflStandings): NflStandingsViewModel {
        val store = ViewModelStore().also { stores += it }
        return NflStandingsViewModel(repo).also { store.put("vm", it) }
    }

    @Test
    fun theConferencesLoadOnceAndTheOpeningConferencesDivisionsWithThem() =
        runTest {
            val repo = FakeNflStandings()
            val vm = model(repo)
            assertTrue(vm.uiState.value.loading)
            runCurrent()

            assertEquals(1, repo.conferenceCalls)
            assertEquals(listOf(NflConference.AFC), repo.divisionCalls)
            assertEquals(listOf(false), repo.liveFlags.distinct())
            assertFalse(vm.uiState.value.loading)
            // The page opens on Division for the AFC: the feed's four divisions, under the feed's own names.
            assertEquals(NflStandingsTab.Division, vm.uiState.value.tab)
            assertEquals(NflConference.AFC, vm.uiState.value.conference)
            assertEquals(
                listOf("AFC East", "AFC North", "AFC South", "AFC West"),
                vm.uiState.value.tables
                    .map { it.name },
            )
        }

    @Test
    fun switchingConferenceLoadsThatConferencesDivisionsOnceAndNeverAgain() =
        runTest {
            val repo = FakeNflStandings()
            val vm = model(repo)
            runCurrent()

            vm.selectConference(NflConference.NFC)
            // Between the switch and the answer the page says it is loading rather than showing the other conference.
            assertTrue(vm.uiState.value.loading)
            assertTrue(
                vm.uiState.value.tables
                    .isEmpty(),
            )
            runCurrent()
            assertEquals(listOf(NflConference.AFC, NflConference.NFC), repo.divisionCalls)
            assertEquals(
                listOf("NFC East", "NFC North", "NFC South", "NFC West"),
                vm.uiState.value.tables
                    .map { it.name },
            )

            // Back and forth: both conferences are held now, so neither costs another request.
            vm.selectConference(NflConference.AFC)
            vm.selectConference(NflConference.NFC)
            vm.selectConference(NflConference.AFC)
            runCurrent()
            assertEquals(listOf(NflConference.AFC, NflConference.NFC), repo.divisionCalls)
            assertEquals(1, repo.conferenceCalls)
            assertFalse(vm.uiState.value.loading)
        }

    @Test
    fun switchingTabsDoesNotRefetch() =
        runTest {
            val repo = FakeNflStandings()
            val vm = model(repo)
            runCurrent()
            val before = repo.conferenceCalls to repo.divisionCalls.size

            NflStandingsTab.entries.forEach { tab ->
                vm.selectTab(tab)
                runCurrent()
                assertEquals(tab, vm.uiState.value.tab)
            }
            vm.selectTab(NflStandingsTab.Division)
            runCurrent()

            assertEquals(before, repo.conferenceCalls to repo.divisionCalls.size)
        }

    @Test
    fun theConferenceTabIsOneTableInSeedOrderAndTheLeagueTabIsAllThirtyTwo() =
        runTest {
            val vm = model(FakeNflStandings())
            runCurrent()

            vm.selectTab(NflStandingsTab.Conference)
            vm.selectConference(NflConference.NFC)
            runCurrent()
            val nfc =
                vm.uiState.value.tables
                    .single()
            assertEquals("National Football Conference", nfc.name)
            // The third and fourth seeds sit under the eighth and ninth in the feed's own table; seed order puts them back.
            assertEquals(
                (1..16).toList(),
                nfc.rows.map { it.playoffSeed },
            )

            vm.selectTab(NflStandingsTab.League)
            runCurrent()
            val league =
                vm.uiState.value.tables
                    .single()
            assertEquals(32, league.rows.size)
            assertEquals("", league.name)
        }

    @Test
    fun aFailureWithNothingCachedReachesTheStateAsAnError() =
        runTest {
            val repo = FakeNflStandings(conferences = Loadable(emptyList(), Freshness.Missing, LoadError.Http(503)))
            val vm = model(repo)
            runCurrent()

            vm.selectTab(NflStandingsTab.Conference)
            runCurrent()
            assertEquals(LoadError.Http(503), vm.uiState.value.error)
            assertFalse(vm.uiState.value.loading)
            assertTrue(
                vm.uiState.value.tables
                    .isEmpty(),
            )
            assertNull(vm.uiState.value.stale)
        }

    @Test
    fun aStaleCopyReachesTheStateWithItsTimestampAndKeepsItsRows() =
        runTest {
            val repo =
                FakeNflStandings(
                    divisions =
                        Loadable(
                            NflStandingsFixtures.divisions.getValue(NflConference.AFC),
                            Freshness.Stale(since),
                            LoadError.Http(503),
                        ),
                )
            val vm = model(repo)
            runCurrent()

            assertEquals(Freshness.Stale(since), vm.uiState.value.stale)
            assertEquals(LoadError.Http(503), vm.uiState.value.error)
            assertEquals(4, vm.uiState.value.tables.size)
        }

    @Test
    fun aThrowingRepositoryBecomesALocalErrorAndStopsTheSpinner() =
        runTest {
            val repo = FakeNflStandings(failWith = IllegalStateException("cache directory is read-only"))
            val vm = model(repo)
            runCurrent()

            assertEquals(LoadError.Local, vm.uiState.value.error)
            assertFalse(vm.uiState.value.loading)
            assertFalse(vm.uiState.value.isRefreshing)
        }

    @Test
    fun refreshReadsEveryHeldTableAgainAndOnlyThose() =
        runTest {
            val repo = FakeNflStandings()
            val vm = model(repo)
            runCurrent()
            vm.selectConference(NflConference.NFC)
            runCurrent()
            val conferencesBefore = repo.conferenceCalls

            vm.refresh()
            assertTrue(vm.uiState.value.isRefreshing)
            runCurrent()

            assertFalse(vm.uiState.value.isRefreshing)
            assertEquals(conferencesBefore + 1, repo.conferenceCalls)
            // Both conferences were held, so both are read again - once each.
            assertEquals(
                listOf(NflConference.AFC, NflConference.NFC, NflConference.NFC, NflConference.AFC),
                repo.divisionCalls,
            )
        }

    @Test
    fun aSecondRefreshWhileOneIsRunningIsIgnored() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeNflStandings(gate = gate)
            val vm = model(repo)
            runCurrent()
            vm.refresh()
            vm.refresh()
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, repo.conferenceCalls)
        }

    /** Answers from the captured tables, counting what it was asked and echoing the freshness it was told to. */
    private class FakeNflStandings(
        private val conferences: Loadable<List<NflStandingTable>> =
            Loadable(NflStandingsFixtures.conferences, Freshness.Fresh, null),
        private val divisions: Loadable<List<NflStandingTable>>? = null,
        private val failWith: Exception? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : NflStandingsRepository {
        var conferenceCalls = 0
        val divisionCalls = mutableListOf<NflConference>()
        val liveFlags = mutableListOf<Boolean>()

        override suspend fun conferences(anyGameLive: Boolean): Loadable<List<NflStandingTable>> {
            conferenceCalls++
            liveFlags += anyGameLive
            failWith?.let { throw it }
            return conferences
        }

        override suspend fun divisions(
            conference: NflConference,
            anyGameLive: Boolean,
        ): Loadable<List<NflStandingTable>> {
            divisionCalls += conference
            liveFlags += anyGameLive
            gate?.await()
            failWith?.let { throw it }
            return divisions ?: Loadable(NflStandingsFixtures.divisions.getValue(conference), Freshness.Fresh, null)
        }
    }
}
