package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PollPolicyTest {
    private val now = Instant.parse("2026-10-26T16:00:00Z")
    private val today = "2026-10-26"

    @Test
    fun liveGameOnTodayPollsEvery25Seconds() {
        assertEquals(25.seconds, delay(game(GameState.Live())))
    }

    @Test
    fun preGamePollsEvery60Seconds() {
        assertEquals(60.seconds, delay(game(GameState.Pre)))
    }

    @Test
    fun scheduledStartExactlyNineMinutesAwayWarms() {
        assertEquals(60.seconds, delay(game(start = now + 9.minutes)))
    }

    @Test
    fun scheduledStartJustBeyondNineMinutesFallsThrough() {
        assertEquals(5.minutes, delay(game(start = now + 9.minutes + 1.nanoseconds)))
    }

    @Test
    fun scheduledStartTenMinutesAwayOnTodayIdles() {
        assertEquals(5.minutes, delay(game(start = now + 10.minutes)))
    }

    @Test
    fun nextStartThreeHoursAwayOnTodayIdles() {
        assertEquals(5.minutes, delay(game(start = now + 3.hours)))
    }

    @Test
    fun pastDateWithEveryGameFinalDoesNotPoll() {
        val games = listOf(game(GameState.Final), game(GameState.Final))
        assertNull(PollPolicy.nextDelay(now, "2026-10-25", today, games))
    }

    @Test
    fun emptyDayDoesNotPollEvenToday() {
        assertNull(PollPolicy.nextDelay(now, today, today, emptyList()))
    }

    @Test
    fun scheduledStartTenMinutesAgoWarms() {
        assertEquals(60.seconds, delay(game(start = now - 10.minutes)))
    }

    @Test
    fun scheduledStartOneHourAgoOnTodayIdles() {
        assertEquals(5.minutes, delay(game(start = now - 1.hours)))
    }

    @Test
    fun scheduledStartOneHourAgoOnPastDateDoesNotPoll() {
        assertNull(PollPolicy.nextDelay(now, "2026-10-25", today, listOf(game(start = now - 1.hours))))
    }

    @Test
    fun scheduledStartExactlyTwentyMinutesAgoWarmsButJustOutsideIdles() {
        assertEquals(60.seconds, delay(game(start = now - 20.minutes)))
        assertEquals(5.minutes, delay(game(start = now - 20.minutes - 1.seconds)))
    }

    @Test
    fun scheduledStartExactlyNowWarms() {
        assertEquals(60.seconds, delay(game(start = now)))
    }

    @Test
    fun scheduledNullStartOnTodayIdles() {
        assertEquals(5.minutes, delay(game(start = null)))
    }

    @Test
    fun unknownStateWithRunningClockPollsEvery25SecondsOnAnyDate() {
        val game = game(GameState.Unknown("unreadable"), period = PeriodInfo(2, "REG", "12:34", running = true))
        for (date in listOf(today, "2026-10-25", "2026-10-29")) {
            assertEquals(25.seconds, PollPolicy.nextDelay(now, date, today, listOf(game)))
        }
    }

    @Test
    fun unknownStateWithStoppedClockAndStartFiveMinutesAgoWarms() {
        val game = game(GameState.Unknown("unreadable"), now - 5.minutes, PeriodInfo(4, "OT", "01:16", running = false))
        assertEquals(60.seconds, delay(game))
    }

    @Test
    fun unknownStateWithStoppedClockAndStartThreeHoursAgoOnTodayWarms() {
        val game = game(GameState.Unknown("unreadable"), now - 3.hours, PeriodInfo(4, "OT", "01:16", running = false))
        assertEquals(60.seconds, delay(game))
    }

    @Test
    fun unknownStateWithStoppedClockAndStartThirtyMinutesAgoWarms() {
        val game = game(GameState.Unknown("unreadable"), now - 30.minutes, PeriodInfo(2, "REG", "00:00", running = false))
        assertEquals(60.seconds, delay(game))
    }

    @Test
    fun unknownStateWithStoppedClockAndStartFiveHoursAgoOnTodayIdles() {
        val game = game(GameState.Unknown("unreadable"), now - 5.hours, PeriodInfo(4, "OT", "01:16", running = false))
        assertEquals(5.minutes, delay(game))
    }

    @Test
    fun unknownStateWithStoppedClockAndStartFiveHoursAgoOnPastDateDoesNotPoll() {
        val game = game(GameState.Unknown("unreadable"), now - 5.hours, PeriodInfo(4, "OT", "01:16", running = false))
        assertNull(PollPolicy.nextDelay(now, "2026-10-25", today, listOf(game)))
    }

    @Test
    fun unknownTerminalStateWithStaleRunningClockOnTodayIdles() {
        val game =
            game(
                GameState.Unknown("unreadable"),
                now - 1.hours,
                PeriodInfo(3, "REG", "00:00", running = true),
                finalKind = FinalKind.REG,
            )
        assertEquals(5.minutes, delay(game))
    }

    @Test
    fun unknownTerminalStateWithStoppedClockAndStartSixtyMinutesAgoOnTodayIdles() {
        val game =
            game(
                GameState.Unknown("unreadable"),
                now - 60.minutes,
                PeriodInfo(4, "OT", "01:16", running = false),
                finalKind = FinalKind.OT,
            )
        assertEquals(5.minutes, delay(game))
    }

    @Test
    fun unknownStateWithNullRunningClockOnTodayIdles() {
        val game = game(GameState.Unknown("unreadable"), period = PeriodInfo(4, "OT", "01:16", running = null))
        assertEquals(5.minutes, delay(game))
    }

    @Test
    fun finalGameWithStoppedClockOnTodayIdles() {
        val game = game(GameState.Final, period = PeriodInfo(4, "OT", "01:16", running = false))
        assertEquals(5.minutes, delay(game))
    }

    @Test
    fun finalGameWithStoppedClockOnPastDateDoesNotPoll() {
        val game = game(GameState.Final, period = PeriodInfo(4, "OT", "01:16", running = false))
        assertNull(PollPolicy.nextDelay(now, "2026-10-25", today, listOf(game)))
    }

    @Test
    fun finalGameWithRunningClockOnPastDateDoesNotPoll() {
        val game = game(GameState.Final, now - 1.hours, PeriodInfo(3, "REG", "00:00", running = true))
        assertNull(PollPolicy.nextDelay(now, "2026-10-25", today, listOf(game)))
    }

    @Test
    fun terminalPregameAndScheduledGamesDoNotWarm() {
        for (state in listOf(GameState.Pre, GameState.Scheduled)) {
            val game = game(state, now, finalKind = FinalKind.REG)
            assertEquals(5.minutes, delay(game))
            assertNull(PollPolicy.nextDelay(now, "2026-10-25", today, listOf(game)))
        }
    }

    @Test
    fun unknownStateWithoutPeriodWithStartFiveMinutesAwayWarms() {
        assertEquals(60.seconds, delay(game(GameState.Unknown("unreadable"), now + 5.minutes)))
    }

    @Test
    fun unknownStateWithoutPeriodOrStartOnTodayIdles() {
        assertEquals(5.minutes, delay(game(GameState.Unknown("unreadable"))))
    }

    @Test
    fun unknownStateWithoutPeriodUsesBoundedStartWindow() {
        val state = GameState.Unknown("unreadable")
        assertEquals(60.seconds, delay(game(state, now - 4.hours)))
        assertEquals(5.minutes, delay(game(state, now - 4.hours - 1.seconds)))
        assertEquals(60.seconds, delay(game(state, now + 9.minutes)))
        assertEquals(5.minutes, delay(game(state, now + 10.minutes)))
    }

    @Test
    fun futureDateWithOnlyScheduledGamesThreeDaysOutDoesNotPoll() {
        val games = listOf(game(start = now + 3.days), game(start = now + 3.days + 1.hours))
        assertNull(PollPolicy.nextDelay(now, "2026-10-29", today, games))
    }

    @Test
    fun liveTakesPrecedenceOverWarmingRegardlessOfOrderAndDate() {
        val live = game(GameState.Live(critical = true))
        val pre = game(GameState.Pre)
        for (games in listOf(listOf(pre, live), listOf(live, pre))) {
            assertEquals(25.seconds, PollPolicy.nextDelay(now, "2026-10-25", today, games))
        }
    }

    @Test
    fun preAndImminentStartsWarmOnOtherDates() {
        for (game in listOf(game(GameState.Pre), game(start = now + 1.minutes))) {
            assertEquals(60.seconds, PollPolicy.nextDelay(now, "2026-10-27", today, listOf(game)))
        }
    }

    @Test
    fun unreadableStateOrStartOnOtherDateDoesNotPoll() {
        val games = listOf(game(GameState.Unknown("?")), game(start = null))
        assertNull(PollPolicy.nextDelay(now, "2026-10-27", today, games))
    }

    @Test
    fun backoffWithIdentityJitterMatchesExactSequence() {
        assertEquals(
            listOf(25, 50, 100, 200, 300, 300).map { it.seconds },
            (1..6).map { PollPolicy.backoff(it) { 0.5 } },
        )
    }

    @Test
    fun backoffWithMinimumJitterAppliesExactMultiplier() {
        assertEquals(22.5.seconds, PollPolicy.backoff(1) { 0.0 })
        assertEquals(90.seconds, PollPolicy.backoff(3) { 0.0 })
    }

    @Test
    fun backoffWithMidpointJitterReturnsExactBase() {
        assertEquals(25.seconds, PollPolicy.backoff(1) { 0.5 })
        assertEquals(100.seconds, PollPolicy.backoff(3) { 0.5 })
    }

    @Test
    fun backoffWithMaximumJitterAppliesExactMultiplier() {
        assertEquals(27.5.seconds, PollPolicy.backoff(1) { 1.0 })
        assertEquals(110.seconds, PollPolicy.backoff(3) { 1.0 })
    }

    @Test
    fun backoffWithMaximumJitterCapsAfterMultiplier() {
        assertEquals(5.minutes, PollPolicy.backoff(6) { 1.0 })
    }

    @Test
    fun jitterStaysWithinTenPercentAndFiveMinuteCap() {
        val bases = listOf(25, 50, 100, 200, 300, 300).map { it.seconds }
        for (jitter in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            for ((index, base) in bases.withIndex()) {
                val actual = PollPolicy.backoff(index + 1) { jitter }
                val context = "jitter=$jitter attempt=${index + 1}: $actual"
                assertTrue(context, actual >= base * 0.9)
                assertTrue(context, actual <= base * 1.1)
                assertTrue(context, actual <= 5.minutes)
            }
        }
    }

    @Test
    fun nonpositiveAttemptsBehaveAsFirstAttempt() {
        for (attempt in listOf(0, -3, Int.MIN_VALUE)) {
            for (jitter in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                assertEquals(PollPolicy.backoff(1) { jitter }, PollPolicy.backoff(attempt) { jitter })
            }
        }
    }

    @Test
    fun largestAttemptSaturatesWithoutOverflow() {
        assertEquals(5.minutes, PollPolicy.backoff(Int.MAX_VALUE) { 0.5 })
    }

    @Test
    fun liveStateWithTerminalKindDoesNotPollAtLiveCadence() {
        // A contradictory payload: the state says Live while gameOutcome says it ended.
        // Believing the state token would poll at 25s for as long as the feed kept saying it.
        val game =
            game(
                GameState.Live(),
                now - 1.hours,
                PeriodInfo(3, "REG", "00:00", running = false),
                finalKind = FinalKind.REG,
            )
        assertEquals(5.minutes, delay(game))
    }

    private fun delay(game: Game): Duration? = PollPolicy.nextDelay(now, today, today, listOf(game))

    private fun game(
        state: GameState = GameState.Scheduled,
        start: Instant? = null,
        period: PeriodInfo? = null,
        finalKind: FinalKind? = null,
    ): Game =
        Game(
            id = GameId("1"),
            date = today,
            state = state,
            home = TeamRef(TeamKey("PHI")),
            away = TeamRef(TeamKey("ANA")),
            startTimeUtc = start,
            period = period,
            finalKind = finalKind,
        )
}
