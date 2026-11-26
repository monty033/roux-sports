package net.montycasa.roux.ui.team

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

/**
 * A football club's route draws football's page.
 *
 * Before this stage the route fell through to the "league not ready" placeholder, which is what a tap on a football
 * club used to get. The assertion is deliberately about which page answered, not about layout: the placeholder and the
 * page are told apart by a tag only the page carries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflTeamRoutingTest {
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
    fun aFootballClubOpensFootballsPageAndNotThePlaceholder() {
        rule.setContent { RouxTheme { RouxNavHost() } }
        rule.waitForIdle()

        // Football is chosen the way a reader chooses it: open the league sheet and pick football.
        rule.onNodeWithTag("nav.bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -2000f))
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("leagues.nfl").performClick()
        rule.waitForIdle()

        rule.onNode(hasText("Teams") and isSelectable()).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.list").fetchSemanticsNodes().isNotEmpty()
        }

        // The list is football's because the drawer opened it: the integration app serves the football payloads.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.row.BUF").fetchSemanticsNodes().isNotEmpty()
        }
        // The body is the tap target that opens a page; the star and bell keep their own taps.
        rule.onNodeWithTag("teams.row.body.BUF").performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("nflTeam.name").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("nflTeam.schedule").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("nflTeam.schedule").assertExists()
        // The placeholder would have answered instead if the route had not been split, and its back affordance is the
        // tag only it carries.
        rule.onAllNodesWithTag("league.notReady.back").assertCountEquals(0)
    }
}
