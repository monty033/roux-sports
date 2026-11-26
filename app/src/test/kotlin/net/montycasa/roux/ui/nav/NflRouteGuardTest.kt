package net.montycasa.roux.ui.nav

import androidx.compose.ui.test.assertTextEquals
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
 * Drives the real nav host with route strings, the way a tap would. An NFL club or game must land on football's own
 * page and never on an NHL page; an unknown league must open no page; a league-less route must still open the NHL club.
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

    private fun nflOvertimeGame() =
        Game(
            id = GameId(League.NFL, "401872923"),
            date = "2026-09-13",
            state = GameState.Final,
            home = TeamRef(TeamKey(League.NFL, "DET")),
            away = TeamRef(TeamKey(League.NFL, "NO")),
        )

    /**
     * A football club's route lands on football's page.
     *
     * This used to assert the placeholder, which is what the route answered before football had a club page. The guard
     * still holds: what must never happen is the *hockey* page answering for a football club.
     */
    @Test
    fun anNflClubRouteLandsOnFootballsPageNotTheNhlPage() {
        launch()
        navigate(teamRoute(TeamKey(League.NFL, "PHI")))

        rule.waitUntil(timeoutMillis = 10_000) { nodes("nflTeam.name").isNotEmpty() }
        assertTrue("the hockey page must not answer for a football club", nodes("team.page.PHI").isEmpty())
        assertTrue("the placeholder must not answer either", nodes("league.notReady.back").isEmpty())
    }

    /** The guard also covers games: an NFL game route must open football's page, not hockey's. */
    @Test
    fun anNflGameRouteLandsOnFootballsGamePageNotTheNhlPage() {
        launch()
        navigate(detailRoute(nflGame()))

        rule.waitUntil(timeoutMillis = 10_000) { nodes("nflGame.hero").isNotEmpty() }
        assertTrue("the hockey game page must not answer for a football game", nodes("detail.hero").isEmpty())
        assertTrue("the hockey tab strip must not answer either", nodes("detail.tab.strip").isEmpty())
        assertTrue("the placeholder must not answer either", nodes("league.notReady.back").isEmpty())
    }

    /** The route hands the event id to the summary request, and the feed's own final wording reaches the hero. */
    @Test
    fun anNflOvertimeGameRouteShowsTheFeedsFinalOt() {
        launch()
        navigate(detailRoute(nflOvertimeGame()))

        rule.waitUntil(timeoutMillis = 10_000) { nodes("nflGame.status").isNotEmpty() }
        rule.onNodeWithTag("nflGame.status").assertTextEquals("Final/OT")
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
