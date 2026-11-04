package net.montycasa.roux.ui.standings

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.respond
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
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.policy.StandingsMode
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.StandingsRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import net.montycasa.roux.ui.scores.ScoreFixture
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

internal fun capturedStandings(): List<StandingRow> = standingsFixtureRows

private val standingsFixtureRows by lazy {
    kotlinx.coroutines.runBlocking {
        val directory =
            java.nio.file.Files
                .createTempDirectory("standings-fixture")
                .toFile()
        val container =
            net.montycasa.roux.AppContainer(
                filesDir = directory.absolutePath.toPath(),
                clock =
                    object : Clock {
                        override fun now() = Instant.parse("2026-10-26T20:00:00Z")
                    },
                engine =
                    io.ktor.client.engine.mock.MockEngine { request ->
                        check(request.url.encodedPath == "/v1/standings/2026-10-26")
                        respond(ScoreFixture.bytes("standings.json"))
                    },
            )
        try {
            requireNotNull(container.standings.standings(anyGameLive = false).data)
        } finally {
            container.close()
            directory.deleteRecursively()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StandingsViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val stores = mutableListOf<ViewModelStore>()
    private val rows = capturedStandings()
    private val now = Instant.parse("2026-10-26T20:00:00Z")

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
    fun loadsThirtyTwoRowsOnceWithNoLiveGameAndSwitchesViewsWithoutRefetch() =
        runTest {
            val repo = FakeStandings()
            val vm = model(repo)
            assertTrue(vm.uiState.value.loading)
            runCurrent()
            // The screen opens on a conference; the league table is the whole-league page this test counts.
            vm.setView(StandingsView.League)
            assertEquals(
                32,
                vm.uiState.value.groups.sections
                    .single()
                    .rows.size,
            )
            assertFalse(vm.uiState.value.loading)
            assertFalse(vm.uiState.value.isRefreshing)
            StandingsView.entries.forEach { view ->
                vm.setView(view)
                assertEquals(view, vm.uiState.value.view)
            }
            assertEquals(listOf(false), repo.calls)
        }

    @Test
    fun theFilterStillFavoursTeamsButTheTableStaysOneRanking() =
        runTest {
            // No favourites block: a filter may still say which teams are mine, but the table is the table, so a
            // league ranking is never split into two lists with the same numbering restarting.
            val prefs = FakePreferences(teams = setOf(rows.first().team.key))
            val vm = model(prefs = prefs)
            runCurrent()
            // The screen opens on a conference; the league ranking is the page this test is about.
            vm.setView(StandingsView.League)
            val shown = vm.uiState.value
            assertTrue(shown.favouritesKnown)
            assertTrue(shown.favouritesOnly)
            assertEquals(listOf("NHL"), shown.groups.sections.map { it.title })
            assertEquals(
                32,
                shown.groups.sections
                    .single()
                    .rows.size,
            )
            assertEquals(
                rows.map { it.team.key },
                shown.groups.sections
                    .single()
                    .rows
                    .map { it.team.key },
            )
        }

    @Test
    fun failedFavouritesReadIsUnknownAndDoesNotPin() =
        runTest {
            val vm = model(prefs = FakePreferences(failRead = true))
            runCurrent()
            assertFalse(vm.uiState.value.favouritesKnown)
            assertEquals(
                16,
                vm.uiState.value.groups.sections
                    .sumOf { it.rows.size },
            )
        }

    @Test
    fun unsupportedFavouritesReadIsUnknownAndDoesNotPin() =
        runTest {
            val vm = model(prefs = FakePreferences(teams = setOf(rows.first().team.key), writable = false))
            runCurrent()
            assertFalse(vm.uiState.value.favouritesKnown)
        }

    @Test
    fun knownEmptyFavouritesDoesNotPin() =
        runTest {
            val vm = model()
            runCurrent()
            assertTrue(vm.uiState.value.favouritesKnown)
        }

    @Test
    fun errorWithoutRowsLeavesGroupsEmpty() =
        runTest {
            listOf(null, emptyList<StandingRow>()).forEach { missing ->
                val vm = model(FakeStandings(Loadable(missing, Freshness.Missing, LoadError.Network)))
                runCurrent()
                assertEquals(LoadError.Network, vm.uiState.value.error)
                assertTrue(
                    vm.uiState.value.groups.sections
                        .isEmpty(),
                )
                assertFalse(vm.uiState.value.loading)
            }
        }

    @Test
    fun aRepositoryThatThrowsBecomesALocalErrorInsteadOfCrashingTheApp() =
        runTest {
            val repo = FakeStandings().apply { failure = IOException("cache is read-only") }
            val vm = model(repo)
            runCurrent()
            assertEquals(LoadError.Local, vm.uiState.value.error)
            assertFalse(vm.uiState.value.loading)
            assertFalse(vm.uiState.value.isRefreshing)
            assertTrue(
                vm.uiState.value.groups.sections
                    .isEmpty(),
            )
        }

    @Test
    fun aThrowAfterStaleRowsKeepsTheirTimestampAndRowsOnScreen() =
        runTest {
            val stale = Freshness.Stale(now)
            val repo = FakeStandings(result = Loadable(rows, stale, LoadError.Network))
            val vm = model(repo)
            runCurrent()
            assertEquals(stale, vm.uiState.value.stale)
            repo.failure = IOException("cache is read-only")
            vm.refresh()
            runCurrent()
            // A failed refresh does not retract what the screen already knew about those rows.
            assertEquals(stale, vm.uiState.value.stale)
            assertEquals(LoadError.Local, vm.uiState.value.error)
            // The Eastern page keeps all sixteen of its teams, whatever the refresh did.
            assertEquals(
                16,
                vm.uiState.value.groups.sections
                    .sumOf { it.rows.size },
            )
        }

    @Test
    fun staleRowsKeepTheRepositoryTimestampAndRefreshError() =
        runTest {
            val stale = Freshness.Stale(now)
            val vm = model(FakeStandings(Loadable(rows, stale, LoadError.Network)))
            runCurrent()
            assertEquals(stale, vm.uiState.value.stale)
            assertEquals(LoadError.Network, vm.uiState.value.error)
            vm.setView(StandingsView.League)
            assertEquals(
                32,
                vm.uiState.value.groups.sections
                    .single()
                    .rows.size,
            )
        }

    @Test
    fun refreshKeepsRowsAndViewWhileLoadingThenPublishesNewRows() =
        runTest {
            val repo = FakeStandings()
            val vm = model(repo)
            runCurrent()
            vm.setMode(StandingsMode.Division)
            val old = vm.uiState.value.groups
            val pending = CompletableDeferred<Loadable<List<StandingRow>>>()
            repo.pending = pending
            vm.refresh()
            runCurrent()
            assertTrue(vm.uiState.value.isRefreshing)
            assertFalse(vm.uiState.value.loading)
            assertEquals(old, vm.uiState.value.groups)
            vm.refresh() // A second gesture must not run a competing request.
            pending.complete(Loadable(rows.take(1), Freshness.Fresh, null))
            runCurrent()
            assertEquals(listOf(false, false), repo.calls)
            assertEquals(StandingsMode.Division, vm.uiState.value.mode)
            assertEquals(
                1,
                vm.uiState.value.groups.sections
                    .sumOf { it.rows.size },
            )
            assertFalse(vm.uiState.value.isRefreshing)
        }

    @Test
    fun everyViewIsGroupedSoTheNeighbouringTableCanDraw() =
        runTest {
            val vm = model()
            runCurrent()

            // A swipe shows the next view's table while the finger is still moving, so all four have to be grouped
            // up front - not only the one being viewed.
            assertEquals(
                StandingsView.entries.toSet(),
                vm.uiState.value.groupsByView.keys,
            )
        }

    private fun model(
        repo: FakeStandings = FakeStandings(),
        prefs: FakePreferences = FakePreferences(),
    ) = StandingsViewModel(
        repo,
        prefs,
    ).also { vm ->
        stores += ViewModelStore().apply { put("standings", vm) }
    }

    private class FakeStandings(
        var result: Loadable<List<StandingRow>> = Loadable(capturedStandings(), Freshness.Fresh, null),
        /** When set, the repository throws instead of answering: a cache it cannot write, for instance. */
        var failure: Exception? = null,
    ) : StandingsRepository {
        val calls = mutableListOf<Boolean>()
        var pending: CompletableDeferred<Loadable<List<StandingRow>>>? = null

        override suspend fun standings(anyGameLive: Boolean): Loadable<List<StandingRow>> {
            calls += anyGameLive
            failure?.let { throw it }
            return pending?.await()
                ?: result
        }

        override suspend fun seasons(): Loadable<List<Season>> = error("not needed")
    }

    private class FakePreferences(
        var teams: Set<TeamKey> = emptySet(),
        var only: Boolean = true,
        val failRead: Boolean = false,
        val writable: Boolean = true,
    ) : PreferencesRepository {
        override suspend fun getThemeMode() = ThemeMode.System

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun getFavourites(): Set<TeamKey> {
            if (failRead) error("unreadable")
            return teams
        }

        override suspend fun getLeagueOrder(): List<String> = net.montycasa.roux.domain.policy.LeagueOrder.canonical

        override suspend fun setLeagueOrder(order: List<String>) = Unit

        override suspend fun getFavouritesOnly() = only

        override suspend fun isWritable() = writable

        override suspend fun setFavourites(teams: Set<TeamKey>) = error("unexpected write")

        override suspend fun setFavouritesOnly(enabled: Boolean) = error("unexpected write")

        override suspend fun getTeamAlertPreferences(team: TeamKey) = TeamAlertPreferences()

        override suspend fun setTeamAlertPreferences(
            team: TeamKey,
            preferences: TeamAlertPreferences,
        ) = Unit

        override suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry? = null

        override suspend fun setNotificationLedgerEntry(
            gameId: String,
            entry: NotificationLedgerEntry,
        ) = Unit

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

        override suspend fun claimFinal(
            gameId: String,
            at: Instant,
        ) = false

        override suspend fun claimIntermission(
            gameId: String,
            period: Int,
            at: Instant,
        ) = false

        override suspend fun claimUpcoming(
            gameId: String,
            at: Instant,
        ) = false

        override suspend fun pruneNotificationLedger(
            olderThan: Instant,
            keep: Set<String>,
        ) = Unit
    }

    @Test
    fun opensOnTheEasternPlayoffPicture() =
        runTest {
            val vm = model(FakeStandings())
            runCurrent()
            assertEquals(StandingsView.Eastern, vm.uiState.value.view)
            assertEquals(StandingsMode.Playoffs, vm.uiState.value.mode)
            // And the grouping on screen belongs to that page, not to the league.
            assertEquals(
                listOf("Atlantic", "Metropolitan", "Wild Card"),
                vm.uiState.value.groups.sections
                    .map { it.title },
            )
        }
}
