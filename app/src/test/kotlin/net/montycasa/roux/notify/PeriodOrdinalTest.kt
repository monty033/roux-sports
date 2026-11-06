package net.montycasa.roux.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodOrdinalTest {
    @Test
    fun rulePeriodsUseTheirOrdinarySuffixes() {
        assertEquals("1st", periodOrdinal(1))
        assertEquals("2nd", periodOrdinal(2))
        assertEquals("3rd", periodOrdinal(3))
        assertEquals("4th", periodOrdinal(4))
    }

    @Test
    fun theTeensKeepTheThSuffix() {
        assertEquals("11th", periodOrdinal(11))
        assertEquals("12th", periodOrdinal(12))
        assertEquals("13th", periodOrdinal(13))
    }

    @Test
    fun numbersEndingInOneStillUseStAboveTheTeens() {
        assertEquals("21st", periodOrdinal(21))
        assertEquals("22nd", periodOrdinal(22))
        assertEquals("23rd", periodOrdinal(23))
    }
}
