package net.montycasa.roux.ui.team

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.RosterPlayer
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
class TeamRosterTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-03T12:00:00Z")

    @Test
    fun scheduleIsSelectedFirstAndTheTabsSwapContentWithoutLosingTheSchedule() {
        showScreen()

        rule.onNodeWithTag("team.tab.Schedule").assertIsSelected()
        rule.onNodeWithTag("team.schedule.list").assertExists()

        rule.onNodeWithTag("team.tab.Roster").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("team.tab.Roster").assertIsSelected()
        rule.onNodeWithTag("team.tab.Schedule").assertIsNotSelected()
        rule.onNodeWithTag("team.roster.list").assertExists()
        rule.onNodeWithTag("team.schedule.list").assertDoesNotExist()

        rule.onNodeWithTag("team.tab.Schedule").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("team.tab.Schedule").assertIsSelected()
        rule.onNodeWithTag("team.schedule.list").assertExists()
        rule.onNodeWithTag("team.roster.list").assertDoesNotExist()
    }

    @Test
    fun theRosterShowsForwardsDefenseGoaliesInThatOrder() {
        showScreen()
        rule.onNodeWithTag("team.tab.Roster").performClick()
        rule.waitForIdle()

        val forwards =
            rule
                .onNodeWithTag("team.roster.group.forwards")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val defense =
            rule
                .onNodeWithTag("team.roster.group.defense")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val goalies =
            rule
                .onNodeWithTag("team.roster.group.goalies")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("Forwards must precede Defense", forwards < defense)
        assertTrue("Defense must precede Goalies", defense < goalies)

        rule.onNodeWithTag("team.roster.player.91.name", useUnmergedTree = true).assertTextEquals("Leo Carlsson")
        rule.onNodeWithTag("team.roster.player.91.number", useUnmergedTree = true).assertTextEquals("91")
        rule.onNodeWithTag("team.roster.player.91.position", useUnmergedTree = true).assertTextEquals("C")
    }

    @Test
    fun aMissingHeadshotFallsBackToInitials() {
        showScreen()
        rule.onNodeWithTag("team.tab.Roster").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("LC", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aRosterFailureKeepsTheHeaderAndTabsAndOffersRetry() {
        var retries = 0
        showScreen(
            roster = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
            onRosterRetry = { retries++ },
        )
        rule.onNodeWithTag("team.tab.Roster").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("team.name").assertIsDisplayed()
        rule.onNodeWithTag("team.tab.Schedule").assertIsDisplayed()
        rule.onNodeWithTag("team.tab.Roster").assertIsSelected()
        rule.onNodeWithTag("team.roster.error").assertIsDisplayed()
        rule.onNodeWithText("Roster could not be loaded.").assertIsDisplayed()
        rule.onNodeWithTag("team.roster.retry").assertExists()

        rule.onNodeWithTag("team.roster.retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun aHeaderRefreshDoesNotPutTheRostersRetryIntoASpinner() {
        var retries = 0
        showScreen(
            roster = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
            headerLoading = true,
            onRosterRetry = { retries++ },
        )
        rule.onNodeWithTag("team.tab.Roster").performClick()
        rule.waitForIdle()

        // The header's own refresh flag must not reach the roster's retry: a spinner there would be a lie, and the
        // button would swallow the tap.
        rule.onNodeWithTag("team.roster.retry").performClick()
        assertEquals(1, retries)
    }

    private fun showScreen(
        roster: Loadable<ClubRoster> = Loadable(sampleRoster, Freshness.Fresh, null),
        headerLoading: Boolean = false,
        rosterLoading: Boolean = false,
        onRosterRetry: () -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                TeamDetailScreen(
                    state =
                        TeamDetailUiState(
                            teamKey = TeamKey("TOR"),
                            row = toronto,
                            isLoading = headerLoading,
                            schedule = Loadable(listOf(game), Freshness.Fresh, null),
                            roster = roster,
                            isRosterLoading = rosterLoading,
                        ),
                    onBack = {},
                    onRosterRetry = onRosterRetry,
                    now = now,
                )
            }
        }
    }

    private val sampleRoster =
        ClubRoster(
            forwards =
                listOf(
                    RosterPlayer(id = 91, name = "Leo Carlsson", sweaterNumber = 91, positionCode = "C", headshotUrl = null),
                    RosterPlayer(id = 12, name = "Sam Colangelo", sweaterNumber = 12, positionCode = "R", headshotUrl = null),
                ),
            defense = listOf(RosterPlayer(id = 14, name = "Drew Helleson", sweaterNumber = 14, positionCode = "D")),
            goalies = listOf(RosterPlayer(id = 31, name = "Laurent Brossoit", sweaterNumber = 31, positionCode = "G")),
        )

    private val game =
        ClubGame(
            id = GameId("next"),
            date = "2026-11-05",
            startTimeUtc = Instant.parse("2026-11-05T20:00:00Z"),
            home = TeamRef(TeamKey("TOR")),
            away = TeamRef(TeamKey("BOS")),
            state = GameState.Scheduled,
        )

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
}
