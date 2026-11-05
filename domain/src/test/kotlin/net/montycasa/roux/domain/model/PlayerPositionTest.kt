package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPositionTest {
    @Test
    fun theWingsPrintAsLwAndRwAndTheOthersAreUnchanged() {
        assertEquals("LW", playerPositionLabel("L"))
        assertEquals("RW", playerPositionLabel("R"))
        assertEquals("C", playerPositionLabel("C"))
        assertEquals("D", playerPositionLabel("D"))
        assertEquals("G", playerPositionLabel("G"))
    }

    /** A code the mapping does not know prints exactly as the feed sent it, rather than being guessed into one of the five. */
    @Test
    fun anUnrecognisedCodePrintsAsTheFeedGivesIt() {
        assertEquals("F", playerPositionLabel("F"))
        assertEquals("X", playerPositionLabel("X"))
        assertEquals("", playerPositionLabel(""))
    }
}
