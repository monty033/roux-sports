package net.montycasa.roux.ui.player

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.math.abs

private const val CURRENT_SEASON = 20262027

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
class PlayerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theFourTabsRenderInTheBriefsOrderWithTheBriefsNames() {
        show(loaded(skaterProfile()))

        val lefts = listOf("season", "career", "gamelog", "info").map { centreOrLeft("player.tab.$it") }
        assertTrue("tabs are not in order: $lefts", lefts.zipWithNext().all { (left, right) -> left < right })
        rule.onNodeWithTag("player.tab.season").assertTextEquals("Season stats")
        rule.onNodeWithTag("player.tab.career").assertTextEquals("Career")
        rule.onNodeWithTag("player.tab.gamelog").assertTextEquals("Game log")
        rule.onNodeWithTag("player.tab.info").assertTextEquals("Info")
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

    @Test
    fun theSeasonHeadersAreAstAndSogAndEachSharesACentreLineWithItsNumbers() {
        show(loaded(skaterProfile()))

        rule.onNodeWithTag("player.season.header.ast").assertTextEquals("AST")
        rule.onNodeWithTag("player.season.header.sog").assertTextEquals("SOG")
        val oneDp = rule.density.density
        listOf("ast", "sog").forEach { key ->
            val header = centreX("player.season.header.$key")
            val numbers = centreX("player.season.row.0.$key")
            assertTrue("$key: header centre $header, numbers centre $numbers", abs(header - numbers) <= oneDp)
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
        selectTab("career")

        rule.onNodeWithTag("player.career.row.0.shg").assertTextEquals("—")
        rule.onNodeWithTag("player.career.row.0.g").assertTextEquals("0")
    }

    @Test
    fun theCareerTableShowsThreeNhlSeasonsThenTheTotalAndLeavesOutTheAhlRow() {
        show(loaded(skaterProfile()))
        selectTab("career")

        rule.onNodeWithTag("player.career.row.0.label").assertTextEquals("2024-25 · Carolina Hurricanes")
        rule.onNodeWithTag("player.career.row.2.label").assertTextEquals("2026-27 · Carolina Hurricanes")
        rule.onNodeWithTag("player.career.row.3").assertDoesNotExist()
        rule.onNodeWithTag("player.career.total.label").assertTextEquals("Total")
        rule.onNodeWithTag("player.career.total.g").assertTextEquals("24")
        rule.onNodeWithTag("player.career.total.toi").assertTextEquals("11:12")
    }

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
        selectTab("career")

        rule.onNodeWithTag("player.career.row.0.label").assertTextEquals("2025-26 · Carolina Hurricanes")
        rule.onNodeWithTag("player.career.row.1.label").assertTextEquals("2025-26 · Boston Bruins")
        rule.onNodeWithTag("player.career.row.2").assertDoesNotExist()
    }

    /**
     * The traded fixture where double counting shows. Two clubs in 2023-24 hold 30 and 20 games, 5 and 4 goals. The true
     * career is 180 games and 31 goals. Counting that season once more for each club would read 230 games and 40 goals.
     */
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
        selectTab("career")

        rule.onNodeWithTag("player.career.total.gp").assertTextEquals("180")
        rule.onNodeWithTag("player.career.total.g").assertTextEquals("31")
    }

    @Test
    fun aGoalieCareerTotalDerivesItsAverageAndSavePercentageAndShowsThePlayoffsRow() {
        show(loaded(goalieProfile()))
        selectTab("career")

        rule.onNodeWithTag("player.career.total.gp").assertTextEquals("50")
        rule.onNodeWithTag("player.career.total.gaa").assertTextEquals("4.00")
        rule.onNodeWithTag("player.career.total.sv").assertTextEquals(".900")
        rule.onNodeWithTag("player.playoffs.label").assertExists()
        rule.onNodeWithTag("player.playoffs.row.gp").assertTextEquals("10")
    }

    @Test
    fun aGoalieSeasonShowsTheNhlRowThenTheAhlRowAndTheGoalieStatSet() {
        show(loaded(goalieProfile()))

        rule.onNodeWithTag("player.number").assertTextEquals("#31 · G")
        rule.onNodeWithTag("player.season.row.0.label").assertTextEquals("NHL · Anaheim Ducks")
        rule.onNodeWithTag("player.season.row.1.label").assertTextEquals("AHL · San Diego Gulls")
        rule.onNodeWithTag("player.season.header.w").assertTextEquals("W")
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

    @Test
    fun theGameLogRendersSevenGamesNewestFirstWhateverOrderTheFeedSentThem() {
        show(loaded(skaterProfile(), gameLog = Loadable(shuffledSkaterGames(), Freshness.Fresh, null)))
        selectTab("gamelog")

        // Each game's shots equal its day, so the newest game (the 7th) shows 7 at the top.
        (0..6).forEach { index ->
            rule.onNodeWithTag("player.gamelog.row.$index.sog").assertTextEquals((7 - index).toString())
        }
        rule.onNodeWithTag("player.gamelog.row.7").assertDoesNotExist()
    }

    @Test
    fun aSkaterGameShowsTheOpponentWithAHomeOrAwayMarkerNotAResult() {
        show(loaded(skaterProfile(), gameLog = Loadable(listOf(skaterGame(day = 1, isHome = false)), Freshness.Fresh, null)))
        selectTab("gamelog")

        rule.onNodeWithTag("player.gamelog.row.0.label").assertTextEquals("Nov 1, 2026 · @ OTT")
        rule.onNodeWithTag("player.gamelog.header.ast").assertTextEquals("AST")
        rule.onNodeWithTag("player.gamelog.header.sog").assertTextEquals("SOG")
        rule.onNodeWithTag("player.gamelog.row.0.dec").assertDoesNotExist()
    }

    @Test
    fun aGoalieLogShowsEachDecisionNotGoalsOrAssists() {
        show(loaded(goalieProfile(), gameLog = Loadable(goalieGames(), Freshness.Fresh, null)))
        selectTab("gamelog")

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
        selectTab("gamelog")

        rule.onNodeWithTag("player.gamelog.empty").assertTextEquals("No games this season.")
        selectTab("career")
        rule.onNodeWithTag("player.career.total.g").assertTextEquals("24")
        selectTab("info")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
        selectTab("season")
        rule.onNodeWithTag("player.season.header.ast").assertTextEquals("AST")
    }

    @Test
    fun aFailedGameLogSaysSoRetriesAndLeavesTheOtherTabsWorking() {
        var retries = 0
        show(
            loaded(skaterProfile(), gameLog = Loadable(null, Freshness.Missing, LoadError.Http(status = 500))),
            onRetry = { retries += 1 },
        )
        selectTab("gamelog")

        rule.onNodeWithText("The game log could not be loaded.").assertExists()
        rule.onNodeWithTag("player.gamelog.retry").performClick()
        assertEquals(1, retries)
        selectTab("info")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
    }

    @Test
    fun theInfoCardIsLabelAndValueRowsWithShootsForASkater() {
        show(loaded(skaterProfile()))
        selectTab("info")

        rule.onNodeWithTag("player.info.shoots.label").assertTextEquals("Shoots")
        rule.onNodeWithTag("player.info.shoots").assertTextEquals("L")
        rule.onNodeWithTag("player.info.height.label").assertTextEquals("Height")
        rule.onNodeWithTag("player.info.height").assertTextEquals("5′ 10″")
        rule.onNodeWithTag("player.info.draft").assertTextEquals("2017 · FLA · Round 2 · Pick 9 · Overall 40")
    }

    @Test
    fun theInfoCardSaysShootsForAGoalieToo() {
        show(loaded(goalieProfile()))
        selectTab("info")

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

    private fun selectTab(key: String) {
        rule.onNodeWithTag("player.tab.$key").performClick()
    }

    private fun centreX(tag: String): Float =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot.center.x

    private fun centreOrLeft(tag: String): Float =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot.left

    private fun loaded(
        profile: PlayerProfile,
        gameLog: Loadable<List<PlayerGame>>? = null,
    ) = PlayerDetailUiState(isLoading = false, profile = Loadable(profile, Freshness.Fresh, null), gameLog = gameLog)

    private fun skaterProfile(seasons: List<PlayerSeasonLine> = skaterSeasons) =
        PlayerProfile(
            playerId = 8480037L,
            firstName = "Aleksi",
            lastName = "Heponiemi",
            position = "C",
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

    private fun line(
        season: Int,
        league: String,
        team: String,
        sequence: Int,
        totals: PlayerTotals,
    ) = PlayerSeasonLine(
        season = season,
        gameTypeId = 2,
        leagueAbbrev = league,
        teamName = team,
        sequence = sequence,
        totals = totals,
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
