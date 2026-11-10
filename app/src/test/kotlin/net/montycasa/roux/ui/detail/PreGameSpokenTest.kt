package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.SeasonRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The spoken sentences for the four pre-game cards, pinned to the 2026-11-10 COL at TOR fixtures.
 *
 * Each expected string is the sentence a fan should hear, worked out from the feed's raw values by hand. A formatter
 * that keeps a dash, drops a rank, reads a percentage as a percent sign or puts the goals after the assists fails
 * here rather than on a device.
 */
class PreGameSpokenTest {
    @Test
    fun theLastTenRowNamesTheClubThenTheRecordThenTheStreak() {
        assertEquals(
            "Toronto Maple Leafs, 6 wins, 3 losses, 1 overtime loss, won the last 2",
            PreGameSpoken.lastTen(
                club = "Toronto Maple Leafs",
                form = ClubForm(record = SeasonRecord(6, 3, 1), streakType = "W", streak = 2),
            ),
        )
    }

    @Test
    fun theLastTenRowCountsInTheSingularWhenThereIsOne() {
        assertEquals(
            "Colorado Avalanche, 1 win, 1 loss, 1 overtime loss, lost the last 1",
            PreGameSpoken.lastTen(
                club = "Colorado Avalanche",
                form = ClubForm(record = SeasonRecord(1, 1, 1), streakType = "L", streak = 1),
            ),
        )
    }

    @Test
    fun theLastTenRowSaysEachStreakCodeInWords() {
        fun streak(
            type: String,
            count: Int,
        ) = PreGameSpoken.lastTen("Utah Mammoth", ClubForm(record = SeasonRecord(6, 3, 1), streakType = type, streak = count))

        assertEquals("Utah Mammoth, 6 wins, 3 losses, 1 overtime loss, won the last 2", streak("W", 2))
        assertEquals("Utah Mammoth, 6 wins, 3 losses, 1 overtime loss, lost the last 3", streak("L", 3))
        assertEquals("Utah Mammoth, 6 wins, 3 losses, 1 overtime loss, lost 1 in overtime", streak("OTL", 1))
        assertEquals("Utah Mammoth, 6 wins, 3 losses, 1 overtime loss, lost 1 in a shootout", streak("SOL", 1))
    }

    @Test
    fun theLastTenRowLeavesOutAStreakTheFeedDidNotSend() {
        assertEquals(
            "Philadelphia Flyers, 5 wins, 4 losses, 1 overtime loss",
            PreGameSpoken.lastTen(
                club = "Philadelphia Flyers",
                form = ClubForm(record = SeasonRecord(5, 4, 1), streakType = null, streak = null),
            ),
        )
        assertEquals(
            "Philadelphia Flyers, 5 wins, 4 losses, 1 overtime loss",
            PreGameSpoken.lastTen(
                club = "Philadelphia Flyers",
                form = ClubForm(record = SeasonRecord(5, 4, 1), streakType = "W", streak = null),
            ),
        )
    }

    @Test
    fun theLastTenRowSaysNothingForAStreakCodeTheFeedDoesNotUse() {
        assertEquals(
            "Philadelphia Flyers, 5 wins, 4 losses, 1 overtime loss",
            PreGameSpoken.lastTen(
                club = "Philadelphia Flyers",
                form = ClubForm(record = SeasonRecord(5, 4, 1), streakType = "X", streak = 2),
            ),
        )
    }

    @Test
    fun theLastTenRowHasNothingToSayWithoutARecord() {
        assertNull(PreGameSpoken.lastTen("Philadelphia Flyers", ClubForm(record = null, streakType = "W", streak = 2)))
    }

    @Test
    fun theSeasonStatRowNamesTheStatThenEachClub() {
        assertEquals(
            "Power play. Toronto Maple Leafs 20.3 percent, 8th. Montréal Canadiens 21.0 percent, 4th.",
            PreGameSpoken.seasonStat(
                label = "Power play",
                away = PreGameSpoken.StatSide("Toronto Maple Leafs", "20.3%", "8th"),
                home = PreGameSpoken.StatSide("Montréal Canadiens", "21.0%", "4th"),
            ),
        )
    }

    @Test
    fun theSeasonStatRowNeverSaysAPercentSign() {
        val spoken =
            PreGameSpoken.seasonStat(
                label = "Penalty kill",
                away = PreGameSpoken.StatSide("Utah Mammoth", "81.2%", "9th"),
                home = PreGameSpoken.StatSide("Philadelphia Flyers", "83.0%", "6th"),
            )

        assertEquals("Penalty kill. Utah Mammoth 81.2 percent, 9th. Philadelphia Flyers 83.0 percent, 6th.", spoken)
        assertEquals(-1, spoken.indexOf("%"))
    }

    @Test
    fun theSeasonStatRowSaysAValueWithNoPercentSignAsItIsWritten() {
        assertEquals(
            "Goals for / game. Utah Mammoth 3.12, 17th. Philadelphia Flyers 3.65, 6th.",
            PreGameSpoken.seasonStat(
                label = "Goals for / game",
                away = PreGameSpoken.StatSide("Utah Mammoth", "3.12", "17th"),
                home = PreGameSpoken.StatSide("Philadelphia Flyers", "3.65", "6th"),
            ),
        )
    }

    @Test
    fun theSeasonStatRowLeavesOutTheRankASideDoesNotHave() {
        assertEquals(
            "Power play. Utah Mammoth 20.3 percent. Philadelphia Flyers 21.0 percent, 4th.",
            PreGameSpoken.seasonStat(
                label = "Power play",
                away = PreGameSpoken.StatSide("Utah Mammoth", "20.3%", "–"),
                home = PreGameSpoken.StatSide("Philadelphia Flyers", "21.0%", "4th"),
            ),
        )
    }

    @Test
    fun theSeasonStatRowLeavesOutASideWithNoValue() {
        assertEquals(
            "Power play. Philadelphia Flyers 21.0 percent, 4th.",
            PreGameSpoken.seasonStat(
                label = "Power play",
                away = PreGameSpoken.StatSide("Utah Mammoth", "", "–"),
                home = PreGameSpoken.StatSide("Philadelphia Flyers", "21.0%", "4th"),
            ),
        )
    }

    @Test
    fun theStackedSeasonStatRowNamesTheStatThenItsOwnClub() {
        assertEquals(
            "Power play. Utah Mammoth 20.3 percent, 9th.",
            PreGameSpoken.seasonStatSide(
                label = "Power play",
                side = PreGameSpoken.StatSide("Utah Mammoth", "20.3%", "9th"),
            ),
        )
        assertNull(
            PreGameSpoken.seasonStatSide(
                label = "Power play",
                side = PreGameSpoken.StatSide("Utah Mammoth", "–", "–"),
            ),
        )
    }

    @Test
    fun theTopPerformerRowNamesThePlayerThenTheCounts() {
        assertEquals(
            "N. MacKinnon, 10 goals, 20 assists, 30 points",
            PreGameSpoken.topPerformer(name = "N. MacKinnon", goals = 10, assists = 20, points = 30),
        )
    }

    @Test
    fun theTopPerformerRowCountsInTheSingularWhenThereIsOne() {
        assertEquals(
            "Mitch Marner, 1 goal, 1 assist, 1 point",
            PreGameSpoken.topPerformer(name = "Mitch Marner", goals = 1, assists = 1, points = 1),
        )
    }

    @Test
    fun theGoaltenderRowNamesTheGoaltenderThenEveryFigure() {
        assertEquals(
            "A. Vanecek, 14 games, 8 wins, 4 losses, 2 overtime losses, 2.71 goals against, 90.6 percent saves",
            PreGameSpoken.goaltender(
                name = "A. Vanecek",
                goalie =
                    GoalieSeason(
                        teamId = 68L,
                        name = "A. Vanecek",
                        gamesPlayed = 14,
                        record = SeasonRecord(8, 4, 2),
                        goalsAgainstAverage = 2.71,
                        savePercentage = 0.906,
                    ),
            ),
        )
    }

    @Test
    fun theGoaltenderRowCountsInTheSingularWhenThereIsOne() {
        assertEquals(
            "K. Kochetkov, 1 game, 1 win, 0 losses, 0 overtime losses, 3.12 goals against, 88.9 percent saves",
            PreGameSpoken.goaltender(
                name = "K. Kochetkov",
                goalie =
                    GoalieSeason(
                        teamId = 68L,
                        name = "K. Kochetkov",
                        gamesPlayed = 1,
                        record = SeasonRecord(1, 0, 0),
                        goalsAgainstAverage = 3.12,
                        savePercentage = 0.889,
                    ),
            ),
        )
    }

    @Test
    fun theGoaltenderRowLeavesOutEveryFigureTheFeedDidNotSend() {
        assertEquals(
            "S. Ersson, 4 games, 2 wins, 2 losses, 0 overtime losses",
            PreGameSpoken.goaltender(
                name = "S. Ersson",
                goalie =
                    GoalieSeason(
                        teamId = 4L,
                        name = "S. Ersson",
                        gamesPlayed = 4,
                        record = SeasonRecord(2, 2, 0),
                        goalsAgainstAverage = null,
                        savePercentage = null,
                    ),
            ),
        )
        assertEquals(
            "Z. Benched",
            PreGameSpoken.goaltender(
                name = "Z. Benched",
                goalie = GoalieSeason(teamId = 68L, name = "Z. Benched", gamesPlayed = null, record = null),
            ),
        )
    }
}
