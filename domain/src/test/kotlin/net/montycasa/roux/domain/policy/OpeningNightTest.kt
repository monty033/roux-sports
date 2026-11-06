package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ScheduleBlock
import net.montycasa.roux.domain.model.ScheduleDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Opening night is a regular-season fact, not the first game the schedule happens to list. The
 * 2026 fixture walk starts on a preseason day, so choosing the first date with any game would name
 * the wrong night and send a fan to an exhibition.
 */
class OpeningNightTest {
    @Test
    fun theFirstRegularSeasonDateWinsOverAnEarlierPreseasonDate() {
        val blocks =
            listOf(
                ScheduleBlock(
                    days =
                        listOf(
                            ScheduleDay("2026-09-19", listOf(1)),
                            ScheduleDay("2026-09-26", listOf(1)),
                            ScheduleDay("2026-09-29", listOf(2)),
                        ),
                ),
            )

        assertEquals("2026-09-29", OpeningNight.from(blocks))
    }

    @Test
    fun theEarliestRegularSeasonDateWinsAcrossBlocksRegardlessOfBlockOrder() {
        val blocks =
            listOf(
                ScheduleBlock(days = listOf(ScheduleDay("2026-10-01", listOf(2)))),
                ScheduleBlock(days = listOf(ScheduleDay("2026-09-30", listOf(2)))),
            )

        assertEquals("2026-09-30", OpeningNight.from(blocks))
    }

    @Test
    fun preseasonOnlyBlocksHaveNoOpeningNight() {
        val blocks =
            listOf(
                ScheduleBlock(days = listOf(ScheduleDay("2026-09-19", listOf(1)), ScheduleDay("2026-09-26", listOf(1)))),
            )

        assertNull(OpeningNight.from(blocks))
    }

    @Test
    fun aDayWithBothPreseasonAndRegularSeasonGamesCountsForTheRegularSeason() {
        val blocks = listOf(ScheduleBlock(days = listOf(ScheduleDay("2026-09-29", listOf(1, 2)))))

        assertEquals("2026-09-29", OpeningNight.from(blocks))
    }

    @Test
    fun noBlocksMeansUnknown() {
        assertNull(OpeningNight.from(emptyList()))
        assertNull(OpeningNight.from(listOf(ScheduleBlock())))
    }
}
