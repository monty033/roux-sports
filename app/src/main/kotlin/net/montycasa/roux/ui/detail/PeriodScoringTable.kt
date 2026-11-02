@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.Composable
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.PeriodScoring

/**
 * The game's goals as a grid: one column per period, then the total.
 *
 * A completed game is read across - both clubs, every period, the official score - which is why this is a table and not
 * a list of "period: 2-1" lines. Overtime earns a column only when the game had one, so a regulation result is not
 * padded with a column of dashes. All overtime periods share one cell; the total comes from the game score.
 */
@Composable
internal fun PeriodScoringTable(
    game: Game,
    landing: GameLanding,
) {
    val periods = landing.periodScoring.sortedBy { it.period }
    val hasOvertime = periods.any { it.period > 3 }
    val periodCount = if (hasOvertime) 4 else 3

    PeriodGrid(
        tags = SCORING_TAGS,
        periodCount = periodCount,
        rows =
            listOf(
                PeriodGridRow(
                    team = game.away,
                    values = goalValues(periods, periodCount) { it.awayGoals },
                    total = game.awayScore,
                ),
                PeriodGridRow(
                    team = game.home,
                    values = goalValues(periods, periodCount) { it.homeGoals },
                    total = game.homeScore,
                ),
            ),
    )
}

/** One value per drawn column; null for a period that did not arrive. Overtime is the sum of every late period. */
private fun goalValues(
    periods: List<PeriodScoring>,
    periodCount: Int,
    pick: (PeriodScoring) -> Int,
): List<Int?> =
    (1..periodCount).map { period ->
        if (period == 4) {
            periods.filter { it.period > 3 }.takeIf { it.isNotEmpty() }?.sumOf(pick)
        } else {
            periods.firstOrNull { it.period == period }?.let(pick)
        }
    }

private val SCORING_TAGS =
    PeriodGridTags(
        table = "detail.scoring.table",
        headerPrefix = "detail.period.header",
        cellPrefix = "detail.period",
        crestPrefix = "detail.scoring.crest",
        logoPrefix = "detail.logo.scoring",
    )
