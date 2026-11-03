package net.montycasa.roux.ui.team

import kotlin.math.round

/** An absent number prints as the em dash; a real number prints as itself. */
internal fun formatStatNumber(value: Int?): String = value?.toString() ?: "—"

/** Seconds as `M:SS`; 958.7857 prints `15:58`, and a real 0 prints `0:00`. */
internal fun formatStatsIceTime(seconds: Double?): String = seconds?.let { formatStatsIceTime(it.toLong()) } ?: "—"

/** The same value as a whole number of seconds. */
internal fun formatStatsIceTime(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "—"
    val minutes = seconds / 60
    val rest = seconds % 60
    return "$minutes:${rest.toString().padStart(2, '0')}"
}

/** An alias for callers that name the column rather than the unit. */
internal fun formatTimeOnIce(seconds: Double?): String = formatStatsIceTime(seconds)

/** The same value as a whole number of seconds. */
internal fun formatTimeOnIce(seconds: Long?): String = formatStatsIceTime(seconds)

/** An alias matching the game page's existing name for the same formatting. */
internal fun formatIceTime(seconds: Double?): String = formatStatsIceTime(seconds)

/** The same value as a whole number of seconds. */
internal fun formatIceTime(seconds: Long?): String = formatStatsIceTime(seconds)

/** A shooting fraction printed as a one-decimal percentage: 0.1 is `10.0%`. */
internal fun formatShootingPercentage(fraction: Double?): String = fraction?.let { formatDecimal(it * 100.0, 1) + "%" } ?: "—"

/** An alias using the feed's spelling. */
internal fun formatShootingPctg(fraction: Double?): String = formatShootingPercentage(fraction)

/** A generic name for the same one-decimal percentage. */
internal fun formatPercentage(fraction: Double?): String = formatShootingPercentage(fraction)

/** A save fraction printed the way hockey prints it: 0.869565 is `.870`, and 0.0 is `.000`. */
internal fun formatSavePercentage(fraction: Double?): String {
    if (fraction == null) return "—"
    val text = formatDecimal(fraction, 3)
    return if (text.startsWith("0.")) text.substring(1) else text
}

/** An alias using the feed's spelling. */
internal fun formatSavePctg(fraction: Double?): String = formatSavePercentage(fraction)

/** An alias for the column label. */
internal fun formatSvPercentage(fraction: Double?): String = formatSavePercentage(fraction)

/** A goals-against average with two decimals: 3.0 is `3.00`, a real 0.0 is `0.00`. */
internal fun formatGoalsAgainstAverage(value: Double?): String = value?.let { formatDecimal(it, 2) } ?: "—"

/** An alias for the column label. */
internal fun formatGaa(value: Double?): String = formatGoalsAgainstAverage(value)

/** A signed plus/minus: `+5`, `-3`, and `0` rather than `+0`; absent prints the em dash. */
internal fun formatPlusMinus(value: Int?): String =
    when {
        value == null -> "—"
        value > 0 -> "+$value"
        else -> value.toString()
    }

private fun formatDecimal(
    value: Double,
    decimals: Int,
): String {
    val scale =
        when (decimals) {
            1 -> 10.0
            2 -> 100.0
            3 -> 1000.0
            else -> 1.0
        }
    val rounded = round(value * scale) / scale
    val text = rounded.toString()
    val dot = text.indexOf('.')
    if (dot < 0) return text + "." + "0".repeat(decimals)
    val have = text.length - dot - 1
    return if (have >= decimals) text.substring(0, dot + 1 + decimals) else text + "0".repeat(decimals - have)
}
