package net.montycasa.roux.data.nhl

import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.policy.OpeningNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three captured blocks are the real 2026 opening-night walk: a preseason week, the week that
 * carries 29 September, and the regular-season week itself.
 */
class ScheduleAdapterTest {
    private val capture = "2026-11-06"

    @Test
    fun thePreseasonBlockCarriesItsNextDateAndNoRegularSeasonGame() {
        val block = parse("schedule-2026-09-19.json")

        assertEquals("2026-09-26", block.nextStartDate)
        assertEquals(7, block.days.size)
        assertTrue(block.days.all { day -> day.gameTypes.isNotEmpty() && day.gameTypes.all { it == 1 } })
        assertNull(OpeningNight.from(listOf(block)))
    }

    @Test
    fun theMiddleBlockCarriesTheFirstRegularSeasonDate() {
        val block = parse("schedule-2026-09-26.json")

        assertEquals("2026-10-03", block.nextStartDate)
        assertTrue(2 in block.days.single { it.date == "2026-09-29" }.gameTypes)
        assertEquals("2026-09-29", OpeningNight.from(listOf(block)))
    }

    @Test
    fun theRegularSeasonBlockIsEntirelyGameTypeTwo() {
        val block = parse("schedule-2026-09-29.json")

        assertEquals("2026-10-06", block.nextStartDate)
        assertEquals(7, block.days.size)
        assertTrue(block.days.all { day -> 2 in day.gameTypes })
        assertEquals("2026-09-29", OpeningNight.from(listOf(block)))
    }

    @Test
    fun theTwoHopWalkAcrossTheCapturedBlocksFindsTheTwentyNinth() {
        val blocks =
            listOf(
                parse("schedule-2026-09-19.json"),
                parse("schedule-2026-09-26.json"),
            )

        assertEquals("2026-09-29", OpeningNight.from(blocks))
    }

    @Test
    fun aMissingGameWeekIsAFailure() {
        val result = NhlAdapter.parseSchedule("""{"nextStartDate":"2026-09-26"}""")

        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("gameWeek"))
    }

    @Test
    fun malformedJsonIsAFailure() {
        listOf("{not json", "null", "[]", "\"bare string\"", "").forEach { json ->
            val result = NhlAdapter.parseSchedule(json)

            assertTrue("Malformed '$json' must fail: $result", result is ParseResult.Failure)
            assertTrue((result as ParseResult.Failure).reason.isNotBlank())
        }
    }

    private fun parse(name: String) = (NhlAdapter.parseSchedule(Fixtures.text(capture, name)) as ParseResult.Success).value
}
