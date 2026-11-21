package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The week and playoff-round fields are additive: a game built the way the NHL build builds one must equal the shape
 * it had before, with both fields absent. That is what keeps this change from disturbing the NHL feed.
 */
class GameTest {
    private val nhlGame =
        Game(
            id = GameId(League.NHL, "2026020100"),
            date = "2026-02-01",
            state = GameState.Final,
            home = TeamRef(TeamKey(League.NHL, "TOR")),
            away = TeamRef(TeamKey(League.NHL, "MTL")),
            homeScore = 3,
            awayScore = 2,
        )

    @Test
    fun anNhlGameHasNoWeekAndNoRound() {
        assertNull(nhlGame.roundLabel)
    }

    @Test
    fun anNhlScoreDayHasNoWeek() {
        val day =
            ScoreDay(
                date = "2026-02-01",
                games = listOf(nhlGame),
                dateIndex = listOf(DateIndexEntry("2026-02-01", 1)),
                droppedCount = 0,
            )

        assertNull(day.week)
    }

    @Test
    fun aGameCarriesTheRoundAndTheDayCarriesTheWeekWhenTheFeedGivesThem() {
        val game = nhlGame.copy(roundLabel = "Wild Card")
        val day =
            ScoreDay(
                date = "2026-11-22",
                games = listOf(game),
                dateIndex = emptyList(),
                droppedCount = 0,
                week = 12,
            )

        assertEquals("Wild Card", game.roundLabel)
        assertEquals(12, day.week)
    }
}
