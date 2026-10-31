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
            scheduler.reconcile(now, day(game(startInHours = 2)), setOf(TeamKey("TOR")), alerts())

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
            scheduler.reconcile(now, today, setOf(TeamKey("TOR")), alerts())
            scheduler.reconcile(now, today, setOf(TeamKey("TOR")), alerts())

            assertEquals("reconciling is idempotent, not additive", 1, watches().size)
            assertEquals(1, watches().count { it.state == WorkInfo.State.ENQUEUED })
        }

    @Test
    fun aPostponedStartRetargetsTheWatchInsteadOfFiringAtTheOldTime() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), setOf(TeamKey("TOR")), alerts())
            // The feed moves the game an hour later; a watch still pointing at the old start would fire an hour early.
            scheduler.reconcile(now, day(game(startInHours = 3)), setOf(TeamKey("TOR")), alerts())

            val work = watches().single()
            assertEquals("one watch, moved rather than duplicated", 1, watches().size)
            assertEquals(3.hours.inWholeMilliseconds, work.initialDelayMillis)
        }

    @Test
    fun anUnchangedStartLeavesTheQueuedWatchAlone() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), setOf(TeamKey("TOR")), alerts())
            val first = watches().single().id
            scheduler.reconcile(now, day(game(startInHours = 2)), setOf(TeamKey("TOR")), alerts())

            // Same id means the queued work was kept, not cancelled and re-created on every pass.
            assertEquals("reconciling an unchanged day must not churn the queue", first, watches().single().id)
            assertEquals(1, watches().size)
        }

    @Test
    fun turningAlertsOffCancelsTheWatchThatWasScheduled() =
        runBlocking {
            val today = day(game(startInHours = 2))
            scheduler.reconcile(now, today, setOf(TeamKey("TOR")), alerts())
            assertEquals(WorkInfo.State.ENQUEUED, watches().single().state)

            scheduler.reconcile(now, today, setOf(TeamKey("TOR")), alerts(start = false, goals = false))

            assertEquals(
                "a game the user no longer wants alerts for must stop waking the device",
                WorkInfo.State.CANCELLED,
                watches().single().state,
            )
        }

    @Test
    fun aGameThatIsNotFollowedIsNotWatched() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), favourites = emptySet(), alertPreferences = alerts())

            assertEquals(emptyList<WorkInfo>(), watches())
        }

    @Test
    fun aFinishedGameIsNotWatched() =
        runBlocking {
            scheduler.reconcile(
                now,
                day(game(startInHours = -4, state = GameState.Final)),
                setOf(TeamKey("TOR")),
                alerts(),
            )

            assertEquals(emptyList<WorkInfo>(), watches())
        }

    @Test
    fun aGameAlreadyUnderWayIsWatchedImmediately() =
        runBlocking {
            // The app may have been closed through the start; the goals still matter, so the watch is now.
            scheduler.reconcile(now, day(game(state = GameState.Live())), setOf(TeamKey("TOR")), alerts())

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
            scheduler.reconcile(now, today, setOf(TeamKey("TOR")), alerts())
            scheduler.reconcile(now, today, setOf(TeamKey("TOR")), alerts())

            val periodic = workManager.getWorkInfosForUniqueWork(WorkManagerGameWatchScheduler.RECONCILE_NAME).get()
            assertEquals("one periodic reconcile, however often it is reconciled", 1, periodic.size)
            val periodicity = periodic.single().periodicityInfo
            assertNotNull("and it is the periodic kind", periodicity)
            assertEquals(12 * 60 * 60 * 1000L, periodicity!!.repeatIntervalMillis)
            assertTrue(periodic.single().state == WorkInfo.State.ENQUEUED)
        }

    @Test
    fun cancelAllRemovesEveryWatchAndTheReconcile() =
        runBlocking {
            scheduler.reconcile(now, day(game(startInHours = 2)), setOf(TeamKey("TOR")), alerts())

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
            scheduler.reconcile(now, day(game(startInHours = 2)), setOf(TeamKey("TOR")), alerts())
            assertEquals(WorkInfo.State.ENQUEUED, watches().single().state)
            assertNull(
                "a completed or cancelled watch must not linger as an error",
                watches().single().state.takeIf { it == WorkInfo.State.FAILED },
            )
        }
}
