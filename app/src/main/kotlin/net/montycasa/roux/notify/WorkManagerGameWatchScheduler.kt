package net.montycasa.roux.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.notify.GameWatchPlan
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

/**
 * [GameWatchScheduler] on WorkManager.
 *
 * Every decision about *which* games deserve a watch is made by [GameWatchPlan]; this class only makes
 * the platform agree with that list, so the interesting rules are not buried in platform plumbing.
 *
 * Idempotence comes from unique work keyed by game id: reconciling twice enqueues each game once, and a
 * game that stops being wanted is cancelled by name rather than left to fire.
 *
 * The WorkManager instance is a constructor parameter rather than `WorkManager.getInstance(context)`
 * because a test that cannot substitute it can only observe the real scheduler, on a real clock.
 */
class WorkManagerGameWatchScheduler(
    private val workManager: WorkManager,
) : GameWatchScheduler {
    override suspend fun reconcile(
        now: Instant,
        day: ScoreDay,
        favourites: Set<TeamKey>,
        alertPreferences: (TeamKey) -> TeamAlertPreferences,
    ) {
        val wanted = GameWatchPlan.wanted(now, day, favourites, alertPreferences)
        val wantedIds = wanted.mapTo(mutableSetOf()) { it.gameId }

        // A game that has finished, or stopped being followed, must stop waking the device. Cancel by name:
        // anything already running is left to finish, and nothing is cancelled that is still wanted.
        for (gameId in scheduledGameIds()) {
            if (gameId !in wantedIds) workManager.cancelUniqueWork(watchName(gameId))
        }

        for (game in wanted) {
            workManager.enqueueUniqueWork(
                watchName(game.gameId),
                // KEEP, not REPLACE: a watch already scheduled for this game is the same watch, and replacing it
                // would reset the delay every time the app starts.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GameWatchWorker>()
                    .setInitialDelay(game.delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    // The unique name is not readable back from WorkInfo, so it is also a tag: that is what lets a
                    // later reconcile find the work it scheduled for a game and cancel it by name.
                    .addTag(watchName(game.gameId))
                    .addTag(WATCH_TAG)
                    .build(),
            )
        }

        // The periodic reconcile is what notices a game whose start time the feed had not published yet.
        workManager.enqueueUniquePeriodicWork(
            RECONCILE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyReconcileWorker>(RECONCILE_INTERVAL_HOURS, TimeUnit.HOURS).build(),
        )
    }

    override suspend fun cancelAll() {
        workManager.cancelUniqueWork(RECONCILE_NAME)
        for (gameId in scheduledGameIds()) workManager.cancelUniqueWork(watchName(gameId))
    }

    /** Game ids with work currently scheduled, read back from the tag rather than remembered in memory. */
    private suspend fun scheduledGameIds(): Set<String> =
        workManager
            .getWorkInfosByTag(WATCH_TAG)
            .await()
            .mapNotNullTo(mutableSetOf()) { info ->
                info.tags
                    .firstOrNull { tag -> tag.startsWith(WATCH_PREFIX) }
                    ?.removePrefix(WATCH_PREFIX)
            }

    /**
     * AWAITS the platform's future without blocking a thread.
     *
     * WorkManager's Java API answers with a [ListenableFuture]; a scheduler that blocked on it would hold
     * a thread per call and still be correct, but there is no reason to when the cancellation is this cheap.
     */
    private suspend fun <T> ListenableFuture<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    try {
                        continuation.resume(get())
                    } catch (failure: Throwable) {
                        // The future's own exception type carries the cause; unwrapping here would hide which call failed.
                        continuation.resumeWithException(failure)
                    }
                },
                // Run the completion on whichever thread completed the future: there is nothing to wait for here.
                Executor { command -> command.run() },
            )
            continuation.invokeOnCancellation { cancel(true) }
        }

    companion object {
        const val WATCH_PREFIX = "watch-"
        const val WATCH_TAG = "game-watch"
        const val RECONCILE_NAME = "daily-reconcile"
        const val RECONCILE_INTERVAL_HOURS = 12L

        fun watchName(gameId: String): String = WATCH_PREFIX + gameId
    }
}

/**
 * The target of a scheduled watch. Scheduling and watching are separate steps on purpose: this work is
 * enqueued now, and what it does when it fires is decided by the code that runs then.
 *
 * It has no body yet - the fetch, the decision and the notification arrive with the notifier and the
 * worker in the steps after this one. Until then a watch fires, finds nothing to do and reports success,
 * which is the truthful answer: nothing failed, and nothing was attempted.
 */
class GameWatchWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.success()
}

/**
 * Periodic target that re-runs the reconcile every [WorkManagerGameWatchScheduler.RECONCILE_INTERVAL_HOURS],
 * so a game whose start time the feed published late is still picked up. Its body arrives with the wiring
 * step; like [GameWatchWorker] it currently reports success without doing anything.
 */
class DailyReconcileWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.success()
}
