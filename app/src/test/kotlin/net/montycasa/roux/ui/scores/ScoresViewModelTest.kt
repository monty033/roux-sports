package net.montycasa.roux.ui.scores

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.FavoritesHint
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
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

    private fun viewModel(
        scores: FakeScoresRepository,
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        clock: Clock = SchedulerClock(testScheduler, origin),
        jitter: () -> Double = { 0.5 },
    ): ScoresViewModel =
        ScoresViewModel(scores, preferences, clock, jitter).also { viewModel ->
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

    override suspend fun prefetchAdjacent(
        date: String,
        today: String,
    ) = Unit
}

private class FakePreferencesRepository(
    var teams: Set<TeamKey> = emptySet(),
    var storedOnly: Boolean? = null,
) : PreferencesRepository {
    val onlyWrites = mutableListOf<Boolean>()

    override suspend fun getFavourites(): Set<TeamKey> = teams

    override suspend fun setFavourites(teams: Set<TeamKey>) {
        this.teams = teams
    }

    var storedOrder = listOf("nhl", "nfl", "mlb")
    val orderWrites = mutableListOf<List<String>>()
    var orderWriteDelay: suspend (List<String>) -> Unit = {}

    override suspend fun getLeagueOrder(): List<String> = storedOrder

    override suspend fun setLeagueOrder(order: List<String>) {
        orderWriteDelay(order)
        storedOrder = order
        orderWrites += order
    }

    override suspend fun getFavouritesOnly(): Boolean = storedOnly ?: true

    var failOnlyWrites = false

    override suspend fun setFavouritesOnly(enabled: Boolean) {
        if (failOnlyWrites) throw IOException("preferences are read-only")
        storedOnly = enabled
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

    override suspend fun pruneNotificationLedger(olderThan: Instant) = Unit
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
