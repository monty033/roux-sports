@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
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
import net.montycasa.roux.domain.model.SkaterSeason
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.AuditStates
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * A node that says nothing of its own: a crest or a photo, whose text beside it already names the club or the player.
 */
private val NO_DESCRIPTION =
    SemanticsMatcher("no description of its own") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
    }

/** A node whose description names the goaltender who never played a game. */
private val NAMES_Z_BENCHED =
    SemanticsMatcher("a description naming Z. Benched") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains("Z. Benched") } == true
    }

/**
 * The spoken reading of the four pre-game cards.
 *
 * The cards are drawn as fragments - a record, a streak code, a percentage, a rank, a badge - and a screen reader reads
 * every fragment on its own, so a listener cannot tell what any number belongs to. Each row is read as one sentence
 * instead, and these tests pin that sentence exactly: the whole of it, not a part of it, so a reordering, a dropped
 * figure or a stray percent sign fails here rather than on a device.
 *
 * The sentences are the ones the four cards say:
 *
 * - "Utah Mammoth, 6 wins, 3 losses, 1 overtime loss, won the last 2"
 * - "Power play. Utah Mammoth 20.3 percent, 14th. Philadelphia Flyers 24.6 percent, 4th."
 * - "J. Kessel, 16 goals, 19 assists, 35 points"
 * - "A. Vanecek, 14 games, 8 wins, 4 losses, 2 overtime losses, 2.71 goals against, 90.6 percent saves"
 *
 * The words on the cards are unchanged: a sentence is added beside them, not instead of them.
 *
 * Each of these is a change the cards must refuse, and each one is caught here:
 *
 * - a record read with a "-" separator instead of "6 wins, 3 losses, 1 overtime loss";
 * - a percentage read as "20.3%" instead of "20.3 percent";
 * - a streak code ignored, so the streak is read as a bare "2" instead of "won the last 2";
 * - a rank dropped from a stat row;
 * - a photo given a description of its own;
 * - the goals and the assists read in the wrong order.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class PreGameSpokenScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the last ten card reads each club's record and streak as one sentence`() {
        showAuditState()

        compose.onNodeWithTag("detail.pregame.last10.away").assertContentDescriptionEquals(
            "Utah Mammoth, 6 wins, 3 losses, 1 overtime loss, won the last 2",
        )
        compose.onNodeWithTag("detail.pregame.last10.home").assertContentDescriptionEquals(
            "Philadelphia Flyers, 5 wins, 4 losses, 1 overtime loss, lost the last 1",
        )
    }

    @Test
    fun `the last ten card still prints the record and the streak as they were`() {
        showAuditState()

        compose.onNodeWithTag("detail.pregame.last10.away").assertTextEquals("6–3–1 (W2)")
        compose.onNodeWithTag("detail.pregame.last10.home").assertTextEquals("5–4–1 (L1)")
    }

    @Test
    fun `the season stats card reads each stat row as one sentence`() {
        showAuditState()

        compose.onNodeWithTag("detail.pregame.stat.Power play").assertContentDescriptionEquals(
            "Power play. Utah Mammoth 20.3 percent, 14th. Philadelphia Flyers 24.6 percent, 4th.",
        )
        compose.onNodeWithTag("detail.pregame.stat.Penalty kill").assertContentDescriptionEquals(
            "Penalty kill. Utah Mammoth 81.2 percent, 9th. Philadelphia Flyers 83.0 percent, 6th.",
        )
        compose.onNodeWithTag("detail.pregame.stat.Face-off").assertContentDescriptionEquals(
            "Face-off. Utah Mammoth 48.7 percent, 21st. Philadelphia Flyers 51.2 percent, 8th.",
        )
        compose.onNodeWithTag("detail.pregame.stat.Goals for / game").assertContentDescriptionEquals(
            "Goals for / game. Utah Mammoth 3.12, 17th. Philadelphia Flyers 3.65, 6th.",
        )
        compose.onNodeWithTag("detail.pregame.stat.Goals against / game").assertContentDescriptionEquals(
            "Goals against / game. Utah Mammoth 3.41, 26th. Philadelphia Flyers 2.88, 5th.",
        )
    }

    @Test
    fun `the top performers card reads each player as one sentence`() {
        showAuditState()

        compose.onNodeWithTag("detail.pregame.performers.away.J. Kessel.row").assertContentDescriptionEquals(
            "J. Kessel, 16 goals, 19 assists, 35 points",
        )
        compose.onNodeWithTag("detail.pregame.performers.away.C. Hayes.row").assertContentDescriptionEquals(
            "C. Hayes, 12 goals, 22 assists, 34 points",
        )
        compose.onNodeWithTag("detail.pregame.performers.away.N. Schmidt.row").assertContentDescriptionEquals(
            "N. Schmidt, 4 goals, 17 assists, 21 points",
        )
        compose.onNodeWithTag("detail.pregame.performers.home.T. Konecny.row").assertContentDescriptionEquals(
            "T. Konecny, 11 goals, 15 assists, 26 points",
        )
        compose.onNodeWithTag("detail.pregame.performers.home.S. Couturier.row").assertContentDescriptionEquals(
            "S. Couturier, 9 goals, 13 assists, 22 points",
        )
        compose.onNodeWithTag("detail.pregame.performers.home.J. Foote.row").assertContentDescriptionEquals(
            "J. Foote, 3 goals, 6 assists, 9 points",
        )
    }

    @Test
    fun `the goaltenders card reads each goaltender as one sentence`() {
        showAuditState()

        compose.onNodeWithTag("detail.pregame.goalies.away.A. Vanecek.row").assertContentDescriptionEquals(
            "A. Vanecek, 14 games, 8 wins, 4 losses, 2 overtime losses, 2.71 goals against, 90.6 percent saves",
        )
        compose.onNodeWithTag("detail.pregame.goalies.away.K. Kochetkov.row").assertContentDescriptionEquals(
            "K. Kochetkov, 9 games, 3.12 goals against, 88.9 percent saves",
        )
        compose.onNodeWithTag("detail.pregame.goalies.home.C. Talbot.row").assertContentDescriptionEquals(
            "C. Talbot, 15 games, 10 wins, 4 losses, 1 overtime loss, 2.58 goals against, 91.4 percent saves",
        )
        compose.onNodeWithTag("detail.pregame.goalies.home.S. Ersson.row").assertContentDescriptionEquals(
            "S. Ersson, 4 games, 2 wins, 2 losses, 0 overtime losses, 3.40 goals against, 87.9 percent saves",
        )
    }

    @Test
    fun `no crest and no photo carries a description of its own`() {
        showTappableState()

        listOf("detail.pregame.last10", "detail.pregame.season", "detail.pregame.performers", "detail.pregame.goalies").forEach { card ->
            listOf("away", "home").forEach { side ->
                compose.onNodeWithTag("$card.club.$side.crest").assert(NO_DESCRIPTION)
            }
        }
        compose.onNodeWithTag("detail.pregame.performers.away.J. Kessel.mugshot", useUnmergedTree = true).assert(NO_DESCRIPTION)
        compose.onNodeWithTag("detail.pregame.performers.away.J. Kessel.mugshot.image", useUnmergedTree = true).assert(NO_DESCRIPTION)
        compose.onNodeWithTag("detail.pregame.performers.away.J. Kessel.initials", useUnmergedTree = true).assert(NO_DESCRIPTION)
        compose.onNodeWithTag("detail.pregame.goalies.away.A. Vanecek.mugshot", useUnmergedTree = true).assert(NO_DESCRIPTION)
    }

    @Test
    fun `a player row is one node that carries the sentence and opens the player page`() {
        showTappableState()

        val row = compose.onNodeWithTag("detail.pregame.performers.away.J. Kessel.row")
        row.assert(hasClickAction())
        row.assertContentDescriptionEquals("J. Kessel, 16 goals, 19 assists, 35 points")
        // The sentence says who the player is; the tap says where it goes.
        val label =
            row
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.OnClick)
                ?.label
        assertEquals("Opens the player page", label)
        // One node: the name and the figures beside it are read as part of the row, not as rows of their own.
        compose.onAllNodesWithTag("detail.pregame.performers.away.J. Kessel.name").assertCountEquals(0)
        compose.onAllNodesWithTag("detail.pregame.performers.away.J. Kessel.figure.0").assertCountEquals(0)
        compose.onAllNodesWithTag("detail.pregame.performers.away.J. Kessel.mugshot").assertCountEquals(0)
    }

    @Test
    fun `a player with no page to open still reads as one sentence`() {
        showTappableState()

        val row = compose.onNodeWithTag("detail.pregame.performers.away.C. Hayes.row")
        row.assert(!hasClickAction())
        row.assertContentDescriptionEquals("C. Hayes, 12 goals, 22 assists, 34 points")
        compose.onAllNodesWithTag("detail.pregame.performers.away.C. Hayes.name").assertCountEquals(0)
    }

    @Test
    fun `a goaltender with no games played is not on the card, so it has no node at all`() {
        showTappableState()

        compose.onAllNodesWithTag("detail.pregame.goalies.away.Z. Benched.row").assertCountEquals(0)
        compose.onAllNodes(NAMES_Z_BENCHED).assertCountEquals(0)
    }

    @Test
    fun `a stat with no rank on one side is read without that rank`() {
        showTappableState()

        compose.onNodeWithTag("detail.pregame.stat.Penalty kill").assertContentDescriptionEquals(
            "Penalty kill. Utah Mammoth 81.2 percent. Philadelphia Flyers 83.0 percent, 6th.",
        )
        compose.onNodeWithTag("detail.pregame.stat.Power play").assertContentDescriptionEquals(
            "Power play. Utah Mammoth 20.3 percent, 14th. Philadelphia Flyers 24.6 percent, 4th.",
        )
    }

    /** The pre-game state the audits and the goldens use: every card's data present, no player with a page to open. */
    private fun showAuditState(fontScale: Float = 1f) {
        val screen = AuditStates.screens().single { it.name == "detail.pregame" }
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                RouxTheme(dark = false) {
                    screen.content()
                }
            }
        }
    }

    /**
     * The same pre-game page with the three cases the shared state cannot show: a player with a page to open, a
     * goaltender who has not played, and a statistic the league did not rank.
     */
    private fun showTappableState() {
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
        val game =
            Game(
                id = GameId("2026020301"),
                date = "2026-11-10",
                state = GameState.Scheduled,
                away = TeamRef(TeamKey("UTA"), name = "Mammoth"),
                home = TeamRef(TeamKey("PHI"), name = "Flyers"),
                startTimeUtc = Instant.parse("2026-11-11T00:00:00Z"),
            )

        val landing =
            GameLanding(
                state = GameState.Scheduled,
                awayTeamId = 68L,
                homeTeamId = 4L,
                goalies =
                    listOf(
                        GoalieSeason(
                            teamId = 68L,
                            name = "A. Vanecek",
                            sweaterNumber = 41,
                            gamesPlayed = 14,
                            record = SeasonRecord(8, 4, 2),
                            goalsAgainstAverage = 2.71,
                            savePercentage = 0.906,
                            playerId = 8477001L,
                        ),
                        // No games played, so this goaltender is not on the card at all.
                        GoalieSeason(teamId = 68L, name = "Z. Benched", sweaterNumber = 35, gamesPlayed = 0, playerId = 8477002L),
                    ),
                skaters =
                    listOf(
                        SkaterSeason(68L, "J. Kessel", goals = 16, assists = 19, points = 35, playerId = 8477101L),
                        SkaterSeason(68L, "C. Hayes", goals = 12, assists = 22, points = 34),
                    ),
            )

        val rail =
            GameRightRail(
                awayForm = ClubForm(record = SeasonRecord(6, 3, 1), streakType = "W", streak = 2),
                homeForm = ClubForm(record = SeasonRecord(5, 4, 1), streakType = "L", streak = 1),
                awaySeasonStats =
                    ClubSeasonStats(
                        powerPlay = RankedStat(0.203, 14),
                        // A rank of zero means the league did not place the club, so the card prints a dash.
                        penaltyKill = RankedStat(0.812, 0),
                        faceoff = RankedStat(0.487, 21),
                        goalsFor = RankedStat(3.12, 17),
                        goalsAgainst = RankedStat(3.41, 26),
                    ),
                homeSeasonStats =
                    ClubSeasonStats(
                        powerPlay = RankedStat(0.246, 4),
                        penaltyKill = RankedStat(0.830, 6),
                        faceoff = RankedStat(0.512, 8),
                        goalsFor = RankedStat(3.65, 6),
                        goalsAgainst = RankedStat(2.88, 5),
                    ),
            )
    }
}

/**
 * The pre-game cards when they stack, which is how they are drawn on a narrow screen with a large font.
 *
 * Stacked, the two clubs no longer share a row, so each stat row belongs to one club and says so on its own.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreGameSpokenStackedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a stacked stat row reads the stat and its own club's value and rank`() {
        val screen = AuditStates.screens().single { it.name == "detail.pregame" }
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                RouxTheme(dark = false) {
                    screen.content()
                }
            }
        }

        compose.onNodeWithTag("detail.pregame.stat.Power play.away").assertContentDescriptionEquals(
            "Power play. Utah Mammoth 20.3 percent, 14th.",
        )
        compose.onNodeWithTag("detail.pregame.stat.Power play.home").assertContentDescriptionEquals(
            "Power play. Philadelphia Flyers 24.6 percent, 4th.",
        )
    }
}
