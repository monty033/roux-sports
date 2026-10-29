@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.PowerPlaySummary
import net.montycasa.roux.domain.model.ShotTotals
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.TeamStat
import net.montycasa.roux.domain.model.ThreeStar
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the page promises: every card answers for its own endpoint, the bars never lie about a lopsided game, and the
 * controls stay pressable at the narrow size and at a doubled font scale.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class GameDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val game =
        Game(
            id = GameId("2026020188"),
            date = "2026-10-26",
            state = GameState.Final,
            home = TeamRef(TeamKey("PHI"), name = "Flyers"),
            away = TeamRef(TeamKey("CBJ"), name = "Blue Jackets"),
            homeScore = 4,
            awayScore = 3,
        )

    private val fullDetail =
        GameDetail(
            landing =
                Loadable(
                    GameLanding(
                        periodScoring = listOf(PeriodScoring(1, 2, 1), PeriodScoring(2, 0, 1)),
                        threeStars = listOf(ThreeStar(1, PlayerRef("T. Konecny", "PHI"))),
                    ),
                    Freshness.Fresh,
                    null,
                ),
            boxscore =
                Loadable(
                    GameBoxscore(goalies = listOf(GoalieLine(PlayerRef("C. Talbot", "PHI"), 25, 28))),
                    Freshness.Fresh,
                    null,
                ),
            rightRail = Loadable(GameRightRail(shots = ShotTotals(awayShots = 21, homeShots = 28)), Freshness.Fresh, null),
        )

    private fun show(
        detail: GameDetail,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, fontScale)) {
                RouxTheme(dark = true) {
                    GameDetailScreen(
                        game = game,
                        state = DetailUiState(detail = detail, refreshing = false, unexpectedFailure = false),
                        onBack = {},
                        onRetry = {},
                    )
                }
            }
        }
    }

    @Test
    fun `a failed landing leaves the stats and the player cards standing`() {
        val error = LoadError.Http(503)
        show(
            fullDetail.copy(
                landing = Loadable(null, Freshness.Missing, error),
            ),
        )

        // The two failures sit inside the cards that failed, and say so there - the rail's card is untouched.
        compose.onNodeWithText("Scoring could not be loaded").assertExists()
        compose.onNodeWithText("Three stars could not be loaded").assertExists()
        compose.onNodeWithText("Game stats could not be loaded").assertDoesNotExist()
    }

    @Test
    fun `a failed right rail leaves the scoring card standing`() {
        show(
            fullDetail.copy(
                rightRail = Loadable(null, Freshness.Missing, LoadError.Http(503)),
            ),
        )

        compose.onNodeWithText("Game stats could not be loaded").assertExists()
        // A failed rail does not reach across and blank the landing's cards.
        compose.onNodeWithText("Scoring could not be loaded").assertDoesNotExist()
        compose.onNodeWithText("Three stars could not be loaded").assertDoesNotExist()
    }

    @Test
    fun `a failed boxscore takes the player cards with it and nothing else`() {
        show(
            fullDetail.copy(
                boxscore = Loadable(null, Freshness.Missing, LoadError.Parse),
            ),
        )

        compose.onNodeWithTag("detail.section.scoring").assertExists()
        compose.onNodeWithTag("detail.section.stats").assertExists()
        compose.onNodeWithTag("detail.tab.CBJ").performClick()
        // One card now carries the whole roster, so its failure is the failure of both lists.
        compose.onNodeWithText("Player stats could not be loaded").assertExists()
        compose.onNodeWithTag("detail.section.goalies").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.skaters").assertDoesNotExist()
    }

    @Test
    fun `each period heading sits over its own column of numbers`() {
        show(fullDetail)

        listOf("1", "2", "F").forEach { label ->
            val header = compose.onNodeWithTag("detail.period.header.$label").getUnclippedBoundsInRoot()
            val awayCell = compose.onNodeWithTag("detail.period.CBJ.$label").getUnclippedBoundsInRoot()
            val homeCell = compose.onNodeWithTag("detail.period.PHI.$label").getUnclippedBoundsInRoot()

            // The heading is centred over the column, so its centre is the column's centre - not a fixed 56 dp offset.
            val headerCentre = (header.left.value + header.right.value) / 2f
            assertEquals(headerCentre, (awayCell.left.value + awayCell.right.value) / 2f, 1f)
            assertEquals(headerCentre, (homeCell.left.value + homeCell.right.value) / 2f, 1f)
        }
    }

    @Test
    fun `the crest, the club and the score share a line and the city hangs above it`() {
        show(fullDetail)

        listOf("away", "home").forEach { side ->
            val crest = compose.onNodeWithTag("detail.hero.$side.crest").getUnclippedBoundsInRoot()
            val name = compose.onNodeWithTag("detail.hero.$side.name").getUnclippedBoundsInRoot()
            val score = compose.onNodeWithTag("detail.hero.$side.score").getUnclippedBoundsInRoot()
            val city = compose.onNodeWithTag("detail.hero.$side.city").getUnclippedBoundsInRoot()

            val crestCentre = (crest.top.value + crest.bottom.value) / 2f
            assertEquals(crestCentre, (name.top.value + name.bottom.value) / 2f, 2f)
            assertEquals(crestCentre, (score.top.value + score.bottom.value) / 2f, 2f)
            // Above the line, and never alongside it.
            assertTrue(city.bottom.value <= name.top.value + 1f)
            assertEquals(city.left.value, name.left.value, 1f)
        }
    }

    // The pills are clickable, so their contents merge into the clickable node: read the unmerged tree.
    private fun labelOf(tag: String): String =
        compose
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    @Test
    fun `the club pills carry a crest and the club's name, not its abbreviation`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.tab.PHI").assertIsDisplayed()
        compose.onNodeWithTag("detail.tab.PHI.crest", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Flyers", labelOf("detail.tab.PHI.label"))
        assertEquals("Blue Jackets", labelOf("detail.tab.CBJ.label"))
        // The summary pill has no club, so it takes no crest.
        assertEquals("Summary", labelOf("detail.tab.Summary.label"))
    }

    @Test
    fun `the players card is headed by the crest and the club's name`() {
        show(fullDetail)
        compose.onNodeWithTag("detail.tab.PHI").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("FLYERS PLAYER STATS").assertIsDisplayed()
        val crest = compose.onNodeWithTag("detail.section.players.crest").getUnclippedBoundsInRoot()
        assertEquals(30f, crest.bottom.value - crest.top.value, 1f)
    }

    @Test
    fun `the club name in the scoreboard is set larger than body text`() {
        show(fullDetail)

        val name = compose.onNodeWithTag("detail.hero.home.name").getUnclippedBoundsInRoot()
        // titleLarge, not titleMedium: the club is the thing the eye should land on first.
        assertTrue("club name is only ${name.bottom.value - name.top.value} dp tall", name.bottom.value - name.top.value >= 20f)
    }

    @Test
    fun `the crests in the scoring rows and the pills are big enough to read`() {
        show(fullDetail)

        val rowCrest = compose.onNodeWithTag("detail.scoring.crest.PHI").getUnclippedBoundsInRoot()
        assertEquals(24f, rowCrest.bottom.value - rowCrest.top.value, 1f)

        val pillCrest = compose.onNodeWithTag("detail.tab.PHI.crest", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(26f, pillCrest.bottom.value - pillCrest.top.value, 1f)
    }

    @Test
    fun `each scoring row carries its club crest`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.scoring.crest.CBJ").assertIsDisplayed()
        compose.onNodeWithTag("detail.scoring.crest.PHI").assertIsDisplayed()
    }

    @Test
    fun `the club name stays put while the stat columns scroll`() {
        show(fullDetail)

        // The rosters live on a club's own destination, so open one before measuring.
        compose.onNodeWithTag("detail.tab.PHI").performClick()
        compose.waitForIdle()

        val before = compose.onNodeWithText("C. Talbot").getUnclippedBoundsInRoot()
        val headerBefore = compose.onNodeWithText("SA").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("detail.section.goalies").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val after = compose.onNodeWithText("C. Talbot").getUnclippedBoundsInRoot()
        val headerAfter = compose.onNodeWithText("SA").getUnclippedBoundsInRoot()

        // The stats really moved - otherwise "the name did not move" proves nothing.
        assertTrue(headerAfter.left.value < headerBefore.left.value)

        // The frozen column is the point: reaching a column must never cost you sight of whose row it is.
        assertEquals(before.left.value, after.left.value, 0.5f)
        compose.onNodeWithText("C. Talbot").assertIsDisplayed()
    }

    @Test
    fun `the back control and the three destinations stay pressable`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.back").assertTouchHeightIsEqualTo(48.dp)
        listOf("detail.tab.Summary", "detail.tab.CBJ", "detail.tab.PHI").forEach { tag ->
            // reachTarget pins the 48 dp minimum, so a tab is pressable even when its visual is shorter.
            compose.onNodeWithTag(tag).assertTouchHeightIsEqualTo(48.dp)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `the controls survive the narrow size at double the font scale`() {
        show(fullDetail, fontScale = 2f)

        compose.onNodeWithTag("detail.back").assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("detail.tab.Summary").assertTouchHeightIsEqualTo(48.dp)
    }
}

/** The bar arithmetic, away from Compose - this is where the floor either holds or does not. */
class BarSharesTest {
    @Test
    fun `a one-sided game keeps a sliver for the side that never scored`() {
        val (away, home) = barShares(awayValue = 1, homeValue = 0, trackDp = 371f)

        // The floor wins over the raw share: 371 - 13 leaves the scoring side the rest.
        assertEquals(13f, home * 371f, 0.01f)
        assertEquals(371f - 13f, away * 371f, 0.01f)
        assertEquals(1f, away + home, 0.0001f)
    }

    @Test
    fun `an even split is half the track each`() {
        val (away, home) = barShares(awayValue = 3, homeValue = 3, trackDp = 360f)

        assertEquals(0.5f, away, 0.0001f)
        assertEquals(0.5f, home, 0.0001f)
    }

    @Test
    fun `proportions follow the goals, not the chance count`() {
        val (away, home) = barShares(awayValue = 2, homeValue = 1, trackDp = 371f)

        assertEquals(2f / 3f, away, 0.0001f)
        assertEquals(1f / 3f, home, 0.0001f)
    }

    @Test
    fun `no power play goals at all draws no fill`() {
        val (away, home) = barShares(awayValue = 0, homeValue = 0, trackDp = 371f)

        assertEquals(0f, away, 0f)
        assertEquals(0f, home, 0f)
    }

    @Test
    fun `neither side can vanish on a lopsided row`() {
        listOf(0 to 9, 9 to 0, 1 to 40, 40 to 1).forEach { (awayGoals, homeGoals) ->
            val (away, home) = barShares(awayGoals, homeGoals, trackDp = 300f)
            assertTrue(away * 300f >= MIN_BAR_SIDE_DP - 0.01f)
            assertTrue(home * 300f >= MIN_BAR_SIDE_DP - 0.01f)
            assertEquals(1f, away + home, 0.0001f)
        }
    }

    @Test
    fun `ice time reads as minutes and seconds, and nothing as a dash`() {
        assertEquals("62:22", formatIceTime(3742))
        assertEquals("0:07", formatIceTime(7))
        assertEquals("—", formatIceTime(0))
    }

    @Test
    fun `an average keeps its decimals even when the value is whole`() {
        assertEquals("2.89", formatAverage(2.886f.toDouble(), 2))
        assertEquals("3.00", formatAverage(3.0, 2))
        assertEquals("92.0", formatAverage(91.96, 1))
    }
}

/** The stats card is judged on the payload the league actually sends, which is ten rows in three notations. */
class ComparisonRowsTest {
    private val captured =
        GameRightRail(
            shots = ShotTotals(awayShots = 15, homeShots = 32),
            powerPlay = PowerPlaySummary(awayGoals = 0, awayChances = 1, homeGoals = 0, homeChances = 2),
            teamStats =
                listOf(
                    TeamStat("sog", "15", "32"),
                    TeamStat("faceoffWinningPctg", "0.56", "0.44"),
                    TeamStat("faceoffWins", "28/50", "22/50"),
                    TeamStat("powerPlay", "0/1", "0/2"),
                    TeamStat("powerPlayPctg", "0.0", "0.0"),
                    TeamStat("pim", "6", "4"),
                    TeamStat("hits", "18", "15"),
                    TeamStat("blockedShots", "19", "12"),
                    TeamStat("giveaways", "13", "15"),
                    TeamStat("takeaways", "5", "1"),
                ),
        )

    @Test
    fun `every published stat becomes a row, and the duplicate notations do not`() {
        val labels = captured.comparisonRows().map { it.label }

        assertEquals(
            listOf("Shots", "Power play", "Faceoffs", "Penalty minutes", "Hits", "Blocked shots", "Giveaways", "Takeaways"),
            labels,
        )
    }

    @Test
    fun `a faceoff fraction of one is read as a percentage, not as fifty-six`() {
        val faceoffs = captured.comparisonRows().first { it.label == "Faceoffs" }

        assertEquals("56%", faceoffs.awayText)
        assertEquals("44%", faceoffs.homeText)
        assertEquals(56, faceoffs.awayValue)
    }

    @Test
    fun `a made over chances row is drawn from the goals, and keeps its own notation`() {
        val powerPlay = captured.comparisonRows().first { it.label == "Power play" }

        assertEquals("0/1", powerPlay.awayText)
        assertEquals("0/2", powerPlay.homeText)
        assertEquals(0, powerPlay.awayValue)
        assertEquals(0, powerPlay.homeValue)
    }
}
