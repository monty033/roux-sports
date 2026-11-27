package net.montycasa.roux.ui.nav

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
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
 * Choosing the other sport takes the reader to that sport's scores.
 *
 * A football standings page and a hockey scores list are different competitions; leaving the football page on screen
 * under a hockey chip describes the wrong league. The test starts on football's standings, chooses hockey from the
 * league sheet, and asserts the scores list answered — not that a drawer closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LeagueSwitchShowsScoresTest {
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
    fun choosingTheOtherSportLandsOnItsScoresPage() {
        rule.setContent { RouxTheme { RouxNavHost() } }
        rule.waitForIdle()

        openLeagues()
        rule.onNodeWithTag("leagues.nfl").performClick()
        rule.waitForIdle()

        // Stand where a league change is most visible: football's standings.
        rule.onNode(hasText("Standings") and isSelectable()).performClick()
        rule.waitForIdle()
        rule.waitUntil(12_000) {
            rule.onAllNodesWithTag("nfl.standings.header").fetchSemanticsNodes().isNotEmpty()
        }

        openLeagues()
        rule.onNodeWithTag("leagues.nhl").performClick()
        rule.waitForIdle()

        // Hockey's scores list is the page that answered.
        rule.waitUntil(12_000) {
            rule.onAllNodesWithTag("scores.list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("scores.list").assertIsDisplayed()
        rule.onAllNodesWithTag("nfl.standings.header").assertCountEquals(0)
    }

    /** Opens the league sheet the way a reader does: pull the bottom bar up. */
    private fun openLeagues() {
        rule.onNodeWithTag("nav.bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -2000f))
            up()
        }
        rule.waitForIdle()
    }
}
