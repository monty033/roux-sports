package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentColorTest {
    @Test fun choicesAreTheNineInSettingsOrder() {
        assertEquals(
            listOf("Orange", "Blue", "Teal", "Green", "Purple", "Red", "Gold", "Pink", "Cyan"),
            AccentColor.entries.map { it.name },
        )
    }

    @Test fun defaultIsTheShippedOrange() {
        assertEquals(AccentColor.Orange, AccentColor.DEFAULT)
        assertEquals(AccentColor.entries.first(), AccentColor.DEFAULT)
    }
}
