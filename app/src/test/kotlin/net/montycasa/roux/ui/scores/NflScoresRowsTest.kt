package net.montycasa.roux.ui.scores

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Football's scoreboard draws rows, and a row opens football's game page.
 *
 * Neither half was covered before, and both failed silently. The harness answered every date with an empty day, so a
 * scoreboard showing no rows looked exactly like a scoreboard whose league was broken; and the row itself was
 * deliberately left dead while the game page did not exist, which a reader experiences as "nothing happens in NFL".
 * This test dates the request to a captured day, asserts the row is drawn, and asserts that tapping it lands on
 * football's page rather than nothing at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflScoresRowsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: ScoresIntegrationApp get() = RuntimeEnvironment.getApplication() as ScoresIntegrationApp

    @After
    @OptIn(DelicateCoilApi::class)
    fun tearDown() {
        SingletonImageLoader.get(app).shutdown()
        SingletonImageLoader.reset()
        runBlocking { app.container.close() }
    }

    @Test
    fun aFootballDayDrawsItsGameAndTheRowOpensFootballsPage() {
        rule.setContent { RouxTheme { RouxNavHost() } }
        rule.waitForIdle()

        // Football through the control a reader uses: the league menu, pulled up from the bar.
        rule.onNodeWithTag("nav.bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -2000f))
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("leagues.nfl").performClick()
        rule.waitForIdle()

        rule.onNode(hasText("Scores") and isSelectable()).performClick()
        rule.waitForIdle()

        // Any football row the day draws: the screen decides which day to open on, and the point here is that the
        // league has rows at all and that a row is live, not which day it chose.
        val anyRow =
            SemanticsMatcher("a football game row") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("scores.game.") == true
            }
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodes(anyRow).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(anyRow)[0].performClick()

        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithTag("nflGame.hero").fetchSemanticsNodes().isNotEmpty()
        }
        // Hockey's page must not be what answered: its pager is the tag only it carries.
        rule.onAllNodesWithTag("detail.pager").assertCountEquals(0)
    }
}
