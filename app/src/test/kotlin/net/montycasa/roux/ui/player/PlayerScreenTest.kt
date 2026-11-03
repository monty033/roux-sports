package net.montycasa.roux.ui.player

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerDraft
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerRecentGame
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
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
    fun theSkaterPageRendersTheHeaderVitalsSeasonCareerAndLastFive() {
        show(loaded(skaterProfile()))

        rule.onNodeWithTag("player.name").assertTextEquals("Aleksi Heponiemi")
        rule.onNodeWithTag("player.number").assertTextEquals("#20 · C")
        rule.onNodeWithTag("player.vitals.height").assertTextEquals("5′ 10″")
        rule.onNodeWithTag("player.vitals.weight").assertTextEquals("155 lb")
        rule.onNodeWithTag("player.vitals.shoots").assertTextEquals("L")
        rule.onNodeWithTag("player.vitals.birthplace").assertTextEquals("Tampere, FIN")
        rule.onNodeWithTag("player.vitals.draft").assertTextEquals("2017 · FLA · Round 2 · Pick 9 · Overall 40")

        rule.onNodeWithText("This season", ignoreCase = true).assertExists()
        rule.onNodeWithTag("player.season.0.header").assertTextEquals("SHL · HV71")
        rule.onNodeWithTag("player.season.0.gp").assertTextEquals("13")

        rule.onNodeWithTag("player.career.gp").assertTextEquals("25")
        rule.onNodeWithTag("player.career.s").assertTextEquals("28")
        rule.onNodeWithTag("player.career.shpct").assertTextEquals("7.1%")
        rule.onNodeWithTag("player.career.toi").assertTextEquals("11:12")

        rule.onNodeWithTag("player.last5.0.stats").assertTextEquals("1 G, 2 A, 3 PTS, +1, 4 S, 12:34 TOI")
    }

    @Test
    fun aMissingStatShowsTheEmDashAndARealZeroShowsZero() {
        show(loaded(skaterProfile(career = skaterTotals(goals = 0, shorthandedGoals = null))))

        rule.onNodeWithTag("player.career.shg").assertTextEquals("—")
        rule.onNodeWithTag("player.career.g").assertTextEquals("0")
    }

    @Test
    fun aProfileWithNoLastFiveGamesHidesThatCard() {
        show(loaded(skaterProfile(lastFive = emptyList())))

        rule.onNodeWithTag("player.name").assertTextEquals("Aleksi Heponiemi")
        rule.onNodeWithTag("player.last5").assertDoesNotExist()
    }

    @Test
    fun aGoalieProfileShowsTheGoalieStatSetAndThePlayoffsLine() {
        show(loaded(goalieProfile()))

        rule.onNodeWithTag("player.number").assertTextEquals("#31 · G")
        rule.onNodeWithTag("player.career.gp").assertTextEquals("142")
        rule.onNodeWithTag("player.career.w").assertTextEquals("65")
        rule.onNodeWithTag("player.career.gaa").assertTextEquals("2.67")
        rule.onNodeWithTag("player.career.sv").assertTextEquals(".910")
        rule.onNodeWithTag("player.career.toi").assertTextEquals("7716:44")
        rule.onNodeWithTag("player.playoffs.label").assertExists()
        rule.onNodeWithTag("player.playoffs.gp").assertTextEquals("10")
    }

    @Test
    fun theNewestSeasonShowsEveryRowWithTheNhlRowFirst() {
        show(loaded(goalieProfile()))

        rule.onNodeWithTag("player.season.0.header").assertTextEquals("NHL · Anaheim Ducks")
        rule.onNodeWithTag("player.season.1.header").assertTextEquals("AHL · San Diego Gulls")
    }

    @Test
    fun aSeasonThatIsNotTheCurrentOneIsNamedByItsYearsAndLeague() {
        val lastSeason =
            skaterProfile(
                seasons =
                    listOf(
                        PlayerSeasonLine(
                            season = 20252026,
                            gameTypeId = 2,
                            leagueAbbrev = "SHL",
                            teamName = "HV71",
                            sequence = 11,
                            totals = skaterTotals(gamesPlayed = 50),
                        ),
                    ),
            )

        show(loaded(lastSeason))

        rule.onNodeWithText("2025-26 SHL", ignoreCase = true).assertExists()
        rule.onNodeWithText("This season", ignoreCase = true).assertDoesNotExist()
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

    private fun loaded(profile: PlayerProfile) = PlayerDetailUiState(isLoading = false, profile = Loadable(profile, Freshness.Fresh, null))

    private fun skaterProfile(
        career: PlayerTotals.Skater = skaterTotals(),
        seasons: List<PlayerSeasonLine> = listOf(currentSkaterSeason),
        lastFive: List<PlayerRecentGame> = listOf(skaterGame),
    ) = PlayerProfile(
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
        shootsCatches = "L",
        draft = PlayerDraft(year = 2017, teamAbbrev = "FLA", round = 2, pickInRound = 9, overallPick = 40),
        career = PlayerCareer(regularSeason = career),
        seasons = seasons,
        lastFive = lastFive,
    )

    private fun goalieProfile() =
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
            shootsCatches = "L",
            career =
                PlayerCareer(
                    regularSeason =
                        PlayerTotals.Goalie(
                            gamesPlayed = 142,
                            gamesStarted = 120,
                            wins = 65,
                            goalsAgainstAverage = 2.66693,
                            savePercentage = 0.90981,
                            timeOnIceSeconds = 463004L,
                        ),
                    playoffs = PlayerTotals.Goalie(gamesPlayed = 10),
                ),
            seasons =
                listOf(
                    PlayerSeasonLine(
                        season = CURRENT_SEASON,
                        gameTypeId = 2,
                        leagueAbbrev = "AHL",
                        teamName = "San Diego Gulls",
                        sequence = 85177,
                        totals = PlayerTotals.Goalie(gamesPlayed = 6),
                    ),
                    PlayerSeasonLine(
                        season = CURRENT_SEASON,
                        gameTypeId = 2,
                        leagueAbbrev = "NHL",
                        teamName = "Anaheim Ducks",
                        sequence = 1,
                        totals = PlayerTotals.Goalie(gamesPlayed = 1),
                    ),
                ),
        )

    private fun skaterTotals(
        gamesPlayed: Int? = 25,
        goals: Int? = 2,
        shorthandedGoals: Int? = 0,
    ) = PlayerTotals.Skater(
        gamesPlayed = gamesPlayed,
        goals = goals,
        assists = 4,
        points = 6,
        plusMinus = -1,
        shots = 28,
        shorthandedGoals = shorthandedGoals,
        shootingPctg = 0.0714,
        avgTimeOnIceSeconds = 672L,
    )

    private val currentSkaterSeason =
        PlayerSeasonLine(
            season = CURRENT_SEASON,
            gameTypeId = 2,
            leagueAbbrev = "SHL",
            teamName = "HV71",
            sequence = 11,
            totals = skaterTotals(gamesPlayed = 13, goals = 0),
        )

    private val skaterGame =
        PlayerRecentGame.Skater(
            gameDate = "2026-11-01",
            opponentAbbrev = "OTT",
            isHome = true,
            goals = 1,
            assists = 2,
            points = 3,
            plusMinus = 1,
            shots = 4,
            timeOnIceSeconds = 754L,
        )
}
