@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.policy.SeriesLine
import net.montycasa.roux.domain.policy.SeriesStanding
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The playoff series line: the round and game number when there are any, then the standing. The round is absent on the
 * right-rail path, and the game number is absent only when the feed did not send one.
 */
internal fun seriesText(line: SeriesLine): String {
    val standing =
        when (val standing = line.standing) {
            is SeriesStanding.Leads -> "${standing.abbrev} leads ${standing.wins}–${standing.losses}"
            is SeriesStanding.Tied -> "Series tied ${standing.wins}–${standing.wins}"
            is SeriesStanding.Wins -> "${standing.abbrev} wins ${standing.wins}–${standing.losses}"
        }
    return (listOfNotNull(line.round, line.gameNumber?.let { "Game $it" }) + standing).joinToString(" · ")
}

internal fun heroStatus(
    game: Game,
    zone: ZoneId,
): String {
    val state = game.state
    return when (state) {
        GameState.Final -> {
            when (game.finalKind) {
                // Two or more overtime periods name their count, as the feed gave it; a single one stays Final/OT.
                FinalKind.OT -> game.otPeriods?.takeIf { it >= 2 }?.let { "Final/${it}OT" } ?: "Final/OT"

                FinalKind.SO -> "Final/SO"

                else -> "Final"
            }
        }

        is GameState.Live -> {
            game.period?.let { period ->
                val label =
                    when {
                        period.ordinal > 3 -> "OT"
                        period.ordinal == 1 -> "1st"
                        period.ordinal == 2 -> "2nd"
                        period.ordinal == 3 -> "3rd"
                        else -> "Live"
                    }
                if (period.inIntermission) "$label INT" else listOfNotNull(label, period.clock).joinToString(" ")
            } ?: "Live"
        }

        GameState.Pre -> {
            "Pregame"
        }

        is GameState.Unknown -> {
            state.raw
        }

        GameState.Scheduled -> {
            game.startTimeUtc?.let { start ->
                val local =
                    java.time.Instant
                        .ofEpochSecond(start.epochSeconds, start.nanosecondsOfSecond.toLong())
                        .atZone(zone)
                "Puck drops " + local.format(DateTimeFormatter.ofPattern("h:mma")).lowercase()
            } ?: "Scheduled"
        }
    }
}
