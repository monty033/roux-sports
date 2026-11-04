package net.montycasa.roux.ui.team

import net.montycasa.roux.domain.policy.LeagueMetric
import kotlin.math.roundToInt

/**
 * One metric's value as the table prints it: per-game values at two places, percentages at one place with a sign,
 * and whole numbers as they are. An absent value is the em dash, never a zero.
 */
internal fun formatLeagueValue(
    metric: LeagueMetric,
    value: Double?,
): String =
    when (metric) {
        LeagueMetric.GoalsForPerGame,
        LeagueMetric.GoalsAgainstPerGame,
        LeagueMetric.ShotsForPerGame,
        LeagueMetric.ShotsAgainstPerGame,
        -> value?.let { formatDecimal(it, 2) } ?: "—"

        LeagueMetric.FaceoffWinPct,
        LeagueMetric.PowerPlayPct,
        LeagueMetric.PenaltyKillPct,
        LeagueMetric.PointPct,
        -> formatPercentage(value)

        LeagueMetric.Points,
        LeagueMetric.TeamShutouts,
        -> formatStatNumber(value?.roundToInt())
    }
