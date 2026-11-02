@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun heroStatus(
    game: Game,
    zone: ZoneId,
): String {
    val state = game.state
    return when (state) {
        GameState.Final -> {
            when (game.finalKind) {
                FinalKind.OT -> "Final/OT"
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
