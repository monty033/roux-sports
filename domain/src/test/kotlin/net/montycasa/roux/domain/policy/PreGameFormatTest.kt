package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.ClubSeasonStats
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.RankedStat
import net.montycasa.roux.domain.model.SeasonRecord
import net.montycasa.roux.domain.model.SkaterSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pre-game card's wording and number formats, pinned to the 2026-11-10 COL at TOR fixtures.
 *
 * The expected strings are the ones a fan should read, worked out from the feed's raw values by hand, so a formatter
 * that rounds the wrong way or drops a teen suffix fails here rather than on a device.
 */
class PreGameFormatTest {
    @Test
    fun ordinalsFollowEnglishIncludingTheTeens() {
        assertEquals("1st", PreGameFormat.ordinal(1))
        assertEquals("2nd", PreGameFormat.ordinal(2))
        assertEquals("3rd", PreGameFormat.ordinal(3))
        assertEquals("4th", PreGameFormat.ordinal(4))
        assertEquals("11th", PreGameFormat.ordinal(11))
        assertEquals("12th", PreGameFormat.ordinal(12))
        assertEquals("13th", PreGameFormat.ordinal(13))
        assertEquals("21st", PreGameFormat.ordinal(21))
        assertEquals("22nd", PreGameFormat.ordinal(22))
        assertEquals("111th", PreGameFormat.ordinal(111))
    }

    @Test
    fun aRankOfZeroOrLessPrintsADashNotAZeroth() {
        assertEquals("–", PreGameFormat.ordinal(0))
        assertEquals("–", PreGameFormat.ordinal(-1))
    }

    @Test
    fun fractionsPrintAsPercentagesWithOneDecimal() {
        assertEquals("23.3%", PreGameFormat.percent(0.233333))
        assertEquals("78.2%", PreGameFormat.percent(0.781818))
        assertEquals("85.9%", PreGameFormat.percent(0.859375))
        assertEquals("49.1%", PreGameFormat.percent(0.490637))
        assertEquals("0.0%", PreGameFormat.percent(0.0))
        assertEquals("100.0%", PreGameFormat.percent(1.0))
    }

    @Test
    fun perGameValuesPrintWithTwoDecimals() {
        assertEquals("4.00", PreGameFormat.perGame(4.0))
        assertEquals("3.06", PreGameFormat.perGame(3.06))
        assertEquals("3.37", PreGameFormat.perGame(3.37))
        assertEquals("2.60", PreGameFormat.perGame(2.59842))
    }

    @Test
    fun recordsPrintWithEnDashes() {
        assertEquals("10–6–2", PreGameFormat.recordText(SeasonRecord(10, 6, 2)))
        assertEquals("COL 10–6–2", PreGameFormat.clubRecordText("COL", SeasonRecord(10, 6, 2)))
    }

    @Test
    fun streakIsTypeAndCountOrNothing() {
        assertEquals("W1", PreGameFormat.streakText(ClubForm(streakType = "W", streak = 1)))
        assertEquals("L3", PreGameFormat.streakText(ClubForm(streakType = "L", streak = 3)))
        assertNull(PreGameFormat.streakText(ClubForm(streakType = "L", streak = null)))
        assertNull(PreGameFormat.streakText(ClubForm(streakType = null, streak = 2)))
    }

    @Test
    fun lastTenPrintsTheRecordThenTheFeedsStreakInParentheses() {
        assertEquals("5–4–1 (L1)", PreGameFormat.lastTenText(ClubForm(SeasonRecord(5, 4, 1), streakType = "L", streak = 1)))
        assertEquals("6–3–1 (W2)", PreGameFormat.lastTenText(ClubForm(SeasonRecord(6, 3, 1), streakType = "W", streak = 2)))
    }

    @Test
    fun anOvertimeOrShootoutStreakPrintsWithItsOwnCode() {
        assertEquals("6–3–1 (OTL1)", PreGameFormat.lastTenText(ClubForm(SeasonRecord(6, 3, 1), streakType = "OTL", streak = 1)))
        assertEquals("6–3–1 (SOL1)", PreGameFormat.lastTenText(ClubForm(SeasonRecord(6, 3, 1), streakType = "SOL", streak = 1)))
    }

    @Test
    fun aClubWithNoStreakPrintsItsRecordAloneWithNoEmptyParentheses() {
        assertEquals("6–3–1", PreGameFormat.lastTenText(ClubForm(SeasonRecord(6, 3, 1), streakType = null, streak = null)))
        assertEquals("6–3–1", PreGameFormat.lastTenText(ClubForm(SeasonRecord(6, 3, 1), streakType = "W", streak = null)))
    }

    @Test
    fun aClubWithNoRecordPrintsNothingForLastTen() {
        assertNull(PreGameFormat.lastTenText(ClubForm(record = null, streakType = "W", streak = 2)))
    }

    @Test
    fun theStreakIsTheFeedsCodeNotWorkedOutFromTheResults() {
        // Neither end of the list is an L, so a streak read from the results could not print L1.
        val form =
            ClubForm(
                record = SeasonRecord(6, 3, 1),
                results = listOf("W", "OTL", "W", "W", "W", "W", "W", "W", "OTL", "W"),
                streakType = "L",
                streak = 1,
            )

        assertEquals("6–3–1 (L1)", PreGameFormat.lastTenText(form))
    }

    @Test
    fun isoDatesPrintAsShortMonthAndDayWithoutLeadingZero() {
        assertEquals("Oct 10", PreGameFormat.shortDate("2026-10-10"))
        assertEquals("Nov 5", PreGameFormat.shortDate("2026-11-05"))
        assertNull(PreGameFormat.shortDate("2026-13-01"))
        assertNull(PreGameFormat.shortDate("not a date"))
    }

    @Test
    fun seasonStatRowsPairEachClubsValueWithItsOwnRank() {
        val rows = PreGameFormat.seasonStatRows(COL_STATS, TOR_STATS)

        assertEquals(5, rows.size)
        val powerPlay = rows.first { it.label == "Power play" }
        assertEquals("23.3%", powerPlay.awayValue)
        assertEquals("11th", powerPlay.awayRank)
        assertEquals("28.6%", powerPlay.homeValue)
        assertEquals("5th", powerPlay.homeRank)

        val goalsAgainst = rows.first { it.label == "Goals against / game" }
        assertEquals("3.06", goalsAgainst.awayValue)
        assertEquals("13th", goalsAgainst.awayRank)
        assertEquals("3.32", goalsAgainst.homeValue)
        assertEquals("20th", goalsAgainst.homeRank)
    }

    @Test
    fun theBarSplitsOnLeagueRankNotOnTheRawValue() {
        // COL's power play is 23.3% and 11th, TOR's 28.6% and 5th. Raw values would split 0.233 : 0.286. Ranks give 22 : 28.
        val powerPlay = PreGameFormat.seasonStatRows(COL_STATS, TOR_STATS).first { it.label == "Power play" }

        assertEquals(22.0, powerPlay.awayBar, 0.0)
        assertEquals(28.0, powerPlay.homeBar, 0.0)
    }

    @Test
    fun goalsAgainstBarRunsOnRankWithNoInversion() {
        // COL allows 3.06 a game and is 13th; TOR allows 3.32 and is 20th. Rank 1 is best for every statistic, so COL's
        // better rank gives it the longer bar: 20 against 13. An inverted row would draw 13 against 20.
        val goalsAgainst = PreGameFormat.seasonStatRows(COL_STATS, TOR_STATS).first { it.label == "Goals against / game" }

        assertEquals(20.0, goalsAgainst.awayBar, 0.0)
        assertEquals(13.0, goalsAgainst.homeBar, 0.0)
        assertTrue(goalsAgainst.awayBar > goalsAgainst.homeBar)
    }

    @Test
    fun rankOneAgainstRankThirtyTwoGivesTheFullBarAndTheLastSliver() {
        // The values say the opposite of the ranks here, so the bars must follow the ranks alone.
        val away = COL_STATS.copy(powerPlay = RankedStat(0.1, 1))
        val home = TOR_STATS.copy(powerPlay = RankedStat(0.9, 32))

        val powerPlay = PreGameFormat.seasonStatRows(away, home).first { it.label == "Power play" }

        assertEquals(32.0, powerPlay.awayBar, 0.0)
        assertEquals(1.0, powerPlay.homeBar, 0.0)
    }

    @Test
    fun equalRanksDrawEqualBarsWhateverTheValues() {
        val away = COL_STATS.copy(powerPlay = RankedStat(0.25, 7))
        val home = TOR_STATS.copy(powerPlay = RankedStat(0.20, 7))

        val powerPlay = PreGameFormat.seasonStatRows(away, home).first { it.label == "Power play" }

        assertEquals(powerPlay.awayBar, powerPlay.homeBar, 0.0)
    }

    @Test
    fun barQualityIsThirtyTwoLessTheRankAndNeverNegative() {
        assertEquals(32, PreGameFormat.barQuality(1))
        assertEquals(17, PreGameFormat.barQuality(16))
        assertEquals(1, PreGameFormat.barQuality(32))
        assertEquals(0, PreGameFormat.barQuality(33))
        assertEquals(0, PreGameFormat.barQuality(40))
    }

    @Test
    fun aRowMissingOnEitherSideIsDroppedRatherThanHalfPrinted() {
        val awayWithoutPowerPlay = COL_STATS.copy(powerPlay = null)

        val rows = PreGameFormat.seasonStatRows(awayWithoutPowerPlay, TOR_STATS)

        assertEquals(4, rows.size)
        assertTrue(rows.none { it.label == "Power play" })
    }

    @Test
    fun noStatsForAClubMeansNoRows() {
        assertTrue(PreGameFormat.seasonStatRows(null, TOR_STATS).isEmpty())
        assertTrue(PreGameFormat.seasonStatRows(COL_STATS, null).isEmpty())
    }

    @Test
    fun goalieGamesLineReadsGamesThenRecord() {
        assertEquals("14 GP · 9–3–1", PreGameFormat.goalieGamesLine(BLACKWOOD))
    }

    @Test
    fun goalieRateLineReadsAverageThenSavePercentage() {
        assertEquals("2.60 GAA · 90.1%", PreGameFormat.goalieRateLine(BLACKWOOD))
    }

    @Test
    fun aGoalieWithoutARecordPrintsGamesAloneWithNoPlaceholder() {
        val noRecord = BLACKWOOD.copy(record = null)

        assertEquals("14 GP", PreGameFormat.goalieGamesLine(noRecord))
    }

    @Test
    fun aGoalieWithNoRateHasNoRateLine() {
        assertNull(PreGameFormat.goalieRateLine(BLACKWOOD.copy(goalsAgainstAverage = null, savePercentage = null)))
    }

    @Test
    fun aGoalieWithNoSeasonHasNoGamesLine() {
        assertNull(PreGameFormat.goalieGamesLine(AKHTYAMOV))
    }

    @Test
    fun goaliesAreSplitByTeamIdNotSweaterNumber() {
        // Sweater 41 is worn by Wedgewood for COL and by Stolarz for TOR. Splitting by number would hand each club both.
        val colGoalies = PreGameFormat.goaliesFor(COL_ID, ALL_GOALIES)
        val torGoalies = PreGameFormat.goaliesFor(TOR_ID, ALL_GOALIES)

        assertEquals(listOf("M. Blackwood", "S. Wedgewood"), colGoalies.map { it.name })
        assertEquals(listOf("S. Bobrovsky", "A. Stolarz"), torGoalies.map { it.name })
    }

    @Test
    fun goaliesSortMostGamesFirst() {
        val torGoalies = PreGameFormat.goaliesFor(TOR_ID, ALL_GOALIES)

        assertEquals(listOf(12, 7), torGoalies.map { it.gamesPlayed })
    }

    @Test
    fun aGoalieWithNoGamesPlayedIsNotListed() {
        // Akhtyamov has no season (the feed sends no games count) and the one here has played none: neither is on the card.
        val unplayed = goalie(TOR_ID, "T. Unplayed", sweater = 99, gamesPlayed = 0)

        val torGoalies = PreGameFormat.goaliesFor(TOR_ID, ALL_GOALIES + unplayed)

        assertEquals(listOf("S. Bobrovsky", "A. Stolarz"), torGoalies.map { it.name })
    }

    @Test
    fun aClubShowsAtMostThreeGoalies() {
        // Four goaltenders have played, so the one with the fewest games is the one cut.
        val fourForOneClub =
            ALL_GOALIES +
                goalie(TOR_ID, "T. Fourth", sweater = 80, gamesPlayed = 3) +
                goalie(TOR_ID, "T. Extra", sweater = 81, gamesPlayed = 1)

        val torGoalies = PreGameFormat.goaliesFor(TOR_ID, fourForOneClub)

        assertEquals(PreGameFormat.GOALIES_PER_CLUB, torGoalies.size)
        assertEquals(listOf("S. Bobrovsky", "A. Stolarz", "T. Fourth"), torGoalies.map { it.name })
    }

    @Test
    fun topPerformersRankByPointsThenGoalsThenAssistsThenName() {
        // Dee leads the points tie on assists over Bee; Bee and Cal tie on everything and sort by name; Ayo has fewer goals.
        val skaters =
            listOf(
                skater(TOR_ID, "Ayo", goals = 4, assists = 6, points = 10),
                skater(TOR_ID, "Bee", goals = 5, assists = 5, points = 10),
                skater(TOR_ID, "Cal", goals = 5, assists = 5, points = 10),
                skater(TOR_ID, "Dee", goals = 5, assists = 6, points = 10),
            )

        val top = PreGameFormat.topPerformers(TOR_ID, skaters)

        assertEquals(listOf("Dee", "Bee", "Cal"), top.map { it.name })
    }

    @Test
    fun topPerformersRankByPointsNotGoals() {
        val skaters =
            listOf(
                skater(TOR_ID, "Scorer", goals = 9, assists = 0, points = 9),
                skater(TOR_ID, "Playmaker", goals = 2, assists = 9, points = 11),
            )

        assertEquals(listOf("Playmaker", "Scorer"), PreGameFormat.topPerformers(TOR_ID, skaters).map { it.name })
    }

    @Test
    fun topPerformersAreCutAtThreeAndOnlyFromTheClubAsked() {
        val skaters =
            listOf(
                skater(TOR_ID, "T. One", goals = 1, assists = 1, points = 2),
                skater(TOR_ID, "T. Two", goals = 2, assists = 2, points = 4),
                skater(TOR_ID, "T. Three", goals = 3, assists = 3, points = 6),
                skater(TOR_ID, "T. Four", goals = 4, assists = 4, points = 8),
                skater(COL_ID, "C. Other", goals = 9, assists = 9, points = 18),
            )

        val top = PreGameFormat.topPerformers(TOR_ID, skaters)

        assertEquals(PreGameFormat.TOP_PERFORMERS_PER_CLUB, top.size)
        assertEquals(listOf("T. Four", "T. Three", "T. Two"), top.map { it.name })
        assertTrue(PreGameFormat.topPerformers(null, skaters).isEmpty())
    }

    @Test
    fun aMissingTeamIdAttributesNoGoalieToEitherClub() {
        assertTrue(PreGameFormat.goaliesFor(null, ALL_GOALIES).isEmpty())
    }

    private companion object {
        const val COL_ID = 21L
        const val TOR_ID = 10L

        val COL_STATS =
            ClubSeasonStats(
                powerPlay = RankedStat(0.233333, 11),
                penaltyKill = RankedStat(0.781818, 14),
                faceoff = RankedStat(0.490637, 21),
                goalsFor = RankedStat(4.0, 3),
                goalsAgainst = RankedStat(3.06, 13),
            )

        val TOR_STATS =
            ClubSeasonStats(
                powerPlay = RankedStat(0.285714, 5),
                penaltyKill = RankedStat(0.859375, 4),
                faceoff = RankedStat(0.517274, 9),
                goalsFor = RankedStat(3.37, 12),
                goalsAgainst = RankedStat(3.32, 20),
            )

        val BLACKWOOD =
            GoalieSeason(
                teamId = COL_ID,
                name = "M. Blackwood",
                sweaterNumber = 39,
                gamesPlayed = 14,
                record = SeasonRecord(9, 3, 1),
                goalsAgainstAverage = 2.59842,
                savePercentage = 0.90085,
            )

        val AKHTYAMOV = GoalieSeason(teamId = TOR_ID, name = "A. Akhtyamov", sweaterNumber = 70)

        val ALL_GOALIES =
            listOf(
                BLACKWOOD,
                goalie(COL_ID, "S. Wedgewood", sweater = 41, gamesPlayed = 5),
                goalie(TOR_ID, "A. Stolarz", sweater = 41, gamesPlayed = 7),
                AKHTYAMOV,
                goalie(TOR_ID, "S. Bobrovsky", sweater = 72, gamesPlayed = 12),
            )

        fun goalie(
            teamId: Long,
            name: String,
            sweater: Int,
            gamesPlayed: Int?,
        ) = GoalieSeason(teamId = teamId, name = name, sweaterNumber = sweater, gamesPlayed = gamesPlayed)

        fun skater(
            teamId: Long,
            name: String,
            goals: Int,
            assists: Int,
            points: Int,
        ) = SkaterSeason(teamId = teamId, name = name, goals = goals, assists = assists, points = points)
    }
}
