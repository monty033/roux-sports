package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.DateIndexEntry
import kotlin.math.abs

/** One calendar day in the strip; an absent feed entry has a count of zero. */
data class DateStripItem(
    val date: String,
    val numberOfGames: Int,
) {
    /** A published zero-count day must remain visible without advertising games. */
    val hasGames: Boolean get() = numberOfGames > 0
}

/**
 * Pure date navigation using ISO YYYY-MM-DD dates and the caller's local today, without a clock.
 * Calendar gaps stay visible so fans can distinguish an off day from a missing navigation item.
 * Dates must be valid Gregorian dates in years 0001..9999; non-null season bounds must be ordered.
 */
object DateStrip {
    /**
     * An inclusive, ordered window around [selectedDate], intersected with the season bounds.
     * Three days either side gives a week of context without an unwieldy strip; callers may vary it.
     * Clipping does not recenter the window: a window entirely outside the season is empty.
     * Null bounds are unbounded within the four-digit date range. Missing days have zero games.
     * If the feed repeats a date, its largest advertised count wins regardless of input order.
     */
    fun items(
        dateIndex: List<DateIndexEntry>,
        selectedDate: String,
        seasonStart: String? = null,
        seasonEnd: String? = null,
        daysEitherSide: Int = 3,
    ): List<DateStripItem> {
        require(daysEitherSide >= 0) { "daysEitherSide must be non-negative" }
        requireOrderedSeason(seasonStart, seasonEnd)
        val selectedDay = dayNumber(selectedDate)
        val first = maxOf(selectedDay - daysEitherSide, seasonStart?.let(::dayNumber) ?: firstSupportedDay)
        val last = minOf(selectedDay + daysEitherSide, seasonEnd?.let(::dayNumber) ?: lastSupportedDay)
        val counts = dateIndex.groupBy { it.date }.mapValues { (_, entries) -> entries.maxOf { it.numberOfGames } }
        return (first..last).map { day ->
            val date = dateFromDay(day)
            DateStripItem(date, counts[date] ?: 0)
        }
    }

    /** Earliest indexed date at or after [fromDate] with a positive count, independent of feed order. */
    fun nextGameDate(
        dateIndex: List<DateIndexEntry>,
        fromDate: String,
    ): String? = dateIndex.asSequence().filter { it.numberOfGames > 0 && it.date >= fromDate }.minOfOrNull { it.date }

    /**
     * Clamp [today] into the season, then choose the nearest indexed game day within that season.
     * Equal distances prefer the upcoming day: a fan wants the next game. If no in-season games
     * are indexed, retain the clamped local date so an empty or incomplete feed is still navigable.
     */
    fun defaultSelection(
        dateIndex: List<DateIndexEntry>,
        today: String,
        seasonStart: String? = null,
        seasonEnd: String? = null,
    ): String {
        requireOrderedSeason(seasonStart, seasonEnd)
        val clamped = today.coerceIn(seasonStart, seasonEnd)
        val targetDay = dayNumber(clamped)
        return dateIndex
            .asSequence()
            .filter {
                it.numberOfGames > 0 &&
                    (seasonStart == null || it.date >= seasonStart) &&
                    (seasonEnd == null || it.date <= seasonEnd)
            }.map { it.date }
            .minWithOrNull(compareBy<String> { abs(dayNumber(it) - targetDay) }.thenByDescending { it })
            ?: clamped
    }

    private fun requireOrderedSeason(
        seasonStart: String?,
        seasonEnd: String?,
    ) {
        require(seasonStart == null || seasonEnd == null || seasonStart <= seasonEnd) {
            "seasonStart must not follow seasonEnd"
        }
    }

    // Duplicated from :data's repo/IsoDate.kt because :domain cannot depend on :data;
    // consolidating these helpers is a later cleanup. Howard Hinnant's days_from_civil /
    // civil_from_days pair: https://howardhinnant.github.io/date_algorithms.html
    // Day numbers make both iteration and nearest-day distances correct across calendar boundaries.
    private val datePattern = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")
    private val firstSupportedDay = daysFromCivil(1, 1, 1)
    private val lastSupportedDay = daysFromCivil(9999, 12, 31)

    private fun dayNumber(date: String): Long {
        val match = requireNotNull(datePattern.matchEntire(date)) { "date must be ISO YYYY-MM-DD: $date" }
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        require(year in 1..9999) { "year out of range: $date" }
        require(month in 1..12) { "month out of range: $date" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $date" }
        return daysFromCivil(year, month, day)
    }

    private fun dateFromDay(days: Long): String {
        val (year, month, day) = civilFromDays(days)
        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(day.toString().padStart(2, '0'))
        }
    }

    private fun daysInMonth(
        year: Int,
        month: Int,
    ): Int =
        when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    private fun daysFromCivil(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val y = (year - if (month <= 2) 1 else 0).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097 + dayOfEra - 719468
    }

    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val dayOfEra = z - era * 146097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
        val year = yearOfEra + era * 400
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthProbe = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * monthProbe + 2) / 5 + 1
        val month = monthProbe + if (monthProbe < 10) 3 else -9
        val correctedYear = year + if (month <= 2) 1 else 0
        return Triple(correctedYear.toInt(), month.toInt(), day.toInt())
    }
}
