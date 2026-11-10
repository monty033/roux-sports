package net.montycasa.roux.ui.scores

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.policy.FavoritesHint
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ScoresViewModelTest {
    private val testScheduler = TestCoroutineScheduler()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun exposedRowsUseStateThenFavoritesThenStartOrder() =
        runTest {
            val final = game(id = "final", state = GameState.Final, home = "MTL")
            val upcoming = game(id = "upcoming", state = GameState.Pre)
            val live = game(id = "live", home = "BOS").copy(away = TeamRef(TeamKey("NYR")))
            val favoriteLive = game(id = "favorite-live", home = "MTL").copy(startTimeUtc = origin)
            val early = live.copy(id = GameId("early"), startTimeUtc = origin - 1.hours)
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, listOf(final, upcoming, live, favoriteLive, early)) }
            val preferences = FakePreferencesRepository(setOf(TeamKey("MTL")), storedOnly = false)
            val viewModel = viewModel(scores, preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(listOf(favoriteLive, early, live, upcoming, final), viewModel.uiState.value.rows)
                viewModel.setFavouritesOnly(true)
                runCurrent()
                assertEquals(listOf(favoriteLive, upcoming, final), viewModel.uiState.value.rows)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun liveGamePollsAtTwentyFiveSeconds() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(120_000)
                runCurrent()

                assertEquals(5, scores.calls.size)
                assertEquals(listOf(0L, 25_000L, 50_000L, 75_000L, 100_000L), scores.times)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aBlockedHostStopsThePollInsteadOfReaskingEveryTwentyFiveSeconds() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val blocked =
                Loadable(
                    ScoreDay("2026-10-26", listOf(game(date = "2026-10-26")), emptyList(), 0),
                    Freshness.Stale(Instant.parse("2026-10-26T20:00:00Z")),
                    LoadError.Blocked("evil.example"),
                )
            scores.respond = { _, _ -> blocked }
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(120_000)
                runCurrent()

                // A live slate would otherwise schedule five loads in two minutes at the 25 s cadence.
                assertEquals(1, scores.calls.size)
                assertEquals(LoadError.Blocked("evil.example"), viewModel.uiState.value.error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun failuresBackOffAndAnEmptyColdStartKeepsTrying() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val offline = Loadable<ScoreDay>(null, Freshness.Missing, LoadError.Network)
            scores.respond = { _, _ -> offline }
            val viewModel = viewModel(scores, jitter = { 0.5 })

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(400_000)
                runCurrent()

                // 25 s, doubled each failure: the loop must never stop just because there is nothing cached to reason from.
                assertEquals(listOf(0L, 25_000L, 75_000L, 175_000L, 375_000L), scores.times)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRetryAfterOutranksTheBackoff() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val limited =
                Loadable<ScoreDay>(null, Freshness.Missing, LoadError.Http(429, 120.seconds))
            scores.respond = { _, _ -> limited }
            val viewModel = viewModel(scores, jitter = { 0.5 })

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(130_000)
                runCurrent()

                assertEquals(listOf(0L, 120_000L), scores.times)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aSuccessResetsTheFailureCadence() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            var failing = true
            scores.respond = { date, _ ->
                if (failing) Loadable(null, Freshness.Missing, LoadError.Network) else freshDay(date)
            }
            val viewModel = viewModel(scores, jitter = { 0.5 })

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(25_000)
                runCurrent()
                // Two failures in: the wait in force is 50 s, so the next load lands at 75 s and succeeds.
                failing = false
                advanceTimeBy(80_000)
                runCurrent()

                // After the success the cadence is 25 s again (75 000 -> 100 000), not the 100 s a third
                // consecutive failure would have waited.
                assertEquals(listOf(0L, 25_000L, 75_000L, 100_000L), scores.times)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aThrowingFavouriteWriteDoesNotTakeTheAppDownOrLockTheNextOne() =
        runTest {
            val preferences = FakePreferencesRepository().apply { failOnlyWrites = true }
            val viewModel = viewModel(FakeScoresRepository(testScheduler), preferences)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.setFavouritesOnly(false)
                runCurrent()

                preferences.failOnlyWrites = false
                viewModel.setFavouritesOnly(true)
                runCurrent()

                // The refused write neither crashed the scope nor left the mutex held.
                assertEquals(listOf(true), preferences.onlyWrites)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun allFinalPastDateDoesNotPoll() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, listOf(game(date = date, state = GameState.Final))) }
            val viewModel = viewModel(scores)
            viewModel.selectDate("2026-10-25")

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(120_000)
                runCurrent()

                assertEquals(listOf(0L), scores.times)
                assertEquals("2026-10-25", expectMostRecentItem().selectedDate)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun pollingStopsFiveSecondsAfterTheCollectorLeaves() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(listOf(0L), scores.times)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(4_900)
            runCurrent()
            assertEquals(listOf(0L), scores.times)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf(0L), scores.times)
        }

    @Test
    fun pollingContinuesInsideTheFiveSecondGrace() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)
            var cancelledAt: Long? = null

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                scores.respond = { date, _ ->
                    try {
                        delay(60_000)
                        freshDay(date)
                    } finally {
                        cancelledAt = testScheduler.currentTime
                    }
                }
                advanceTimeBy(24_000)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf(0L, 25_000L), scores.times)
            advanceTimeBy(3_999)
            runCurrent()
            assertEquals(null, cancelledAt)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(29_000L, cancelledAt)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf(0L, 25_000L), scores.times)
        }

    @Test
    fun resubscribingUsesTheCacheAwareLoad() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertTrue(expectMostRecentItem().rows.isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf(0L), scores.times)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(listOf(0L, 60_000L), scores.times)
                assertEquals("resubscription must stay cache-aware, not forced", emptyList<Long>(), scores.refreshCalls.map { it.at })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun pullToRefreshBypassesTheFreshCache() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(1_000)
                viewModel.onPullToRefresh()
                runCurrent()

                // The pull is exactly one forced load, and it is not a cache-aware one.
                assertEquals("a pull must ask the repository to refresh", listOf(1_000L), scores.refreshCalls.map { it.at })
                assertEquals("a pull must not go through loadDay's fresh-cache short-circuit", listOf(0L), scores.times)
                // The forced load restarts the cadence from itself, and that cadence is polling: cache-aware.
                advanceTimeBy(25_000)
                runCurrent()
                assertEquals(listOf(0L, 26_000L), scores.times)
                assertEquals("polling ticks must not force", listOf(1_000L), scores.refreshCalls.map { it.at })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedRefreshKeepsTheRowsAndMarksThemStale() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val original = expectMostRecentItem()
                val stale = Freshness.Stale(origin)
                scores.respond = { date, _ -> Loadable(freshDay(date).data, stale, LoadError.Network) }
                viewModel.refresh()
                runCurrent()

                val failed = expectMostRecentItem()
                assertTrue(failed.rows.isNotEmpty())
                assertEquals(original.rows, failed.rows)
                assertEquals(LoadError.Network, failed.error)
                assertEquals(stale, failed.freshness)
                assertFalse(failed.isRefreshing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun favouritesOnlyDefaultsToTrueAndToggles() =
        runTest {
            val favourite = game(id = "favourite", home = "MTL")
            val other = game(id = "other", home = "NYR")
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, listOf(other, favourite)) }
            val preferences = FakePreferencesRepository(teams = setOf(TeamKey("MTL")))
            val viewModel = viewModel(scores, preferences)

            viewModel.uiState.test {
                assertTrue(awaitItem().favouritesOnly)
                runCurrent()
                val filtered = expectMostRecentItem()
                assertTrue(filtered.favouritesOnly)
                assertEquals(listOf(favourite), filtered.rows)

                viewModel.setFavouritesOnly(false)
                runCurrent()
                val all = expectMostRecentItem()
                assertFalse(all.favouritesOnly)
                assertEquals(listOf(favourite, other), all.rows)
                assertEquals(false, preferences.storedOnly)
                assertEquals(listOf(false), preferences.onlyWrites)
                assertEquals(1, scores.calls.size)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(6_000)
            runCurrent()
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertFalse(viewModel.uiState.value.favouritesOnly)
                assertEquals(listOf(favourite, other), viewModel.uiState.value.rows)
                cancelAndIgnoreRemainingEvents()
            }

            // Persistence also applies to a new ViewModel, beyond the retained in-memory toggle.
            val recreated = viewModel(scores, preferences)
            recreated.uiState.test {
                awaitItem()
                runCurrent()
                assertFalse(expectMostRecentItem().favouritesOnly)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun emptyDayDoesNotSpinAndRefreshRestartsIt() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, emptyList()) }
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(120_000)
                runCurrent()
                assertEquals(listOf(0L), scores.times)
                assertTrue(expectMostRecentItem().rows.isEmpty())

                viewModel.refresh()
                runCurrent()
                // refresh() is the user asking, so it forces: refreshDay, not the cache-aware loadDay.
                assertEquals(listOf(0L), scores.times)
                assertEquals(listOf(120_000L), scores.refreshCalls.map { it.at })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun changingDateRestartsAStoppedLoop() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, emptyList()) }
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.selectDate("2026-10-27")
                runCurrent()

                assertEquals(listOf("2026-10-26", "2026-10-27"), scores.calls.map { it.date })
                assertEquals("2026-10-27", expectMostRecentItem().selectedDate)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aDayIsStillKnownAfterItStopsBeingABeside() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            // A day index wide enough for neighbours to exist at all: without one nothing sits beside the selection.
            val index = listOf("2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29").map { DateIndexEntry(it, 1) }
            scores.respond = { date, _ ->
                Loadable(ScoreDay(date, listOf(game(date = date)), index, 0), Freshness.Fresh, null)
            }
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.selectDate("2026-10-27")
                runCurrent()
                val before = expectMostRecentItem().rowsByDate.keys
                assertTrue("nothing was known to carry over: $before", before.isNotEmpty())

                // One more day along: what was beside 27 is no longer beside 28, and must survive. Dropping it is what
                // left the pager drawing one blank frame between the settle and the load landing.
                viewModel.selectDate("2026-10-28")
                runCurrent()

                val after = expectMostRecentItem().rowsByDate.keys
                assertEquals("$before -> $after", before, before.intersect(after))
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The rows beside the selection are cached by date, so a changed settings snapshot has to rebuild them.
     *
     * Those rows are what a page draws the moment it settles, before any load for that day lands. They are
     * filtered and ordered once, from the favourites current at that moment: if a favourite is chosen later
     * and nothing rebuilds them, the neighbour keeps showing the old selection while [ScoresUiState] reports
     * the new one - including the favourite set the accent is drawn from. The dates and the selection are
     * exactly as they were here, so only the settings moved.
     */
    @Test
    fun aChangedFavouriteSetRebuildsTheNeighbourRowsItAlreadyCached() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val index =
                listOf("2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29")
                    .map { DateIndexEntry(it, 1) }
            scores.respond = { date, _ ->
                Loadable(ScoreDay(date, listOf(game(date = date)), index, 0), Freshness.Fresh, null)
            }
            // The day beside the selection, already on disk: one game with MTL, one with neither.
            val withMtl = game(date = "2026-10-28", id = "mtl-game", home = "MTL")
            val withBos = game(date = "2026-10-28", id = "bos-game", home = "BOS")
            scores.cache("2026-10-28", ScoreDay("2026-10-28", listOf(withMtl, withBos), index, 0))
            val preferences = FakePreferencesRepository(teams = setOf(TeamKey("MTL")))
            val viewModel = viewModel(scores, preferences)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.selectDate("2026-10-27")
                runCurrent()
                val warmed = expectMostRecentItem()
                assertEquals(setOf(TeamKey("MTL")), warmed.favouriteTeams)
                assertEquals(listOf(withMtl), warmed.rowsByDate["2026-10-28"])

                // A favourite chosen elsewhere, with the dates and the selection exactly as they were.
                preferences.emitFavourites(setOf(TeamKey("BOS")))
                runCurrent()
                val reFiltered = expectMostRecentItem()
                assertEquals(setOf(TeamKey("BOS")), reFiltered.favouriteTeams)
                assertEquals(
                    "the cached neighbour must be filtered by the settings uiState now exposes",
                    listOf(withBos),
                    reFiltered.rowsByDate["2026-10-28"],
                )

                // The filter too: all games now, favourites first, still built from one snapshot.
                viewModel.setFavouritesOnly(false)
                runCurrent()
                val unfiltered = expectMostRecentItem()
                assertFalse(unfiltered.favouritesOnly)
                assertEquals(listOf(withBos, withMtl), unfiltered.rowsByDate["2026-10-28"])
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The pager must never see rows filtered under a favourite set the state no longer reports.
     *
     * The neighbour's rows used to be rebuilt by a flow that collected the settings a second time and re-read
     * the cache whenever they changed, so for as long as that read took the state already carried the new
     * favourites while the rows beside it still carried the old filter: the accent follows the first, the pager
     * falls back to the second, and a favourite's own game was drawn without its stripe. That page's read is
     * held open below to make that window observable - on a real disk it is short, but it is never zero.
     */
    @Test
    fun aFavouriteChangeReachesTheNeighbourPageWhileTheCacheReadIsStillHeldOpen() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val index =
                listOf("2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29")
                    .map { DateIndexEntry(it, 1) }
            scores.respond = { date, _ ->
                Loadable(ScoreDay(date, listOf(game(date = date)), index, 0), Freshness.Fresh, null)
            }
            // The neighbour's own slate: one game with MTL, one with BOS, so the two favourite sets disagree about
            // which of them is a favourite's game at all.
            val withMtl = game(date = "2026-10-28", id = "mtl-game", home = "MTL")
            val withBos = game(date = "2026-10-28", id = "bos-game", home = "BOS")
            scores.cache("2026-10-28", ScoreDay("2026-10-28", listOf(withMtl, withBos), index, 0))
            val preferences = FakePreferencesRepository(teams = setOf(TeamKey("MTL")))
            val viewModel = viewModel(scores, preferences)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.selectDate("2026-10-27")
                runCurrent()
                val warmed = expectMostRecentItem()
                assertEquals(setOf(TeamKey("MTL")), warmed.favouriteTeams)
                assertEquals(listOf(withMtl), warmed.rowsByDate["2026-10-28"])

                // The neighbour has been read once. From here a read of that page waits, so the window between a
                // settings change and whatever used to follow it stays open for as long as the test needs - and
                // only that page is held, which is the one whose rows would otherwise stay stale.
                val gate = CompletableDeferred<Unit>()
                val readsWhenWarm = scores.cacheReads.size
                scores.cacheGateDate = "2026-10-28"
                scores.cacheGate = gate
                try {
                    preferences.emitFavourites(setOf(TeamKey("BOS")))
                    runCurrent()
                    val reFanned = expectMostRecentItem()
                    assertEquals(
                        "the state the pager reads must already report the new favourites",
                        setOf(TeamKey("BOS")),
                        reFanned.favouriteTeams,
                    )
                    assertEquals(
                        "the neighbour must already be filtered by those favourites, not the ones they replaced",
                        listOf(withBos),
                        reFanned.rowsByDate["2026-10-28"],
                    )

                    // The filter itself, with the cache still held open: both games, the favourite's first.
                    viewModel.setFavouritesOnly(false)
                    runCurrent()
                    val unfiltered = expectMostRecentItem()
                    assertFalse(unfiltered.favouritesOnly)
                    assertEquals(listOf(withBos, withMtl), unfiltered.rowsByDate["2026-10-28"])
                } finally {
                    // Release in every path, so a failed assertion cannot leave the test waiting on the gate.
                    scores.cacheGateDate = null
                    scores.cacheGate = null
                    gate.complete(Unit)
                }

                // A settings change re-filters what is already remembered; it never sends the reader back to disk.
                assertEquals(
                    "a changed favourite set must not re-read the cache",
                    readsWhenWarm,
                    scores.cacheReads.size,
                )
                assertEquals(
                    "the held page's gate was never reached",
                    emptyList<String>(),
                    scores.cacheGateEntered,
                )

                runCurrent()
                val settled = viewModel.uiState.value
                assertEquals(setOf(TeamKey("BOS")), settled.favouriteTeams)
                assertFalse(settled.favouritesOnly)
                assertEquals(listOf(withBos, withMtl), settled.rowsByDate["2026-10-28"])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun darkLaunchSelectsOpeningNightAndNamesTheSeasonThatOpens() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val opener = game(id = "opener", date = "2026-09-29")
            scores.respond = { date, _ ->
                when (date) {
                    "2026-07-20" -> offSeasonDay(date)
                    else -> freshDay(date, listOf(opener))
                }
            }
            scores.resolveOpeningNight = { "2026-09-29" }
            val viewModel =
                viewModel(
                    scores,
                    clock = FixedClock(Instant.parse("2026-07-20T16:00:00Z")),
                )

            viewModel.uiState.test {
                awaitItem()
                runCurrent()

                assertEquals(listOf("2026-07-20", "2026-09-29"), scores.calls.map { it.date })
                assertEquals(listOf("2026-09-19"), scores.openingNightCalls)
                val state = expectMostRecentItem()
                assertEquals("2026-09-29", state.selectedDate)
                assertEquals("2026-06-14", state.offSeason?.lastGameDate)
                assertEquals("2026-09-19", state.offSeason?.nextGameDate)
                assertEquals("2025–26", state.offSeason?.seasonLabel)
                assertEquals("2026-09-29", state.offSeason?.openingNight?.date)
                assertEquals("2026–27", state.offSeason?.openingNight?.seasonLabel)
                assertEquals(listOf(opener), state.rows)
                assertNull(state.error)
                assertFalse(state.isRefreshing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun anUnknownOpeningNightFallsBackToTheLastGameDayAndShowsEveryGameDespiteFavourites() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val favourite = game(id = "favourite", home = "MTL")
            val other =
                game(id = "final", home = "FLA").copy(away = TeamRef(TeamKey("EDM")))
            scores.respond = { date, _ ->
                when (date) {
                    "2026-07-20" -> offSeasonDay(date)
                    else -> freshDay(date, listOf(favourite, other))
                }
            }
            scores.resolveOpeningNight = { null }
            val preferences = FakePreferencesRepository(teams = setOf(TeamKey("MTL")))
            val viewModel =
                viewModel(
                    scores,
                    preferences,
                    clock = FixedClock(Instant.parse("2026-07-20T16:00:00Z")),
                )

            viewModel.uiState.test {
                awaitItem()
                runCurrent()

                assertEquals(listOf("2026-07-20", "2026-06-14"), scores.calls.map { it.date })
                assertEquals(listOf("2026-09-19"), scores.openingNightCalls)
                val state = expectMostRecentItem()
                assertEquals("2026-06-14", state.selectedDate)
                assertEquals("2026-06-14", state.offSeason?.lastGameDate)
                assertEquals("2026-09-19", state.offSeason?.nextGameDate)
                assertEquals("2025–26", state.offSeason?.seasonLabel)
                assertNull(state.offSeason?.openingNight)
                // The card's rule: the Final is shown even though MTL is the only favourite.
                assertEquals(listOf(favourite, other), state.rows)
                assertNull(state.favoritesHint)
                assertNull(state.error)
                assertFalse(state.isRefreshing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aDateChosenBeforeTheFirstLoadCompletesBeatsTheOpener() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ ->
                delay(1_000)
                offSeasonDay(date)
            }
            scores.resolveOpeningNight = { "2026-09-29" }
            val viewModel =
                viewModel(
                    scores,
                    clock = FixedClock(Instant.parse("2026-07-20T16:00:00Z")),
                )

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                // The first load is still asleep when the viewer picks a day.
                viewModel.selectDate("2026-07-21")
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()

                assertEquals("2026-07-21", viewModel.uiState.value.selectedDate)
                assertEquals("2026-07-21", expectMostRecentItem().selectedDate)
                assertEquals(emptyList<String>(), scores.openingNightCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun openingNightIsResolvedOnlyOncePerSession() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ ->
                when (date) {
                    "2026-07-20" -> offSeasonDay(date)
                    else -> offSeasonDay(date)
                }
            }
            scores.resolveOpeningNight = { "2026-09-29" }
            val viewModel =
                viewModel(
                    scores,
                    clock = FixedClock(Instant.parse("2026-07-20T16:00:00Z")),
                )

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals("2026-09-29", expectMostRecentItem().selectedDate)
                assertEquals(1, scores.openingNightCalls.size)

                // A later dark day in the same session must not ask the schedule walk again.
                viewModel.selectDate("2026-07-21")
                runCurrent()

                assertEquals("2026-07-21", expectMostRecentItem().selectedDate)
                assertEquals(1, scores.openingNightCalls.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun midnightDoesNotRunTheOffSeasonSwitchAgain() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ ->
                when (date) {
                    // The launch's dark day lands on 14 June.
                    "2026-07-20" -> offSeasonDay(date)

                    // Make the landed day's feed dark too: a re-run of the switch would move it to 13 June.
                    "2026-06-14" -> offSeasonDay(date, previous = "2026-06-13")

                    else -> offSeasonDay(date)
                }
            }
            val clock = SchedulerClock(testScheduler, Instant.parse("2026-07-21T03:59:59Z"))
            val viewModel = viewModel(scores, clock = clock)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals("2026-06-14", expectMostRecentItem().selectedDate)

                advanceTimeBy(1_000)
                runCurrent()

                val afterMidnight = expectMostRecentItem()
                assertEquals("2026-06-14", afterMidnight.selectedDate)
                assertEquals("2026-07-21", afterMidnight.today)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aMidnightReloadDoesNotOverrideADatePickedAfterTheLaunch() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ ->
                when (date) {
                    "2026-07-20" -> offSeasonDay(date)
                    "2026-06-14" -> freshDay(date, listOf(game(date = date)))
                    else -> offSeasonDay(date)
                }
            }
            val clock = SchedulerClock(testScheduler, Instant.parse("2026-07-21T03:59:59Z"))
            val viewModel = viewModel(scores, clock = clock)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals("2026-06-14", expectMostRecentItem().selectedDate)
                viewModel.selectDate("2026-06-15")
                runCurrent()

                advanceTimeBy(1_000)
                runCurrent()

                val afterMidnight = expectMostRecentItem()
                assertEquals("2026-06-15", afterMidnight.selectedDate)
                assertEquals("2026-07-21", afterMidnight.today)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun newYorkMidnightRestartsAStoppedLoop() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, emptyList()) }
            // UTC is already October 27; New York is still October 26 for one second.
            val clock = SchedulerClock(testScheduler, Instant.parse("2026-10-27T03:59:59Z"))
            val viewModel = viewModel(scores, clock = clock)

            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals("2026-10-26", initial.selectedDate)
                assertEquals("2026-10-26", initial.today)
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()

                assertEquals(listOf(0L, 1_000L), scores.times)
                assertEquals(listOf("2026-10-26", "2026-10-27"), scores.calls.map { it.today })
                val afterMidnight = expectMostRecentItem()
                assertEquals("2026-10-26", afterMidnight.selectedDate)
                assertEquals("2026-10-27", afterMidnight.today)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * A live game refreshes every 25 seconds; a progress indicator on each of those background fetches
     * would flash on the primary screen all evening. Only a new upstream start announces progress.
     */
    @Test
    fun aCadenceTickDoesNotMarkRefreshing() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)
            val progress = mutableListOf<Boolean>()
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect { progress += it.isRefreshing }
                }
            runCurrent()
            assertTrue("expected the first load", scores.calls.isNotEmpty())

            progress.clear()
            advanceTimeBy(60_000)
            runCurrent()

            assertTrue("expected cadence ticks, not one load", scores.calls.size >= 3)
            assertEquals("no cadence tick may announce progress", emptyList<Boolean>(), progress.filter { it })
            collector.cancel()
        }

    @Test
    fun aSlowRefreshShowsProgressAlongsideExistingRows() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val original = expectMostRecentItem()
                scores.respond = { date, _ ->
                    delay(1_000)
                    freshDay(date)
                }
                viewModel.refresh()
                runCurrent()
                val refreshing = expectMostRecentItem()
                assertTrue(refreshing.isRefreshing)
                assertEquals(original.rows, refreshing.rows)

                advanceTimeBy(1_000)
                runCurrent()
                assertFalse(expectMostRecentItem().isRefreshing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun noFavouritesShowsThePolicyHintAndAllRows() =
        runTest {
            val viewModel = viewModel(FakeScoresRepository(testScheduler))
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = expectMostRecentItem()
                assertTrue(state.favouritesOnly)
                assertEquals(FavoritesHint.PickTeams, state.favoritesHint)
                assertTrue(state.rows.isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The accent reads the favourite teams themselves, so the screen is handed the same set the filter
     * and the ordering used. A set of flags per row could not survive a change made in the Teams tab.
     */
    @Test
    fun theStateCarriesTheFavouriteTeamsThatFilteredAndOrderedTheRows() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, listOf(game(date = date, home = "MTL"))) }
            val preferences = FakePreferencesRepository(teams = setOf(TeamKey("MTL"), TeamKey("TOR")))
            val viewModel = viewModel(scores, preferences)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(setOf(TeamKey("MTL"), TeamKey("TOR")), expectMostRecentItem().favouriteTeams)

                // A favourite chosen elsewhere reaches the screen the way it reaches the filter.
                preferences.emitFavourites(setOf(TeamKey("BOS")))
                runCurrent()
                assertEquals(setOf(TeamKey("BOS")), expectMostRecentItem().favouriteTeams)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun headerAndDrawerMetadataUseTheLoadedUnfilteredDayWithoutAnExtraRequest() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ ->
                freshDay(
                    date,
                    listOf(
                        game(date = date, state = GameState.Live()),
                        game(
                            date = date,
                            state = GameState.Live(critical = true),
                        ).copy(id = GameId("other"), home = TeamRef(TeamKey("BOS")), away = TeamRef(TeamKey("NYR"))),
                    ),
                )
            }
            val preferences = FakePreferencesRepository(setOf(TeamKey("TOR")), storedOnly = true)
            val viewModel = viewModel(scores, preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val filtered = expectMostRecentItem()
                assertEquals(1, filtered.rows.size)
                assertEquals(2, filtered.liveCount)
                assertEquals(1, filtered.favouriteCount)
                assertEquals(filtered.selectedDate, filtered.loadedDate)
                viewModel.setFavouritesOnly(false)
                runCurrent()
                val all = expectMostRecentItem()
                assertEquals(2, all.rows.size)
                assertEquals(2, all.liveCount)
                assertEquals(1, scores.calls.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun leagueOrderWritesAreSerializedAndRestoredByANewViewModel() =
        runTest {
            val preferences = FakePreferencesRepository()
            val scores = FakeScoresRepository(testScheduler)
            preferences.orderWriteDelay = { if (it.first() == "nfl") delay(100) }
            val viewModel = viewModel(scores, preferences)
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(listOf("nhl", "nfl", "mlb"), expectMostRecentItem().leagueOrder)
                viewModel.setLeagueOrder(listOf("nfl", "nhl", "mlb"))
                viewModel.setLeagueOrder(listOf("mlb", "nfl", "nhl"))
                runCurrent()
                assertTrue(preferences.orderWrites.isEmpty())
                assertEquals(listOf("mlb", "nfl", "nhl"), expectMostRecentItem().leagueOrder)
                advanceTimeBy(100)
                runCurrent()
                assertEquals(listOf(listOf("nfl", "nhl", "mlb"), listOf("mlb", "nfl", "nhl")), preferences.orderWrites)
                assertEquals(preferences.storedOrder, viewModel.uiState.value.leagueOrder)
                cancelAndIgnoreRemainingEvents()
            }
            val recreated = viewModel(scores, preferences)
            recreated.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(listOf("mlb", "nfl", "nhl"), expectMostRecentItem().leagueOrder)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun selectingADayPrefetchesItOnceTheSelectionSettles() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, emptyList()) }
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.selectDate("2026-10-27")
                runCurrent()
                // Nothing is warmed while the finger is still moving.
                advanceTimeBy(400)
                runCurrent()
                assertEquals(emptyList<String>(), scores.prefetchCalls.map { it.date })

                advanceTimeBy(200)
                runCurrent()
                assertEquals(listOf("2026-10-27"), scores.prefetchCalls.map { it.date })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun flickingThroughDaysPrefetchesOnlyWhereTheUserStopped() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { date, _ -> freshDay(date, emptyList()) }
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                viewModel.selectDate("2026-10-28")
                runCurrent()
                advanceTimeBy(200)
                viewModel.selectDate("2026-10-29")
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()

                assertEquals("only the day they stopped on", listOf("2026-10-29"), scores.prefetchCalls.map { it.date })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedLoadWithNoDataDoesNotPrefetch() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            scores.respond = { _, _ -> Loadable(null, Freshness.Missing, LoadError.Network) }
            val viewModel = viewModel(scores)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(2_000)
                runCurrent()

                // The neighbours would fail the same way; a request whose only outcome is an error is battery for nothing.
                assertEquals(emptyList<String>(), scores.prefetchCalls.map { it.date })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun drawerStateFollowsPreferenceEmissions() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val preferences = FakePreferencesRepository()
            val viewModel = viewModel(scores, preferences)

            viewModel.drawerState.test {
                awaitItem()
                runCurrent()
                assertEquals(0, viewModel.drawerState.value.favouriteCount)

                preferences.emitFavourites(setOf(TeamKey("TOR"), TeamKey("MTL")))
                runCurrent()
                assertEquals(2, viewModel.drawerState.value.favouriteCount)

                viewModel.setLeagueOrder(listOf("mlb", "nhl", "nfl"))
                runCurrent()
                assertEquals(listOf("mlb", "nhl", "nfl"), viewModel.drawerState.value.leagueOrder)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun drawerStateNeverStartsAPoll() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val viewModel = viewModel(scores, FakePreferencesRepository())

            viewModel.drawerState.test {
                awaitItem()
                runCurrent()
                advanceTimeBy(60_000)
                runCurrent()

                assertTrue("the drawer must not be a reason to poll", scores.calls.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun drawerSelectionFollowsTheFilter() =
        runTest {
            val scores = FakeScoresRepository(testScheduler)
            val preferences = FakePreferencesRepository()
            val viewModel = viewModel(scores, preferences)

            viewModel.drawerState.test {
                awaitItem()
                runCurrent()

                viewModel.selectLeague("myTeams")
                runCurrent()
                assertEquals("myTeams", viewModel.drawerState.value.selectedLeagueId)
                assertTrue(preferences.onlyWrites.last())

                viewModel.selectLeague("nfl")
                runCurrent()
                assertEquals("nfl", viewModel.drawerState.value.selectedLeagueId)
                assertFalse(preferences.onlyWrites.last())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The day the scores screen browses is the device's civil day, hour by hour across the weekend the clocks change.
     *
     * A table rather than one instant: the failure this guards against is a boundary that moves with DST, and a single
     * case would sit comfortably inside one offset.
     */
    @Test
    fun todayIsTheDeviceCivilDayAcrossTheWholeDstWeekend() =
        runTest {
            // Los Angeles, not the league's zone: it transitions on the same night and sits three hours away, so a
            // table run in Toronto would agree with the bug it is meant to catch.
            val zone = TimeZone.of("America/Los_Angeles")
            val start = Instant.parse("2026-10-31T12:00:00Z")
            repeat(48) { hour ->
                val now = start + hour.hours
                val viewModel = viewModel(FakeScoresRepository(testScheduler), clock = FixedClock(now), zone = zone)
                assertEquals(
                    "hour $hour at $now must be the device's day",
                    LeagueCalendar.leagueDay(now, zone),
                    viewModel.uiState.value.today,
                )
            }
        }

    @Test
    fun aLatePacificEveningStaysOnTheDevicesDay() =
        runTest {
            // 04:30Z on 1 November is 00:30 in Toronto and 21:30 on 31 October in Vancouver. The league's zone would
            // already be showing tomorrow's slate to that fan; the device's clock is the decision, so it is the 31st.
            val now = Instant.parse("2026-11-01T04:30:00Z")
            val vancouver = TimeZone.of("America/Vancouver")
            val viewModel = viewModel(FakeScoresRepository(testScheduler), clock = FixedClock(now), zone = vancouver)

            assertEquals("2026-10-31", viewModel.uiState.value.today)
            assertEquals("2026-11-01", LeagueCalendar.leagueDay(now, TimeZone.of("America/Toronto")))
        }

    private fun viewModel(
        scores: FakeScoresRepository,
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        clock: Clock = SchedulerClock(testScheduler, origin),
        jitter: () -> Double = { 0.5 },
        zone: TimeZone = TimeZone.of("America/Toronto"),
    ): ScoresViewModel =
        ScoresViewModel(scores, preferences, clock, zone, jitter).also { viewModel ->
            stores += ViewModelStore().apply { put("scores", viewModel) }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val origin: Instant,
) : Clock {
    override fun now(): Instant = origin + scheduler.currentTime.milliseconds
}

@OptIn(ExperimentalCoroutinesApi::class)
private class FakeScoresRepository(
    private val scheduler: TestCoroutineScheduler,
) : ScoresRepository {
    data class Call(
        val at: Long,
        val date: String,
        val today: String,
    )

    val calls = mutableListOf<Call>()
    val times: List<Long> get() = calls.map { it.at }
    var respond: suspend (String, String) -> Loadable<ScoreDay> = { date, _ -> freshDay(date) }

    override suspend fun loadDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay> {
        calls += Call(scheduler.currentTime, date, today)
        return respond(date, today)
    }

    /** Forced loads are recorded apart from ordinary ones: the whole point of refreshDay is that it is not loadDay. */
    val refreshCalls = mutableListOf<Call>()

    override suspend fun refreshDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay> {
        refreshCalls += Call(scheduler.currentTime, date, today)
        return respond(date, today)
    }

    val prefetchCalls = mutableListOf<Call>()

    override suspend fun prefetchAdjacent(
        date: String,
        today: String,
    ) {
        prefetchCalls += Call(scheduler.currentTime, date, today)
    }

    /**
     * Days already on disk: the neighbour pages read these and never ask for them.
     *
     * The seam is this narrow because [cachedDay] is the only way rows for a day other than the selected one
     * reach the screen, and what these tests are about is which settings snapshot those rows were built with.
     */
    private val disk = mutableMapOf<String, ScoreDay>()

    /** Every cache read, in order: a settings change must not add to it. */
    val cacheReads = mutableListOf<String>()

    /**
     * A cache read of [cacheGateDate] waits on [cacheGate] instead of returning, so a test can hold open the
     * window between a settings change and the read that used to follow it. Only the named date is delayed -
     * every other page keeps reading - so what stalls is specifically the page whose rows would otherwise stay
     * stale. Arm both after the warm-up; both null (every other test) lets every read return at once.
     */
    var cacheGateDate: String? = null
    var cacheGate: CompletableDeferred<Unit>? = null

    /** The dates that actually reached the gate: which read stalled, rather than which was expected to. */
    val cacheGateEntered = mutableListOf<String>()

    fun cache(
        date: String,
        day: ScoreDay,
    ) {
        disk[date] = day
    }

    override suspend fun cachedDay(
        date: String,
        today: String,
    ): ScoreDay? {
        cacheReads += date
        if (date == cacheGateDate) {
            cacheGateEntered += date
            cacheGate?.await()
        }
        return disk[date]
    }

    /** The opener the repository would walk to; null models "not known yet". */
    var resolveOpeningNight: suspend (String) -> String? = { null }
    val openingNightCalls = mutableListOf<String>()

    override suspend fun openingNight(startDate: String): String? {
        openingNightCalls += startDate
        return resolveOpeningNight(startDate)
    }
}

private class FakePreferencesRepository(
    teams: Set<TeamKey> = emptySet(),
    storedOnly: Boolean? = null,
) : PreferencesRepository {
    override suspend fun getThemeMode() = ThemeMode.System

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    // Backed by flows so the observing reads behave like the real store: a write reaches a collector. The plain
    // properties below keep every existing test reading naturally.
    private val favouritesFlow = MutableStateFlow(teams)
    private val onlyFlow = MutableStateFlow(storedOnly ?: true)
    private val orderFlow = MutableStateFlow(listOf("nhl", "nfl", "mlb"))

    val onlyWrites = mutableListOf<Boolean>()

    var teams: Set<TeamKey>
        get() = favouritesFlow.value
        set(value) {
            favouritesFlow.value = value
        }

    /** Simulates a write made elsewhere, e.g. the team picker, reaching this screen. */
    fun emitFavourites(teams: Set<TeamKey>) {
        favouritesFlow.value = teams
    }

    override suspend fun getFavourites(): Set<TeamKey> = teams

    override suspend fun setFavourites(teams: Set<TeamKey>) {
        favouritesFlow.value = teams
    }

    override fun observeFavourites(): Flow<Set<TeamKey>> = favouritesFlow

    var storedOrder: List<String>
        get() = orderFlow.value
        set(value) {
            orderFlow.value = value
        }

    val orderWrites = mutableListOf<List<String>>()
    var orderWriteDelay: suspend (List<String>) -> Unit = {}

    override suspend fun getLeagueOrder(): List<String> = storedOrder

    override suspend fun setLeagueOrder(order: List<String>) {
        orderWriteDelay(order)
        orderFlow.value = order
        orderWrites += order
    }

    override fun observeLeagueOrder(): Flow<List<String>> = orderFlow

    var storedOnly: Boolean?
        get() = onlyFlow.value
        set(value) {
            onlyFlow.value = value ?: true
        }

    override suspend fun getFavouritesOnly(): Boolean = onlyFlow.value

    override fun observeFavouritesOnly(): Flow<Boolean> = onlyFlow

    var failOnlyWrites = false

    override suspend fun setFavouritesOnly(enabled: Boolean) {
        if (failOnlyWrites) throw IOException("preferences are read-only")
        onlyFlow.value = enabled
        onlyWrites += enabled
    }

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

    override suspend fun isWritable(): Boolean = true

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

private val origin = Instant.parse("2026-10-26T23:06:00Z")

private fun game(
    date: String = "2026-10-26",
    state: GameState = GameState.Live(),
    id: String = "game",
    home: String = "MTL",
): Game =
    Game(
        id = GameId(id),
        date = date,
        state = state,
        home = TeamRef(TeamKey(home)),
        away = TeamRef(TeamKey("TOR")),
        startTimeUtc = origin - 6.minutes,
    )

private fun freshDay(
    date: String,
    games: List<Game> = listOf(game(date)),
): Loadable<ScoreDay> = Loadable(ScoreDay(date, games, emptyList(), 0), Freshness.Fresh, null)

/** A captured off-season day: the feed's previous and next game days are in different seasons. */
private fun offSeasonDay(
    date: String,
    previous: String? = "2026-06-14",
    next: String? = "2026-09-19",
): Loadable<ScoreDay> =
    Loadable(
        ScoreDay(
            date = date,
            games = emptyList(),
            dateIndex = emptyList(),
            droppedCount = 0,
            previousGameDate = previous,
            nextGameDate = next,
        ),
        Freshness.Fresh,
        null,
    )

/** A clock pinned to one instant: these tests are about which day that instant falls on, not about the passage of time. */
private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
