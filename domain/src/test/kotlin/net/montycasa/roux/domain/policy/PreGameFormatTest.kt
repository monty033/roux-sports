package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.ClubSeasonStats
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.RankedStat
import net.montycasa.roux.domain.model.SeasonRecord
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
    fun goalieStatsReadAsGamesRecordAverageAndSavePercentage() {
        assertEquals("14 GP · 9–3–1 · 2.60 GAA · 90.1%", PreGameFormat.goalieStats(BLACKWOOD))
    }

    @Test
    fun aGoalieWithoutARecordPrintsGamesAverageAndSavePercentageWithNoPlaceholder() {
        val noRecord = BLACKWOOD.copy(record = null)

        assertEquals("14 GP · 2.60 GAA · 90.1%", PreGameFormat.goalieStats(noRecord))
    }

    @Test
    fun aGoalieWithNoSeasonHasNoStatsLineAndNoStarterLabel() {
        assertNull(PreGameFormat.goalieStats(AKHTYAMOV))
        assertEquals("A. Akhtyamov #70", PreGameFormat.goalieHeading(AKHTYAMOV))
    }

    @Test
    fun goaliesAreSplitByTeamIdNotSweaterNumber() {
        // Sweater 41 is worn by Wedgewood for COL and by Stolarz for TOR. Splitting by number would hand each club both.
        val colGoalies = PreGameFormat.goaliesFor(COL_ID, ALL_GOALIES)
        val torGoalies = PreGameFormat.goaliesFor(TOR_ID, ALL_GOALIES)

        assertEquals(listOf("M. Blackwood", "S. Wedgewood"), colGoalies.map { it.name })
        assertEquals(listOf("S. Bobrovsky", "A. Stolarz", "A. Akhtyamov"), torGoalies.map { it.name })
    }

    @Test
    fun goaliesSortMostGamesFirstAndAnUnplayedGoalieLast() {
        val torGoalies = PreGameFormat.goaliesFor(TOR_ID, ALL_GOALIES)

        assertEquals(listOf(12, 7, null), torGoalies.map { it.gamesPlayed })
    }

    @Test
    fun aClubShowsAtMostThreeGoalies() {
        // One game played ranks above Akhtyamov's none, so the extra goalie takes the third place and he is the one cut.
        val fourForOneClub = ALL_GOALIES + goalie(TOR_ID, "T. Extra", sweater = 80, gamesPlayed = 1)

        val torGoalies = PreGameFormat.goaliesFor(TOR_ID, fourForOneClub)

        assertEquals(PreGameFormat.GOALIES_PER_CLUB, torGoalies.size)
        assertEquals(listOf("S. Bobrovsky", "A. Stolarz", "T. Extra"), torGoalies.map { it.name })
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
    }
}
