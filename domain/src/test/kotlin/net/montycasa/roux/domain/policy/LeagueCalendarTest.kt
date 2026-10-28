package net.montycasa.roux.domain.policy

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

/**
 * The rollover decision, pinned: the day turns at the device's local midnight, not in the league's zone.
 *
 * Times are UTC so the cases read as facts about zones rather than about whatever machine runs them.
 */
class LeagueCalendarTest {
    private val vancouver = TimeZone.of("America/Vancouver")
    private val toronto = TimeZone.of("America/Toronto")

    @Test
    fun aLateEveningGameStaysOnTheDevicesDay() {
        // 04:30Z on the 27th is 9:30 PM on the 26th in Vancouver: a fan watching that night is still on the 26th.
        assertEquals("2026-10-26", LeagueCalendar.leagueDay(Instant.parse("2026-10-27T04:30:00Z"), vancouver))
    }

    @Test
    fun theSameInstantIsADifferentLeagueDayInTheLeaguesZone() {
        // Which is exactly why the zone is an argument: the Eastern answer would show this fan tomorrow's slate.
        val instant = Instant.parse("2026-10-27T04:30:00Z")
        assertEquals("2026-10-27", LeagueCalendar.leagueDay(instant, toronto))
        assertEquals("2026-10-26", LeagueCalendar.leagueDay(instant, vancouver))
    }

    @Test
    fun bothSidesOfTheDstChangeKeepTheRightDay() {
        // Autumn clocks go back on 2026-11-01, moving midnight from 04:00Z to 05:00Z in Toronto. No instant may land on
        // the wrong side of a date boundary because of it. The league's own zone is the one that still changes: Pacific
        // time is a fixed UTC-7 in current tzdata (British Columbia dropped the fall-back), so a Vancouver case here
        // would assert nothing about DST.
        assertEquals("2026-10-31", LeagueCalendar.leagueDay(Instant.parse("2026-11-01T03:30:00Z"), toronto))
        assertEquals("2026-11-01", LeagueCalendar.leagueDay(Instant.parse("2026-11-01T04:30:00Z"), toronto))
        assertEquals("2026-11-01", LeagueCalendar.leagueDay(Instant.parse("2026-11-02T04:30:00Z"), toronto))
        assertEquals("2026-11-02", LeagueCalendar.leagueDay(Instant.parse("2026-11-02T05:30:00Z"), toronto))
    }

    @Test
    fun midnightItselfBelongsToTheNewDay() {
        // A boundary is inclusive: the first instant of a day is that day, not the one before it.
        assertEquals("2026-10-27", LeagueCalendar.leagueDay(Instant.parse("2026-10-27T07:00:00Z"), vancouver))
        assertEquals("2026-10-26", LeagueCalendar.leagueDay(Instant.parse("2026-10-27T06:59:59Z"), vancouver))
    }
}
