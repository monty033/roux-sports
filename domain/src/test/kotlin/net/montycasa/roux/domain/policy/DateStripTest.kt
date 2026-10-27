package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.DateIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DateStripTest {
    private val index =
        listOf(
            DateIndexEntry("2026-10-26", 9),
            DateIndexEntry("2026-10-24", 5),
            DateIndexEntry("2026-10-25", 0),
        )

    @Test
    fun unsortedIndexProducesOrderedItemsWithCountsAndZeroCountDayHasNoGames() {
        val items = DateStrip.items(index, "2026-10-25", daysEitherSide = 1)
        assertEquals(listOf("2026-10-24", "2026-10-25", "2026-10-26"), items.map { it.date })
        assertEquals(listOf(5, 0, 9), items.map { it.numberOfGames })
        assertTrue(items[0].hasGames)
        assertFalse(items[1].hasGames)
        assertTrue(items[2].hasGames)
    }

    @Test
    fun nextGameDateSkipsZeroCountDaysRegardlessOfIndexOrder() {
        assertEquals("2026-10-26", DateStrip.nextGameDate(index, "2026-10-25"))
    }

    @Test
    fun nextGameDateIncludesTheRequestedDayWhenItHasGames() {
        assertEquals("2026-10-24", DateStrip.nextGameDate(index, "2026-10-24"))
    }

    @Test
    fun nextGameDateReturnsNullWhenOnlyEarlierGamesExist() {
        assertNull(DateStrip.nextGameDate(index, "2026-10-27"))
    }

    @Test
    fun seasonWithIdenticalStartAndEndYieldsExactlyOneDay() {
        assertEquals(
            listOf(DateStripItem("2026-10-25", 0)),
            DateStrip.items(index, "2026-10-25", seasonStart = "2026-10-25", seasonEnd = "2026-10-25"),
        )
    }

    @Test
    fun windowAtSeasonStartNeverIncludesEarlierDates() {
        val items = DateStrip.items(index, "2026-10-25", seasonStart = "2026-10-25", daysEitherSide = 2)
        assertEquals(listOf("2026-10-25", "2026-10-26", "2026-10-27"), items.map { it.date })
    }

    @Test
    fun windowAtSeasonEndNeverIncludesLaterDates() {
        val items = DateStrip.items(index, "2026-10-25", seasonEnd = "2026-10-25", daysEitherSide = 2)
        assertEquals(listOf("2026-10-23", "2026-10-24", "2026-10-25"), items.map { it.date })
    }

    @Test
    fun windowsEntirelyBeforeOrAfterSeasonAreEmptyRatherThanRecentered() {
        for (selectedDate in listOf("2026-10-20", "2026-10-30")) {
            assertTrue(
                DateStrip
                    .items(index, selectedDate, seasonStart = "2026-10-24", seasonEnd = "2026-10-26")
                    .isEmpty(),
            )
        }
    }

    @Test
    fun defaultSelectionUsesCallersLocalTodayWhenItHasGames() {
        val gamesToday = index + DateIndexEntry("2026-10-25", 4)
        assertEquals("2026-10-25", DateStrip.defaultSelection(gamesToday, today = "2026-10-25"))
    }

    @Test
    fun defaultSelectionMovesToNearestEarlierGameWhenItIsCloser() {
        val games = listOf(DateIndexEntry("2026-10-24", 5), DateIndexEntry("2026-10-28", 9))
        assertEquals("2026-10-24", DateStrip.defaultSelection(games, today = "2026-10-25"))
    }

    @Test
    fun defaultSelectionMovesToNearestUpcomingGameWhenItIsCloser() {
        val games = listOf(DateIndexEntry("2026-10-21", 5), DateIndexEntry("2026-10-26", 9))
        assertEquals("2026-10-26", DateStrip.defaultSelection(games, today = "2026-10-25"))
    }

    @Test
    fun defaultSelectionBreaksEqualDistanceTiesInFavorOfUpcomingGame() {
        for (games in listOf(index, index.reversed())) {
            assertEquals("2026-10-26", DateStrip.defaultSelection(games, today = "2026-10-25"))
        }
    }

    @Test
    fun defaultSelectionClampsTodayAtBothSeasonBoundaries() {
        assertEquals(
            "2026-10-24",
            DateStrip.defaultSelection(index, today = "2026-10-01", seasonStart = "2026-10-24"),
        )
        assertEquals(
            "2026-10-26",
            DateStrip.defaultSelection(index, today = "2026-11-01", seasonEnd = "2026-10-26"),
        )
    }

    @Test
    fun defaultSelectionExcludesGamesOutsideSeasonEvenWhenTheyAreCloser() {
        val games = listOf(DateIndexEntry("2026-10-24", 5), DateIndexEntry("2026-10-28", 9))
        assertEquals(
            "2026-10-28",
            DateStrip.defaultSelection(games, today = "2026-10-25", seasonStart = "2026-10-25"),
        )
        assertEquals(
            "2026-10-24",
            DateStrip.defaultSelection(games, today = "2026-10-27", seasonEnd = "2026-10-27"),
        )
    }

    @Test
    fun defaultSelectionRetainsClampedDateWhenSeasonHasNoIndexedGames() {
        assertEquals(
            "2026-10-25",
            DateStrip.defaultSelection(index, today = "2026-10-01", seasonStart = "2026-10-25", seasonEnd = "2026-10-25"),
        )
        assertEquals(
            "2026-10-25",
            DateStrip.defaultSelection(emptyList(), today = "2026-11-01", seasonEnd = "2026-10-25"),
        )
    }

    @Test
    fun dateAbsentFromIndexStillAppearsWithZeroGames() {
        val items = DateStrip.items(index, "2026-10-27", daysEitherSide = 1)
        assertEquals(listOf("2026-10-26", "2026-10-27", "2026-10-28"), items.map { it.date })
        assertEquals(DateStripItem("2026-10-27", 0), items[1])
        assertFalse(items[1].hasGames)
    }

    @Test
    fun emptyIndexProducesNoGameItemsNullNextDateAndLocalDefaultWithoutCrashing() {
        val items = DateStrip.items(emptyList(), "2026-10-25", daysEitherSide = 1)
        assertEquals(listOf("2026-10-24", "2026-10-25", "2026-10-26"), items.map { it.date })
        assertTrue(items.all { !it.hasGames && it.numberOfGames == 0 })
        assertNull(DateStrip.nextGameDate(emptyList(), "2026-10-25"))
        assertEquals("2026-10-25", DateStrip.defaultSelection(emptyList(), today = "2026-10-25"))
    }

    @Test
    fun indexContainingOnlyZeroCountsHasNoNextGameAndRetainsToday() {
        val games = listOf(DateIndexEntry("2026-10-24", 0), DateIndexEntry("2026-10-26", 0))
        assertNull(DateStrip.nextGameDate(games, "2026-10-23"))
        assertEquals("2026-10-25", DateStrip.defaultSelection(games, today = "2026-10-25"))
    }

    @Test
    fun defaultWindowHasThreeDaysEitherSideWithoutSeasonBounds() {
        assertEquals(
            (22..28).map { "2026-10-$it" },
            DateStrip.items(index, "2026-10-25").map { it.date },
        )
    }

    @Test
    fun zeroRadiusProducesOnlySelectedDate() {
        assertEquals(
            listOf(DateStripItem("2026-10-26", 9)),
            DateStrip.items(index, "2026-10-26", daysEitherSide = 0),
        )
    }

    @Test
    fun dayArithmeticAndNearestSelectionAreStableAcrossMonthAndYearBoundaries() {
        for ((center, expected) in listOf(
            "2026-10-31" to listOf("2026-10-30", "2026-10-31", "2026-11-01"),
            "2026-12-31" to listOf("2026-12-30", "2026-12-31", "2027-01-01"),
        )) {
            val games = listOf(DateIndexEntry(expected.first(), 5), DateIndexEntry(expected.last(), 9))
            assertEquals(expected, DateStrip.items(games, center, daysEitherSide = 1).map { it.date })
            assertEquals(expected.last(), DateStrip.nextGameDate(games, center))
            assertEquals(expected.last(), DateStrip.defaultSelection(games, today = center))
            assertEquals(
                listOf(center, expected.last(), expected.last().dropLast(2) + "02"),
                DateStrip.items(games, expected.last(), daysEitherSide = 1).map { it.date },
            )
        }
    }

    @Test
    fun dayArithmeticHonorsGregorianLeapYearsIncludingCenturyExceptions() {
        for ((year, februaryEnd) in listOf("2026" to "28", "2028" to "29", "1900" to "28", "2000" to "29")) {
            assertEquals(
                listOf("$year-02-$februaryEnd", "$year-03-01", "$year-03-02"),
                DateStrip.items(emptyList(), "$year-03-01", daysEitherSide = 1).map { it.date },
            )
        }
    }

    /**
     * The century rule is the one part of the calendar the day arithmetic does not exercise: stepping from
     * February 28 to March 1 is correct whether or not 1900 has a 29th, so the loop above passes even when the
     * rule is deleted. It lives in `daysInMonth`, which only validation reaches - so validation has to be the
     * thing tested, by handing it a February 29 that may not exist.
     */
    @Test
    fun februaryTwentyNinthIsValidOnlyInALeapYear() {
        for (leapDay in listOf("2028-02-29", "2000-02-29")) {
            assertEquals(
                listOf(leapDay),
                DateStrip.items(emptyList(), leapDay, daysEitherSide = 0).map { it.date },
            )
        }

        for (notALeapDay in listOf("1900-02-29", "2100-02-29", "2026-02-29")) {
            assertThrows(IllegalArgumentException::class.java) {
                DateStrip.items(emptyList(), notALeapDay)
            }
        }
    }

    @Test
    fun windowStaysWithinFourDigitDateRange() {
        assertEquals(
            listOf("0001-01-01", "0001-01-02"),
            DateStrip.items(emptyList(), "0001-01-01", daysEitherSide = 1).map { it.date },
        )
        assertEquals(
            listOf("9999-12-30", "9999-12-31"),
            DateStrip.items(emptyList(), "9999-12-31", daysEitherSide = 1).map { it.date },
        )
    }

    @Test
    fun repeatedDateUsesLargestCountRegardlessOfFeedOrder() {
        val games = index + DateIndexEntry("2026-10-26", 0)
        for (entries in listOf(games, games.reversed())) {
            assertEquals(listOf(DateStripItem("2026-10-26", 9)), DateStrip.items(entries, "2026-10-26", daysEitherSide = 0))
        }
    }

    @Test
    fun negativeWindowRadiusIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DateStrip.items(index, "2026-10-25", daysEitherSide = -1)
        }
    }

    @Test
    fun reversedSeasonBoundsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DateStrip.items(index, "2026-10-25", seasonStart = "2026-10-26", seasonEnd = "2026-10-24")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DateStrip.defaultSelection(index, "2026-10-25", seasonStart = "2026-10-26", seasonEnd = "2026-10-24")
        }
    }
}
