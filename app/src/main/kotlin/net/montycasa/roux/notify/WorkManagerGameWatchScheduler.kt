package net.montycasa.roux.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.montycasa.roux.AppContainer
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.notify.GameWatchPlan
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.notify.WatchedGame
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
 * A watch carries the start time it was scheduled for, as a tag. That is what lets a reconcile tell "this
 * is already the right work" from "the game moved", so a postponed start replaces the stale watch instead
 * of leaving it to fire at the old time - and an unchanged start leaves the queued work alone rather than
 * re-creating it on every pass.
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

        val scheduled = scheduledWatches()

        // A game that has finished, or stopped being followed, must stop waking the device. Cancel by name:
        // anything already running is left to finish, and nothing is cancelled that is still wanted.
        for (gameId in scheduled.keys) {
            if (gameId !in wantedIds) workManager.cancelUniqueWork(watchName(gameId))
        }

        for (game in wanted) {
            if (settled(scheduled[game.gameId], game)) continue
            workManager.enqueueUniqueWork(
                watchName(game.gameId),
                // Only reached when the game has no live watch of its own, so replacing cannot cancel work in
                // flight - and a stale watch has to be replaced, because KEEP would leave it pointing at the old
                // start time. Retargeting is safe: the delay is derived from the start time, so the fire time
                // stays put however often this runs.
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GameWatchWorker>()
                    .setInitialDelay(game.delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    // The unique name is not readable back from WorkInfo, so it is also a tag: that is what lets a
                    // later reconcile find the work it scheduled for a game and cancel it by name.
                    .addTag(watchName(game.gameId))
                    .addTag(targetTag(game))
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
        for (gameId in scheduledWatches().keys) workManager.cancelUniqueWork(watchName(gameId))
    }

    /**
     * True when the work already recorded for this game is the work the plan is asking for, so enqueuing
     * another would only churn.
     *
     * A watch that has already run is not re-armed: in the live-game case the plan will keep asking for it, and
     * a fresh watch on every pass would ring again for the start the user has already been told about.
     */
    private fun settled(
        existing: ScheduledWatch?,
        game: WatchedGame,
    ): Boolean =
        when (existing?.state) {
            null, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> false

            // Working right now: replacing it would cancel it mid-flight.
            WorkInfo.State.RUNNING -> true

            // It has already fired. That is the end of the matter for a game under way - a fresh watch on every
            // pass would ring again for a start the user has been told about. But a game that is *still* waiting
            // for a future start is a watch that has not been waited for yet: the first one fired before the feed
            // postponed the game, and without re-arming nothing would be watching at the new time.
            WorkInfo.State.SUCCEEDED -> game.firesAt == null

            // Queued for exactly this start time (or due now, for a game under way): nothing to change.
            else -> existing.target == target(game)
        }

    /** Scheduled watches by game id, read back from their tags rather than remembered in memory. */
    private suspend fun scheduledWatches(): Map<String, ScheduledWatch> =
        workManager
            .getWorkInfosByTag(WATCH_TAG)
            .await()
            .mapNotNull { info ->
                val gameId =
                    info.tags.firstOrNull { it.startsWith(WATCH_PREFIX) }?.removePrefix(WATCH_PREFIX)
                        ?: return@mapNotNull null
                val target =
                    info.tags.firstOrNull { it.startsWith(TARGET_PREFIX) }?.removePrefix(TARGET_PREFIX)
                        ?: return@mapNotNull null
                gameId to ScheduledWatch(target, info.state)
            }
            // A game can have several recorded generations; the unfinished one, or else the last one, is the one
            // that describes what the platform is actually going to do.
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, generations) -> generations.firstOrNull { !it.state.isFinished } ?: generations.last() }

    /** What the work was scheduled for: a start time, or "due now" for a game already under way. */
    private fun target(game: WatchedGame): String = game.firesAt?.toString() ?: DUE_NOW

    private fun targetTag(game: WatchedGame): String = TARGET_PREFIX + target(game)

    private data class ScheduledWatch(
        val target: String,
        val state: WorkInfo.State,
    )

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
        const val TARGET_PREFIX = "target:"
        const val DUE_NOW = "due-now"
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
    /** The same container the UI uses, so the worker and the screens share one store and one client. */
    val container: AppContainer,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.success()
}

/**
 * Periodic target that re-runs the reconcile every [WorkManagerGameWatchScheduler.RECONCILE_INTERVAL_HOURS],
 * so a game whose start time the feed published after the app's last pass is still picked up.
 *
 * [GameWatchWorker] still has no body: the fetch, the decision and the notification arrive with the notifier.
 * This one does have one, because the promise its name makes - "reconcile again later" - is a promise about
 * reconciliation, not about notifying.
 */
class DailyReconcileWorker(
    context: Context,
    parameters: WorkerParameters,
    val reconciler: GameWatchReconciler,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            reconciler.reconcile()
            Result.success()
        } catch (cancelled: CancellationException) {
            // WorkManager cancels by cancelling this coroutine; reporting a retry here would resurrect work the
            // platform just asked to stop.
            throw cancelled
        } catch (failure: Exception) {
            // A reconcile that could not read its inputs is worth another attempt; an input that is simply
            // absent is not a failure at all, and the reconciler returns instead of throwing for it.
            Result.retry()
        }
}
