package net.montycasa.roux.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.RouxApp
import net.montycasa.roux.data.prefs.DataStorePreferencesRepository
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.policy.Alert
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path.Companion.toPath
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The worker's own responsibility: turn a pass into a WorkManager result, and nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameWatchWorkerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val app: Context = ApplicationProvider.getApplicationContext()

    private class ThrowingScores : ScoresRepository {
        override suspend fun loadDay(
            date: String,
            today: String,
        ): Loadable<ScoreDay> = throw IllegalStateException("the slate could not be read")

        override suspend fun prefetchAdjacent(
            date: String,
            today: String,
        ) = Unit
    }

    private class NoopScheduler : GameWatchScheduler {
        override suspend fun reconcile(
            now: Instant,
            day: ScoreDay,
            favourites: Set<TeamKey>,
            alertPreferences: (TeamKey) -> TeamAlertPreferences,
        ) = Unit

        override suspend fun watchAgain(
            gameId: String,
            delay: Duration,
        ) = Unit

        override suspend fun cancelAll() = Unit
    }

    private fun run(scores: ScoresRepository): GameWatchRun =
        GameWatchRun(
            preferences =
                DataStorePreferencesRepository(
                    directory = temporaryFolder.newFolder("datastore").absolutePath.toPath(),
                    scope = CoroutineScope(Dispatchers.IO),
                ),
            scores = scores,
            scheduler = NoopScheduler(),
            poster = AlertPoster { _: Alert, _: Game -> NotificationOutcome.NothingToPost },
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2025-01-02T02:00:00Z")
                },
        )

    private fun worker(
        run: GameWatchRun,
        gameId: String?,
    ): GameWatchWorker =
        TestListenableWorkerBuilder<GameWatchWorker>(app)
            .setInputData(workDataOf(*(if (gameId == null) emptyArray() else arrayOf(GameWatchWorker.KEY_GAME_ID to gameId))))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = GameWatchWorker(appContext, workerParameters, (app as RouxApp).container, run)
                },
            ).build()

    @Test
    fun aRepositoryThatThrowsIsRetried() =
        runBlocking {
            val result = worker(run(ThrowingScores()), "2025020123").doWork()

            assertTrue("a pass that could not read its slate is worth another attempt", result is ListenableWorker.Result.Retry)
        }

    @Test
    fun aWatchWithNoGameIdDoesNothingRatherThanRetryingForever() =
        runBlocking {
            val result = worker(run(ThrowingScores()), gameId = null).doWork()

            // Nothing to act on and nothing to retry: a malformed request must not become an endless loop.
            assertTrue("no game id is a no-op", result is ListenableWorker.Result.Success)
        }
}
