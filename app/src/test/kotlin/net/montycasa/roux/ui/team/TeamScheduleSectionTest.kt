package net.montycasa.roux.ui.team

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.nav.detailRoute
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
class TeamScheduleSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-03T12:00:00Z")

    @Test
    fun tappingAnUpcomingAndAResultBothOpenThatGamesRoute() {
        var route: String? = null
        show(
            state =
                state(
                    schedule =
                        Loadable(
                            listOf(upcoming, result),
                            Freshness.Fresh,
                            null,
                        ),
                ),
            onGameClick = { game -> route = detailRoute(game) },
        )

        rule.onNodeWithTag("team.schedule.upcoming.row.upcoming-1").performClick()
        rule.waitForIdle()
        assertEquals(detailRoute(upcoming), route)
        assertTrue(route.orEmpty().startsWith("detail/upcoming-1"))

        rule.onNodeWithTag("team.schedule.results.row.result-1").performClick()
        rule.waitForIdle()
        assertEquals(detailRoute(result), route)
        assertTrue(route.orEmpty().startsWith("detail/result-1"))
    }

    @Test
    fun anEmptyUpcomingGroupKeepsItsPlainLine() {
        show(state = state(schedule = Loadable(listOf(result), Freshness.Fresh, null)))

        rule.onNodeWithTag("team.schedule.upcoming.empty").assertIsDisplayed()
        rule.onNodeWithText("No upcoming games.").assertIsDisplayed()
        rule.onNodeWithTag("team.schedule.results.row.result-1").assertIsDisplayed()
    }

    @Test
    fun aScheduleFailureLeavesTheHeaderOnScreen() {
        show(
            state =
                state(
                    schedule =
                        Loadable(
                            data = null,
                            freshness = Freshness.Missing,
                            error = LoadError.Network,
                        ),
                ),
        )

        rule.onNodeWithTag("team.name").assertIsDisplayed()
        rule.onNodeWithTag("team.schedule.error").assertIsDisplayed()
        rule.onNodeWithText("Schedule could not be loaded.").assertIsDisplayed()
        rule.onNodeWithTag("team.schedule.retry").assertExists()
    }

    @Test
    fun aPostponedGameSaysSoRatherThanShowingANormalStartTime() {
        show(
            state =
                state(
                    schedule =
                        Loadable(
                            listOf(upcoming.copy(gameScheduleState = "PPD")),
                            Freshness.Fresh,
                            null,
                        ),
                ),
        )

        rule.onNodeWithText("Postponed").assertIsDisplayed()
    }

    @Test
    fun aFinishedRowSaysFinalAndNoScoreline() {
        show(state = state(schedule = Loadable(listOf(result), Freshness.Fresh, null)))

        rule.onNodeWithTag("team.schedule.results.row.result-1.state", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Final", useUnmergedTree = true).assertExists()
        // There is no score in the payload, so the row has no numeric result to show.
        assertTrue(
            rule
                .onAllNodesWithText("4")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    private fun show(
        state: TeamDetailUiState,
        onGameClick: (ClubGame) -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                TeamDetailScreen(
                    state = state,
                    onBack = {},
                    onGameClick = onGameClick,
                )
            }
        }
    }

    private fun state(schedule: Loadable<List<ClubGame>>): TeamDetailUiState =
        TeamDetailUiState(
            teamKey = TeamKey("TOR"),
            row = toronto,
            isLoading = false,
            schedule = schedule,
        )

    private val upcoming =
        ClubGame(
            id = GameId("upcoming-1"),
            date = "2026-11-04",
            startTimeUtc = Instant.parse("2026-11-04T20:00:00Z"),
            venue = "Scotiabank Arena",
            home = TeamRef(TeamKey("TOR")),
            away = TeamRef(TeamKey("BOS")),
            state = GameState.Scheduled,
        )

    private val result =
        ClubGame(
            id = GameId("result-1"),
            date = "2026-11-01",
            startTimeUtc = Instant.parse("2026-11-01T20:00:00Z"),
            venue = "TD Garden",
            home = TeamRef(TeamKey("BOS")),
            away = TeamRef(TeamKey("TOR")),
            state = GameState.Final,
            finished = true,
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
