package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSeasonRowsTest {
    /** The newest season that has an NHL regular row decides the tab; the newer AHL row does not win. */
    @Test
    fun theNewestNhlRegularSeasonIsTakenEvenWhenANewerSeasonHoldsOnlyAnotherLeague() {
        val rows =
            listOf(
                line(20252026, "NHL", "Carolina Hurricanes", 1, gameType = 2),
                line(20262027, "AHL", "Charlotte Checkers", 1, gameType = 2),
            )

        assertEquals(listOf(20252026), rows.newestNhlRegularSeasonRows().map { it.season })
    }

    /** A season's other league rows are not in the list, so the tab never shows two grids for one NHL season. */
    @Test
    fun anotherLeagueRowInTheSameSeasonIsLeftOut() {
        val rows =
            listOf(
                line(20262027, "NHL", "Anaheim Ducks", 1, gameType = 2),
                line(20262027, "AHL", "San Diego Gulls", 85177, gameType = 2),
            )

        assertEquals(listOf("Anaheim Ducks"), rows.newestNhlRegularSeasonRows().map { it.teamName })
    }

    /** A traded season keeps one row per club, in the feed's club order. */
    @Test
    fun aTradedSeasonKeepsOneRowPerClubInClubOrder() {
        val rows =
            listOf(
                line(20252026, "NHL", "Boston Bruins", 2, gameType = 2),
                line(20252026, "NHL", "Carolina Hurricanes", 1, gameType = 2),
            )

        assertEquals(
            listOf("Carolina Hurricanes", "Boston Bruins"),
            rows.newestNhlRegularSeasonRows().map { it.teamName },
        )
    }

    /** Playoff rows never make the Season tab; a season with only a playoff row is not the tab's season. */
    @Test
    fun playoffRowsAreLeftOutAndAPlayoffOnlySeasonDoesNotCount() {
        val rows =
            listOf(
                line(20242025, "NHL", "Carolina Hurricanes", 1, gameType = 2),
                line(20252026, "NHL", "Carolina Hurricanes", 1, gameType = 3),
            )

        assertEquals(listOf(20242025), rows.newestNhlRegularSeasonRows().map { it.season })
    }

    @Test
    fun aPlayerWithNoNhlRegularRowHasNoSeasonRows() {
        val rows = listOf(line(20252026, "SHL", "HV71", 11, gameType = 2))

        assertTrue(rows.newestNhlRegularSeasonRows().isEmpty())
    }

    private fun line(
        season: Int,
        league: String,
        team: String,
        sequence: Int,
        gameType: Int,
    ) = PlayerSeasonLine(
        season = season,
        gameTypeId = gameType,
        leagueAbbrev = league,
        teamName = team,
        sequence = sequence,
        totals = PlayerTotals.Skater(gamesPlayed = 1),
    )
}
