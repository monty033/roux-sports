package net.montycasa.roux.ui.team

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
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
import net.montycasa.roux.domain.model.ClubSchedule
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
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
    fun theElevenRowsShowAnasValuesAndRanksInTheTabsOrder() {
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
        showTab(league = Loadable(LeagueSummary(season = "20262027", clubs = rivals), Freshness.Fresh, null))

        rule.onNodeWithText("There are no league stats for this club yet.").assertIsDisplayed()
        rule.onNodeWithTag("team.stats.table", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * A table that fell back to last season names that season above the table, so its numbers are not read as this
     * season's. The note sits above the table rather than beside it.
     */
    @Test
    fun aFallbackSeasonNamesItselfAboveTheTable() {
        showTab(league = Loadable(leagueWith(ana).copy(season = "20252026"), Freshness.Fresh, null))

        rule.onNodeWithTag("team.stats.season").assertIsDisplayed().assertTextEquals("2025-26 season")
        val note = rule.onNodeWithTag("team.stats.season").fetchSemanticsNode().boundsInRoot
        val table = rule.onNodeWithTag("team.stats.table", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(note.bottom <= table.top)
    }

    /** The current season's table carries no label, so nothing about this season's numbers changes. */
    @Test
    fun theCurrentSeasonsTableCarriesNoSeasonLabel() {
        showTab()

        rule.onNodeWithTag("team.stats.season").assertDoesNotExist()
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

    /** Fewest penalty minutes is first: the metric counts in the lower-is-better direction. */
    @Test
    fun theFewestPenaltyMinutesRanksFirst() {
        showTab(league = Loadable(leagueWith(ana.copy(penaltyMinutes = 1)), Freshness.Fresh, null))

        rule
            .onNodeWithTag("team.stats.rank.PenaltyMinutes", useUnmergedTree = true)
            .assertTextEquals("1st")
    }

    /** The most penalty minutes is last, so the lower-is-better direction is not inverted for the whole league. */
    @Test
    fun theMostPenaltyMinutesRanksLast() {
        showTab(league = Loadable(leagueWith(ana.copy(penaltyMinutes = 999)), Freshness.Fresh, null))

        rule
            .onNodeWithTag("team.stats.rank.PenaltyMinutes", useUnmergedTree = true)
            .assertTextEquals("32nd")
    }

    /**
     * Each bar is the club's share of the league: rank 1 fills its track and the last place fills 1 / clubs. The
     * tolerance is one dp, measured against the track the bar sits in.
     */
    @Test
    fun eachRankBarFillsItsShareOfTheTrack() {
        showTab()

        expectedRows.forEach { row ->
            val rank = row.rank.takeWhile { it.isDigit() }.toInt()
            val expected = (CLUB_COUNT - rank + 1).toFloat() / CLUB_COUNT
            val track = barNode(row.metric, "team.stats.bar.")
            val fill = barNode(row.metric, "team.stats.barFill.")
            val ratio = fill.width / track.width
            assertTrue(
                "${row.label} filled $ratio of its track, expected $expected",
                abs(ratio - expected) <= TOLERANCE_DP * DENSITY / track.width,
            )
        }
    }

    /** The last place fills exactly 1 / clubs of the track, which is the floor a bar can reach. */
    @Test
    fun theLastPlaceBarFillsOneClubsShareOfTheTrack() {
        showTab(league = Loadable(leagueWith(ana.copy(penaltyMinutes = 999)), Freshness.Fresh, null))

        val track = barNode(LeagueMetric.PenaltyMinutes, "team.stats.bar.")
        val fill = barNode(LeagueMetric.PenaltyMinutes, "team.stats.barFill.")
        assertTrue(
            "last place filled ${fill.width / track.width}, expected ${1f / CLUB_COUNT}",
            abs(fill.width / track.width - 1f / CLUB_COUNT) <= TOLERANCE_DP * DENSITY / track.width,
        )
    }

    /** The bar is decoration: the rank cell already says the rank, so the bar must not add a second spoken copy. */
    @Test
    fun theBarHasNoContentDescription() {
        showTab()

        listOf("team.stats.bar.GoalsForPerGame", "team.stats.barFill.GoalsForPerGame").forEach { tag ->
            val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertFalse("$tag has a content description", node.config.contains(SemanticsProperties.ContentDescription))
        }
    }

    /** A stat with no rank has no share to draw, so its row has no bar; every other row keeps its bar. */
    @Test
    fun aStatWithNoRankDrawsNoBarAndTheOtherRowsStillDo() {
        showTab(league = Loadable(leagueWith(ana.copy(penaltyMinutes = null)), Freshness.Fresh, null))

        rule.onAllNodesWithTag("team.stats.bar.PenaltyMinutes", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("team.stats.barFill.PenaltyMinutes", useUnmergedTree = true).assertCountEquals(0)
        expectedRows.filter { it.metric != LeagueMetric.PenaltyMinutes }.forEach { row ->
            rule.onNodeWithTag("team.stats.bar.${row.metric.name}", useUnmergedTree = true).assertExists()
        }
    }

    /**
     * With the penalties table down, every club's penalty minutes are null. The penalty row shows a dash and no bar,
     * and every other row still renders its value and rank.
     */
    @Test
    fun withPenaltiesFailingTheOtherRanksStillRender() {
        val withoutPenalties =
            LeagueSummary(
                season = "20262027",
                clubs = listOf(ana.copy(penaltyMinutes = null)) + rivals.map { it.copy(penaltyMinutes = null) },
            )
        showTab(league = Loadable(withoutPenalties, Freshness.Fresh, null))

        rule
            .onNodeWithTag("team.stats.rank.PenaltyMinutes", useUnmergedTree = true)
            .assertTextEquals("—")
        expectedRows.filter { it.metric != LeagueMetric.PenaltyMinutes }.forEach { row ->
            rule
                .onNodeWithTag("team.stats.rank.${row.metric.name}", useUnmergedTree = true)
                .assertTextEquals(row.rank)
        }
    }

    private fun barNode(
        metric: LeagueMetric,
        prefix: String,
    ) = rule.onNodeWithTag("$prefix${metric.name}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

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
                            schedule = Loadable(ClubSchedule(season = "20262027", games = emptyList()), Freshness.Fresh, null),
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
    private fun leagueWith(club: LeagueClub) = LeagueSummary(season = "20262027", clubs = listOf(club) + rivals)

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
            teamId = 24,
            penaltyMinutes = 205,
        )

    /**
     * Written out here rather than read from [LeagueMetric.higherIsBetter]: if the app flipped a direction, a fixture
     * that followed it would hide the flip from this screen, so the screen pins the direction on its own.
     */
    private val lowerIsBetter =
        setOf(LeagueMetric.GoalsAgainstPerGame, LeagueMetric.ShotsAgainstPerGame, LeagueMetric.PenaltyMinutes)

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
            LeagueMetric.PenaltyMinutes to 3,
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
            penaltyMinutes = better(LeagueMetric.PenaltyMinutes, ana.penaltyMinutes!!.toDouble(), 1.0).toInt(),
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
            ExpectedRow(LeagueMetric.PenaltyMinutes, "Penalty minutes", "205", "4th"),
            ExpectedRow(LeagueMetric.Points, "Points", "13", "4th"),
            ExpectedRow(LeagueMetric.PointPct, "Point %", "46.4%", "1st"),
            ExpectedRow(LeagueMetric.TeamShutouts, "Shutouts", "0", "2nd"),
        )

    private companion object {
        const val CLUB_COUNT = 32

        /** The Robolectric qualifier above is 420 dpi, which is 2.625 pixels per dp. */
        const val DENSITY = 420f / 160f
        const val TOLERANCE_DP = 1f
    }
}
