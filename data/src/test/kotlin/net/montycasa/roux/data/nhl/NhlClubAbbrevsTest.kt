package net.montycasa.roux.data.nhl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NhlClubAbbrevsTest {
    @Test
    fun aCurrentClubNameMapsToItsAbbreviation() {
        assertEquals("CAR", NhlClubAbbrevs.forName("Carolina Hurricanes"))
        assertEquals("VGK", NhlClubAbbrevs.forName("Vegas Golden Knights"))
        assertEquals("ANA", NhlClubAbbrevs.forName("Anaheim Ducks"))
    }

    @Test
    fun aNameWithSurroundingSpaceStillMaps() {
        assertEquals("EDM", NhlClubAbbrevs.forName("  Edmonton Oilers "))
    }

    @Test
    fun aNameTheTableDoesNotKnowHasNoAbbreviation() {
        assertNull(NhlClubAbbrevs.forName("Carolina"))
        assertNull(NhlClubAbbrevs.forName("Charlotte Checkers"))
    }
}
