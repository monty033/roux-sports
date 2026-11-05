package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerCareerTableTest {
    @Test
    fun threeNhlSeasonsAndOneAhlSeasonShowThreeRowsOldestFirst() {
        val rows =
            PlayerCareerTable.rows(
                listOf(
                    line(20262027, "NHL", "Carolina Hurricanes", 1, skater(goals = 3)),
                    line(20232024, "NHL", "Carolina Hurricanes", 1, skater(goals = 1)),
                    line(20252026, "AHL", "Charlotte Checkers", 1, skater(goals = 99)),
                    line(20242025, "NHL", "Carolina Hurricanes", 1, skater(goals = 2)),
                ),
            )

        assertEquals(listOf(20232024, 20242025, 20262027), rows.map { it.season })
        assertEquals(listOf("NHL", "NHL", "NHL"), rows.map { it.leagueAbbrev })
    }

    @Test
    fun playoffRowsAndRowsWithNoSeasonAreLeftOutOfTheCareerTable() {
        val rows =
            PlayerCareerTable.rows(
                listOf(
                    line(20242025, "NHL", "Carolina Hurricanes", 1, skater(), gameType = 3),
                    line(null, "NHL", "Carolina Hurricanes", 1, skater()),
                    line(20242025, "NHL", "Carolina Hurricanes", 1, skater()),
                ),
            )

        assertEquals(1, rows.size)
        assertEquals(2, rows.single().gameTypeId)
    }

    @Test
    fun aTradedSeasonKeepsOneRowPerClubInClubOrderAndEachRowNamesItsClub() {
        val rows =
            PlayerCareerTable.rows(
                listOf(
                    line(20252026, "NHL", "Boston Bruins", 2, skater(gamesPlayed = 20)),
                    line(20252026, "NHL", "Carolina Hurricanes", 1, skater(gamesPlayed = 30)),
                ),
            )

        assertEquals(listOf("Carolina Hurricanes", "Boston Bruins"), rows.map { it.teamName })
        assertEquals(listOf(20252026, 20252026), rows.map { it.season })
    }

    /**
     * The fixture where double counting shows. The 2023-24 season is traded, so it has two club rows: 5 goals and 30 games
     * for one club, 4 goals and 20 games for the other. Counting that season twice (once per club and once more) would
     * read 40 goals and 230 games. The true sums are 31 goals and 180 games.
     */
    @Test
    fun theTotalSumsEachSeasonOnceSoATradedSeasonCountsItsTwoClubsOnlyOnce() {
        val seasons =
            listOf(
                line(20222023, "NHL", "Carolina Hurricanes", 1, skater(gamesPlayed = 60, goals = 10, shots = 100)),
                line(20232024, "NHL", "Carolina Hurricanes", 1, skater(gamesPlayed = 30, goals = 5, shots = 40)),
                line(20232024, "NHL", "Boston Bruins", 2, skater(gamesPlayed = 20, goals = 4, shots = 30)),
                line(20242025, "NHL", "Boston Bruins", 1, skater(gamesPlayed = 70, goals = 12, shots = 150)),
            )

        val total = PlayerCareerTable.total(PlayerCareerTable.rows(seasons), PlayerCareer()) as PlayerTotals.Skater

        assertEquals(180, total.gamesPlayed)
        assertEquals(31, total.goals)
        assertEquals(320, total.shots)
        assertEquals(31.0 / 320.0, total.shootingPctg!!, 0.000001)
    }

    @Test
    fun aMissingNumberInAnyRowMakesThatTotalAbsentRatherThanShort() {
        val total =
            PlayerCareerTable.total(
                listOf(
                    line(20222023, "NHL", "Carolina Hurricanes", 1, skater(gamesPlayed = 60, goals = 10)),
                    line(20232024, "NHL", "Carolina Hurricanes", 1, skater(gamesPlayed = 30, goals = null)),
                ),
                PlayerCareer(),
            ) as PlayerTotals.Skater

        assertEquals(90, total.gamesPlayed)
        assertNull(total.goals)
    }

    @Test
    fun aCareerWithNoNhlRowsHasNoTotal() {
        assertNull(PlayerCareerTable.total(emptyList(), PlayerCareer()))
    }

    @Test
    fun faceoffPercentageAndIceTimeComeFromTheFeedsCareerTotalsNotFromASum() {
        val career =
            PlayerCareer(
                regularSeason = skater(faceoffWinningPctg = 0.5, avgTimeOnIceSeconds = 672L),
            )

        val total =
            PlayerCareerTable.total(
                listOf(line(20222023, "NHL", "Carolina Hurricanes", 1, skater(gamesPlayed = 60))),
                career,
            ) as PlayerTotals.Skater

        assertEquals(0.5, total.faceoffWinningPctg!!, 0.000001)
        assertEquals(672L, total.avgTimeOnIceSeconds)
    }

    @Test
    fun aGoalieTotalDerivesItsAverageAndSavePercentageFromTheSums() {
        val total =
            PlayerCareerTable.total(
                listOf(
                    line(20242025, "NHL", "Anaheim Ducks", 1, goalie(goalsAgainst = 60, shotsAgainst = 600, timeOnIce = 54000L)),
                    line(20252026, "NHL", "Anaheim Ducks", 1, goalie(goalsAgainst = 40, shotsAgainst = 400, timeOnIce = 36000L)),
                ),
                PlayerCareer(),
            ) as PlayerTotals.Goalie

        assertEquals(100, total.goalsAgainst)
        assertEquals(1000, total.shotsAgainst)
        assertEquals(90000L, total.timeOnIceSeconds)
        assertEquals(4.0, total.goalsAgainstAverage!!, 0.000001)
        assertEquals(0.9, total.savePercentage!!, 0.000001)
    }

    @Test
    fun aSkaterRowAndAGoalieRowCannotShareATotal() {
        assertNull(
            PlayerCareerTable.total(
                listOf(
                    line(20242025, "NHL", "Anaheim Ducks", 1, skater()),
                    line(20252026, "NHL", "Anaheim Ducks", 1, goalie()),
                ),
                PlayerCareer(),
            ),
        )
    }

    private fun line(
        season: Int?,
        league: String?,
        team: String?,
        sequence: Int?,
        totals: PlayerTotals,
        gameType: Int = 2,
    ) = PlayerSeasonLine(
        season = season,
        gameTypeId = gameType,
        leagueAbbrev = league,
        teamName = team,
        sequence = sequence,
        totals = totals,
    )

    private fun skater(
        gamesPlayed: Int? = 10,
        goals: Int? = 1,
        shots: Int? = 10,
        faceoffWinningPctg: Double? = null,
        avgTimeOnIceSeconds: Long? = null,
    ) = PlayerTotals.Skater(
        gamesPlayed = gamesPlayed,
        goals = goals,
        shots = shots,
        faceoffWinningPctg = faceoffWinningPctg,
        avgTimeOnIceSeconds = avgTimeOnIceSeconds,
    )

    private fun goalie(
        goalsAgainst: Int? = 10,
        shotsAgainst: Int? = 100,
        timeOnIce: Long? = 3600L,
    ) = PlayerTotals.Goalie(
        gamesPlayed = 1,
        goalsAgainst = goalsAgainst,
        shotsAgainst = shotsAgainst,
        timeOnIceSeconds = timeOnIce,
    )
}
