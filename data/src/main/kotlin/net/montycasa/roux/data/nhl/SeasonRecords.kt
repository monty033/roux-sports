package net.montycasa.roux.data.nhl

import net.montycasa.roux.domain.model.SeasonRecord

/**
 * Reads the feed's `W-L-OTL` record string, such as `10-6-2`.
 *
 * Returns null unless there are exactly three non-negative whole numbers. A record that does not parse is left off the
 * page rather than printed with a guessed overtime count.
 */
internal fun String?.toSeasonRecord(): SeasonRecord? {
    val parts = this?.trim()?.split('-') ?: return null
    if (parts.size != 3) return null
    val wins = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val losses = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val overtimeLosses = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return SeasonRecord(wins = wins, losses = losses, overtimeLosses = overtimeLosses)
}
