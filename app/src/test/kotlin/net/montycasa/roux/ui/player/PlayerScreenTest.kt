package net.montycasa.roux.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
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

private const val CURRENT_SEASON = 20262027

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
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
        rule.onNodeWithTag("player.season.row.0.label").assertTextEquals("NHL · Carolina Hurricanes")
        rule.onNodeWithTag("player.season.row.0.gp").assertTextEquals("13")
        rule.onNodeWithTag("player.season.row.0.sog").assertTextEquals("30")
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
     * A season's playoff row is marked as playoffs and follows that season's regular row. The playoff total follows the
     * regular total.
     */
    @Test
    fun aPlayoffRowFollowsItsSeasonAndThePlayoffTotalFollowsTheRegularTotal() {
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
        rule.onNodeWithTag("player.career.row.1.label").assertTextEquals("2024-25 · Playoffs")
        rule.onNodeWithTag("player.career.row.1.gp").assertTextEquals("6")
        rule.onNodeWithTag("player.career.row.2.label").assertTextEquals("2025-26")
        rule.onNodeWithTag("player.career.playoffs.total.label").assertTextEquals("Playoffs total")
        rule.onNodeWithTag("player.career.playoffs.total.gp").assertTextEquals("6")
        assertTrue(top("player.career.row.1") > top("player.career.row.0"))
        assertTrue(top("player.career.total") < top("player.career.playoffs.total"))
        rule.onNodeWithTag("player.playoffs.label").assertDoesNotExist()
    }

    @Test
    fun aGoalieCareerTotalDerivesItsAverageAndSavePercentageAndShowsThePlayoffsTotal() {
        show(loaded(goalieProfile()))
        selectTab("Career")

        rule.onNodeWithTag("player.career.total.gp").assertTextEquals("50")
        rule.onNodeWithTag("player.career.total.gaa").assertTextEquals("4.00")
        rule.onNodeWithTag("player.career.total.sv").assertTextEquals(".900")
        rule.onNodeWithTag("player.career.playoffs.total.label").assertTextEquals("Playoffs total")
        rule.onNodeWithTag("player.career.playoffs.total.gp").assertTextEquals("10")
    }

    @Test
    fun aGoalieSeasonShowsTheNhlRowThenTheAhlRowAndTheGoalieStatSet() {
        show(loaded(goalieProfile()))

        rule.onNodeWithTag("player.number").assertTextEquals("#31 · G")
        rule.onNodeWithTag("player.season.row.0.label").assertTextEquals("NHL · Anaheim Ducks")
        rule.onNodeWithTag("player.season.row.1.label").assertTextEquals("AHL · San Diego Gulls")
        rule.onNodeWithTag("player.season.row.0.w.label").assertTextEquals("W")
    }

    @Test
    fun theSeasonTitleNamesAPastSeasonByItsYearsAndLeague() {
        show(
            loaded(
                skaterProfile(
                    seasons = listOf(line(20252026, "SHL", "HV71", 11, skaterTotals(gamesPlayed = 50))),
                ),
            ),
        )

        rule.onNodeWithText("2025-26 SHL", ignoreCase = true).assertExists()
        rule.onNodeWithText("This season", ignoreCase = true).assertDoesNotExist()
    }

    /** The heading names the season in full years, and the rows read the date as "Nov 1". */
    @Test
    fun theGameLogHeadingNamesTheSeasonInFullYearsAndTheRowDateReadsNovOne() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithText("GAME LOG 2026-2027 SEASON").assertExists()
        rule.onNodeWithTag("player.gamelog.row.0.label").assertTextEquals("Nov 1")
    }

    @Test
    fun theGameLogRendersSevenGamesNewestFirstWhateverOrderTheFeedSentThem() {
        show(loaded(skaterProfile(), gameLog = Loadable(shuffledSkaterGames(), Freshness.Fresh, null)))
        selectTab("GameLog")

        // Each game's shots equal its day, so the newest game (the 7th) shows 7 at the top.
        (0..6).forEach { index ->
            rule.onNodeWithTag("player.gamelog.row.$index.sog").assertTextEquals((7 - index).toString())
        }
        rule.onNodeWithTag("player.gamelog.row.7").assertDoesNotExist()
    }

    @Test
    fun aSkaterGameShowsTheOpponentWithAHomeOrAwayMarkerNotAResult() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithTag("player.gamelog.row.0.opp").assertTextEquals("@ OTT")
        rule.onNodeWithTag("player.gamelog.header.opp").assertTextEquals("Opp")
        rule.onNodeWithTag("player.gamelog.header.ast").assertTextEquals("AST")
        rule.onNodeWithTag("player.gamelog.header.sog").assertTextEquals("SOG")
        rule.onNodeWithTag("player.gamelog.row.0.dec").assertDoesNotExist()
    }

    @Test
    fun aGoalieLogShowsEachDecisionNotGoalsOrAssists() {
        show(loaded(goalieProfile(), gameLog = Loadable(goalieGames(), Freshness.Fresh, null)))
        selectTab("GameLog")

        listOf("OT", "W", "L", "W", "OT", "L", "W").forEachIndexed { index, decision ->
            rule.onNodeWithTag("player.gamelog.row.$index.dec").assertTextEquals(decision)
        }
        rule.onNodeWithTag("player.gamelog.header.dec").assertTextEquals("DEC")
        rule.onNodeWithTag("player.gamelog.header.g").assertDoesNotExist()
        rule.onNodeWithTag("player.gamelog.row.0.g").assertDoesNotExist()
        rule.onNodeWithTag("player.gamelog.row.0.ast").assertDoesNotExist()
    }

    @Test
    fun anEmptyGameLogShowsItsEmptyStateAndTheOtherTabsStillWork() {
        show(loaded(skaterProfile(), gameLog = Loadable(emptyList(), Freshness.Fresh, null)))
        selectTab("GameLog")

        rule.onNodeWithTag("player.gamelog.empty").assertTextEquals("No games this season.")
        selectTab("Career")
        rule.onNodeWithTag("player.career.total.g").assertTextEquals("24")
        selectTab("Info")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
        selectTab("Season")
        rule.onNodeWithTag("player.season.row.0.ast.label").assertTextEquals("AST")
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

    @Test
    fun theInfoCardIsLabelAndValueRowsWithShootsForASkater() {
        show(loaded(skaterProfile()))
        selectTab("Info")

        rule.onNodeWithTag("player.info.shoots.label").assertTextEquals("Shoots")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
        rule.onNodeWithTag("player.info.height.label").assertTextEquals("Height")
        rule.onNodeWithTag("player.info.height").assertTextEquals("5′ 10″")
        rule.onNodeWithTag("player.info.draft").assertTextEquals("2017 · FLA · Round 2 · Pick 9 · Overall 40")
    }

    @Test
    fun theInfoCardSaysShootsForAGoalieToo() {
        show(loaded(goalieProfile()))
        selectTab("Info")

        rule.onNodeWithTag("player.info.shoots.label").assertTextEquals("Shoots")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
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
    ) {
        rule.setContent {
            RouxTheme {
                PlayerDetailScreen(
                    state = state,
                    currentSeason = CURRENT_SEASON,
                    onBack = {},
                    onRetry = onRetry,
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

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun top(tag: String): Float = bounds(tag).top

    private fun loaded(
        profile: PlayerProfile,
        gameLog: Loadable<List<PlayerGame>>? = null,
    ) = PlayerDetailUiState(isLoading = false, profile = Loadable(profile, Freshness.Fresh, null), gameLog = gameLog)

    private fun skaterProfile(
        seasons: List<PlayerSeasonLine> = skaterSeasons,
        position: String = "C",
    ) = PlayerProfile(
        playerId = 8480037L,
        firstName = "Aleksi",
        lastName = "Heponiemi",
        position = position,
        sweaterNumber = 20,
        heightInches = 70,
        weightPounds = 155,
        birthDate = "1999-01-09",
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
    ) = PlayerGame.Skater(
        gameId = 1000L + day,
        gameDate = "2026-11-0$day",
        opponentAbbrev = "OTT",
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
