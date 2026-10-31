package net.montycasa.roux.notify

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import net.montycasa.roux.AppContainer

/**
 * Builds this app's workers with the app's own container.
 *
 * WorkManager's default factory constructs workers by reflection on `(Context, WorkerParameters)`, so a
 * worker that needs a repository has to reach for a service locator or open a second store of its own.
 * Both are the same mistake here: DataStore permits one live instance per file per process, so a second
 * store would be refused at first use - and if it were not, the ledger the worker reads would not be the
 * ledger the app writes.
 *
 * Returning null for an unknown class hands it back to the default factory, which is what makes this
 * additive rather than a restriction.
 */
class RouxWorkerFactory(
    private val container: AppContainer,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            GameWatchWorker::class.java.name -> {
                GameWatchWorker(
                    appContext,
                    workerParameters,
                    container,
                    watchRun(appContext),
                )
            }

            DailyReconcileWorker::class.java.name -> {
                DailyReconcileWorker(appContext, workerParameters, reconciler(appContext))
            }

            else -> {
                null
            }
        }

    private fun watchRun(appContext: Context): GameWatchRun =
        GameWatchRun(
            preferences = container.preferences,
            scores = container.scores,
            scheduler = WorkManagerGameWatchScheduler(WorkManager.getInstance(appContext)),
            poster = Notifier(appContext),
            clock = container.clock,
        )

    private fun reconciler(appContext: Context): GameWatchReconciler =
        GameWatchReconciler(
            preferences = container.preferences,
            scores = container.scores,
            // WorkManager's own instance: a worker enqueueing through anything else would agree with a
            // scheduler the app never reads back.
            scheduler = WorkManagerGameWatchScheduler(WorkManager.getInstance(appContext)),
            clock = container.clock,
        )
}
