package net.montycasa.roux.ui.team

import org.junit.Assert.assertEquals
import org.junit.Test

/** The number formatting the Players table inherited from the stats feed. */
class PlayerStatsFormattingTest {
    @Test
    fun theThreeNumberTrapsAreFormattedForPeople() {
        // The feed's ice time is seconds, not minutes: 958.7857 is 15:58 a game.
        assertEquals("15:58", formatStatsIceTime(958.7857))
        // Shooting is a fraction: 0.1 means ten percent.
        assertEquals("10.0%", formatShootingPercentage(0.1))
        // Save percentage is a fraction printed the way hockey prints it.
        assertEquals(".870", formatSavePercentage(0.869565))
        assertEquals("3.00", formatGoalsAgainstAverage(3.0))
    }

    @Test
    fun aRealZeroIsNotAbsence() {
        assertEquals("0.00", formatGoalsAgainstAverage(0.0))
        assertEquals(".000", formatSavePercentage(0.0))
        assertEquals("0", formatStatNumber(0))
        assertEquals("0:00", formatStatsIceTime(0.0))
    }

    @Test
    fun anAbsentNumberIsTheEmDash() {
        assertEquals("—", formatStatNumber(null))
        assertEquals("—", formatGoalsAgainstAverage(null))
        assertEquals("—", formatSavePercentage(null))
        assertEquals("—", formatPlusMinus(null))
        assertEquals("—", formatStatsIceTime(null as Double?))
    }

    @Test
    fun plusMinusKeepsItsSignButZeroIsNotPlusZero() {
        assertEquals("+5", formatPlusMinus(5))
        assertEquals("-3", formatPlusMinus(-3))
        assertEquals("0", formatPlusMinus(0))
    }
}
