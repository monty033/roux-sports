package net.montycasa.roux.ui.player

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GoalieDecision
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerDraft
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val CURRENT_SEASON = 20262027

/** The date the Info card counts ages to. Born 2025-11-05 is one year old on it. */
private val TODAY = LocalDate(2026, 11, 5)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
// Native graphics so text is measured with real glyph widths; the frozen column's width is measured from them.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theBottomNavigationRendersTheFourTabsInOrderWithTheBriefsNames() {
        show(loaded(skaterProfile()))

        val lefts = listOf("Season", "Career", "GameLog", "Info").map { bounds("player.tab.$it").left }
        assertTrue("tabs are not in order: $lefts", lefts.zipWithNext().all { (left, right) -> left < right })
        rule.onNodeWithTag("player.tab.Season").assertTextEquals("Season")
        rule.onNodeWithTag("player.tab.Career").assertTextEquals("Career")
        rule.onNodeWithTag("player.tab.GameLog").assertTextEquals("Game log")
        rule.onNodeWithTag("player.tab.Info").assertTextEquals("Info")
        rule.onNodeWithTag("player.tabs").assertDoesNotExist()
    }

    /** The bar sits under the pager, not above it, and each tab is at least 48 dp tall. */
    @Test
    fun theBottomNavigationSitsBelowThePagerAndEveryTabIsReachableAt48dp() {
        show(loaded(skaterProfile()))

        val pager = bounds("player.pager")
        val nav = bounds("player.tab.Season")
        assertTrue("nav top ${nav.top} is above the pager bottom ${pager.bottom}", nav.top >= pager.bottom - 1f)
        val minimum = with(rule.density) { 48.dp.toPx() }
        listOf("Season", "Career", "GameLog", "Info").forEach { tab ->
            val height =
                rule
                    .onNodeWithTag("player.tab.$tab")
                    .fetchSemanticsNode()
                    .size.height
                    .toFloat()
            assertTrue("$tab is $height px tall, below $minimum px", height >= minimum)
        }
    }

    @Test
    fun theSkaterPageRendersTheHeaderAndTheSeasonRowWithAstAndSog() {
        show(loaded(skaterProfile()))

        rule.onNodeWithTag("player.name").assertTextEquals("Aleksi Heponiemi")
        rule.onNodeWithTag("player.number").assertTextEquals("#20 · C")
        rule.onNodeWithText("This season", ignoreCase = true).assertExists()
        rule.onNodeWithTag("player.season.row.0.gp").assertTextEquals("13")
        rule.onNodeWithTag("player.season.row.0.sog").assertTextEquals("30")
    }

    /** The season card prints no league or club line above its grid, for an NHL season or any other. */
    @Test
    fun theSeasonCardPrintsNoLeagueOrClubLine() {
        show(loaded(skaterProfile()))

        rule.onNodeWithTag("player.season.row.0.label").assertDoesNotExist()
        rule.onAllNodesWithText("NHL · ", substring = true).assertCountEquals(0)
    }

    /**
     * An NHL player whose newest season also has an AHL row sees one card and one grid. The AHL row is not on this tab.
     */
    @Test
    fun theSeasonTabRendersOneCardAndOneGridWhenANewerSeasonAlsoHasAnotherLeague() {
        show(
            loaded(
                skaterProfile(
                    seasons =
                        listOf(
                            line(20262027, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 13)),
                            line(20262027, "AHL", "Charlotte Checkers", 2, skaterTotals(gamesPlayed = 5, goals = 99)),
                        ),
                ),
            ),
        )

        rule.onAllNodesWithTag("player.season").assertCountEquals(1)
        rule.onNodeWithTag("player.season.row.0.gp").assertTextEquals("13")
        rule.onNodeWithTag("player.season.row.1").assertDoesNotExist()
        rule.onAllNodesWithText("Charlotte", substring = true).assertCountEquals(0)
    }

    /** A traded season keeps both clubs as grids inside the one season card, with no club line above either grid. */
    @Test
    fun aTradedSeasonShowsBothClubGridsInsideTheOneSeasonCardWithNoClubLine() {
        show(
            loaded(
                skaterProfile(
                    seasons =
                        listOf(
                            line(20252026, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 30)),
                            line(20252026, "NHL", "Boston Bruins", 2, skaterTotals(gamesPlayed = 20)),
                        ),
                ),
            ),
        )

        rule.onAllNodesWithTag("player.season").assertCountEquals(1)
        rule.onNodeWithTag("player.season.row.0.label").assertDoesNotExist()
        rule.onNodeWithTag("player.season.row.1.label").assertDoesNotExist()
        rule.onNodeWithTag("player.season.row.0.gp").assertTextEquals("30")
        rule.onNodeWithTag("player.season.row.1.gp").assertTextEquals("20")
        assertTrue("the second club is not below the first", top("player.season.row.1.gp") > top("player.season.row.0.gp"))
    }

    /** A wing is printed as LW or RW; the feed's C, D and G are unchanged. */
    @Test
    fun aLeftWingIsPrintedAsLwInTheHeader() {
        show(loaded(skaterProfile(position = "L")))

        rule.onNodeWithTag("player.number").assertTextEquals("#20 · LW")
    }

    /** The Season tab is a grid: each stat's label sits above its value in the same cell. */
    @Test
    fun theSeasonGridSetsEachLabelAboveItsValueWithAstAndSogNamed() {
        show(loaded(skaterProfile()))

        rule.onNodeWithTag("player.season.row.0.ast.label").assertTextEquals("AST")
        rule.onNodeWithTag("player.season.row.0.sog.label").assertTextEquals("SOG")
        rule.onNodeWithTag("player.season.row.0.ast").assertTextEquals("3")
        listOf("ast", "sog").forEach { key ->
            val label = bounds("player.season.row.0.$key.label")
            val value = bounds("player.season.row.0.$key")
            assertTrue("$key: label bottom ${label.bottom} is below its value top ${value.top}", label.bottom <= value.top + 1f)
            assertEquals(label.left, value.left, 1f)
        }
    }

    @Test
    fun aMissingStatShowsTheEmDashAndARealZeroShowsZero() {
        show(
            loaded(
                skaterProfile(
                    seasons = listOf(line(20252026, "NHL", "Carolina Hurricanes", 1, skaterTotals(goals = 0, shorthandedGoals = null))),
                ),
            ),
        )
        selectTab("Career")

        rule.onNodeWithTag("player.career.row.0.shg").assertTextEquals("—")
        rule.onNodeWithTag("player.career.row.0.g").assertTextEquals("0")
    }

    @Test
    fun theCareerTableShowsThreeNhlSeasonsThenTheTotalAndLeavesOutTheAhlRow() {
        show(loaded(skaterProfile()))
        selectTab("Career")

        rule.onNodeWithTag("player.career.row.0.label").assertTextEquals("2024-25")
        rule.onNodeWithTag("player.career.row.2.label").assertTextEquals("2026-27")
        rule.onNodeWithTag("player.career.row.3").assertDoesNotExist()
        rule.onNodeWithTag("player.career.total.label").assertTextEquals("Total")
        rule.onNodeWithTag("player.career.total.g").assertTextEquals("24")
        rule.onNodeWithTag("player.career.total.toi").assertTextEquals("11:12")
    }

    /** The season is the frozen first column; the club is its own column after it, under a "Team" header. */
    @Test
    fun theCareerClubIsItsOwnColumnAfterTheSeasonAndTheHeaderNamesIt() {
        show(loaded(skaterProfile()))
        selectTab("Career")

        rule.onNodeWithTag("player.career.header.club").assertTextEquals("Team")
        rule.onNodeWithTag("player.career.row.0.club").assertTextEquals("CAR")
        rule.onNodeWithTag("player.career.row.0.label").assertTextEquals("2024-25")
        assertTrue(bounds("player.career.row.0.label").right <= bounds("player.career.row.0.club").left + 1f)
    }

    /** The career's frozen column is sized to its content: no wider than its widest season label. */
    @Test
    fun theCareerFrozenColumnIsNoWiderThanItsWidestSeasonLabel() {
        show(loaded(skaterProfile()))
        selectTab("Career")

        val widest = (0..2).maxOf { bounds("player.career.row.$it.label").width }
        val frozen = bounds("player.career.row.0.frozen").width
        assertTrue("the frozen column is $frozen px, wider than its widest label $widest px", frozen <= widest + 2f)
    }

    /** The frozen season column keeps its place and stays on screen while the stat columns scroll shut. */
    @Test
    fun theFrozenSeasonColumnStaysInViewWhileTheStatColumnsScroll() {
        show(loaded(skaterProfile()))
        selectTab("Career")
        val labelLeft = bounds("player.career.row.0.label").left
        val statLeft = bounds("player.career.row.0.gp").left

        rule.onNodeWithTag("player.career.row.0.gp").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("player.career.row.0.label").assertIsDisplayed()
        assertEquals(labelLeft, bounds("player.career.row.0.label").left, 0.5f)
        assertTrue("the stat columns did not scroll", bounds("player.career.row.0.gp").left < statLeft)
    }

    @Test
    fun theCareerTotalSumsTheSeasonsOnceNotTheClubsTwice() {
        show(
            loaded(
                skaterProfile(
                    seasons =
                        listOf(
                            line(20222023, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 60, goals = 10)),
                            line(20232024, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 30, goals = 5)),
                            line(20232024, "NHL", "Boston Bruins", 2, skaterTotals(gamesPlayed = 20, goals = 4)),
                            line(20242025, "NHL", "Boston Bruins", 1, skaterTotals(gamesPlayed = 70, goals = 12)),
                        ),
                ),
            ),
        )
        selectTab("Career")

        rule.onNodeWithTag("player.career.total.gp").assertTextEquals("180")
        rule.onNodeWithTag("player.career.total.g").assertTextEquals("31")
    }

    /** A traded season has one row per club, both labelled with the same season and each with its own club. */
    @Test
    fun aTradedSeasonShowsOneCareerRowPerClubEachNamingItsClub() {
        show(
            loaded(
                skaterProfile(
                    seasons =
                        listOf(
                            line(20252026, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 30, goals = 5)),
                            line(20252026, "NHL", "Boston Bruins", 2, skaterTotals(gamesPlayed = 20, goals = 4)),
                        ),
                ),
            ),
        )
        selectTab("Career")

        rule.onNodeWithTag("player.career.row.0.label").assertTextEquals("2025-26")
        rule.onNodeWithTag("player.career.row.1.label").assertTextEquals("2025-26")
        rule.onNodeWithTag("player.career.row.0.club").assertTextEquals("CAR")
        rule.onNodeWithTag("player.career.row.1.club").assertTextEquals("BOS")
        rule.onNodeWithTag("player.career.row.2").assertDoesNotExist()
    }

    /**
     * Playoff rows get their own card below the career card, with their own total at its foot. The career card keeps
     * the regular rows only.
     */
    @Test
    fun aPlayoffRowGetsItsOwnCardBelowTheCareerCardWithItsOwnTotal() {
        show(
            loaded(
                skaterProfile(
                    seasons =
                        skaterSeasons +
                            line(20242025, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 6, goals = 1), gameType = 3),
                ),
            ),
        )
        selectTab("Career")

        rule.onNodeWithTag("player.career.row.0.label").assertTextEquals("2024-25")
        rule.onNodeWithTag("player.career.row.1.label").assertTextEquals("2025-26")
        rule.onNodeWithTag("player.career.row.2.label").assertTextEquals("2026-27")
        rule.onNodeWithTag("player.career.row.3").assertDoesNotExist()
        rule.onNodeWithTag("player.playoffs.row.0.label").assertTextEquals("2024-25")
        rule.onNodeWithTag("player.playoffs.row.0.gp").assertTextEquals("6")
        rule.onNodeWithTag("player.playoffs.row.1").assertDoesNotExist()
        rule.onNodeWithTag("player.playoffs.total.label").assertTextEquals("Total")
        rule.onNodeWithTag("player.playoffs.total.gp").assertTextEquals("6")
        assertTrue("the playoff card is not below the career card", top("player.playoffs") > top("player.career.total"))
    }

    /** A player with no playoff rows has no playoff card, even when the feed sent a playoff career line. */
    @Test
    fun aPlayerWithNoPlayoffRowsHasNoPlayoffCardEvenWhenTheFeedSentAPlayoffLine() {
        show(loaded(goalieProfile()))
        selectTab("Career")

        rule.onNodeWithTag("player.career.total.gp").assertTextEquals("50")
        rule.onNodeWithTag("player.playoffs").assertDoesNotExist()
        rule.onNodeWithTag("player.playoffs.total").assertDoesNotExist()
    }

    @Test
    fun aGoalieCareerTotalDerivesItsAverageAndSavePercentage() {
        show(loaded(goalieProfile()))
        selectTab("Career")

        rule.onNodeWithTag("player.career.total.gp").assertTextEquals("50")
        rule.onNodeWithTag("player.career.total.gaa").assertTextEquals("4.00")
        rule.onNodeWithTag("player.career.total.sv").assertTextEquals(".900")
    }

    /** The Season tab holds the NHL row of the newest season, and the AHL row of that season stays in Career only. */
    @Test
    fun aGoalieSeasonShowsOnlyTheNhlRowAndTheGoalieStatSet() {
        show(loaded(goalieProfile()))

        rule.onNodeWithTag("player.number").assertTextEquals("#31 · G")
        rule.onNodeWithTag("player.season.row.0.label").assertDoesNotExist()
        rule.onNodeWithTag("player.season.row.0.w.label").assertTextEquals("W")
        rule.onNodeWithTag("player.season.row.1").assertDoesNotExist()
    }

    /** A past season is titled by its years alone; the club names the league in its own row. */
    @Test
    fun theSeasonTitleNamesAPastNhlSeasonByItsYears() {
        show(
            loaded(
                skaterProfile(
                    seasons = listOf(line(20252026, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 50))),
                ),
            ),
        )

        rule.onNode(hasText("2025-26") and hasAnyAncestor(hasTestTag("player.season"))).assertExists()
        rule.onNodeWithText("This season", ignoreCase = true).assertDoesNotExist()
    }

    /** The heading names the season in full years, and the row's label reads the date and opponent as "Nov 1 · @ OTT". */
    @Test
    fun theGameLogHeadingNamesTheSeasonInFullYearsAndTheRowLabelReadsDateAndOpponent() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithText("GAME LOG 2026-2027 SEASON").assertExists()
        rule.onNodeWithTag("player.gamelog.row.0.label", useUnmergedTree = true).assertTextEquals("Nov 1 · @ OTT")
    }

    @Test
    fun theGameLogRendersSevenGamesNewestFirstWhateverOrderTheFeedSentThem() {
        show(loaded(skaterProfile(), gameLog = Loadable(shuffledSkaterGames(), Freshness.Fresh, null)))
        selectTab("GameLog")

        // Each game's shots equal its day, so the newest game (the 7th) shows 7 at the top.
        (0..6).forEach { index ->
            rule.onNodeWithTag("player.gamelog.row.$index.sog", useUnmergedTree = true).assertTextEquals((7 - index).toString())
        }
        rule.onNodeWithTag("player.gamelog.row.7", useUnmergedTree = true).assertDoesNotExist()
    }

    /** The opponent is printed with a home or away marker, and it sits in the frozen label, not a scrolling column. */
    @Test
    fun aSkaterGameShowsTheOpponentWithAHomeOrAwayMarkerNotAResult() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithTag("player.gamelog.row.0.label", useUnmergedTree = true).assertTextEquals("Nov 1 · @ OTT")
        rule.onAllNodesWithTag("player.gamelog.header.opp").assertCountEquals(0)
        rule.onNodeWithTag("player.gamelog.header.ast").assertTextEquals("AST")
        rule.onNodeWithTag("player.gamelog.header.sog").assertTextEquals("SOG")
        rule.onNodeWithTag("player.gamelog.row.0.dec", useUnmergedTree = true).assertDoesNotExist()
    }

    /** The date and the opponent share one line, joined by the separator dot. */
    @Test
    fun theFrozenGameLogLineHoldsTheDateAndTheOpponentOnOneLineWithADot() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithTag("player.gamelog.row.0.label", useUnmergedTree = true).assertTextEquals("Nov 1 · @ OTT")
        assertEquals(1, lineCount("player.gamelog.row.0.label"))
    }

    /** A home game reads "vs", with the same dot after the date. */
    @Test
    fun aHomeGameReadsVsWithTheSeparatorDot() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = true)), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithTag("player.gamelog.row.0.label", useUnmergedTree = true).assertTextEquals("Nov 1 · vs OTT")
    }

    /** The frozen column is only as wide as its one line, and that line does not wrap. */
    @Test
    fun theFrozenGameLogColumnIsSizedToItsLineNotTheFixedWidth() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null)))
        selectTab("GameLog")

        val frozen = bounds("player.gamelog.row.0.frozen").width
        val fixed = with(rule.density) { 136.dp.toPx() }
        assertTrue("the frozen column is $frozen px, not narrower than the fixed $fixed px", frozen < fixed)
        assertEquals(bounds("player.gamelog.row.0.label").width, frozen, 2f)
    }

    /** A long opponent widens the frozen column to fit its text, and the text still sits on one line. */
    @Test
    fun theFrozenGameLogColumnWidensToALongOpponentAndTheLineStaysOnOneLine() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, opponent = longOpponent)), Freshness.Fresh, null)))
        selectTab("GameLog")

        val frozen = bounds("player.gamelog.row.0.frozen").width
        val fixed = with(rule.density) { 136.dp.toPx() }
        assertTrue("the frozen column is $frozen px, not wider than the fixed $fixed px", frozen > fixed)
        assertEquals(1, lineCount("player.gamelog.row.0.label"))
    }

    /** The frozen date and opponent keep their place while the numbers scroll sideways. */
    @Test
    fun theFrozenGameLogColumnStaysPutWhileTheNumbersScroll() {
        // At 360 dp the long opponent leaves less room than the numbers need, so there is something to scroll.
        show(
            loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, opponent = longOpponent)), Freshness.Fresh, null)),
            widthDp = 360,
        )
        selectTab("GameLog")
        rule.waitForIdle()
        val labelLeft = bounds("player.gamelog.row.0.label").left
        // Touch bounds are the cell's layout, not clipped to the scroller, so a cell scrolled out of view still has a place.
        val firstStatLeft = touchBounds("player.gamelog.row.0.g").left
        assertTrue("the stat cell is not laid out before the swipe", firstStatLeft > 0f)

        // A drag that starts in the numbers and runs about 100 dp left: longer than touch slop, and shorter than the numbers
        // overflow. A longer drag would hand its remainder to the pager and turn the tab.
        val distance = with(rule.density) { 100.dp.toPx() }
        rule.onNodeWithTag("player.gamelog.row.0", useUnmergedTree = true).performTouchInput {
            swipe(Offset(right - 4f, centerY), Offset(right - 4f - distance, centerY))
        }
        rule.waitForIdle()

        rule.onNodeWithTag("player.gamelog.row.0.label", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(labelLeft, bounds("player.gamelog.row.0.label").left, 0.5f)
        assertTrue(
            "the numbers did not scroll: g was $firstStatLeft, now ${touchBounds("player.gamelog.row.0.g").left}; " +
                "cells ${skaterLogKeys.map { "$it=${touchBounds("player.gamelog.row.0.$it")}/${bounds("player.gamelog.row.0.$it")}" }}; " +
                "card ends at ${bounds("player.gamelog").right}; " +
                "label ends at ${touchBounds("player.gamelog.row.0.label").right}",
            touchBounds("player.gamelog.row.0.g").left < firstStatLeft,
        )
    }

    /** A game row is a 48 dp button: a tap reports the game's id, and a screen reader hears it as a button. */
    @Test
    fun aTappedGameRowReportsItsGameIdAndIsAtLeast48dpTallAsAButton() {
        val clicked = mutableListOf<Long>()
        show(
            loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1)), Freshness.Fresh, null)),
            onGameClick = { clicked += it },
        )
        selectTab("GameLog")

        val row = rule.onNodeWithTag("player.gamelog.row.0", useUnmergedTree = true)
        row.assertHasClickAction()
        assertEquals(Role.Button, row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        val minimum = with(rule.density) { 48.dp.toPx() }
        val height =
            row
                .fetchSemanticsNode()
                .size.height
                .toFloat()
        assertTrue("the row is $height px tall, below $minimum px", height >= minimum)

        row.performClick()

        assertEquals(listOf(1001L), clicked)
    }

    /** A skater's whole game log lays out inside the card at 360 dp: every column is placed, none past the card's edge. */
    @Test
    fun aSkatersWholeGameLogFitsInsideTheCardAt360dp() {
        show(loaded(skaterProfile(), gameLog = Loadable(shuffledSkaterGames(), Freshness.Fresh, null)), widthDp = 360)
        selectTab("GameLog")

        val card = bounds("player.gamelog")
        assertFitsInCard(skaterLogKeys.flatMap { listOf("player.gamelog.header.$it", "player.gamelog.row.0.$it") }, card)
    }

    /** At 360 dp each number sits centred under its header, so the two centres match. */
    @Test
    fun aSkatersNumbersAreCentredUnderTheirHeadersAt360dp() {
        show(loaded(skaterProfile(), gameLog = Loadable(shuffledSkaterGames(), Freshness.Fresh, null)), widthDp = 360)
        selectTab("GameLog")

        skaterLogKeys.forEach { key ->
            val header = touchBounds("player.gamelog.header.$key")
            val value = touchBounds("player.gamelog.row.0.$key")
            assertTrue("$key is not laid out", header.width > 0f && value.width > 0f)
            assertEquals("$key is not centred under its header", (header.left + header.right) / 2f, (value.left + value.right) / 2f, 1f)
        }
    }

    /** A goalie's whole game log lays out inside the card at 360 dp: every column is placed, none past the card's edge. */
    @Test
    fun aGoaliesWholeGameLogFitsInsideTheCardAt360dp() {
        show(loaded(goalieProfile(), gameLog = Loadable(goalieGames(), Freshness.Fresh, null)), widthDp = 360)
        selectTab("GameLog")

        val card = bounds("player.gamelog")
        assertFitsInCard(goalieLogKeys.flatMap { listOf("player.gamelog.header.$it", "player.gamelog.row.0.$it") }, card)
    }

    /**
     * The career table's columns are fitted to their own content instead of a fixed 56 dp, which removes most of the
     * sideways scroll: thirteen columns at a fixed width needed 1243 px of travel at 360 dp, and fitted columns need
     * under 380. The bound is the regression guard - dropping the fitted widths puts it back over 1200. Per-column air (10 % of each column's own content) and a 28 dp floor cost about 150 px of that saving and are included in the bound.
     */
    @Test
    fun aSkatersCareerTableIsFittedToItsContentSoItBarelyScrollsAt360dp() {
        show(loaded(skaterProfile()), widthDp = 360)
        selectTab("Career")

        val max =
            rule
                .onAllNodesWithTag("player.career.scroll")[0]
                .fetchSemanticsNode()
                .config[SemanticsProperties.HorizontalScrollAxisRange]
                .maxValue()
        assertTrue("the career table scrolls by $max px, so its columns are not fitted to their content", max <= 540f)
    }

    /** The heading names the season being played, not the profile's newest season, which here is an older one. */
    @Test
    fun theGameLogHeadingNamesTheCurrentSeasonNotTheProfilesNewest() {
        show(
            loaded(
                skaterProfile(seasons = listOf(line(20252026, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 60)))),
                gameLog = Loadable(emptyList(), Freshness.Fresh, null),
            ),
        )
        selectTab("GameLog")

        rule.onNodeWithText("GAME LOG 2026-2027 SEASON").assertExists()
    }

    @Test
    fun aGoalieLogShowsEachDecisionNotGoalsOrAssists() {
        show(loaded(goalieProfile(), gameLog = Loadable(goalieGames(), Freshness.Fresh, null)))
        selectTab("GameLog")

        listOf("OT", "W", "L", "W", "OT", "L", "W").forEachIndexed { index, decision ->
            rule.onNodeWithTag("player.gamelog.row.$index.dec", useUnmergedTree = true).assertTextEquals(decision)
        }
        rule.onNodeWithTag("player.gamelog.header.dec").assertTextEquals("DEC")
        rule.onNodeWithTag("player.gamelog.header.g").assertDoesNotExist()
        rule.onNodeWithTag("player.gamelog.row.0.g", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("player.gamelog.row.0.ast", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anEmptyGameLogShowsItsEmptyStateAndTheOtherTabsStillWork() {
        show(loaded(skaterProfile(), gameLog = Loadable(emptyList(), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithTag("player.gamelog.empty").assertTextEquals("No NHL games yet")
        selectTab("Career")
        rule.onNodeWithTag("player.career.total.g").assertTextEquals("24")
        selectTab("Info")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
        selectTab("Season")
        rule.onNodeWithTag("player.season.row.0.ast.label").assertTextEquals("AST")
    }

    /** The season being played had no games, so the log names the older regular season its rows come from. */
    @Test
    fun aFallbackLogNamesTheOlderRegularSeasonItShows() {
        show(
            loaded(
                skaterProfile(seasons = listOf(line(20252026, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 60)))),
                gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null),
                gameLogFallbackSeason = 20252026,
            ),
        )
        selectTab("GameLog")

        rule.onNodeWithText("2025–26 REGULAR SEASON · NO GAMES YET THIS SEASON").assertExists()
        rule.onNodeWithTag("player.gamelog.row.0.label", useUnmergedTree = true).assertTextEquals("Nov 1 · @ OTT")
    }

    @Test
    fun aFailedGameLogSaysSoRetriesAndLeavesTheOtherTabsWorking() {
        var retries = 0
        show(
            loaded(skaterProfile(), gameLog = Loadable(null, Freshness.Missing, LoadError.Http(status = 500))),
            onRetry = { retries += 1 },
        )
        selectTab("GameLog")

        rule.onNodeWithText("The game log could not be loaded.").assertExists()
        rule.onNodeWithTag("player.gamelog.retry").performClick()
        assertEquals(1, retries)
        selectTab("Info")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
    }

    /** A swipe turns the page one tab at a time, and the bar's selection follows it, both ways. */
    @Test
    fun aSwipeMovesThroughTheTabsAndTheBottomBarFollowsIt() {
        show(loaded(skaterProfile()))
        rule.onNodeWithTag("player.tab.Season").assertIsSelected()

        swipePager(towardsTheNextTab = true)
        rule.onNodeWithTag("player.tab.Career").assertIsSelected()
        swipePager(towardsTheNextTab = true)
        rule.onNodeWithTag("player.tab.GameLog").assertIsSelected()
        swipePager(towardsTheNextTab = true)
        rule.onNodeWithTag("player.tab.Info").assertIsSelected()
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")

        swipePager(towardsTheNextTab = false)
        rule.onNodeWithTag("player.tab.GameLog").assertIsSelected()
        swipePager(towardsTheNextTab = false)
        rule.onNodeWithTag("player.tab.Career").assertIsSelected()
        swipePager(towardsTheNextTab = false)
        rule.onNodeWithTag("player.tab.Season").assertIsSelected()
    }

    /** The five facts, each with its label and its value. Height and weight share one cell. */
    @Test
    fun theInfoCardShowsTheFiveFactsAsLabelsAndValuesForASkater() {
        show(loaded(skaterProfile()))
        selectTab("Info")

        rule.onNodeWithTag("player.info.heightweight.label").assertTextEquals("Height and weight")
        rule.onNodeWithTag("player.info.heightweight").assertTextEquals("5′ 10″ / 155 lb")
        rule.onNodeWithTag("player.info.shoots.label").assertTextEquals("Shoots")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
        rule.onNodeWithTag("player.info.born.label").assertTextEquals("Born")
        rule.onNodeWithTag("player.info.born").assertTextEquals("Jan 9, 1999 (27)")
        rule.onNodeWithTag("player.info.birthplace.label").assertTextEquals("Birthplace")
        rule.onNodeWithTag("player.info.birthplace").assertTextEquals("Tampere, FIN")
        rule.onNodeWithTag("player.info.draft.label").assertTextEquals("Draft")
        rule.onNodeWithTag("player.info.draft").assertTextEquals("2017 · FLA · Round 2 · Pick 9 · Overall 40")
    }

    @Test
    fun theInfoCardSaysShootsForAGoalieToo() {
        show(loaded(goalieProfile()))
        selectTab("Info")

        rule.onNodeWithTag("player.info.shoots.label").assertTextEquals("Shoots")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
    }

    /**
     * The card is exactly five facts, read across then down. A screen reader hears them in the order the eye reads them,
     * so the tree's order is the order on the screen, sorted by row and then by column.
     */
    @Test
    fun theInfoFactsReadAcrossThenDownAndAScreenReaderHearsThemInThatOrder() {
        show(loaded(skaterProfile()))
        selectTab("Info")

        val facts = rule.onAllNodes(infoValue).fetchSemanticsNodes()
        assertEquals("the card does not hold exactly five facts", 5, facts.size)
        val inTreeOrder = facts.map { it.config[SemanticsProperties.TestTag] }
        val onScreen =
            facts
                .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
                .map { it.config[SemanticsProperties.TestTag] }

        assertEquals(
            listOf(
                "player.info.heightweight",
                "player.info.shoots",
                "player.info.born",
                "player.info.birthplace",
                "player.info.draft",
            ),
            inTreeOrder,
        )
        assertEquals("the reading order differs from the screen order", onScreen, inTreeOrder)
        assertEquals(bounds("player.info.heightweight.cell").left, bounds("player.info.born.cell").left, 0.5f)
        assertTrue(
            "the right column is not to the right",
            bounds("player.info.shoots.cell").left > bounds("player.info.heightweight.cell").left,
        )
    }

    /** Draft spans both columns: its cell starts where the left column starts and ends where the right one ends. */
    @Test
    fun theDraftCellSpansTheWholeCardAcrossBothColumns() {
        show(loaded(skaterProfile()))
        selectTab("Info")

        val draft = bounds("player.info.draft.cell")
        val heightWeight = bounds("player.info.heightweight.cell")
        val shoots = bounds("player.info.shoots.cell")
        assertEquals(heightWeight.left, draft.left, 1f)
        assertEquals(shoots.right, draft.right, 1f)
        assertTrue(
            "the draft is ${draft.width} px, not wider than one column ${heightWeight.width} px",
            draft.width > heightWeight.width * 1.5f,
        )
    }

    /** A player born on today's date a year ago is one year old, not zero, on that day. */
    @Test
    fun aPlayerBornOnTodayAYearAgoIsOneOnTheBirthday() {
        show(loaded(skaterProfile(birthDate = "2025-11-05")), today = LocalDate(2026, 11, 5))
        selectTab("Info")

        rule.onNodeWithTag("player.info.born").assertTextEquals("Nov 5, 2025 (1)")
    }

    /** The day before that birthday the player is still zero: the age does not turn over early. */
    @Test
    fun aPlayerIsStillZeroTheDayBeforeTheirBirthday() {
        show(loaded(skaterProfile(birthDate = "2025-11-05")), today = LocalDate(2026, 11, 4))
        selectTab("Info")

        rule.onNodeWithTag("player.info.born").assertTextEquals("Nov 5, 2025 (0)")
    }

    /** A missing fact prints the dash, and the draft still spans the card when the feed has no draft record. */
    @Test
    fun aMissingFactPrintsTheDashAndTheDraftStillSpansTheWholeCard() {
        show(
            loaded(
                skaterProfile().copy(
                    heightInches = null,
                    weightPounds = null,
                    shoots = null,
                    birthDate = null,
                    birthCity = null,
                    birthCountry = null,
                    draft = null,
                ),
            ),
        )
        selectTab("Info")

        rule.onNodeWithTag("player.info.heightweight").assertTextEquals("— / —")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("—")
        rule.onNodeWithTag("player.info.born").assertTextEquals("—")
        rule.onNodeWithTag("player.info.birthplace").assertTextEquals("—")
        rule.onNodeWithTag("player.info.draft").assertTextEquals("—")
        assertEquals(bounds("player.info.heightweight.cell").left, bounds("player.info.draft.cell").left, 1f)
        assertEquals(bounds("player.info.shoots.cell").right, bounds("player.info.draft.cell").right, 1f)
    }

    @Test
    fun aFailedLoadWithNothingToShowShowsTheErrorAndRetryCallsBack() {
        var retries = 0
        show(
            PlayerDetailUiState(
                isLoading = false,
                profile = Loadable(null, Freshness.Missing, LoadError.Http(status = 500)),
            ),
            onRetry = { retries += 1 },
        )

        rule.onNodeWithTag("player.error").assertExists()
        rule.onNodeWithTag("player.retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun aPageStillLoadingSaysSo() {
        show(PlayerDetailUiState())

        rule.onNodeWithTag("player.loading").assertExists()
    }

    private fun show(
        state: PlayerDetailUiState,
        onRetry: () -> Unit = {},
        onGameClick: (Long) -> Unit = {},
        today: LocalDate = TODAY,
        widthDp: Int? = null,
    ) {
        rule.setContent {
            RouxTheme {
                PlayerDetailScreen(
                    state = state,
                    currentSeason = CURRENT_SEASON,
                    today = today,
                    onBack = {},
                    onRetry = onRetry,
                    onGameClick = onGameClick,
                    modifier = widthDp?.let { Modifier.width(it.dp) } ?: Modifier,
                )
            }
        }
    }

    private fun selectTab(name: String) {
        rule.onNodeWithTag("player.tab.$name").performClick()
    }

    /**
     * A horizontal swipe on the pager, starting near its top edge. Starting at the top keeps the touch off the career and
     * game-log tables, whose own sideways scroll would otherwise take the gesture.
     */
    private fun swipePager(towardsTheNextTab: Boolean) {
        rule.onNodeWithTag("player.pager").performTouchInput {
            val y = top + 4f
            val start = if (towardsTheNextTab) Offset(right - 16f, y) else Offset(left + 16f, y)
            val end = if (towardsTheNextTab) Offset(left + 16f, y) else Offset(right - 16f, y)
            swipe(start, end)
        }
        rule.waitForIdle()
    }

    /** The info card's value lines: every tagged fact value, but neither its label nor the cell that wraps the pair. */
    private val infoValue =
        SemanticsMatcher("an info value") { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            tag != null && tag.startsWith("player.info.") && !tag.endsWith(".label") && !tag.endsWith(".cell")
        }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Where a node is laid out, not clipped to the scroller it sits in, so a cell scrolled off screen still has a position. */
    private fun touchBounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot

    /**
     * Each tag is laid out and ends inside the card's right edge. A cell scrolled past the viewport is not laid out at all
     * and reports empty bounds, so an empty rect fails here as well as one that runs past the edge.
     */
    private fun assertFitsInCard(
        tags: List<String>,
        card: Rect,
    ) {
        tags.forEach { tag ->
            val cell = touchBounds(tag)
            assertTrue("$tag is not laid out inside the card", cell.width > 0f)
            assertTrue("$tag ends at ${cell.right}, past the card at ${card.right}", cell.right <= card.right)
        }
    }

    private fun top(tag: String): Float = bounds(tag).top

    /** How many lines the text under a tag lays out to. A label that wraps reads as more than one. */
    private fun lineCount(tag: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        return layouts.single().lineCount
    }

    private fun loaded(
        profile: PlayerProfile,
        gameLog: Loadable<List<PlayerGame>>? = null,
        gameLogFallbackSeason: Int? = null,
    ) = PlayerDetailUiState(
        isLoading = false,
        profile = Loadable(profile, Freshness.Fresh, null),
        gameLog = gameLog,
        gameLogFallbackSeason = gameLogFallbackSeason,
    )

    private fun skaterProfile(
        seasons: List<PlayerSeasonLine> = skaterSeasons,
        position: String = "C",
        birthDate: String? = "1999-01-09",
    ) = PlayerProfile(
        playerId = 8480037L,
        firstName = "Aleksi",
        lastName = "Heponiemi",
        position = position,
        sweaterNumber = 20,
        heightInches = 70,
        weightPounds = 155,
        birthDate = birthDate,
        birthCity = "Tampere",
        birthCountry = "FIN",
        shoots = "L",
        draft = PlayerDraft(year = 2017, teamAbbrev = "FLA", round = 2, pickInRound = 9, overallPick = 40),
        career = PlayerCareer(regularSeason = PlayerTotals.Skater(faceoffWinningPctg = 0.5, avgTimeOnIceSeconds = 672L)),
        seasons = seasons,
    )

    private fun goalieProfile(seasons: List<PlayerSeasonLine> = goalieSeasons) =
        PlayerProfile(
            playerId = 8476316L,
            firstName = "Laurent",
            lastName = "Brossoit",
            position = "G",
            sweaterNumber = 31,
            heightInches = 74,
            weightPounds = 217,
            birthDate = "1993-03-23",
            birthCity = "Port Alberni",
            birthCountry = "CAN",
            shoots = "L",
            career = PlayerCareer(playoffs = PlayerTotals.Goalie(gamesPlayed = 10)),
            seasons = seasons,
        )

    /** An opponent name long enough that its line is wider than the fixed label width, for the frozen-column tests. */
    private val longOpponent = "Vancouver Canucks Hockey Club"

    /** The stat keys of a skater's game log, in column order. */
    private val skaterLogKeys = listOf("g", "ast", "pts", "pm", "sog", "toi")

    /** The stat keys of a goalie's game log, in column order. */
    private val goalieLogKeys = listOf("dec", "sa", "ga", "sv", "toi")

    /** The club abbreviations the feed never sends; the adapter supplies them, so the fixtures here do the same. */
    private val clubAbbrevs = mapOf("Carolina Hurricanes" to "CAR", "Boston Bruins" to "BOS", "Anaheim Ducks" to "ANA")

    private fun line(
        season: Int,
        league: String,
        team: String,
        sequence: Int,
        totals: PlayerTotals,
        gameType: Int = 2,
    ) = PlayerSeasonLine(
        season = season,
        gameTypeId = gameType,
        leagueAbbrev = league,
        teamName = team,
        sequence = sequence,
        totals = totals,
        teamAbbrev = clubAbbrevs[team],
    )

    private fun skaterTotals(
        gamesPlayed: Int? = 25,
        goals: Int? = 2,
        assists: Int? = 4,
        shots: Int? = 28,
        shorthandedGoals: Int? = 0,
    ) = PlayerTotals.Skater(
        gamesPlayed = gamesPlayed,
        goals = goals,
        assists = assists,
        plusMinus = -1,
        shots = shots,
        shorthandedGoals = shorthandedGoals,
        shootingPctg = 0.0714,
        avgTimeOnIceSeconds = 672L,
    )

    private fun goalie(
        gamesPlayed: Int? = null,
        gamesStarted: Int? = null,
        wins: Int? = null,
        losses: Int? = null,
        overtimeLosses: Int? = null,
        goalsAgainst: Int? = null,
        shotsAgainst: Int? = null,
        timeOnIceSeconds: Long? = null,
        shutouts: Int? = null,
    ) = PlayerTotals.Goalie(
        gamesPlayed = gamesPlayed,
        gamesStarted = gamesStarted,
        wins = wins,
        losses = losses,
        overtimeLosses = overtimeLosses,
        goalsAgainst = goalsAgainst,
        shotsAgainst = shotsAgainst,
        timeOnIceSeconds = timeOnIceSeconds,
        shutouts = shutouts,
    )

    /** Three NHL seasons and one AHL season. The career tab shows the three NHL rows only. */
    private val skaterSeasons =
        listOf(
            line(20242025, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 60, goals = 10, assists = 15, shots = 100)),
            line(20252026, "AHL", "Charlotte Checkers", 1, skaterTotals(gamesPlayed = 10, goals = 99, assists = 1, shots = 3)),
            line(20252026, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 70, goals = 12, assists = 20, shots = 120)),
            line(20262027, "NHL", "Carolina Hurricanes", 1, skaterTotals(gamesPlayed = 13, goals = 2, assists = 3, shots = 30)),
        )

    /** Two NHL seasons, the current one with its AHL row. Sums: 50 games, 1,000 shots against, 100 goals against. */
    private val goalieSeasons =
        listOf(
            line(
                20252026,
                "NHL",
                "Anaheim Ducks",
                1,
                goalie(
                    gamesPlayed = 30,
                    gamesStarted = 28,
                    wins = 12,
                    losses = 10,
                    overtimeLosses = 3,
                    goalsAgainst = 60,
                    shotsAgainst = 600,
                    timeOnIceSeconds = 54000L,
                    shutouts = 2,
                ),
            ),
            line(
                20262027,
                "NHL",
                "Anaheim Ducks",
                1,
                goalie(
                    gamesPlayed = 20,
                    gamesStarted = 20,
                    wins = 10,
                    losses = 8,
                    overtimeLosses = 2,
                    goalsAgainst = 40,
                    shotsAgainst = 400,
                    timeOnIceSeconds = 36000L,
                    shutouts = 1,
                ),
            ),
            line(20262027, "AHL", "San Diego Gulls", 85177, goalie(gamesPlayed = 6)),
        )

    private fun skaterGame(
        day: Int,
        isHome: Boolean = true,
        opponent: String = "OTT",
    ) = PlayerGame.Skater(
        gameId = 1000L + day,
        gameDate = "2026-11-0$day",
        opponentAbbrev = opponent,
        isHome = isHome,
        goals = 1,
        assists = 2,
        points = 3,
        plusMinus = 1,
        shots = day,
        timeOnIceSeconds = 754L,
    )

    /** Seven games in a shuffled feed order. Each game's shots equal its day. */
    private fun shuffledSkaterGames(): List<PlayerGame> = listOf(3, 7, 1, 5, 2, 6, 4).map { skaterGame(it) }

    /** Seven goalie games in a shuffled order. Days 1 to 7 are W, L, OT, W, L, W, OT. */
    private fun goalieGames(): List<PlayerGame> {
        val decisions =
            listOf(
                GoalieDecision.Win,
                GoalieDecision.Loss,
                GoalieDecision.OvertimeLoss,
                GoalieDecision.Win,
                GoalieDecision.Loss,
                GoalieDecision.Win,
                GoalieDecision.OvertimeLoss,
            )
        return listOf(3, 7, 1, 5, 2, 6, 4).map { day ->
            PlayerGame.Goalie(
                gameId = 2000L + day,
                gameDate = "2026-11-0$day",
                opponentAbbrev = "VAN",
                isHome = day % 2 == 0,
                decision = decisions[day - 1],
                shotsAgainst = 20 + day,
                goalsAgainst = day,
                savePercentage = 0.9,
                timeOnIceSeconds = 3600L,
            )
        }
    }
}
