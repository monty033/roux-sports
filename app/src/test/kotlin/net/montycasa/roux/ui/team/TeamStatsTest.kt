package net.montycasa.roux.ui.team

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeagueSummary
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.LeagueMetric
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * The Team Stats tab, rendered for Anaheim against a 32-club league built so every rank is known in advance.
 *
 * Anaheim's values are the captured ones. Each metric has a fixed number of rivals that beat Anaheim on it, one
 * step better in the metric's direction; every other rival matches Anaheim exactly, and a tie does not count. So
 * Anaheim's place on each metric is one plus that count, and the expected ranks can be checked by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
class TeamStatsTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-03T12:00:00Z")

    @Test
    fun theTenRowsShowAnasValuesAndRanksInTheBriefsOrder() {
        showTab()

        rule.onNodeWithTag("team.stats.header", useUnmergedTree = true).assertTextEquals("14 GP · 6-7-1 · 13 pts")
        val labels =
            rule
                .onAllNodesWithTag("team.stats.label", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }
        assertEquals(expectedRows.map { it.label }, labels)
        expectedRows.forEach { row ->
            rule
                .onNodeWithTag("team.stats.value.${row.metric.name}", useUnmergedTree = true)
                .assertTextEquals(row.value)
            rule
                .onNodeWithTag("team.stats.rank.${row.metric.name}", useUnmergedTree = true)
                .assertTextEquals(row.rank)
        }
    }

    /** A null value prints as the em dash and earns no rank; a zero fallback would read as a real power-play rate. */
    @Test
    fun aMissingValueShowsADashAndNoRank() {
        showTab(league = Loadable(leagueWith(ana.copy(powerPlayPct = null)), Freshness.Fresh, null))

        rule
            .onNodeWithTag("team.stats.value.PowerPlayPct", useUnmergedTree = true)
            .assertTextEquals("—")
        rule
            .onNodeWithTag("team.stats.rank.PowerPlayPct", useUnmergedTree = true)
            .assertTextEquals("—")
        assertEquals(
            listOf("Power play %, no rank"),
            rule
                .onNodeWithTag("team.stats.rank.PowerPlayPct", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription],
        )
    }

    /**
     * The per-game branch carries its own dash, separate from the percentage one. Without this case a null
     * goals-against prints `0.00`, which reads as a club that has conceded nothing.
     */
    @Test
    fun aMissingPerGameValueAlsoShowsTheDash() {
        showTab(league = Loadable(leagueWith(ana.copy(goalsAgainstPerGame = null)), Freshness.Fresh, null))

        rule
            .onNodeWithTag("team.stats.value.GoalsAgainstPerGame", useUnmergedTree = true)
            .assertTextEquals("—")
        rule
            .onNodeWithTag("team.stats.rank.GoalsAgainstPerGame", useUnmergedTree = true)
            .assertTextEquals("—")
    }

    /** TalkBack must say which metric, its value, and the rank out of the whole league, not a bare "2nd". */
    @Test
    fun theRankCellSpeaksTheMetricValueAndRankOutOfTheLeague() {
        showTab()

        assertEquals(
            listOf("Goals against per game, 3.79, 2nd of 32"),
            rule
                .onNodeWithTag("team.stats.rank.GoalsAgainstPerGame", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription],
        )
    }

    @Test
    fun aClubMissingFromTheLeagueShowsTheAbsentLineAndNoTable() {
        showTab(league = Loadable(LeagueSummary(rivals), Freshness.Fresh, null))

        rule.onNodeWithText("There are no league stats for this club yet.").assertIsDisplayed()
        rule.onNodeWithTag("team.stats.table", useUnmergedTree = true).assertDoesNotExist()
    }

    /** A refresh that failed keeps the last table on screen, and the failure's retry calls back once. */
    @Test
    fun aStaleTableKeepsTheRowsAndRetryCallsBack() {
        var retries = 0
        showTab(
            league =
                Loadable(
                    leagueWith(ana),
                    Freshness.Stale(Instant.parse("2026-11-02T12:00:00Z")),
                    LoadError.Http(500),
                ),
            onRetry = { retries++ },
        )

        rule.onNodeWithText("The league table could not be refreshed. Showing the last one.").assertIsDisplayed()
        rule.onNodeWithTag("team.stats.table", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("team.stats.retry").performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }

    /** With no table at all, a failure is said plainly and its retry is the only way forward. */
    @Test
    fun aFailedLoadWithNoTableSaysSoAndRetryCallsBack() {
        var retries = 0
        showTab(
            league = Loadable(null, Freshness.Missing, LoadError.Network),
            onRetry = { retries++ },
        )

        rule.onNodeWithText("League stats could not be loaded.").assertIsDisplayed()
        rule.onNodeWithTag("team.stats.table", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("team.stats.retry").performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }

    private fun showTab(
        league: Loadable<LeagueSummary>? = Loadable(leagueWith(ana), Freshness.Fresh, null),
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                TeamDetailScreen(
                    state =
                        TeamDetailUiState(
                            teamKey = TeamKey("ANA"),
                            row = anaRow,
                            isLoading = false,
                            schedule = Loadable(emptyList(), Freshness.Fresh, null),
                            leagueStats = league,
                        ),
                    onBack = {},
                    onRetry = {},
                    onLeagueStatsRetry = onRetry,
                    now = now,
                )
            }
        }
        rule.onNodeWithTag("team.tab.TeamStats").performClick()
        rule.waitForIdle()
    }

    /** The 32-club league with [club] as Anaheim's line. */
    private fun leagueWith(club: LeagueClub) = LeagueSummary(listOf(club) + rivals)

    private val anaRow =
        StandingRow(
            team = TeamRef(TeamKey("ANA"), name = "Anaheim Ducks", shortName = "Ducks"),
            gp = 14,
            w = 6,
            l = 7,
            otl = 1,
            pts = 13,
            leagueSequence = 20,
            conferenceSequence = 8,
            divisionSequence = 3,
            wildcardSequence = 0,
            conference = Conference.Western,
            division = Division.Pacific,
        )

    /** The captured Anaheim line: the same values the adapter test reads from the 32-club capture. */
    private val ana =
        LeagueClub(
            teamName = "Anaheim Ducks",
            gamesPlayed = 14,
            wins = 6,
            losses = 7,
            otLosses = 1,
            ties = null,
            points = 13,
            pointPct = 0.46428,
            goalsForPerGame = 3.5,
            goalsAgainstPerGame = 3.78571,
            shotsForPerGame = 25.0,
            shotsAgainstPerGame = 27.57142,
            faceoffWinPct = 0.46979,
            powerPlayPct = 0.340425,
            penaltyKillPct = 0.763637,
            teamShutouts = 0,
        )

    /**
     * Written out here rather than read from [LeagueMetric.higherIsBetter]: if the app flipped a direction, a fixture
     * that followed it would hide the flip from this screen, so the screen pins the direction on its own.
     */
    private val lowerIsBetter = setOf(LeagueMetric.GoalsAgainstPerGame, LeagueMetric.ShotsAgainstPerGame)

    /** How many rivals beat Anaheim on each metric. Anaheim's rank is one plus this count. */
    private val beaters =
        mapOf(
            LeagueMetric.GoalsForPerGame to 0,
            LeagueMetric.GoalsAgainstPerGame to 1,
            LeagueMetric.ShotsForPerGame to 2,
            LeagueMetric.ShotsAgainstPerGame to 3,
            LeagueMetric.FaceoffWinPct to 0,
            LeagueMetric.PowerPlayPct to 1,
            LeagueMetric.PenaltyKillPct to 2,
            LeagueMetric.Points to 3,
            LeagueMetric.PointPct to 0,
            LeagueMetric.TeamShutouts to 1,
        )

    private val rivals: List<LeagueClub> = (0 until 31).map { rival(it) }

    private fun rival(index: Int): LeagueClub {
        fun better(
            metric: LeagueMetric,
            value: Double,
            step: Double,
        ): Double =
            when {
                index >= beaters.getValue(metric) -> value
                metric in lowerIsBetter -> value - step
                else -> value + step
            }
        return LeagueClub(
            teamName = "Rival $index",
            goalsForPerGame = better(LeagueMetric.GoalsForPerGame, ana.goalsForPerGame!!, 1.0),
            goalsAgainstPerGame = better(LeagueMetric.GoalsAgainstPerGame, ana.goalsAgainstPerGame!!, 1.0),
            shotsForPerGame = better(LeagueMetric.ShotsForPerGame, ana.shotsForPerGame!!, 1.0),
            shotsAgainstPerGame = better(LeagueMetric.ShotsAgainstPerGame, ana.shotsAgainstPerGame!!, 1.0),
            faceoffWinPct = better(LeagueMetric.FaceoffWinPct, ana.faceoffWinPct!!, 0.01),
            powerPlayPct = better(LeagueMetric.PowerPlayPct, ana.powerPlayPct!!, 0.01),
            penaltyKillPct = better(LeagueMetric.PenaltyKillPct, ana.penaltyKillPct!!, 0.01),
            points = better(LeagueMetric.Points, ana.points!!.toDouble(), 1.0).toInt(),
            pointPct = better(LeagueMetric.PointPct, ana.pointPct!!, 0.01),
            teamShutouts = better(LeagueMetric.TeamShutouts, ana.teamShutouts!!.toDouble(), 1.0).toInt(),
        )
    }

    private data class ExpectedRow(
        val metric: LeagueMetric,
        val label: String,
        val value: String,
        val rank: String,
    )

    private val expectedRows =
        listOf(
            ExpectedRow(LeagueMetric.GoalsForPerGame, "Goals for per game", "3.50", "1st"),
            ExpectedRow(LeagueMetric.GoalsAgainstPerGame, "Goals against per game", "3.79", "2nd"),
            ExpectedRow(LeagueMetric.ShotsForPerGame, "Shots for per game", "25.00", "3rd"),
            ExpectedRow(LeagueMetric.ShotsAgainstPerGame, "Shots against per game", "27.57", "4th"),
            ExpectedRow(LeagueMetric.FaceoffWinPct, "Faceoff win %", "47.0%", "1st"),
            ExpectedRow(LeagueMetric.PowerPlayPct, "Power play %", "34.0%", "2nd"),
            ExpectedRow(LeagueMetric.PenaltyKillPct, "Penalty kill %", "76.4%", "3rd"),
            ExpectedRow(LeagueMetric.Points, "Points", "13", "4th"),
            ExpectedRow(LeagueMetric.PointPct, "Point %", "46.4%", "1st"),
            ExpectedRow(LeagueMetric.TeamShutouts, "Shutouts", "0", "2nd"),
        )
}
