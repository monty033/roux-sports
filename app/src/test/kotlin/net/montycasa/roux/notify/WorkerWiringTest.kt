package net.montycasa.roux.notify

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.TestListenableWorkerBuilder
import net.montycasa.roux.RouxApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The wiring this app is responsible for, checked where it can actually be observed: the factory the app
 * configures, and the manifest node that keeps WorkManager's own initializer from running first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkerWiringTest {
    private val app: RouxApp = ApplicationProvider.getApplicationContext<Application>() as RouxApp

    @Test
    fun aWorkerBuiltByTheAppsFactoryUsesTheAppsOwnStore() {
        val factory = RouxWorkerFactory(app.container)

        val worker = TestListenableWorkerBuilder<GameWatchWorker>(app).setWorkerFactory(factory).build()
        val daily = TestListenableWorkerBuilder<DailyReconcileWorker>(app).setWorkerFactory(factory).build()

        // Identity, not equality: two stores over one file are exactly what DataStore refuses, so a worker that
        // built its own would fail at first use - and, before that, would read a ledger nobody else writes.
        assertSame(app.container.preferences, worker.container.preferences)
        assertSame(app.container.scores, worker.container.scores)
        // The periodic worker reaches the same store through the reconciler the factory builds for it.
        assertSame(app.container.preferences, daily.reconciler.preferences)
    }

    @Test
    fun theAppSuppliesTheWorkManagerConfigurationItself() {
        val configuration = (app as Configuration.Provider).workManagerConfiguration

        assertTrue("the app must hand WorkManager its factory", configuration.workerFactory is RouxWorkerFactory)
        assertNotNull(configuration.executor)
    }

    /**
     * Read from the source manifest because the merged manifest is assembled at build time; what this app
     * controls is the removal node, and without it the initializer wins the race with `onCreate`.
     */
    @Test
    fun theDefaultWorkManagerInitializerIsRemoved() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("expected the app manifest at ${manifest.absolutePath}", manifest.exists())

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val metadata =
            document.getElementsByTagName("meta-data").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }
            }

        val initializer = metadata.singleOrNull { it.getAttribute("android:name") == WORK_MANAGER_INITIALIZER }
        assertNotNull("the initializer must be named so it can be removed", initializer)
        assertTrue(
            "the initializer has to be removed with tools:node, not merely declared",
            initializer!!.getAttribute("tools:node") == "remove",
        )

        val startupProvider =
            document
                .getElementsByTagName("provider")
                .let { nodes ->
                    (0 until nodes.length).map { nodes.item(it) as Element }
                }.singleOrNull { it.getAttribute("android:name") == STARTUP_PROVIDER }
        assertNotNull("the removal has to live inside androidx.startup's provider", startupProvider)
        assertTrue(
            "and that provider must be merged, or the node replaces rather than edits it",
            startupProvider!!.getAttribute("tools:node") == "merge",
        )
    }

    private companion object {
        const val WORK_MANAGER_INITIALIZER = "androidx.work.WorkManagerInitializer"
        const val STARTUP_PROVIDER = "androidx.startup.InitializationProvider"
    }
}
