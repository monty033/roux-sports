package net.montycasa.roux.notify

import android.content.Context
import androidx.work.WorkManager
import net.montycasa.roux.AppContainer

/**
 * The scheduler the app itself uses: WorkManager's own instance, because work enqueued through anything else
 * would be work the app cannot read back.
 */
internal fun gameWatchScheduler(context: Context): WorkManagerGameWatchScheduler =
    WorkManagerGameWatchScheduler(WorkManager.getInstance(context))

/**
 * The one assembly of [GameWatchReconciler]'s inputs.
 *
 * Three callers need exactly this - the pass on app start, the periodic worker, and tests - and they have to
 * agree about it. A reconciler reading a store the app does not write would cancel the watches the app had
 * just scheduled, and a scheduler over a different WorkManager instance would be scheduling into a queue
 * nobody reads back. Keeping the assembly in one function is what makes that agreement structural rather
 * than a thing three call sites have to remember.
 */
internal fun gameWatchReconciler(
    context: Context,
    container: AppContainer,
): GameWatchReconciler =
    GameWatchReconciler(
        preferences = container.preferences,
        scores = container.scores,
        scheduler = gameWatchScheduler(context),
        clock = container.clock,
    )
