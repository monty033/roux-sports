package net.montycasa.roux.ui.team

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.GoalieStat
import net.montycasa.roux.domain.model.SkaterStat
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
class TeamStatsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun statsIsSelectableAndShowsBothTablesWithHeaders() {
        showScreen()

        rule.onNodeWithTag("team.tab.Stats").assertIsNotSelected().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("team.tab.Stats").assertIsSelected()
        rule.onNodeWithTag("team.stats.list").assertExists()
        rule.onNodeWithTag("team.stats.group.skaters").assertExists()
        rule.onNodeWithTag("team.stats.group.goalies").assertExists()

        listOf("GP", "G", "A", "PTS", "+/-", "PIM").forEach { header ->
            rule.onNodeWithTag("team.stats.group.skaters.header.$header").assertExists()
        }
        listOf("GP", "W", "L", "OTL", "GAA", "SV%", "SO").forEach { header ->
            rule.onNodeWithTag("team.stats.group.goalies.header.$header").assertExists()
        }
        rule.onNodeWithText("Skaters").assertExists()
        rule.onNodeWithText("Goalies").assertExists()
    }

    @Test
    fun theRowsCarryTheFormattedNumbers() {
        showScreen()
        rule.onNodeWithTag("team.tab.Stats").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("team.stats.player.1.name", useUnmergedTree = true).assertTextEquals("Leo Carlsson")
        rule.onNodeWithTag("team.stats.player.1.position", useUnmergedTree = true).assertTextEquals("C")
        rule.onNodeWithTag("team.stats.player.1.pts", useUnmergedTree = true).assertTextEquals("21")
        rule.onNodeWithTag("team.stats.player.1.pim", useUnmergedTree = true).assertTextEquals("8")

        rule.onNodeWithTag("team.stats.player.31.name", useUnmergedTree = true).assertTextEquals("Lukas Dostal")
        rule.onNodeWithTag("team.stats.player.31.gaa", useUnmergedTree = true).assertTextEquals("3.56")
        rule.onNodeWithTag("team.stats.player.31.sv%", useUnmergedTree = true).assertTextEquals(".872")
    }

    @Test
    fun aStatsFailureKeepsTheHeaderAndTabRowAndOffersRetry() {
        var retries = 0
        showScreen(
            stats = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
            onStatsRetry = { retries++ },
        )
        rule.onNodeWithTag("team.tab.Stats").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("team.name").assertIsDisplayed()
        rule.onNodeWithTag("team.tab.Schedule").assertIsDisplayed()
        rule.onNodeWithTag("team.tab.Stats").assertIsSelected()
        rule.onNodeWithTag("team.stats.error").assertIsDisplayed()
        rule.onNodeWithText("Stats could not be loaded.").assertIsDisplayed()
        rule.onNodeWithTag("team.stats.retry").assertExists()

        rule.onNodeWithTag("team.stats.retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun switchingToStatsDoesNotRefetchTheRosterOrSchedule() {
        var rosterRetries = 0
        var scheduleRetries = 0
        showScreen(
            onRosterRetry = { rosterRetries++ },
            onRetry = { scheduleRetries++ },
        )

        rule.onNodeWithTag("team.tab.Stats").performClick()
        rule.waitForIdle()

        assertEquals(0, rosterRetries)
        assertEquals(0, scheduleRetries)
    }

    @Test
    fun theThreeNumberTrapsAreFormattedForPeople() {
        // The feed's ice time is seconds, not minutes: 958.7857 is 15:58 a game.
        assertEquals("15:58", formatStatsIceTime(958.7857))
        // Shooting is a fraction: 0.1 means ten percent.
        assertEquals("10.0%", formatShootingPercentage(0.1))
        // Save percentage is a fraction printed the way hockey prints it.
        assertEquals(".870", formatSavePercentage(0.869565))
        assertEquals("3.00", formatGoalsAgainstAverage(3.0))
    }

    @Test
    fun plusMinusKeepsItsSignButZeroIsNotPlusZero() {
        assertEquals("+5", formatPlusMinus(5))
        assertEquals("-3", formatPlusMinus(-3))
        assertEquals("0", formatPlusMinus(0))
    }

    private fun showScreen(
        stats: Loadable<ClubStats> = Loadable(sampleStats, Freshness.Fresh, null),
        statsLoading: Boolean = false,
        onStatsRetry: () -> Unit = {},
        onRosterRetry: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                TeamDetailScreen(
                    state =
                        TeamDetailUiState(
                            teamKey = TeamKey("TOR"),
                            row = toronto,
                            isLoading = false,
                            roster =
                                Loadable(
                                    net.montycasa.roux.domain.model
                                        .ClubRoster(),
                                    Freshness.Fresh,
                                    null,
                                ),
                            stats = stats,
                            isStatsLoading = statsLoading,
                        ),
                    onBack = {},
                    onRetry = onRetry,
                    onRosterRetry = onRosterRetry,
                    onStatsRetry = onStatsRetry,
                )
            }
        }
    }

    private val sampleStats =
        ClubStats(
            skaters =
                listOf(
                    SkaterStat(
                        id = 1,
                        name = "Leo Carlsson",
                        positionCode = "C",
                        gamesPlayed = 14,
                        goals = 4,
                        assists = 17,
                        points = 21,
                        plusMinus = 8,
                        penaltyMinutes = 8,
                        avgTimeOnIcePerGame = 958.7857,
                    ),
                    SkaterStat(
                        id = 2,
                        name = "Sam Colangelo",
                        positionCode = "R",
                        gamesPlayed = 20,
                        goals = 2,
                        assists = 8,
                        points = 10,
                        plusMinus = -3,
                        penaltyMinutes = 4,
                    ),
                ),
            goalies =
                listOf(
                    GoalieStat(
                        id = 31,
                        name = "Lukas Dostal",
                        gamesPlayed = 9,
                        wins = 4,
                        losses = 4,
                        overtimeLosses = 1,
                        goalsAgainstAverage = 3.555612,
                        savePercentage = 0.871901,
                        shutouts = 0,
                    ),
                    GoalieStat(
                        id = 35,
                        name = "Ville Husso",
                        gamesPlayed = 5,
                        wins = 1,
                        losses = 3,
                        overtimeLosses = 0,
                        goalsAgainstAverage = 4.12038,
                        savePercentage = 0.857143,
                        shutouts = 0,
                    ),
                ),
        )

    private val toronto =
        StandingRow(
            team = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs"),
            gp = 12,
            w = 9,
            l = 2,
            otl = 1,
            pts = 19,
            leagueSequence = 7,
            conferenceSequence = 3,
            divisionSequence = 2,
            wildcardSequence = 0,
            conference = Conference.Eastern,
            division = Division.Atlantic,
        )
}
