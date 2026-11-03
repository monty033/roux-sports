package net.montycasa.roux.ui.team

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
class TeamDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val toronto =
        StandingRow(
            team = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs"),
            gp = 12,
            w = 9,
            l = 2,
            otl = 1,
            pts = 19,
            leagueSequence = 7,
            conferenceSequence = 3,
            divisionSequence = 2,
            wildcardSequence = 0,
            conference = Conference.Eastern,
            division = Division.Atlantic,
        )

    private fun show(state: TeamDetailUiState) {
        rule.setContent { RouxTheme { TeamDetailScreen(state = state, onBack = {}) } }
    }

    @Test
    fun theHeaderShowsTheRecordPointsAndBothRanksFromTheRow() {
        show(TeamDetailUiState(TeamKey("TOR"), row = toronto, isLoading = false))

        rule.onNodeWithTag("team.name").assertIsDisplayed().assertTextEquals("Toronto Maple Leafs")
        rule.onNodeWithTag("team.record").assertIsDisplayed()
        rule.onNodeWithText("9-2-1").assertIsDisplayed()
        rule.onNodeWithTag("team.points").assertIsDisplayed()
        rule.onNodeWithText("19 points").assertIsDisplayed()
        // Conference and league are asserted separately: swapping the two sequence fields must fail here.
        rule.onNodeWithTag("team.rank.conference").assertIsDisplayed()
        rule.onNodeWithText("3rd in conference").assertIsDisplayed()
        rule.onNodeWithTag("team.rank.league").assertIsDisplayed()
        rule.onNodeWithText("7th in league").assertIsDisplayed()
        rule.onNodeWithTag("team.division").assertIsDisplayed()
        rule.onNodeWithText("Eastern Conference · Atlantic Division").assertIsDisplayed()
    }

    @Test
    fun anAbsentRowShowsTheCrestAndNameButNoNumericRecord() {
        show(TeamDetailUiState(TeamKey("ANA"), row = null, isLoading = false))

        rule.onNodeWithTag("team.crest").assertIsDisplayed()
        rule.onNodeWithTag("team.name").assertIsDisplayed().assertTextEquals("Anaheim Ducks")
        rule.onNodeWithTag("team.noRecord").assertIsDisplayed()
        rule.onNodeWithText("The standings table doesn’t have this team’s record yet.").assertIsDisplayed()

        listOf("team.record", "team.points", "team.rank.conference", "team.rank.league", "team.division").forEach { tag ->
            rule.onNodeWithTag(tag).assertDoesNotExist()
        }
        assertEquals(
            "an absent row must not be rendered as a zero record",
            0,
            rule.onAllNodesWithText("0-0-0").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun ranksUseTheFeedOrderRatherThanASortPosition() {
        // A different pair of values from the fixture: if either field were substituted for the other, this fails.
        show(
            TeamDetailUiState(
                TeamKey("TOR"),
                row = toronto.copy(conferenceSequence = 8, leagueSequence = 1),
                isLoading = false,
            ),
        )

        rule.onNodeWithText("8th in conference").assertIsDisplayed()
        rule.onNodeWithText("1st in league").assertIsDisplayed()
    }
}
