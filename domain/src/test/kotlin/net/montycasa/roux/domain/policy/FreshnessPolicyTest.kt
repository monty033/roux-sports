package net.montycasa.roux.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FreshnessPolicyTest {
    private val now = Instant.parse("2026-10-26T16:00:00Z")

    @Test
    fun liveDayCachedTwentySecondsAgoIsFresh() {
        val maxAge = scoreDayMaxAge(slate = DaySlate(size = 3, live = 2))
        assertEquals(25.seconds, maxAge)
        assertFalse(FreshnessPolicy.isStale(now - 20.seconds, now, maxAge))
    }

    @Test
    fun liveDayCachedThirtySecondsAgoIsStale() {
        val maxAge = scoreDayMaxAge(slate = DaySlate(size = 3, live = 2))
        assertTrue(FreshnessPolicy.isStale(now - 30.seconds, now, maxAge))
    }

    @Test
    fun pastAllFinalDayNeverExpiresEvenAfterThirtyDays() {
        val maxAge = scoreDayMaxAge(slate = DaySlate(size = 3, finished = 3), day = DayRelation.Past)
        assertNull(maxAge)
        assertFalse(FreshnessPolicy.isStale(now - 30.days, now, maxAge))
    }

    @Test
    fun liveGameTakesPrecedenceOverAnOtherwiseFinishedPastDay() {
        // Three games: one still live, two finished. A past day that is not fully finished is
        // not permanent, and the live game forces the fast cadence regardless of the date.
        assertEquals(25.seconds, scoreDayMaxAge(slate = DaySlate(size = 3, live = 1, finished = 2), day = DayRelation.Past))
    }

    @Test
    fun allFinalTodayStillExpiresAfterFiveMinutes() {
        // The plan gives today five minutes when nothing is live. "All final" on today used to
        // grant a permanent cache, which hid a result corrected after the horn.
        assertEquals(5.minutes, scoreDayMaxAge(slate = DaySlate(size = 3, finished = 3)))
    }

    @Test
    fun allFinalFutureDayStillExpiresAfterSixHours() {
        // "All games are final" is vacuously true over an empty or unpublished day, so a future
        // date can report it while carrying no games at all. Trusting that pinned an empty
        // schedule forever, hiding the slate once it was published.
        assertEquals(6.hours, scoreDayMaxAge(slate = DaySlate(size = 3, finished = 3), day = DayRelation.Future))
        assertNotNull(scoreDayMaxAge(slate = DaySlate(size = 3, finished = 3), day = DayRelation.Future))
    }

    @Test
    fun impossibleSlateIsRejectedRatherThanAbsorbed() {
        // A caller reporting more finished games than games must not be quietly trusted: with a
        // tolerant comparison the over-count makes an unresolved past day permanent.
        assertThrows(IllegalArgumentException::class.java) { DaySlate(size = 2, finished = 3) }
        assertThrows(IllegalArgumentException::class.java) { DaySlate(size = 2, live = 3) }
        // Negative live passes the disjoint and finished checks, so only the live range
        // rejects it - and with live below zero a fully finished past day still goes permanent.
        assertThrows(IllegalArgumentException::class.java) { DaySlate(size = 3, live = -1, finished = 3) }
        assertThrows(IllegalArgumentException::class.java) { DaySlate(size = -1) }
        assertThrows(IllegalArgumentException::class.java) { DaySlate(size = 3, finished = -1) }
        assertThrows(IllegalArgumentException::class.java) { DaySlate(size = 2, live = 2, finished = 2) }
        // Summing the counts overflows: Int.MAX_VALUE + Int.MAX_VALUE wraps negative, so an
        // addition-based disjoint check admits an impossible slate and yields a live lifetime.
        assertThrows(IllegalArgumentException::class.java) {
            DaySlate(size = Int.MAX_VALUE, live = Int.MAX_VALUE, finished = Int.MAX_VALUE)
        }
    }

    @Test
    fun emptyPastSlateDoesNotBecomePermanent() {
        // "All games are final" is vacuously true over zero games. A past day with no published
        // games - a feed gap, or a day that was never filled in - must not cache itself forever,
        // or a later backfill never appears.
        val maxAge = scoreDayMaxAge(slate = DaySlate(size = 0, finished = 0), day = DayRelation.Past)
        assertNotNull(maxAge)
        assertEquals(5.minutes, maxAge)
    }

    @Test
    fun futureDayExpiresAfterSixHours() {
        assertEquals(6.hours, scoreDayMaxAge(day = DayRelation.Future, nextStartAt = now + 3.minutes))
    }

    @Test
    fun idleTodayWithoutNextStartExpiresAfterFiveMinutes() {
        assertEquals(5.minutes, scoreDayMaxAge())
    }

    @Test
    fun nextStartInThreeMinutesExpiresInExactlyTwoMinutes() {
        assertEquals(1.minutes, FreshnessPolicy.nextStartLead)
        assertEquals(2.minutes, scoreDayMaxAge(nextStartAt = now + 3.minutes))
    }

    @Test
    fun nextStartInThirtySecondsFloorsWindowAtZero() {
        assertEquals(Duration.ZERO, scoreDayMaxAge(nextStartAt = now + 30.seconds))
    }

    @Test
    fun pastStartFloorsWindowAtZero() {
        assertEquals(Duration.ZERO, scoreDayMaxAge(nextStartAt = now - 3.minutes))
    }

    @Test
    fun distantNextStartCapsWindowAtFiveMinutes() {
        assertEquals(5.minutes, scoreDayMaxAge(nextStartAt = now + 1.hours))
    }

    @Test
    fun liveStandingsExpireAfterTenMinutes() {
        assertEquals(10.minutes, FreshnessPolicy.standingsMaxAge(anyGameLive = true))
    }

    @Test
    fun idleStandingsExpireAfterOneHour() {
        assertEquals(1.hours, FreshnessPolicy.standingsMaxAge(anyGameLive = false))
    }

    @Test
    fun seasonsExpireAfterTwentyFourHours() {
        assertEquals(24.hours, FreshnessPolicy.seasonsMaxAge())
    }

    @Test
    fun liveDetailExpiresAfterThirtySeconds() {
        assertEquals(30.seconds, FreshnessPolicy.detailMaxAge(isLive = true, isFinal = false))
    }

    @Test
    fun finalDetailExpiresAfterTwentyFourHours() {
        // A final boxscore is not immutable: the league corrects scoring the next day, so the old
        // "final means never expires" rule kept the first copy for ever. A day picks up the fix.
        val maxAge = FreshnessPolicy.detailMaxAge(isLive = false, isFinal = true)
        assertEquals(24.hours, maxAge)
        assertFalse(FreshnessPolicy.isStale(now - 1.hours, now, maxAge))
    }

    @Test
    fun scheduledDetailExpiresSoAPregameScreenRefreshesAtPuckDrop() {
        // A scheduled game is not live, but it is not final either. Treating "not live" as
        // "never changes" left a pregame detail frozen forever: the entry outlived the start,
        // and because the scheduled time precedes the puck drop by several minutes, the screen
        // stayed pregame well after the announced time.
        assertEquals(5.minutes, FreshnessPolicy.detailMaxAge(isLive = false, isFinal = false))
        assertNotNull(FreshnessPolicy.detailMaxAge(isLive = false, isFinal = false))
    }

    @Test
    fun liveDetailTakesPrecedenceOverAFinalFlag() {
        assertEquals(30.seconds, FreshnessPolicy.detailMaxAge(isLive = true, isFinal = true))
    }

    @Test
    fun exactlyAtMaxAgeIsStillFresh() {
        assertFalse(FreshnessPolicy.isStale(now - 25.seconds, now, 25.seconds))
    }

    @Test
    fun oneNanosecondPastMaxAgeIsStale() {
        assertTrue(FreshnessPolicy.isStale(now - 25.seconds - 1.nanoseconds, now, 25.seconds))
    }

    @Test
    fun nullMaxAgeNeverExpires() {
        assertFalse(FreshnessPolicy.isStale(now - 365.days, now, null))
    }

    @Test
    fun pastDayIgnoresNextStartAndIdles() {
        // A past day should be final. When it isn't - a postponement, a late result - the window
        // is idle traffic, and a stale start time on that day must NOT clip it the way today's
        // window is clipped, because the scheduled start it references already passed.
        // Asserting only 5.minutes with no nextStartAt would pass even with the branch deleted:
        // the fall-through lands on the same value. The clip is what distinguishes them.
        assertEquals(5.minutes, scoreDayMaxAge(day = DayRelation.Past, nextStartAt = now + 3.minutes))
        assertNotEquals(6.hours, scoreDayMaxAge(day = DayRelation.Past))
    }

    @Test
    fun futureDayIgnoresAnUpcomingStartTime() {
        // nextStartAt only clips today's idle window; a future day is already long-lived.
        assertEquals(6.hours, scoreDayMaxAge(day = DayRelation.Future, nextStartAt = now + 2.minutes))
    }

    private fun scoreDayMaxAge(
        slate: DaySlate = DaySlate(size = 3, finished = 0),
        day: DayRelation = DayRelation.Today,
        nextStartAt: Instant? = null,
    ): Duration? = FreshnessPolicy.scoreDayMaxAge(now, slate, day, nextStartAt)
}
