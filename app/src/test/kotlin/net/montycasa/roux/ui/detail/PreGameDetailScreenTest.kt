@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
import net.montycasa.roux.domain.model.SkaterSeason
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.Venue
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
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
    fun `a scheduled game shows both records in the hero and the cards in page order`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        compose.onNodeWithTag("detail.hero.away.record", useUnmergedTree = true).assertTextEquals("COL 10–6–2")
        compose.onNodeWithTag("detail.hero.home.record", useUnmergedTree = true).assertTextEquals("TOR 12–4–1")
        compose.onNodeWithText("LAST TEN GAMES").assertExists()
        compose.onNodeWithTag("detail.pregame.stat.Power play").assertExists()
        compose.onNodeWithTag("detail.pregame.series.standing").assertTextEquals("TOR leads 1–0")
        compose.onNodeWithText("M. Blackwood").assertExists()
        compose.onNodeWithText("12 GP · 9–3–1").assertExists()

        val tops =
            listOf(
                "detail.section.last10",
                "detail.section.season-stats",
                "detail.section.season-series",
                "detail.section.top-performers",
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
    fun `the two-club cards put the away club on the left and the home club on the right`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        listOf("detail.pregame.last10", "detail.pregame.season", "detail.pregame.performers", "detail.pregame.goalies").forEach { block ->
            assertLeftOf("$block.club.away.name", "$block.club.home.name")
        }
        assertLeftOf("detail.pregame.last10.away", "detail.pregame.last10.home")
        assertLeftOf("detail.pregame.performers.away", "detail.pregame.performers.home")
        assertLeftOf("detail.pregame.goalies.away", "detail.pregame.goalies.home")
    }

    @Test
    fun `the last ten card prints each record and streak in its own half, with no result chips`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        compose.onNodeWithTag("detail.pregame.last10.away").assertTextEquals("6–3–1 (L1)")
        compose.onNodeWithTag("detail.pregame.last10.home").assertTextEquals("7–2–1 (W2)")
        compose.onAllNodesWithText("W").assertCountEquals(0)
        compose.onAllNodesWithText("L").assertCountEquals(0)
    }

    @Test
    fun `the top performers are the three highest point scorers for each club, in points order`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        compose.onNodeWithText("10 G · 20 A · 30 PTS").assertExists()
        compose.onNodeWithText("11 G · 16 A · 27 PTS").assertExists()
        // Lehkonen has the most goals for COL, so a card ranked by goals would list him ahead of Makar. He is fourth on points.
        compose.onNodeWithText("A. Lehkonen").assertDoesNotExist()
        assertAbove("N. MacKinnon", "M. Necas")
        assertAbove("M. Necas", "C. Makar")
    }

    @Test
    fun `a goaltender with no games played is not listed on his club's card`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        compose.onNodeWithText("A. Stolarz").assertExists()
        compose.onNodeWithText("Z. Benched").assertDoesNotExist()
    }

    @Test
    fun `a top performer with a known id is one click target, and a tap opens that player`() {
        val clicked = mutableListOf<Long>()
        show(scheduled, landing = preGameLanding, rail = preGameRail, onPlayerClick = { clicked += it })

        val row = "detail.pregame.performers.away.N. MacKinnon.row"
        compose.onNodeWithTag(row).assert(hasClickAction()).assert(hasContentDescription("N. MacKinnon, opens the player page"))
        compose.onNodeWithTag(row).performScrollTo().performClick()
        compose.onNodeWithTag("detail.pregame.performers.home.A. Matthews.row").performScrollTo().performClick()

        assertEquals(listOf(8477492L, 8479318L), clicked)
    }

    @Test
    fun `a goaltender with a known id is a click target that opens that goaltender`() {
        val clicked = mutableListOf<Long>()
        show(scheduled, landing = preGameLanding, rail = preGameRail, onPlayerClick = { clicked += it })

        compose
            .onNodeWithTag("detail.pregame.goalies.away.M. Blackwood.row")
            .assert(hasClickAction())
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithTag("detail.pregame.goalies.home.A. Stolarz.row")
            .assert(hasClickAction())
            .performScrollTo()
            .performClick()

        assertEquals(listOf(8478464L, 8477974L), clicked)
    }

    @Test
    fun `a player with no id has no click action and nothing is passed on a tap`() {
        val clicked = mutableListOf<Long>()
        show(scheduled, landing = preGameLanding, rail = preGameRail, onPlayerClick = { clicked += it })

        compose.onNodeWithTag("detail.pregame.performers.away.C. Makar.row").assert(!hasClickAction())
        compose.onNodeWithText("C. Makar").assertExists()
        assertTrue("a tap on a player with no id opened $clicked", clicked.isEmpty())
    }

    @Test
    fun `the photo and the name are one target, and neither is a target of its own`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        val photo = "detail.pregame.performers.away.N. MacKinnon"
        compose.onNodeWithTag("$photo.row").assert(hasClickAction())
        compose.onNodeWithTag("$photo.mugshot", useUnmergedTree = true).assert(!hasClickAction())
        compose.onNodeWithTag("$photo.name", useUnmergedTree = true).assert(!hasClickAction())
    }

    @Test
    fun `a player row is at least 48 dp tall, its photo is 44 dp at normal scale, and the card headers carry 26 dp crests`() {
        show(scheduled, landing = preGameLanding, rail = preGameRail)

        val row = compose.onNodeWithTag("detail.pregame.performers.away.N. MacKinnon.row").getUnclippedBoundsInRoot()
        val rowHeight = row.bottom - row.top
        assertTrue("the row is $rowHeight tall, under 48 dp", rowHeight >= 48.dp)
        val photo = compose.onNodeWithTag("detail.pregame.performers.away.N. MacKinnon.mugshot", useUnmergedTree = true)
        val photoBounds = photo.getUnclippedBoundsInRoot()
        assertEquals("the photo is not 44 dp", 44f, (photoBounds.right - photoBounds.left).value, 0.5f)
        listOf("detail.pregame.last10", "detail.pregame.season", "detail.pregame.performers", "detail.pregame.goalies").forEach { block ->
            listOf("away", "home").forEach { side ->
                val crest = compose.onNodeWithTag("$block.club.$side.crest").getUnclippedBoundsInRoot()
                assertEquals("$block $side crest is not 26 dp", 26f, (crest.right - crest.left).value, 0.5f)
            }
        }
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
        compose.onNodeWithTag("detail.section.top-performers").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.goaltenders").assertDoesNotExist()
    }

    @Test
    fun `a scheduled game the feed knows nothing about draws none of the four cards`() {
        show(scheduled, landing = GameLanding(state = GameState.Scheduled), rail = GameRightRail())

        compose.onNodeWithTag("detail.hero.away.record", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail.section.last10").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.season-stats").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.season-series").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.top-performers").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.goaltenders").assertDoesNotExist()
    }

    /** Asserts the node tagged [left] starts to the left of the node tagged [right], by the centre of each. */
    private fun assertLeftOf(
        left: String,
        right: String,
    ) {
        val l = compose.onNodeWithTag(left).getUnclippedBoundsInRoot().let { it.left + it.right }
        val r = compose.onNodeWithTag(right).getUnclippedBoundsInRoot().let { it.left + it.right }
        assertTrue("$left should sit left of $right: $l against $r", l < r)
    }

    private fun assertAbove(
        upper: String,
        lower: String,
    ) {
        val u =
            compose
                .onNodeWithText(upper)
                .fetchSemanticsNode()
                .positionInRoot.y
        val d =
            compose
                .onNodeWithText(lower)
                .fetchSemanticsNode()
                .positionInRoot.y
        assertTrue("$upper should sit above $lower: $u against $d", u < d)
    }

    private fun show(
        game: Game,
        landing: GameLanding,
        rail: GameRightRail,
        onPlayerClick: (Long) -> Unit = {},
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
                    onPlayerClick = onPlayerClick,
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
                goalies =
                    listOf(
                        GoalieSeason(
                            teamId = 21L,
                            name = "M. Blackwood",
                            sweaterNumber = 30,
                            gamesPlayed = 12,
                            record = SeasonRecord(9, 3, 1),
                            playerId = 8478464L,
                        ),
                        GoalieSeason(teamId = 21L, name = "Z. Benched", sweaterNumber = 35, gamesPlayed = 0, playerId = 8479999L),
                        GoalieSeason(
                            teamId = 10L,
                            name = "A. Stolarz",
                            sweaterNumber = 41,
                            gamesPlayed = 8,
                            record = SeasonRecord(5, 2, 1),
                            playerId = 8477974L,
                        ),
                    ),
                skaters =
                    listOf(
                        SkaterSeason(21L, "N. MacKinnon", goals = 10, assists = 20, points = 30, playerId = 8477492L),
                        SkaterSeason(21L, "M. Necas", goals = 9, assists = 14, points = 23, playerId = 8478398L),
                        // No id: this performer's row has no page to open, so it is plain text.
                        SkaterSeason(21L, "C. Makar", goals = 5, assists = 16, points = 21),
                        SkaterSeason(21L, "A. Lehkonen", goals = 12, assists = 5, points = 17, playerId = 8477955L),
                        SkaterSeason(10L, "A. Matthews", goals = 11, assists = 16, points = 27, playerId = 8479318L),
                        SkaterSeason(10L, "W. Nylander", goals = 11, assists = 11, points = 22, playerId = 8477939L),
                        SkaterSeason(10L, "J. Roslovic", goals = 8, assists = 10, points = 18, playerId = 8478029L),
                    ),
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
