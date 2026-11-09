package net.montycasa.roux.data.nhl

import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.RankedStat
import net.montycasa.roux.domain.model.SeasonRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pre-game payloads for COL at TOR on 2026-11-10, read as the page reads them.
 *
 * The three fixtures are the only pre-game payloads these tests run against. Each assertion states a value the feed
 * sent, so a mapping that reads the wrong field, reverses a list or pairs a club with the other club's rank fails here.
 */
class PreGameAdapterTest {
    @Test
    fun landingCarriesEachClubsRecordAndTeamId() {
        val landing = landing()

        assertEquals(SeasonRecord(wins = 10, losses = 6, overtimeLosses = 2), landing.awayRecord)
        assertEquals(SeasonRecord(wins = 8, losses = 9, overtimeLosses = 2), landing.homeRecord)
        assertEquals(21L, landing.awayTeamId)
        assertEquals(10L, landing.homeTeamId)
    }

    @Test
    fun theFutureGameIsScheduled() {
        assertEquals(GameState.Scheduled, landing().state)
    }

    @Test
    fun theGoalieListIsFlatAndKeepsFeedOrderWithEachGoaliesOwnClub() {
        val goalies = landing().goalies

        assertEquals(listOf("M. Blackwood", "S. Wedgewood", "A. Stolarz", "A. Akhtyamov", "S. Bobrovsky"), goalies.map { it.name })
        assertEquals(listOf(21L, 21L, 10L, 10L, 10L), goalies.map { it.teamId })
    }

    @Test
    fun aGoalieWithSeasonStatsKeepsEveryNumberTheFeedSent() {
        val blackwood = landing().goalies.first()

        assertEquals(
            GoalieSeason(
                teamId = 21L,
                name = "M. Blackwood",
                sweaterNumber = 39,
                gamesPlayed = 14,
                record = SeasonRecord(wins = 9, losses = 3, overtimeLosses = 1),
                goalsAgainstAverage = 2.59842,
                savePercentage = 0.90085,
            ),
            blackwood,
        )
    }

    @Test
    fun aGoalieWithNoSeasonIsKeptWithNameAndSweaterOnly() {
        val akhtyamov = landing().goalies.first { it.name == "A. Akhtyamov" }

        assertEquals(GoalieSeason(teamId = 10L, name = "A. Akhtyamov", sweaterNumber = 70), akhtyamov)
    }

    @Test
    fun aRecordWithoutTheOvertimeCountIsLeftOffRatherThanGuessed() {
        val raw = fixture(LANDING).replace("\"record\":\"8-9-2\"", "\"record\":\"8-9\"")

        assertNull(parseLanding(raw).homeRecord)
    }

    @Test
    fun last10KeepsTheFeedsResultOrderAndStreak() {
        val away = rail().awayForm

        assertEquals(
            ClubForm(
                record = SeasonRecord(wins = 5, losses = 4, overtimeLosses = 1),
                results = listOf("W", "OTL", "L", "W", "W", "W", "L", "W", "L", "L"),
                streakType = "W",
                streak = 1,
            ),
            away,
        )
    }

    @Test
    fun last10ForTheHomeClubReadsItsOwnResultsAndStreak() {
        val home = rail().homeForm

        assertEquals(listOf("L", "L", "L", "W", "L", "L", "W", "W", "W", "W"), home?.results)
        assertEquals("L", home?.streakType)
        assertEquals(3, home?.streak)
    }

    @Test
    fun seasonStatsPairEachValueWithThatClubsOwnRank() {
        val rail = rail()

        assertEquals(RankedStat(0.233333, 11), rail.awaySeasonStats?.powerPlay)
        assertEquals(RankedStat(0.781818, 14), rail.awaySeasonStats?.penaltyKill)
        assertEquals(RankedStat(0.490637, 21), rail.awaySeasonStats?.faceoff)
        assertEquals(RankedStat(4.0, 3), rail.awaySeasonStats?.goalsFor)
        assertEquals(RankedStat(3.06, 13), rail.awaySeasonStats?.goalsAgainst)

        assertEquals(RankedStat(0.285714, 5), rail.homeSeasonStats?.powerPlay)
        assertEquals(RankedStat(0.859375, 4), rail.homeSeasonStats?.penaltyKill)
        assertEquals(RankedStat(0.517274, 9), rail.homeSeasonStats?.faceoff)
        assertEquals(RankedStat(3.37, 12), rail.homeSeasonStats?.goalsFor)
        assertEquals(RankedStat(3.32, 20), rail.homeSeasonStats?.goalsAgainst)
    }

    @Test
    fun aStatWithoutItsRankIsNotKept() {
        val raw = fixture(RAIL).replace("\"ppPctgRank\":11,", "")

        assertNull(parseRail(raw).awaySeasonStats?.powerPlay)
        assertEquals(RankedStat(0.285714, 5), parseRail(raw).homeSeasonStats?.powerPlay)
    }

    @Test
    fun seasonSeriesKeepsTheFinishedMeetingWithItsDateAndScores() {
        val finished = rail().seasonSeries.first()

        assertEquals(2026020080L, finished.gameId)
        assertEquals(2, finished.gameType)
        assertEquals(GameState.Final, finished.state)
        assertEquals("TOR", finished.awayAbbrev)
        assertEquals(6, finished.awayScore)
        assertEquals("COL", finished.homeAbbrev)
        assertEquals(3, finished.homeScore)
        assertEquals("2026-10-10", finished.gameDate)
    }

    @Test
    fun theUnplayedGameIsListedWithoutScores() {
        val upcoming = rail().seasonSeries.last()

        assertEquals(2026020286L, upcoming.gameId)
        assertEquals(GameState.Scheduled, upcoming.state)
        assertNull(upcoming.awayScore)
        assertNull(upcoming.homeScore)
    }

    @Test
    fun thePreGameRailCarriesNoShotsOrPowerPlay() {
        val rail = rail()

        assertNull(rail.shots)
        assertNull(rail.powerPlay)
        assertEquals(emptyList<String>(), rail.teamStats.map { it.label })
    }

    private fun landing(): GameLanding = parseLanding(fixture(LANDING))

    private fun rail(): GameRightRail = parseRail(fixture(RAIL))

    private fun parseLanding(json: String): GameLanding {
        val parsed = LandingAdapter.parse(json)
        check(parsed is ParseResult.Success) { "landing fixture did not parse: $parsed" }
        return parsed.value
    }

    private fun parseRail(json: String): GameRightRail {
        val parsed = RightRailAdapter.parse(json)
        check(parsed is ParseResult.Success) { "right rail fixture did not parse: $parsed" }
        return parsed.value
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fx/2026-11-09/$name")) { "missing fixture $name" }
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val LANDING = "landing-2026020286-pregame.json"
        const val RAIL = "right-rail-2026020286-pregame.json"
    }
}
