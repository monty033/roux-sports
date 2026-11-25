package net.montycasa.roux.ui.teams

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.ReachTargetTag
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The football picker: thirty-two clubs under eight division headings, with hockey's list left alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflTeamsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val football = NflTeamsFixtures.state()

    private val rowMatcher =
        SemanticsMatcher("team row") {
            val tag = it.config.getOrNull(SemanticsProperties.TestTag)
            tag?.startsWith("teams.row.") == true && tag.startsWith("teams.row.body.").not()
        }
    private val headingMatcher =
        SemanticsMatcher("division heading") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("teams.group.") == true
        }

    private fun launch(
        state: TeamsUiState,
        onToggle: (TeamKey) -> Unit = {},
        onTeamClick: (TeamKey) -> Unit = {},
        onOpenAlerts: (TeamRef) -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                TeamsScreen(
                    state = state,
                    onToggle = onToggle,
                    onRetry = {},
                    onTeamClick = onTeamClick,
                    onOpenAlerts = onOpenAlerts,
                )
            }
        }
    }

    @Test
    fun footballDrawsThirtyTwoClubsInEightDivisions() {
        launch(football)

        rule.onAllNodes(rowMatcher, useUnmergedTree = true).assertCountEquals(32)
        rule.onAllNodes(headingMatcher, useUnmergedTree = true).assertCountEquals(8)
        listOf("AFC East", "AFC North", "AFC South", "AFC West", "NFC East", "NFC North", "NFC South", "NFC West").forEach {
            rule.onNodeWithTag("teams.group.$it", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun theHeadingsFollowTheFeedsOrderAndEachClubSitsUnderItsOwnDivision() {
        // Four clubs each, in the order the screen draws them: the division is a fact of the feed, so a club under the
        // wrong heading would be a made-up division.
        assertEquals(
            listOf("AFC East", "AFC North", "AFC South", "AFC West", "NFC East", "NFC North", "NFC South", "NFC West"),
            football.groups.map { it.division },
        )
        assertTrue(football.groups.all { it.teams.size == 4 })
        assertEquals(football.rows, football.groups.flatMap { it.teams })
        assertEquals(
            setOf("BUF", "NE", "NYJ", "MIA"),
            football.groups
                .first()
                .teams
                .map { it.abbrev }
                .toSet(),
        )
    }

    @Test
    fun footballRowsKeepTheSharedRowsCrestNameStarAndBell() {
        val clicked = mutableListOf<TeamKey>()
        val toggled = mutableListOf<TeamKey>()
        val alerts = mutableListOf<TeamRef>()
        launch(football, onToggle = { toggled += it }, onTeamClick = { clicked += it }, onOpenAlerts = { alerts += it })
        val eagles = football.rows.single { it.abbrev == "PHI" }

        rule.onNodeWithTag("teams.row.body.PHI").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(eagles.name!!)),
        )
        rule.onNodeWithTag("teams.row.body.PHI").performClick()
        rule.onNodeWithTag("teams.alerts.open.PHI").performScrollTo().performClick()
        toggle("PHI").performScrollTo().performClick()

        assertEquals(listOf(TeamKey(League.NFL, "PHI")), clicked)
        assertEquals(listOf(TeamKey(League.NFL, "PHI")), toggled)
        assertEquals(listOf(eagles), alerts)
    }

    @Test
    fun aFootballStarIsOnInFootballsListAndOffInHockeysForTheSameAbbreviation() {
        val starred = setOf(TeamKey(League.NFL, "PHI"))
        launch(NflTeamsFixtures.state(favourites = starred))
        toggle("PHI").performScrollTo().assertIsOn()
    }

    @Test
    fun aFootballStarDoesNotStarHockeysClubWithTheSameAbbreviation() {
        val hockey = teamStandings().map { it.team }
        assertTrue("the collision needs a hockey PHI", hockey.any { it.abbrev == "PHI" })
        launch(TeamsUiState(rows = hockey, favourites = setOf(TeamKey(League.NFL, "PHI")), isRefreshing = false))
        toggle("PHI").performScrollTo().assertIsOff()
    }

    @Test
    fun aHockeyStarDoesNotStarTheFootballClubWithTheSameAbbreviation() {
        launch(NflTeamsFixtures.state(favourites = setOf(TeamKey(League.NHL, "PHI"))))
        toggle("PHI").performScrollTo().assertIsOff()
    }

    @Test
    fun hockeysListDrawsNoDivisionHeadings() {
        val hockey = teamStandings().map { it.team }
        launch(TeamsUiState(rows = hockey, isRefreshing = false))

        rule.onAllNodes(rowMatcher, useUnmergedTree = true).assertCountEquals(32)
        rule.onAllNodes(headingMatcher, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun toggle(abbrev: String) = rule.onNode(SemanticsMatcher.expectValue(ReachTargetTag, "teams.toggle.$abbrev"))
}
