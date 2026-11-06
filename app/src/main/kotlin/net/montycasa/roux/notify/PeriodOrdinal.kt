package net.montycasa.roux.notify

/** The ordinal used in intermission copy: 1st, 2nd, 3rd, 4th, 11th, 21st. */
internal fun periodOrdinal(period: Int): String {
    val suffix =
        when {
            period % 100 in 11..13 -> "th"
            period % 10 == 1 -> "st"
            period % 10 == 2 -> "nd"
            period % 10 == 3 -> "rd"
            else -> "th"
        }
    return "$period$suffix"
}
