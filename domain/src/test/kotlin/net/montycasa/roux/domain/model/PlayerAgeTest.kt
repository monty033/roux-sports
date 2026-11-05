package net.montycasa.roux.domain.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerAgeTest {
    private val today = LocalDate(2026, 11, 5)

    /** Born on today's date a year ago: one year old today, not zero. */
    @Test
    fun aPlayerBornOnTodaysDateAYearAgoIsOneNotZero() {
        assertEquals(1, profile(birthDate = "2025-11-05").ageOn(today))
    }

    /** The day before the birthday is still the younger age. */
    @Test
    fun theDayBeforeTheBirthdayIsStillTheYoungerAge() {
        assertEquals(0, profile(birthDate = "2025-11-05").ageOn(LocalDate(2026, 11, 4)))
        assertEquals(0, profile(birthDate = "2025-11-06").ageOn(today))
    }

    @Test
    fun theAgeCountsWholeYearsOnceTheBirthdayHasPassed() {
        assertEquals(27, profile(birthDate = "1999-01-09").ageOn(today))
    }

    /** A missing or unreadable birth date has no age, rather than a guessed one. */
    @Test
    fun aMissingOrUnreadableBirthDateHasNoAge() {
        assertNull(profile(birthDate = null).ageOn(today))
        assertNull(profile(birthDate = "not a date").ageOn(today))
    }

    /** A birth date after today cannot give a negative age. */
    @Test
    fun aBirthDateAfterTodayHasNoAge() {
        assertNull(profile(birthDate = "2027-01-01").ageOn(today))
    }

    private fun profile(birthDate: String?) =
        PlayerProfile(
            playerId = 1L,
            firstName = "Aleksi",
            lastName = "Heponiemi",
            position = "C",
            birthDate = birthDate,
        )
}
