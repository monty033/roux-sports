package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.SeriesLine
import net.montycasa.roux.domain.policy.SeriesStanding
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

    @Test
    fun aSingleOvertimeStaysFinalOt() {
        assertEquals("Final/OT", heroStatus(finalOt(otPeriods = 1), ZoneId.of("UTC")))
    }

    @Test
    fun anOvertimeCountOfNullStaysFinalOt() {
        assertEquals("Final/OT", heroStatus(finalOt(otPeriods = null), ZoneId.of("UTC")))
    }

    @Test
    fun twoOvertimePeriodsNameTheCount() {
        assertEquals("Final/2OT", heroStatus(finalOt(otPeriods = 2), ZoneId.of("UTC")))
    }

    @Test
    fun aSeriesLineNamesTheRoundAndGameThenTheLead() {
        assertEquals(
            "1st Round · Game 1 · BUF leads 1–0",
            seriesText(SeriesLine("1st Round", 1, SeriesStanding.Leads("BUF", 1, 0))),
        )
    }

    @Test
    fun aTiedSeriesReadsSeriesTied() {
        assertEquals(
            "1st Round · Game 4 · Series tied 2–2",
            seriesText(SeriesLine("1st Round", 4, SeriesStanding.Tied(2))),
        )
    }

    /** The right rail names no round, so its line starts at the game number. */
    @Test
    fun aClinchedSeriesFromTheRightRailReadsTheWinnerWithoutARound() {
        assertEquals("Game 6 · BUF wins 4–2", seriesText(SeriesLine(null, 6, SeriesStanding.Wins("BUF", 4, 2))))
    }

    private fun finalOt(otPeriods: Int?): Game = game.copy(state = GameState.Final, finalKind = FinalKind.OT, otPeriods = otPeriods)
}
