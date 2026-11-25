package net.montycasa.roux.ui.standings

import androidx.compose.ui.geometry.Offset
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
import net.montycasa.roux.enterLeagueMode
import net.montycasa.roux.ui.nav.RouxNavHost
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

/** The Standings destination draws the table of the league the reader has chosen, through the shipped shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflStandingsRoutingTest {
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

    private fun openStandings() {
        rule.onNode(hasText("Standings") and isSelectable()).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("standings.header").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("nfl.standings.header").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun footballSelectedDrawsTheFootballTableAndNotHockeys() {
        rule.setContent { RouxTheme { RouxNavHost() } }
        rule.waitForIdle()
        rule.onNodeWithTag("nav.bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -2000f))
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("leagues.nfl").performClick()
        rule.waitForIdle()

        openStandings()

        rule.onAllNodesWithTag("nfl.standings.header").assertCountEquals(1)
        rule.onAllNodesWithTag("standings.header").assertCountEquals(0)
        // Football's own columns, not hockey's: eight of them, and none of the hockey points figure.
        rule.onAllNodesWithTag("nfl.standings.col.DIFF").assertCountEquals(1)
        rule.onAllNodesWithTag("standings.cell.0").assertCountEquals(0)
    }

    @Test
    fun hockeySelectedDrawsTheHockeyTableAndNotFootballs() {
        rule.setContent { RouxTheme { RouxNavHost() } }
        rule.enterLeagueMode()

        openStandings()

        rule.onAllNodesWithTag("standings.header").assertCountEquals(1)
        rule.onAllNodesWithTag("nfl.standings.header").assertCountEquals(0)
    }
}
