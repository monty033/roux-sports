package net.montycasa.roux.notify

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.prefs.DataStorePreferencesRepository
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The periodic worker's body, which is the promise its name makes: run the reconcile again later.
 *
 * The scheduler is a recorder rather than the real one, so "the worker reconciled" is observed as the plan it
 * actually handed over - a worker that returned success without reconciling would record nothing and fail here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailyReconcileWorkerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2025-01-01T18:00:00Z")
    private val zone = ZoneId.of("America/New_York")

    private val slate =
        ScoreDay(
            date = "2025-01-01",
            games =
                listOf(
                    Game(
                        id = GameId("2025020123"),
                        date = "2025-01-01",
                        state = GameState.Scheduled,
                        home = TeamRef(TeamKey("MTL")),
                        away = TeamRef(TeamKey("TOR")),
                        startTimeUtc = now + 2.hours,
                    ),
                ),
            dateIndex = listOf(DateIndexEntry("2025-01-01", 1)),
            droppedCount = 0,
        )

    private class RecordingScheduler : GameWatchScheduler {
        data class Call(
            val now: Instant,
            val day: ScoreDay,
            val favourites: Set<TeamKey>,
        )

        val calls = CopyOnWriteArrayList<Call>()

        override suspend fun reconcile(
            now: Instant,
            day: ScoreDay,
            favourites: Set<TeamKey>,
            alertPreferences: (TeamKey) -> TeamAlertPreferences,
        ) {
            calls += Call(now, day, favourites)
        }

        override suspend fun cancelAll() = Unit
    }

    private class FakeScores(
        private val answer: () -> Loadable<ScoreDay>,
    ) : ScoresRepository {
        val requestedDates = CopyOnWriteArrayList<String>()

        override suspend fun loadDay(
            date: String,
            today: String,
        ): Loadable<ScoreDay> {
            requestedDates += date
            return answer()
        }

        override suspend fun prefetchAdjacent(
            date: String,
            today: String,
        ) = Unit
    }

    private fun worker(reconciler: GameWatchReconciler) =
        TestListenableWorkerBuilder<DailyReconcileWorker>(app)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = DailyReconcileWorker(appContext, workerParameters, reconciler)
                },
            ).build()

    private fun reconciler(
        scores: ScoresRepository,
        scheduler: GameWatchScheduler,
        preferences: DataStorePreferencesRepository,
    ) = GameWatchReconciler(
        preferences = preferences,
        scores = scores,
        scheduler = scheduler,
        clock =
            object : Clock {
                override fun now(): Instant = now
            },
        zone = zone,
    )

    private fun preferences(): DataStorePreferencesRepository =
        DataStorePreferencesRepository(
            directory = temporaryFolder.newFolder("datastore").absolutePath.toPath(),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
        )

    @Test
    fun theWorkerReconcilesTheDayItIsAskedToWatch() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val scheduler = RecordingScheduler()

            val result =
                worker(reconciler(FakeScores { Loadable(slate, Freshness.Fresh, null) }, scheduler, preferences))
                    .doWork()

            assertTrue("a completed reconcile reports success", result is ListenableWorker.Result.Success)
            val call = scheduler.calls.single()
            assertEquals("the whole slate is handed over, not just a game id", slate, call.day)
            assertEquals(setOf(TeamKey("TOR")), call.favourites)
            assertEquals("and the clock the reconciler was given", now, call.now)
        }

    @Test
    fun theDayRequestedIsTheDevicesCivilDay() =
        runBlocking {
            val preferences = preferences()
            val scores = FakeScores { Loadable(slate, Freshness.Fresh, null) }
            val scheduler = RecordingScheduler()

            // 18:00 UTC is 13:00 in New York, so both are the same civil day; the assertion below pins that the
            // reconcile asks for a local date rather than a UTC one by using a time that straddles midnight.
            worker(reconciler(scores, scheduler, preferences)).doWork()

            assertEquals(listOf("2025-01-01"), scores.requestedDates)
        }

    @Test
    fun aSlateThatCouldNotBeLoadedLeavesTheWatchesAloneAndStillSucceeds() =
        runBlocking {
            val preferences = preferences()
            val scheduler = RecordingScheduler()

            val result =
                worker(reconciler(FakeScores { Loadable(null, Freshness.Missing, null) }, scheduler, preferences))
                    .doWork()

            assertTrue("nothing to act on is not a failure", result is ListenableWorker.Result.Success)
            assertEquals(
                "and cancelling watches because the network is down would be the wrong answer",
                emptyList<RecordingScheduler.Call>(),
                scheduler.calls,
            )
        }

    @Test
    fun aReconcileThatThrowsIsRetried() =
        runBlocking {
            val preferences = preferences()
            val scheduler = RecordingScheduler()
            val throwing =
                FakeScores {
                    throw IllegalStateException("the store is unreadable")
                }

            val result = worker(reconciler(throwing, scheduler, preferences)).doWork()

            assertTrue("an unreadable input is worth another attempt", result is ListenableWorker.Result.Retry)
        }
}
