@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.ClubSeasonStats
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.RankedStat
import net.montycasa.roux.domain.model.SeasonRecord
import net.montycasa.roux.domain.model.SeasonSeriesGame
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.Venue
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * The pre-game page: the records in the hero and the four cards after the stats, and nothing of them once a game has
 * started. Each card hides on its own when its source is empty.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class PreGameDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a scheduled game shows both records in the hero and the four cards in page order`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        compose.onNodeWithTag("detail.hero.away.record", useUnmergedTree = true).assertTextEquals("COL 10–6–2")
        compose.onNodeWithTag("detail.hero.home.record", useUnmergedTree = true).assertTextEquals("TOR 12–4–1")
        compose.onNodeWithTag("detail.pregame.last10.away").assertExists()
        compose.onNodeWithTag("detail.pregame.stat.Power play").assertExists()
        compose.onNodeWithTag("detail.pregame.series.standing").assertTextEquals("TOR leads 1–0")
        compose.onNodeWithText("M. Blackwood #30").assertExists()

        val tops =
            listOf(
                "detail.section.last10",
                "detail.section.season-stats",
                "detail.section.season-series",
                "detail.section.goaltenders",
                "detail.section.info",
            ).map { tag ->
                compose
                    .onNodeWithTag(tag)
                    .fetchSemanticsNode()
                    .positionInRoot.y
            }
        assertTrue("the cards are out of page order: $tops", tops.zipWithNext().all { (above, below) -> above < below })
    }

    @Test
    fun `a scheduled game names no starter for either club`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        compose.onAllNodesWithText("starter", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun `a final game keeps its page, with no records and no pre-game cards`() {
        showStarted(GameState.Final)
    }

    @Test
    fun `a live game keeps its page, with no records and no pre-game cards`() {
        showStarted(GameState.Live())
    }

    // The data is present for a started game too; only the game's state decides, so this fails if the gate is dropped.
    private fun showStarted(state: GameState) {
        show(scheduled.copy(state = state), landing = preGameLanding.copy(state = state), rail = preGameRail)

        compose.onNodeWithTag("detail.hero.away.record", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail.hero.home.record", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail.section.last10").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.season-stats").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.season-series").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.goaltenders").assertDoesNotExist()
    }

    @Test
    fun `a scheduled game the feed knows nothing about draws none of the four cards`() {
        show(scheduled, landing = GameLanding(state = GameState.Scheduled), rail = GameRightRail())

        compose.onNodeWithTag("detail.hero.away.record", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail.section.last10").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.season-stats").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.season-series").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.goaltenders").assertDoesNotExist()
    }

    private fun show(
        game: Game,
        landing: GameLanding,
        rail: GameRightRail,
    ) {
        val detail =
            GameDetail(
                landing = Loadable(landing, Freshness.Fresh, null),
                boxscore = Loadable(GameBoxscore(), Freshness.Fresh, null),
                rightRail = Loadable(rail, Freshness.Fresh, null),
            )
        compose.setContent {
            RouxTheme(dark = false) {
                GameDetailScreen(
                    game = game,
                    state = DetailUiState(detail = detail, refreshing = false, unexpectedFailure = false),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
    }

    private companion object {
        val scheduled =
            Game(
                id = GameId("2026020286"),
                date = "2026-11-10",
                state = GameState.Scheduled,
                away = TeamRef(TeamKey("COL"), name = "Avalanche"),
                home = TeamRef(TeamKey("TOR"), name = "Maple Leafs"),
                startTimeUtc = Instant.parse("2026-11-11T00:00:00Z"),
            )

        val preGameLanding =
            GameLanding(
                state = GameState.Scheduled,
                venue = Venue("Ball Arena", "Denver"),
                awayRecord = SeasonRecord(10, 6, 2),
                homeRecord = SeasonRecord(12, 4, 1),
                awayTeamId = 21L,
                homeTeamId = 10L,
                goalies = listOf(GoalieSeason(teamId = 21L, name = "M. Blackwood", sweaterNumber = 30, gamesPlayed = 12)),
            )

        val preGameRail =
            GameRightRail(
                awayForm =
                    ClubForm(
                        record = SeasonRecord(6, 3, 1),
                        results = listOf("W", "L", "W", "W", "OTL", "W", "L", "W", "W", "L"),
                        streakType = "L",
                        streak = 1,
                    ),
                homeForm = ClubForm(record = SeasonRecord(7, 2, 1), results = listOf("W", "W", "L"), streakType = "W", streak = 2),
                awaySeasonStats = seasonStats(powerPlay = 0.25, rank = 12),
                homeSeasonStats = seasonStats(powerPlay = 0.22, rank = 20),
                seasonSeries =
                    listOf(
                        SeasonSeriesGame(
                            gameId = 2025020101L,
                            gameType = 2,
                            state = GameState.Final,
                            awayAbbrev = "TOR",
                            awayScore = 4,
                            homeAbbrev = "COL",
                            homeScore = 2,
                            gameDate = "2025-12-01",
                        ),
                    ),
            )

        fun seasonStats(
            powerPlay: Double,
            rank: Int,
        ) = ClubSeasonStats(
            powerPlay = RankedStat(powerPlay, rank),
            penaltyKill = RankedStat(0.8, 5),
            faceoff = RankedStat(0.5, 10),
            goalsFor = RankedStat(3.4, 4),
            goalsAgainst = RankedStat(2.9, 9),
        )
    }
}
