package net.montycasa.roux.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The platform's own record is what these tests read, not the scheduler's intent: a request that was
 * built and never enqueued would pass a test that inspected the builder, and the device would stay asleep.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkManagerGameWatchSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerGameWatchScheduler
    private val now = Instant.parse("2025-01-01T18:00:00Z")
    private val gameId = "2025020123"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                // A factory is needed because a zero-delay watch is due at once and this executor runs due work
                // inline. It builds the real worker with an inert pass: this test is about what the platform was
                // asked to schedule, and a pass that fetched would put the network inside a scheduling assertion.
                .setWorkerFactory(InertWorkerFactory(context))
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerGameWatchScheduler(workManager)
    }

    private fun game(
        startInHours: Int = 2,
        state: GameState = GameState.Scheduled,
        home: String = "MTL",
        away: String = "TOR",
    ) = Game(
        id = GameId(gameId),
        date = "2025-02-01",
        state = state,
        home = TeamRef(TeamKey(home)),
        away = TeamRef(TeamKey(away)),
        startTimeUtc = now + startInHours.hours,
    )

    private fun day(vararg games: Game) = ScoreDay("2025-02-01", games.toList(), listOf(DateIndexEntry("2025-02-01", games.size)), 0)

    private fun alerts(
        start: Boolean = true,
        goals: Boolean = true,
    ) = { _: TeamKey -> TeamAlertPreferences(start = start, goals = goals) }

    private fun watches(): List<WorkInfo> = workManager.getWorkInfosByTag(WorkManagerGameWatchScheduler.WATCH_TAG).get()

    @Test
    fun aFavouriteTwoHoursAheadIsWatchedOnceWithADelayOfTwoHours() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())

            // Read back by unique name: that is what makes a second reconcile replace nothing.
            val work = workManager.getWorkInfosForUniqueWork(WorkManagerGameWatchScheduler.watchName(gameId)).get()
            assertEquals("one watch for one followed game", 1, work.size)
            assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
            val delay = work.single().initialDelayMillis
            assertTrue(
                "the watch must wait for the start, not fire now: delay was ${delay}ms",
                delay in 7_200_000L - 1_000L..7_200_000L + 1_000L,
            )
        }

    @Test
    fun reconcilingTwiceLeavesExactlyOneWatch() =
        runBlocking {
            val today = day(game(startInHours = 2))
            scheduler.reconcile(now, today, alerts())
            scheduler.reconcile(now, today, alerts())

            assertEquals("reconciling is idempotent, not additive", 1, watches().size)
            assertEquals(1, watches().count { it.state == WorkInfo.State.ENQUEUED })
        }

    @Test
    fun aPostponedStartRetargetsTheWatchInsteadOfFiringAtTheOldTime() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())
            // The feed moves the game an hour later; a watch still pointing at the old start would fire an hour early.
            scheduler.reconcile(now, day(game(startInHours = 3)), alerts())

            val work = watches().single()
            assertEquals("one watch, moved rather than duplicated", 1, watches().size)
            assertEquals(3.hours.inWholeMilliseconds, work.initialDelayMillis)
        }

    @Test
    fun anUnchangedStartLeavesTheQueuedWatchAlone() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())
            val first = watches().single().id
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())

            // Same id means the queued work was kept, not cancelled and re-created on every pass.
            assertEquals("reconciling an unchanged day must not churn the queue", first, watches().single().id)
            assertEquals(1, watches().size)
        }

    @Test
    fun aWatchThatFiredBeforeTheGameWasPostponedIsReArmedForTheNewStart() =
        runBlocking {
            // Due now, so the watch runs and finishes before the feed moves the game back to a future start.
            scheduler.reconcile(now, day(game(startInHours = 0)), alerts())
            assertEquals(WorkInfo.State.SUCCEEDED, watches().single().state)

            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())

            // The spent watch stays in the platform's record; what matters is that something is armed for 8 pm.
            val armed = watches().filter { !it.state.isFinished }
            assertEquals("the postponed game is watched again, not left with a spent watch", 1, armed.size)
            assertEquals(2.hours.inWholeMilliseconds, armed.single().initialDelayMillis)
            assertEquals(WorkInfo.State.ENQUEUED, armed.single().state)
        }

    @Test
    fun aGameUnderWayIsNotReWatchedOnceItsWatchHasFired() =
        runBlocking {
            // The plan keeps asking for a live game on every pass; re-arming each time would alert again.
            scheduler.reconcile(now, day(game(state = GameState.Live())), alerts())
            val fired = watches().single()
            scheduler.reconcile(now, day(game(state = GameState.Live())), alerts())

            assertEquals("one watch, already spent, and no second one", 1, watches().size)
            assertEquals(fired.id, watches().single().id)
        }

    @Test
    fun turningAlertsOffCancelsTheWatchThatWasScheduled() =
        runBlocking {
            val today = day(game(startInHours = 2))
            scheduler.reconcile(now, today, alerts())
            assertEquals(WorkInfo.State.ENQUEUED, watches().single().state)

            scheduler.reconcile(now, today, alerts(start = false, goals = false))

            assertEquals(
                "a game the user no longer wants alerts for must stop waking the device",
                WorkInfo.State.CANCELLED,
                watches().single().state,
            )
        }

    @Test
    fun aGameWhoseTeamsHaveNoSwitchOnIsNotWatched() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts(start = false, goals = false))

            assertEquals(emptyList<WorkInfo>(), watches())
        }

    @Test
    fun aGameFinishedLongAgoIsNotWatched() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = -10, state = GameState.Final)), alerts())

            assertEquals(emptyList<WorkInfo>(), watches())
        }

    @Test
    fun aReconcileRightAfterTheHornKeepsThatGamesWatchAlive() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())
            assertEquals(WorkInfo.State.ENQUEUED, watches().single().state)

            scheduler.reconcile(now, day(game(startInHours = -3, state = GameState.Final)), alerts())

            assertTrue(
                "the pass after the horn is still wanted, so it must not be cancelled: ${watches().map { it.state }}",
                watches().any { it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED) },
            )
        }

    @Test
    fun aReconcileAfterTheWindowCancelsTheFinishedGamesWatch() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())
            assertEquals(WorkInfo.State.ENQUEUED, watches().single().state)

            scheduler.reconcile(now, day(game(startInHours = -7, state = GameState.Final)), alerts())

            assertEquals(WorkInfo.State.CANCELLED, watches().single().state)
        }

    @Test
    fun aGameAlreadyUnderWayIsWatchedImmediately() =
        runBlocking {
            // The app may have been closed through the start; the goals still matter, so the watch is now.
            scheduler.reconcile(now, day(game(state = GameState.Live())), alerts())

            val work = watches().single()
            // A zero delay is due at once, and the test executor runs due work inline: "it already ran" is the
            // correct observation, not "it is still waiting".
            assertTrue("a live game waits for nothing: delay was ${work.initialDelayMillis}ms", work.initialDelayMillis <= 1_000L)
            assertTrue(
                "a live game's watch is due at once: state was ${work.state}",
                work.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED),
            )
        }

    /**
     * Pins the *request*, not what it will do: the periodic worker's body arrives with the notifier, so this
     * asserts the interval it is scheduled at and that repeated reconciles do not stack copies of it. It is
     * deliberately not evidence that a late-published game gets picked up - that needs the body.
     */
    @Test
    fun thePeriodicReconcileIsEnqueuedExactlyOnceAtTwelveHours() =
        runBlocking {
            val today = day(game(startInHours = 2))
            scheduler.reconcile(now, today, alerts())
            scheduler.reconcile(now, today, alerts())

            val periodic = workManager.getWorkInfosForUniqueWork(WorkManagerGameWatchScheduler.RECONCILE_NAME).get()
            assertEquals("one periodic reconcile, however often it is reconciled", 1, periodic.size)
            val periodicity = periodic.single().periodicityInfo
            assertNotNull("and it is the periodic kind", periodicity)
            assertEquals(12 * 60 * 60 * 1000L, periodicity!!.repeatIntervalMillis)
            // Not pinned to ENQUEUED: this harness runs due work inline, so a periodic request can already have
            // run by the time it is read back. What matters here is that the request is live and not refused;
            // what the body does when it runs is DailyReconcileWorkerTest's subject.
            val state = periodic.single().state
            assertTrue(
                "the periodic request must still be live, was $state",
                state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING,
            )
        }

    @Test
    fun cancelAllRemovesEveryWatchAndTheReconcile() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())

            scheduler.cancelAll()

            assertEquals(WorkInfo.State.CANCELLED, watches().single().state)
            assertEquals(
                WorkInfo.State.CANCELLED,
                workManager
                    .getWorkInfosForUniqueWork(WorkManagerGameWatchScheduler.RECONCILE_NAME)
                    .get()
                    .single()
                    .state,
            )
            // A reconcile after cancelAll re-arms everything, so cancelling is not a one-way door.
            scheduler.reconcile(now, day(game(startInHours = 2)), alerts())
            assertEquals(WorkInfo.State.ENQUEUED, watches().single().state)
            assertNull(
                "a completed or cancelled watch must not linger as an error",
                watches().single().state.takeIf { it == WorkInfo.State.FAILED },
            )
        }
}

/**
 * Builds the real workers with a [GameWatchRun] that reads nothing, so scheduling tests never touch the network.
 * The worker's own behaviour is [GameWatchRunTest]'s subject.
 */
private class InertWorkerFactory(
    private val context: Context,
) : androidx.work.WorkerFactory() {
    private val run =
        GameWatchRun(
            preferences = (context.applicationContext as net.montycasa.roux.RouxApp).container.preferences,
            scores =
                object : net.montycasa.roux.domain.repo.ScoresRepository {
                    override suspend fun loadDay(
                        date: String,
                        today: String,
                    ): net.montycasa.roux.domain.Loadable<net.montycasa.roux.domain.model.ScoreDay> =
                        net.montycasa.roux.domain
                            .Loadable(null, net.montycasa.roux.domain.Freshness.Missing, null)

                    override suspend fun prefetchAdjacent(
                        date: String,
                        today: String,
                    ) = Unit
                },
            scheduler =
                object : net.montycasa.roux.domain.notify.GameWatchScheduler {
                    override suspend fun reconcile(
                        now: kotlin.time.Instant,
                        day: net.montycasa.roux.domain.model.ScoreDay,
                        alertPreferences: (net.montycasa.roux.domain.model.TeamKey) -> net.montycasa.roux.domain.repo.TeamAlertPreferences,
                    ) = Unit

                    override suspend fun watchAgain(
                        gameId: String,
                        delay: kotlin.time.Duration,
                    ) = Unit

                    override suspend fun cancelAll() = Unit
                },
            poster =
                object : AlertPoster {
                    override fun post(
                        alert: net.montycasa.roux.domain.policy.Alert,
                        game: net.montycasa.roux.domain.model.Game,
                    ) = NotificationOutcome.NothingToPost
                },
            clock = (context.applicationContext as net.montycasa.roux.RouxApp).container.clock,
        )

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: androidx.work.WorkerParameters,
    ): androidx.work.ListenableWorker? =
        when (workerClassName) {
            GameWatchWorker::class.java.name -> {
                GameWatchWorker(
                    appContext,
                    workerParameters,
                    (appContext.applicationContext as net.montycasa.roux.RouxApp).container,
                    run,
                )
            }

            else -> {
                null
            }
        }
}
