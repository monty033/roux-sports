package net.montycasa.roux.ui.teams

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.StandingsRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val stores = mutableListOf<ViewModelStore>()
    private val tor = TeamKey("TOR")
    private val mtl = TeamKey("MTL")

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
    fun allThirtyTwoTeamsAreSortedByNameWithSortedFavouritesFirst() =
        runTest {
            val favourites = setOf(tor, mtl, TeamKey("BOS"))
            val viewModel = viewModel(preferences = FakePreferences(favourites))
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                val names = state.rows.map { it.name }
                assertEquals(32, state.rows.size)
                assertEquals(
                    32,
                    state.rows
                        .map { it.key }
                        .toSet()
                        .size,
                )
                assertEquals(listOf("Boston Bruins", "Montréal Canadiens", "Toronto Maple Leafs"), names.take(3))
                assertEquals(names.drop(3).sortedBy { it }, names.drop(3))
                assertEquals(favourites, state.favourites)
                assertFalse(state.isRefreshing)
            }
        }

    @Test
    fun aNullNameIsRetainedAndSortedByItsAbbreviation() =
        runTest {
            val standings = FakeStandings()
            standings.result =
                standings.result.copy(data = teamStandings().map { if (it.team.key == tor) it.copy(team = TeamRef(tor)) else it })
            viewModel(standings).uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertEquals(32, state.rows.size)
                assertEquals(TeamRef(tor), state.rows.single { it.key == tor })
                val labels = state.rows.map { it.name ?: it.abbrev }
                assertEquals(labels.sorted(), labels)
            }
        }

    @Test
    fun togglingTorontoPersistsTheFullSetThenRemovesItAndLeavesTheOrderAlone() =
        runTest {
            val preferences = FakePreferences()
            val viewModel = viewModel(preferences = preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                expectMostRecentItem()
                viewModel.toggle(tor)
                runCurrent()
                assertEquals(setOf(tor), preferences.teams)
                val selected = expectMostRecentItem()
                assertEquals(setOf(tor), selected.favourites)
                // Where it was. A favourite sorts first at the next load - not under the finger that just
                // tapped it - so the row a thumb is on does not jump away mid-tap.
                assertEquals("Anaheim Ducks", selected.rows.first().name)
                viewModel.toggle(tor)
                runCurrent()
                assertEquals(emptySet<TeamKey>(), preferences.teams)
                val cleared = expectMostRecentItem()
                assertTrue(cleared.favourites.isEmpty())
                assertEquals("Anaheim Ducks", cleared.rows.first().name)
                assertEquals(listOf(setOf(tor), emptySet<TeamKey>()), preferences.writes)
            }
        }

    @Test
    fun aSuspendedWriteCannotLoseAConcurrentToggle() =
        runTest {
            val preferences = FakePreferences(setOf(TeamKey("BOS")))
            val gate = CompletableDeferred<Unit>()
            preferences.beforeWrite = { gate.await() }
            val viewModel = viewModel(preferences = preferences)
            runCurrent()
            viewModel.toggle(tor)
            runCurrent()
            viewModel.toggle(mtl)
            runCurrent()
            assertEquals(1, preferences.writes.size)
            assertEquals(setOf(TeamKey("BOS")), preferences.teams)
            gate.complete(Unit)
            runCurrent()
            val expected = setOf(TeamKey("BOS"), tor, mtl)
            assertEquals(expected, preferences.teams)
            assertEquals(listOf(setOf(TeamKey("BOS"), tor), expected), preferences.writes)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(expected, expectMostRecentItem().favourites)
            }
        }

    @Test
    fun twoConcurrentTogglesOfTheSameTeamCancelEachOther() =
        runTest {
            val preferences = FakePreferences()
            val gate = CompletableDeferred<Unit>()
            preferences.beforeWrite = { gate.await() }
            val viewModel = viewModel(preferences = preferences)
            viewModel.toggle(tor)
            viewModel.toggle(tor)
            runCurrent()
            assertEquals(listOf(setOf(tor)), preferences.writes)
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(setOf(tor), emptySet<TeamKey>()), preferences.writes)
            assertTrue(preferences.teams.isEmpty())
        }

    @Test
    fun aToggleWaitsForTheInitialReadAndPreservesStoredFavourites() =
        runTest {
            val preferences = FakePreferences(setOf(mtl))
            val gate = CompletableDeferred<Unit>()
            preferences.beforeRead = { gate.await() }
            val viewModel = viewModel(preferences = preferences)
            viewModel.toggle(tor)
            runCurrent()
            assertTrue(preferences.writes.isEmpty())
            gate.complete(Unit)
            runCurrent()
            assertEquals(setOf(mtl, tor), preferences.teams)
            assertEquals(1, preferences.reads)
        }

    @Test
    fun favouritesAreReadOnceOnInitAndLocalWritesSurviveResubscription() =
        runTest {
            val preferences = FakePreferences()
            val viewModel = viewModel(preferences = preferences)
            runCurrent()
            assertEquals(1, preferences.reads)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.toggle(tor)
                runCurrent()
                assertEquals(setOf(tor), expectMostRecentItem().favourites)
            }
            advanceTimeBy(5_001)
            runCurrent()
            // A second read would replace the successfully written local selection with this value.
            preferences.teams = setOf(mtl)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(setOf(tor), viewModel.uiState.value.favourites)
                assertEquals(1, preferences.reads)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * A throwing read used to kill the shared flow, which left the picker stuck on "loading" forever with
     * no retry path. Favourites are convenience data: the teams must still be there to choose from.
     */
    @Test
    fun aFailedPreferenceReadLeavesThePickerUsable() =
        runTest {
            val preferences = FakePreferences(setOf(tor))
            preferences.beforeRead = { throw IOException("preferences unreadable") }
            viewModel(preferences = preferences).uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertEquals(32, state.rows.size)
                assertEquals(emptySet<TeamKey>(), state.favourites)
                assertFalse(state.isRefreshing)
            }
        }

    /**
     * The write is the thing that can fail in the field. It must not throw out of `viewModelScope` (that
     * reaches the default handler and crashes), must not show a selection it did not save, and must say so.
     */
    @Test
    fun aFailedWriteKeepsTheOldSelectionReportsItAndRecovers() =
        runTest {
            val preferences = FakePreferences()
            val viewModel = viewModel(preferences = preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                expectMostRecentItem()
                preferences.beforeWrite = { throw IOException("disk full") }
                viewModel.toggle(tor)
                runCurrent()
                val failed = expectMostRecentItem()
                assertEquals(emptySet<TeamKey>(), failed.favourites)
                assertTrue(failed.saveFailed)
                preferences.beforeWrite = {}
                viewModel.toggle(tor)
                runCurrent()
                val recovered = expectMostRecentItem()
                assertEquals(setOf(tor), recovered.favourites)
                assertFalse(recovered.saveFailed)
            }
        }

    /**
     * The tap can arrive before the store has answered, when `editable` still holds its default. The check
     * after the await is what catches that window; without it a read-only store gets a write attempt.
     */
    @Test
    fun aTapMadeBeforeTheStoreAnswersIsDroppedWhenTheStoreTurnsOutReadOnly() =
        runTest {
            val preferences = FakePreferences(setOf(tor))
            preferences.writable = false
            val gate = CompletableDeferred<Unit>()
            preferences.beforeRead = { gate.await() }
            val viewModel = viewModel(preferences = preferences)
            viewModel.uiState.test {
                awaitItem()
                viewModel.toggle(mtl)
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertTrue(preferences.writes.isEmpty())
                assertFalse(expectMostRecentItem().canEdit)
            }
        }

    /**
     * A read that threw leaves us not knowing the selection. Treating that as "nothing is favourited" would
     * let the next tap write a set built from a guess and wipe favourites this process never saw, so an
     * unknown selection is neither shown as empty nor editable.
     */
    @Test
    fun aFailedReadIsUnknownRatherThanAnEmptySelectionAndCannotBeOverwritten() =
        runTest {
            val preferences = FakePreferences(setOf(tor))
            preferences.beforeRead = { throw IOException("preferences unreadable") }
            val viewModel = viewModel(preferences = preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertEquals(32, state.rows.size)
                assertFalse(state.selectionKnown)
                assertFalse(state.canEdit)
                viewModel.toggle(mtl)
                runCurrent()
                assertTrue("a tap must not overwrite a selection that was never read", preferences.writes.isEmpty())
            }
        }

    /**
     * The real repository answers a forward-version file with an empty set and `isWritable() == false`
     * (DataStorePreferencesRepository.getFavourites/requireWritable). The fake reproduces that pair here,
     * because a fake that returns the stored teams in this case would hide the whole problem.
     */
    @Test
    fun aForwardVersionStoreIsUnknownRatherThanEmpty() =
        runTest {
            val preferences = FakePreferences(teams = emptySet())
            preferences.writable = false
            viewModel(preferences = preferences).uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertFalse("an unreadable selection must not be reported as known", state.selectionKnown)
                assertFalse(state.canEdit)
            }
        }

    /**
     * Failing to answer "can this be written?" is not permission to write. Assuming yes here would let an
     * edit land on a selection whose state is unknown.
     */
    @Test
    fun aWritabilityCheckThatThrowsIsTreatedAsNotWritable() =
        runTest {
            val preferences = FakePreferences(setOf(tor))
            preferences.writableThrows = true
            viewModel(preferences = preferences).uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertFalse(state.canEdit)
                assertFalse(state.selectionKnown)
            }
        }

    /** A store written by a newer version answers reads and refuses writes; the tap must not be attempted. */
    @Test
    fun aStoreThatRefusesWritesDisablesEditingAndIgnoresTaps() =
        runTest {
            val preferences = FakePreferences(setOf(tor))
            preferences.writable = false
            val viewModel = viewModel(preferences = preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertFalse(state.canEdit)
                assertEquals(setOf(tor), state.favourites)
                viewModel.toggle(mtl)
                runCurrent()
                assertTrue(preferences.writes.isEmpty())
                // No emission is expected from an ignored tap, so read the current state rather than awaiting one.
                assertEquals(setOf(tor), viewModel.uiState.value.favourites)
            }
        }

    @Test
    fun missingStandingsExposeRetryAndRetryLoadsTeams() =
        runTest {
            val standings = FakeStandings()
            standings.result = Loadable(null, Freshness.Missing, LoadError.Network)
            val viewModel = viewModel(standings)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val missing = expectMostRecentItem()
                assertTrue(missing.rows.isEmpty())
                assertEquals(Freshness.Missing, missing.freshness)
                assertEquals(LoadError.Network, missing.error)
                assertTrue(missing.canRetry)
                standings.result = Loadable(teamStandings(), Freshness.Fresh, null)
                viewModel.retry()
                runCurrent()
                val recovered = expectMostRecentItem()
                assertEquals(32, recovered.rows.size)
                assertFalse(recovered.canRetry)
                assertEquals(listOf(false, false), standings.calls)
            }
        }

    @Test
    fun staleStandingsKeepRowsDuringFailureAndWhileRetryIsSuspended() =
        runTest {
            val standings = FakeStandings()
            val stale = Freshness.Stale(Instant.parse("2026-10-26T23:00:00Z"))
            standings.result = Loadable(teamStandings(), stale, LoadError.Network)
            val viewModel = viewModel(standings)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val saved = expectMostRecentItem()
                assertEquals(32, saved.rows.size)
                assertEquals(stale, saved.freshness)
                assertEquals(LoadError.Network, saved.error)
                assertTrue(saved.canRetry)
                val gate = CompletableDeferred<Unit>()
                standings.beforeLoad = { gate.await() }
                viewModel.retry()
                runCurrent()
                val refreshing = expectMostRecentItem()
                assertEquals(saved.rows, refreshing.rows)
                assertTrue(refreshing.isRefreshing)
                assertFalse(refreshing.canRetry)
                gate.complete(Unit)
                runCurrent()
                assertEquals(saved, expectMostRecentItem())
            }
        }

    @Test
    fun togglingAFavouriteDoesNotMoveItsRow() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val before = expectMostRecentItem()
                val rowBefore = before.rows.indexOfFirst { it.key == tor }
                assertTrue("the fixture must list Toronto below the top", rowBefore > 0)

                viewModel.toggle(tor)
                runCurrent()

                val after = expectMostRecentItem()
                // The tap took effect...
                assertTrue(tor in after.favourites)
                // ...and the list did not rearrange itself under the finger that made it.
                assertEquals(rowBefore, after.rows.indexOfFirst { it.key == tor })
            }
        }

    @Test
    fun theRowOrderCatchesUpOnAnExplicitRetry() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val before = expectMostRecentItem()
                assertTrue(before.rows.indexOfFirst { it.key == tor } > 0)

                viewModel.toggle(tor)
                runCurrent()
                // Still where it was: the selection changed, the order did not.
                assertTrue(expectMostRecentItem().rows.indexOfFirst { it.key == tor } > 0)

                viewModel.retry()
                runCurrent()

                val after = expectMostRecentItem()
                assertEquals("a retry is the user asking for the list to be re-sorted", 0, after.rows.indexOfFirst { it.key == tor })
            }
        }

    private fun viewModel(
        standings: FakeStandings = FakeStandings(),
        preferences: FakePreferences = FakePreferences(),
    ): TeamsViewModel =
        TeamsViewModel(standings, preferences).also { model ->
            stores += ViewModelStore().apply { put("teams", model) }
        }
}

private class FakeStandings : StandingsRepository {
    var result = Loadable(teamStandings().reversed(), Freshness.Fresh, null)
    val calls = mutableListOf<Boolean>()
    var beforeLoad: suspend () -> Unit = {}

    override suspend fun standings(anyGameLive: Boolean): Loadable<List<StandingRow>> {
        calls += anyGameLive
        beforeLoad()
        return result
    }

    override suspend fun seasons(): Loadable<List<Season>> = error("Not used by the picker")
}

private class FakePreferences(
    var teams: Set<TeamKey> = emptySet(),
) : PreferencesRepository {
    var reads = 0
    val writes = mutableListOf<Set<TeamKey>>()
    var beforeRead: suspend () -> Unit = {}
    var beforeWrite: suspend () -> Unit = {}
    var writable = true
    var writableThrows = false

    override suspend fun getFavourites(): Set<TeamKey> {
        reads++
        beforeRead()
        return teams
    }

    override suspend fun setFavourites(teams: Set<TeamKey>) {
        writes += teams
        beforeWrite()
        this.teams = teams
    }

    override suspend fun getLeagueOrder(): List<String> = net.montycasa.roux.domain.policy.LeagueOrder.canonical

    override suspend fun setLeagueOrder(order: List<String>) = Unit

    override suspend fun getFavouritesOnly(): Boolean = true

    override suspend fun setFavouritesOnly(enabled: Boolean) = Unit

    override suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences = TeamAlertPreferences()

    override suspend fun setTeamAlertPreferences(
        team: TeamKey,
        preferences: TeamAlertPreferences,
    ) = Unit

    override suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry? = null

    override suspend fun setNotificationLedgerEntry(
        gameId: String,
        entry: NotificationLedgerEntry,
    ) = Unit

    override suspend fun isWritable(): Boolean {
        if (writableThrows) throw IOException("cannot tell")
        return writable
    }

    // This fake models settings, not the notification ledger: nothing here ever claims an alert.
    override suspend fun claimGoal(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ) = false

    override suspend fun claimStart(
        gameId: String,
        goalBaseline: Int,
        at: Instant,
    ) = false

    override suspend fun claimBaseline(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ) = false

    override suspend fun pruneNotificationLedger(
        olderThan: Instant,
        keep: Set<String>,
    ) = Unit
}

internal fun teamStandings(): List<StandingRow> =
    listOf(
        "ANA" to "Anaheim Ducks",
        "BOS" to "Boston Bruins",
        "BUF" to "Buffalo Sabres",
        "CAR" to "Carolina Hurricanes",
        "CBJ" to "Columbus Blue Jackets",
        "CGY" to "Calgary Flames",
        "CHI" to "Chicago Blackhawks",
        "COL" to "Colorado Avalanche",
        "DAL" to "Dallas Stars",
        "DET" to "Detroit Red Wings",
        "EDM" to "Edmonton Oilers",
        "FLA" to "Florida Panthers",
        "LAK" to "Los Angeles Kings",
        "MIN" to "Minnesota Wild",
        "MTL" to "Montréal Canadiens",
        "NJD" to "New Jersey Devils",
        "NSH" to "Nashville Predators",
        "NYI" to "New York Islanders",
        "NYR" to "New York Rangers",
        "OTT" to "Ottawa Senators",
        "PHI" to "Philadelphia Flyers",
        "PIT" to "Pittsburgh Penguins",
        "SEA" to "Seattle Kraken",
        "SJS" to "San Jose Sharks",
        "STL" to "St. Louis Blues",
        "TBL" to "Tampa Bay Lightning",
        "TOR" to "Toronto Maple Leafs",
        "UTA" to "Utah Mammoth",
        "VAN" to "Vancouver Canucks",
        "VGK" to "Vegas Golden Knights",
        "WPG" to "Winnipeg Jets",
        "WSH" to "Washington Capitals",
    ).map { (abbrev, name) ->
        StandingRow(
            team = TeamRef(TeamKey(abbrev), name),
            gp = 0,
            w = 0,
            l = 0,
            otl = 0,
            pts = 0,
            leagueSequence = 0,
            conferenceSequence = 0,
            divisionSequence = 0,
            wildcardSequence = 0,
            conference = Conference.Eastern,
            division = Division.Atlantic,
        )
    }
