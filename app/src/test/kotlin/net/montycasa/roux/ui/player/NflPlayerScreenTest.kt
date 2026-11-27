package net.montycasa.roux.ui.player

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflGameColumn
import net.montycasa.roux.domain.model.NflGameLog
import net.montycasa.roux.domain.model.NflGameLogEntry
import net.montycasa.roux.domain.model.NflGameStatGroup
import net.montycasa.roux.domain.model.NflPlayerId
import net.montycasa.roux.domain.model.NflPlayerProfile
import net.montycasa.roux.domain.model.NflPlayerSeason
import net.montycasa.roux.domain.model.NflSeasonStat
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.team.NflTeamPageFixtures
import net.montycasa.roux.ui.team.NflTeamPageScreen
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The football player page: its header, summary and feed-built game log, and the roster row that opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflPlayerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theHeaderRendersTheProfileFactsTheFeedStates() {
        show(loaded())

        rule.onNodeWithTag("nflPlayer.name").assertTextEquals("Jared Goff")
        rule.onNodeWithTag("nflPlayer.identity").assertTextEquals("#16 · QB")
        rule.onNodeWithTag("nflPlayer.team").assertTextEquals("Detroit Lions")
        rule.onNodeWithTag("nflPlayer.facts").assertTextEquals("6' 4\" · 217 lbs · Age 32 · California · 11th Season")
    }

    @Test
    fun theSeasonSummaryRendersTheFeedsOwnValues() {
        show(loaded())

        rule.onNodeWithTag("nflPlayer.summary.passingYards.label").assertTextEquals("Passing Yards")
        rule.onNodeWithTag("nflPlayer.summary.passingYards").assertTextEquals("2,716")
        rule.onNodeWithTag("nflPlayer.summary.adjQBR").assertTextEquals("63.9")
    }

    /** The log's headings come from the feed, and the newest game is the first row whatever order the log holds. */
    @Test
    fun theGameLogRendersTheFeedsColumnsNewestGameFirst() {
        show(loaded())

        rule.onNodeWithTag("nflPlayer.log.header.completions").assertTextEquals("CMP")
        rule.onNodeWithTag("nflPlayer.log.header.rushingYards").assertTextEquals("YDS")
        rule.onNodeWithTag("nflPlayer.log.label.0").assertTextEquals("Nov 26 · vs CHI · L")
        rule.onNodeWithTag("nflPlayer.log.row.0.completions").assertTextEquals("25")
        rule.onNodeWithTag("nflPlayer.log.label.1").assertTextEquals("Nov 20 · @ GB · W")
        rule.onNodeWithTag("nflPlayer.log.row.1.completions").assertTextEquals("19")
    }

    /** A stat the feed did not state leaves an empty cell: no zero and no dash are printed in its place. */
    @Test
    fun aMissingStatPrintsNothing() {
        show(loaded())

        rule.onNodeWithTag("nflPlayer.log.row.0.interceptions").assertTextEquals("")
        rule.onNodeWithText("-").assertDoesNotExist()
        rule.onAllNodesWithTag("nflPlayer.log.row.0.interceptions").assertCountEquals(1)
    }

    @Test
    fun aRosterRowOpensItsPlayersFootballPage() {
        val opened = mutableListOf<NflPlayerId>()
        rule.setContent {
            RouxTheme {
                NflTeamPageScreen(
                    state = NflTeamPageFixtures.buffalo(),
                    onBack = {},
                    onPlayerClick = { opened += it },
                )
            }
        }
        rule.onNodeWithTag("nflTeam.tab.Roster").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nflTeam.rosterRow.allen").performClick()

        assertEquals(listOf(NflPlayerId("allen")), opened)
    }

    private fun show(state: NflPlayerUiState) {
        rule.setContent {
            RouxTheme {
                NflPlayerScreen(
                    state = state,
                    onBack = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun loaded() =
        NflPlayerUiState(
            playerId = NflPlayerId("3046779"),
            profile = Loadable(profile(), Freshness.Fresh, null),
            gameLog = Loadable(log(), Freshness.Fresh, null),
            loading = false,
        )

    private fun profile() =
        NflPlayerProfile(
            playerId = NflPlayerId("3046779"),
            name = "Jared Goff",
            positionAbbreviation = "QB",
            jersey = "#16",
            heightText = "6' 4\"",
            weightText = "217 lbs",
            age = 32,
            college = "California",
            experienceText = "11th Season",
            team = TeamRef(TeamKey(League.NFL, "DET"), name = "Detroit Lions"),
            season = NflPlayerSeason(year = 2026, displayName = "2026"),
            seasonSummaryTitle = "2026 regular season stats",
            seasonStats =
                listOf(
                    NflSeasonStat("passingYards", "Passing Yards", "2,716"),
                    NflSeasonStat("passingTouchdowns", "Passing Touchdowns", "20"),
                    NflSeasonStat("interceptions", "Interceptions", "4"),
                    NflSeasonStat("adjQBR", "Adjusted QBR", "63.9"),
                ),
        )

    private fun log() =
        NflGameLog(
            seasonLabel = "2026 Regular Season",
            groups =
                listOf(
                    NflGameStatGroup(
                        name = "passing",
                        displayName = "Passing",
                        columns =
                            listOf(
                                column("completions", "Completions", "CMP"),
                                column("interceptions", "Interceptions", "INT"),
                            ),
                    ),
                    NflGameStatGroup(
                        name = "rushing",
                        displayName = "Rushing",
                        columns = listOf(column("rushingYards", "Rushing Yards", "YDS")),
                    ),
                ),
            games =
                listOf(
                    NflGameLogEntry(
                        eventId = "401872923",
                        gameDate = "2026-11-20T18:00:00.000+00:00",
                        atVs = "@",
                        opponentAbbreviation = "GB",
                        result = "W",
                        stats = mapOf("completions" to "19", "rushingYards" to "4"),
                    ),
                    NflGameLogEntry(
                        eventId = "401873081",
                        gameDate = "2026-11-26T18:00:00.000+00:00",
                        atVs = "vs",
                        opponentAbbreviation = "CHI",
                        result = "L",
                        stats = mapOf("completions" to "25", "rushingYards" to "10"),
                    ),
                ),
        )

    private fun column(
        name: String,
        displayName: String,
        shortLabel: String,
    ) = NflGameColumn(name = name, displayName = displayName, shortLabel = shortLabel)
}
