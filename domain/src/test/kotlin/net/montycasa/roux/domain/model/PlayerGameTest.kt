package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGameTest {
    @Test
    fun newestFirstSortsByDateDescendingWithUndatedRowsLastAndSameDayByGameId() {
        val games =
            listOf(
                skater(gameId = 3, date = "2026-10-28"),
                skater(gameId = 5, date = null),
                skater(gameId = 8, date = "2026-11-03"),
                skater(gameId = 9, date = "2026-11-03"),
            )

        assertEquals(listOf(9L, 8L, 3L, 5L), games.newestFirst().map { it.gameId })
    }

    @Test
    fun newestFirstDoesNotDependOnTheFeedsOrder() {
        val ascending =
            listOf(
                skater(gameId = 1, date = "2026-10-01"),
                skater(gameId = 2, date = "2026-10-02"),
                skater(gameId = 3, date = "2026-10-03"),
            )

        assertEquals(listOf(3L, 2L, 1L), ascending.newestFirst().map { it.gameId })
        assertEquals(listOf(3L, 2L, 1L), ascending.reversed().newestFirst().map { it.gameId })
    }

    private fun skater(
        gameId: Long,
        date: String?,
    ): PlayerGame = PlayerGame.Skater(gameId = gameId, gameDate = date, opponentAbbrev = "OTT", isHome = true)
}
