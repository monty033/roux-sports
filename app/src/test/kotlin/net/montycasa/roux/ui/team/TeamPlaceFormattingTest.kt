package net.montycasa.roux.ui.team

import net.montycasa.roux.domain.model.Conference
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamPlaceFormattingTest {
    /** English ordinals: the teens take "th" even where the last digit says otherwise. */
    @Test
    fun ordinalsFollowEnglishIncludingTheTeens() {
        assertEquals("1st", ordinal(1))
        assertEquals("2nd", ordinal(2))
        assertEquals("3rd", ordinal(3))
        assertEquals("4th", ordinal(4))
        assertEquals("11th", ordinal(11))
        assertEquals("12th", ordinal(12))
        assertEquals("13th", ordinal(13))
        assertEquals("21st", ordinal(21))
        assertEquals("22nd", ordinal(22))
        assertEquals("23rd", ordinal(23))
        assertEquals("111th", ordinal(111))
        assertEquals("112th", ordinal(112))
        assertEquals("121st", ordinal(121))
    }

    /** A sequence of zero means the feed did not place the club; the page prints a dash, never "0th". */
    @Test
    fun aNonPositiveRankIsADashNotAnOrdinal() {
        assertEquals("–", ordinal(0))
    }

    @Test
    fun conferencesReadEastAndWest() {
        assertEquals("East", conferenceShortName(Conference.Eastern))
        assertEquals("West", conferenceShortName(Conference.Western))
    }

    /** An unrecognised conference shows the feed's own word, trimmed, rather than being guessed at. */
    @Test
    fun anUnrecognisedConferenceShowsTheFeedsWord() {
        assertEquals("Northern", conferenceShortName(Conference.Unknown("  Northern ")))
    }
}
