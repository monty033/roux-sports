package net.montycasa.roux.ui.teams

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.reach.ReachTargetTag
import net.montycasa.roux.ui.scores.ScoreFixture
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.coroutines.CoroutineContext

/** G6 crosses the real shell, both ViewModel factories and the on-disk preference store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesPersistenceTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: ScoresIntegrationApp get() = RuntimeEnvironment.getApplication() as ScoresIntegrationApp
    private val mounted = mutableStateOf(true)
    private var owner = newOwner()
    private val preferenceFile get() = File(app.filesDir, "datastore/roux.preferences_pb")
    private var gatedDispatcher: PendingStoreDispatcher? = null

    @After
    fun tearDown() {
        gatedDispatcher?.resume()
        unmount()
        resetImages()
        runBlocking { app.container.close() }
    }

    @Test
    fun torPickedInTeamsSurvivesRestartAndFiltersScoresUntilFavouritesOnlyIsTurnedOff() {
        launchShell()
        openTeams()
        pickTor()
        restart()

        val torGames =
            ScoreFixture.games.filter { game ->
                listOf("awayTeam", "homeTeam").any {
                    game
                        .getValue(it)
                        .jsonObject
                        .getValue("abbrev")
                        .jsonPrimitive.content == "TOR"
                }
            }
        assertTrue(
            "the fixture must exercise both filtered and excluded games",
            torGames.isNotEmpty() && torGames.size < ScoreFixture.games.size,
        )
        val torIds = torGames.map { it.getValue("id").jsonPrimitive.content }.toSet()
        waitForGame(torIds.first())
        rule.onNodeWithContentDescription("Favourites only").assertDoesNotExist()
        assertTrue(runBlocking { app.container.preferences.getFavouritesOnly() })
        assertEquals(torIds, composedGameIds())
        ScoreFixture.games.filter { it.getValue("id").jsonPrimitive.content !in torIds }.forEach {
            rule.onNodeWithTag("scores.game.${it.getValue("id").jsonPrimitive.content}").assertDoesNotExist()
        }

        openTeams()
        torToggle().assertIsOn()
        barItem("Scores").performClick()
        rule.onNodeWithTag("nav.menu").performClick()
        rule.onNodeWithTag("leagues.nhl").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onNodeWithTag("scores.header").fetchSemanticsNode()
            !runBlocking { app.container.preferences.getFavouritesOnly() }
        }
        rule.onNodeWithContentDescription("Favourites only").assertDoesNotExist()
        val seen = mutableSetOf<String>()
        ScoreFixture.games.forEach { game ->
            val id = game.getValue("id").jsonPrimitive.content
            rule.onNodeWithTag("scores.list").performScrollToNode(hasTestTag("scores.game.$id"))
            rule.onNodeWithTag("scores.game.$id").assertIsDisplayed()
            seen += composedGameIds()
        }
        assertEquals(ScoreFixture.games.map { it.getValue("id").jsonPrimitive.content }.toSet(), seen)
        assertTrue(app.requests.any { it.encodedPath == "/v1/score/${ScoreFixture.date}" })
    }

    @Test
    fun aCompletedClearSurvivesRestart() {
        launchShell()
        openTeams()
        pickTor()
        // The pick has to be shown to have persisted before the clear means anything: with no write at all,
        // "off after a restart" is what this test would see and it would pass while storing nothing.
        restart()
        openTeams()
        torToggle().performScrollTo().assertIsOn()
        torToggle().performClick()
        waitForTor(ToggleableState.Off)
        restart()
        openTeams()
        torToggle().performScrollTo().assertIsOff()
    }

    @Test
    fun closingWithAClearPendingKeepsTheLastCompleteStoredSelection() {
        val dispatcher = PendingStoreDispatcher().also { gatedDispatcher = it }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        runBlocking { app.restart(scope) }
        launchShell()
        openTeams()
        pickTor()
        val saved = preferenceFile.readBytes()

        // Hold DataStore's actor after the UI requests the clear but before it can persist it.
        // This observes a pending real write without relying on filesystem timing or a sleep.
        dispatcher.pause()
        torToggle().performClick()
        rule.waitUntil(timeoutMillis = 10_000) { dispatcher.hasPendingWork() }
        torToggle().assertIsOn()
        assertArrayEquals(saved, preferenceFile.readBytes())
        resetImages()
        runBlocking {
            val closing = async(start = CoroutineStart.UNDISPATCHED) { app.container.close() }
            assertTrue(scope.coroutineContext.job.isCancelled)
            assertFalse("close must wait for the queued store work to finish", closing.isCompleted)
            dispatcher.resume()
            closing.await()
        }
        unmount()
        assertTrue(scope.coroutineContext.job.isCompleted)
        // Comparing bytes prevents the corruption handler's empty default from hiding a torn file.
        assertArrayEquals(saved, preferenceFile.readBytes())
        runBlocking { app.restart() }
        remount()
        openTeams()
        torToggle().assertIsOn()
        assertArrayEquals(saved, preferenceFile.readBytes())
    }

    @Test
    fun fullyVisibleRowsAreMeasuredInsideTheRealShell() {
        launchShell()
        openTeams()
        val rows = rule.onAllNodes(teamRowMatcher, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(32, rows.size)
        val viewport = rule.onNodeWithTag("teams.list").getUnclippedBoundsInRoot()
        val visible =
            rows.count { row ->
                val tag = row.config[SemanticsProperties.TestTag]
                val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
                bounds.top >= viewport.top && bounds.bottom <= viewport.bottom &&
                    bounds.left >= viewport.left && bounds.right <= viewport.right
            }
        println("Teams in RouxNavHost at 411x923: $visible fully visible rows (list viewport: $viewport)")
        // A bound, not an exact count: the contract is capacity ("a picker you can scan"), and pinning the
        // exact number would fail on a harmless change in font metrics or row spacing as readily as on a
        // regression. Seven is the measured floor inside the real shell at this size: the grabber strip above the
        // bar costs the eighth row, and that strip is the only thing on a parked screen that says the menu exists.
        assertTrue("only $visible fully visible teams in RouxNavHost at 411x923, expected at least 7", visible >= 7)
    }

    private fun launchShell() {
        rule.setContent {
            if (mounted.value) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    RouxTheme { RouxNavHost() }
                }
            }
        }
    }

    private fun unmount() {
        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        rule.runOnIdle { owner.viewModelStore.clear() }
    }

    private fun remount() {
        rule.runOnIdle {
            owner = newOwner()
            mounted.value = true
        }
    }

    private fun restart() {
        unmount()
        resetImages()
        runBlocking { app.restart() }
        remount()
    }

    @OptIn(DelicateCoilApi::class)
    private fun resetImages() {
        SingletonImageLoader.get(app).shutdown()
        SingletonImageLoader.reset()
    }

    private fun openTeams() {
        barItem("Teams").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.row.TOR").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    private fun pickTor() {
        torToggle().performScrollTo().assertIsOff().performClick()
        waitForTor(ToggleableState.On)
        torToggle().performScrollTo().assertIsDisplayed().assertIsOn()
    }

    private fun waitForTor(state: ToggleableState) {
        rule.waitUntil(timeoutMillis = 10_000) {
            torToggle().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState) == state
        }
    }

    private fun waitForGame(id: String) {
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithTag("scores.game.$id").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun composedGameIds(): Set<String> =
        rule
            .onAllNodes(
                SemanticsMatcher("score game") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("scores.game.") ==
                        true
                },
            ).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag].removePrefix("scores.game.") }
            .toSet()

    private fun torToggle() = rule.onNode(SemanticsMatcher.expectValue(ReachTargetTag, "teams.toggle.TOR"))

    private fun barItem(label: String) = rule.onNode(hasText(label) and isSelectable())

    private fun newOwner(): ViewModelStoreOwner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    private val teamRowMatcher =
        SemanticsMatcher("team row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("teams.row.") == true
        }
}

/** Pauses only the supplied DataStore scope; Compose and the real ViewModels keep running. */
private class PendingStoreDispatcher : CoroutineDispatcher() {
    private val queued = mutableListOf<Pair<CoroutineContext, Runnable>>()
    private var paused = false

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        synchronized(queued) {
            if (paused) {
                queued += context to block
            } else {
                Dispatchers.IO.dispatch(context, block)
            }
        }
    }

    fun pause() = synchronized(queued) { paused = true }

    fun hasPendingWork(): Boolean = synchronized(queued) { queued.isNotEmpty() }

    fun resume() {
        synchronized(queued) {
            paused = false
            queued.forEach { (context, block) -> Dispatchers.IO.dispatch(context, block) }
            queued.clear()
        }
    }
}
