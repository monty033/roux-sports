package net.montycasa.roux.ui.teams

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

/** The Teams destination draws the list of the league the reader has chosen, through the shipped shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflTeamsRoutingTest {
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

    private fun openTeams() {
        rule.onNode(hasText("Teams") and isSelectable()).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun footballSelectedDrawsFootballsGroupedListAndNotHockeys() {
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

        openTeams()
        // The clubs arrive after the list frame: wait for the first division heading rather than for the frame.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.group.AFC East", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithTag("teams.group.AFC East", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("teams.group.NFC West", useUnmergedTree = true).assertCountEquals(1)
        // A football club is on the page, and a hockey-only club is not.
        rule.onAllNodesWithTag("teams.row.KC", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("teams.row.TOR", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun hockeySelectedDrawsHockeysListAndNoDivisionHeadings() {
        rule.setContent { RouxTheme { RouxNavHost() } }
        rule.enterLeagueMode()

        openTeams()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.row.TOR", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithTag("teams.group.AFC East", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("teams.row.KC", useUnmergedTree = true).assertCountEquals(0)
    }
}
