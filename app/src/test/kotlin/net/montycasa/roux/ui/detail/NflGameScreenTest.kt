package net.montycasa.roux.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflGameStatus
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.util.Locale

/**
 * Football's game page: what the hero states, how the four sections read, and the two states that only exist as
 * synthetic fixtures today. The live and scheduled tests say **crafted** because no NFL game was in progress or
 * scheduled when the 25 November corpus was taken; they prove the layout, not a captured feed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflGameScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(
        detail: net.montycasa.roux.domain.model.NflGameDetail = NflGameFixtures.finalDetail(),
        game: net.montycasa.roux.domain.model.Game = NflGameFixtures.finalGame(),
        freshness: Freshness = Freshness.Fresh,
        error: LoadError? = null,
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                NflGameScreen(
                    game = game,
                    state = NflGameUiState(detail = Loadable(detail, freshness, error)),
                    onBack = {},
                    onRetry = onRetry,
                    zone = ZoneId.of("America/New_York"),
                    locale = Locale.US,
                )
            }
        }
    }

    @Test
    fun aFinalGameReadsTheFeedsFinalWording() {
        show(detail = NflGameFixtures.finalDetail().copy(status = NflGameStatus.Final("Final")))

        rule.onNodeWithTag("nflGame.status").assertTextEquals("Final")
        rule.onAllNodesWithText("Final/OT").assertCountEquals(0)
    }

    @Test
    fun anOvertimeFinalReadsFinalOt() {
        show()

        rule.onNodeWithTag("nflGame.status").assertTextEquals("Final/OT")
    }

    @Test
    fun theHeroShowsBothClubsTheirScoresAndTheFeedsRecords() {
        show()

        rule.onNodeWithTag("nflGame.hero.away.abbrev").assertTextEquals("NO")
        rule.onNodeWithTag("nflGame.hero.home.abbrev").assertTextEquals("DET")
        rule.onNodeWithTag("nflGame.hero.away.score").assertTextEquals("30")
        rule.onNodeWithTag("nflGame.hero.home.score").assertTextEquals("31")
        rule.onNodeWithTag("nflGame.hero.away.record").assertTextEquals("0-1")
        rule.onNodeWithTag("nflGame.hero.home.record").assertTextEquals("1-0")
    }

    @Test
    fun theHeroOmitsARecordTheFeedDidNotState() {
        val withoutRecords =
            NflGameFixtures.finalDetail().let { detail ->
                detail.copy(
                    away = detail.away.copy(record = null),
                    home = detail.home.copy(record = null),
                )
            }
        show(detail = withoutRecords)

        rule.onAllNodesWithTag("nflGame.hero.away.record").assertCountEquals(0)
        rule.onAllNodesWithTag("nflGame.hero.home.record").assertCountEquals(0)
        // An absent record is not a 0-0 line.
        rule.onAllNodesWithText("0-0").assertCountEquals(0)
    }

    @Test
    fun scoringRowsGroupByQuarterAndCarryTheRunningScore() {
        show()

        listOf(1, 3, 4, 5).forEach { quarter ->
            rule.onNodeWithTag("nflGame.scoring.quarter.$quarter").assertExists()
        }
        rule.onNodeWithTag("nflGame.scoring.quarter.5").assertTextEquals("OT")
        rule.onNodeWithTag("nflGame.scoring.1.0.clock").assertTextEquals("3:39")
        rule.onNodeWithTag("nflGame.scoring.1.0.club").assertTextEquals("DET")
        rule.onNodeWithTag("nflGame.scoring.1.0.text").assertTextEquals("Jahmyr Gibbs 1 Yd Rush (Jake Bates Kick)")
        rule.onNodeWithTag("nflGame.scoring.1.0.score").assertTextEquals("0–7")
        // Payload order is kept inside the quarter: 11:14 before 4:27.
        rule.onNodeWithTag("nflGame.scoring.3.0.clock").assertTextEquals("11:14")
        rule.onNodeWithTag("nflGame.scoring.3.1.clock").assertTextEquals("4:27")
    }

    @Test
    fun teamStatsPrintRatioValuesVerbatimAndLeaveAMissingSideBlank() {
        val missingHomeRushing =
            NflGameFixtures.finalDetail().copy(
                teamStats =
                    NflGameFixtures.finalDetail().teamStats.map { stat ->
                        if (stat.name == "rushingYards") stat.copy(home = null) else stat
                    },
            )
        show(detail = missingHomeRushing)

        rule.onNodeWithTag("nflGame.stat.thirdDownEff.away").assertTextEquals("8-17")
        rule.onNodeWithTag("nflGame.stat.thirdDownEff.home").assertTextEquals("5-13")
        rule.onNodeWithTag("nflGame.stat.possessionTime.away").assertTextEquals("31:35")
        rule.onNodeWithTag("nflGame.stat.possessionTime.home").assertTextEquals("36:51")
        rule.onNodeWithTag("nflGame.stat.rushingYards.away").assertTextEquals("91")
        rule.onNodeWithTag("nflGame.stat.rushingYards.home").assertTextEquals("")
    }

    @Test
    fun leadersPrintOneLinePerCategoryWithTheFeedsValue() {
        show()

        rule.onNodeWithTag("nflGame.leaders.DET.passingYards.category").assertTextEquals("Passing Yards")
        rule
            .onNodeWithTag("nflGame.leaders.DET.passingYards.line")
            .assertTextEquals("Jared Goff · 26/39, 206 YDS, 2 TD")
        rule
            .onNodeWithTag("nflGame.leaders.NO.passingYards.line")
            .assertTextEquals("Tyler Shough · 35/56, 410 YDS, 3 TD, 2 INT")
        listOf("passingYards", "rushingYards", "receivingYards", "sacks", "totalTackles").forEach { category ->
            rule.onNodeWithTag("nflGame.leaders.DET.$category").assertExists()
        }
    }

    @Test
    fun aCraftedLiveGameShowsItsQuarterAndClockAndLiveScores() {
        // Crafted, not captured: no NFL game was in progress on 25 November 2026. This proves the layout only.
        show(detail = NflGameFixtures.craftedLiveDetail(), game = NflGameFixtures.liveGame())

        rule.onNodeWithTag("nflGame.status").assertTextEquals("Q3 14:12")
        rule.onNodeWithTag("nflGame.hero.away.score").assertTextEquals("10")
        rule.onNodeWithTag("nflGame.hero.home.score").assertTextEquals("17")
        rule.onNodeWithTag("nflGame.section.scoring").assertIsDisplayed()
    }

    @Test
    fun aCraftedScheduledGameShowsTheKickoffAndNoBodySections() {
        // Crafted, not captured: no NFL game was scheduled on 25 November 2026. This proves the layout only.
        show(detail = NflGameFixtures.craftedScheduledDetail(), game = NflGameFixtures.scheduledGame())

        rule.onNodeWithTag("nflGame.status").assertTextEquals("Thu, Nov 19 · 8:15 PM")
        rule.onAllNodesWithTag("nflGame.hero.away.score").assertCountEquals(0)
        rule.onAllNodesWithTag("nflGame.section.scoring").assertCountEquals(0)
        rule.onAllNodesWithTag("nflGame.section.stats").assertCountEquals(0)
        rule.onAllNodesWithTag("nflGame.section.leaders").assertCountEquals(0)
    }

    @Test
    fun aFailedSummaryOffersTheRetry() {
        var retried = 0
        rule.setContent {
            RouxTheme {
                NflGameScreen(
                    game = NflGameFixtures.finalGame(),
                    state = NflGameUiState(detail = Loadable(null, Freshness.Missing, LoadError.Network)),
                    onBack = {},
                    onRetry = { retried++ },
                    zone = ZoneId.of("America/New_York"),
                    locale = Locale.US,
                )
            }
        }

        rule.onNodeWithText("The game could not be loaded.").assertIsDisplayed()
        rule.onNodeWithTag("nflGame.retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun aStaleCopySaysItIsTheLastSavedVersion() {
        show(freshness = Freshness.Stale(kotlin.time.Instant.parse("2026-11-25T20:00:00Z")))

        rule.onNodeWithTag("nflGame.stale").assertTextEquals("Showing the last saved version of this game.")
    }
}
