package net.montycasa.roux.domain.policy

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun theSeasonChangesAtJulyFirst() {
        // Both sides of the boundary, plus a mid-season date that must stay in the season that began
        // the previous July.
        assertEquals(
            "20252026",
            LeagueCalendar.seasonId(Instant.parse("2026-06-30T12:00:00Z"), TimeZone.UTC),
        )
        assertEquals(
            "20262027",
            LeagueCalendar.seasonId(Instant.parse("2026-07-01T12:00:00Z"), TimeZone.UTC),
        )
        assertEquals(
            "20252026",
            LeagueCalendar.seasonId(Instant.parse("2026-01-15T12:00:00Z"), TimeZone.UTC),
        )
    }

    @Test
    fun theSeasonFollowsTheDevicesCivilDayRatherThanUtc() {
        // Late on 30 June in Vancouver is already 1 July in UTC, and the club page must still ask for
        // the season the device is actually in.
        val instant = Instant.parse("2026-07-01T06:30:00Z")
        assertEquals("20252026", LeagueCalendar.seasonId(instant, vancouver))
        assertEquals("20262027", LeagueCalendar.seasonId(instant, toronto))
    }

    @Test
    fun thePreviousSeasonIsTheOneImmediatelyBefore() {
        assertEquals("20252026", LeagueCalendar.previousSeasonId("20262027"))
        assertEquals("19992000", LeagueCalendar.previousSeasonId("20002001"))
    }

    @Test
    fun aMalformedSeasonIsRejectedRatherThanInventingOne() {
        listOf("2026", "202620270", "2026-27", "2026202a").forEach { bad ->
            val thrown = runCatching { LeagueCalendar.previousSeasonId(bad) }.exceptionOrNull()
            assertTrue("'$bad' must be rejected, got $thrown", thrown is IllegalArgumentException)
        }
    }

    @Test
    fun aSeasonWhoseYearsAreNotConsecutiveIsRejected() {
        val thrown = runCatching { LeagueCalendar.previousSeasonId("20262029") }.exceptionOrNull()
        assertTrue("20262029 is not a season, got $thrown", thrown is IllegalArgumentException)
    }
}
