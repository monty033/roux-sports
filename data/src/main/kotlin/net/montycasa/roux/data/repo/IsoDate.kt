package net.montycasa.roux.data.repo

/**
 * Shifts an ISO `YYYY-MM-DD` date by a whole number of days.
 *
 * Paging is one day at a time and `:data` has no date library, so the arithmetic is done here on
 * proleptic-Gregorian day numbers rather than by adding to the day-of-month. String manipulation
 * would be wrong at every month boundary and in February, and the neighbourhood this feeds is the
 * exact place a wrong date would be cached under the wrong key.
 *
 * The algorithms are the standard civil-from-days pair (Howard Hinnant's `days_from_civil` and
 * `civil_from_days`), which are exact over any year a four-digit date can express.
 */
internal object IsoDate {
    private val PATTERN = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")

    fun shift(
        date: String,
        days: Int,
    ): String {
        val match =
            requireNotNull(PATTERN.matchEntire(date)) {
                "date must be ISO YYYY-MM-DD: $date"
            }
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        require(month in 1..12) { "month out of range: $date" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $date" }

        val (y, m, d) = civilFromDays(daysFromCivil(year, month, day) + days)
        return buildString {
            append(y.toString().padStart(4, '0'))
            append('-')
            append(m.toString().padStart(2, '0'))
            append('-')
            append(d.toString().padStart(2, '0'))
        }
    }

    private fun daysInMonth(
        year: Int,
        month: Int,
    ): Int =
        when (month) {
            2 -> if (isLeap(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    private fun isLeap(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

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
