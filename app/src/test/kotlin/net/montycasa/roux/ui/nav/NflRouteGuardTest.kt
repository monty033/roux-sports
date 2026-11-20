package net.montycasa.roux.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.scores.waitForFixtureScores
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real nav host with route strings, the way a tap would. An NFL club or game must land on the placeholder
 * and never on an NHL page; an unknown league must open no page; a league-less route must still open the NHL club.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
class NflRouteGuardTest {
    @get:Rule
    val rule = createComposeRule()

    private var controller: NavHostController? = null

    private fun launch() {
        rule.setContent {
            RouxTheme(dark = false) {
                val nav = rememberNavController()
                controller = nav
                RouxNavHost(navController = nav)
            }
        }
        rule.waitForFixtureScores()
    }

    private fun navigate(route: String) {
        rule.runOnIdle { controller!!.navigate(route) }
        rule.waitForIdle()
    }

    private fun nodes(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes()

    private fun nflGame() =
        Game(
            id = GameId(League.NFL, "401873066"),
            date = "2026-11-19",
            state = GameState.Final,
            home = TeamRef(TeamKey(League.NFL, "HOU")),
            away = TeamRef(TeamKey(League.NFL, "IND")),
        )

    /** The placeholder is the NFL landing; its back control is the only proof it is on screen. */
    @Test
    fun anNflClubRouteLandsOnThePlaceholderNotTheNhlPage() {
        launch()
        navigate(teamRoute(TeamKey(League.NFL, "PHI")))

        rule.onNodeWithTag("league.notReady.back").assertIsDisplayed()
        assertTrue(nodes("team.page.PHI").isEmpty())
    }

    /** The guard also covers games: an NFL game route must not open the NHL game page. */
    @Test
    fun anNflGameRouteLandsOnThePlaceholderNotTheGamePage() {
        launch()
        navigate(detailRoute(nflGame()))

        rule.onNodeWithTag("league.notReady.back").assertIsDisplayed()
        assertTrue(nodes("detail.back").isEmpty())
    }

    @Test
    fun anUnknownLeagueClubRouteOpensNoPage() {
        launch()
        navigate("team/PHI?league=mlb")

        assertTrue(nodes("league.notReady.back").isEmpty())
        assertTrue(nodes("team.page.PHI").isEmpty())
    }

    @Test
    fun anUnknownLeagueGameRouteOpensNoPage() {
        launch()
        navigate("detail/401873066?away=IND&home=HOU&league=mlb")

        assertTrue(nodes("league.notReady.back").isEmpty())
        assertTrue(nodes("detail.back").isEmpty())
    }

    /** A route written before leagues were named has no league argument and must still open the NHL club. */
    @Test
    fun aLeagueLessClubRouteStillOpensTheNhlPage() {
        launch()
        navigate("team/ANA")

        rule.waitUntil(timeoutMillis = 10_000) { nodes("team.page.ANA").isNotEmpty() }
        assertTrue(nodes("league.notReady.back").isEmpty())
    }
}
