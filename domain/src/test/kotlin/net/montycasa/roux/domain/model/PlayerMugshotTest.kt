package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The path is built, not received, so its shape and its refusals are both worth pinning. */
class PlayerMugshotTest {
    @Test
    fun thePathIsSeasonClubAndId() {
        assertEquals(
            "https://assets.nhle.com/mugs/nhl/20262027/PHI/8478439.png",
            PlayerMugshot.url(8478439L, "PHI", 2026),
        )
    }

    @Test
    fun aMissingPartMeansNoRequestAtAll() {
        assertNull(PlayerMugshot.url(null, "PHI", 2026))
        assertNull(PlayerMugshot.url(0L, "PHI", 2026))
        assertNull(PlayerMugshot.url(8478439L, "  ", 2026))
        assertNull(PlayerMugshot.url(8478439L, "PHI", null))
    }

    @Test
    fun theSeasonTurnsOverInAugust() {
        // A game in October belongs to the season that started that autumn; one in March belongs to the previous.
        assertEquals(2026, PlayerMugshot.seasonStartYear("2026-10-26"))
        assertEquals(2025, PlayerMugshot.seasonStartYear("2026-03-14"))
        assertEquals(2026, PlayerMugshot.seasonStartYear("2026-08-01"))
        assertEquals(2025, PlayerMugshot.seasonStartYear("2026-07-31"))
    }

    @Test
    fun anUnreadableDateDrawsInitialsInstead() {
        assertNull(PlayerMugshot.seasonStartYear(null))
        assertNull(PlayerMugshot.seasonStartYear(""))
        assertNull(PlayerMugshot.seasonStartYear("2026-10"))
        assertNull(PlayerMugshot.seasonStartYear("not-a-date"))
        assertNull(PlayerMugshot.seasonStartYear("2026-13-01"))
    }
}
