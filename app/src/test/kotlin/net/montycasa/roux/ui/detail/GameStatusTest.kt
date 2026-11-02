package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class GameStatusTest {
    private val game = Game(GameId("1"), "2026-10-26", GameState.Live(), TeamRef(TeamKey("PHI")), TeamRef(TeamKey("CBJ")))

    @Test
    fun intermissionSuppressesClock() {
        assertEquals("2nd INT", heroStatus(game.copy(period = PeriodInfo(2, "REG", "12:34", true)), ZoneId.of("UTC")))
    }

    @Test
    fun missingPeriodFallsBackToLive() {
        assertEquals("Live", heroStatus(game, ZoneId.of("UTC")))
    }
}
