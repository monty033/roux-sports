@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.Composable
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.PeriodShots

/**
 * The game's shots as the same grid the goals use: one column per period, then the total.
 *
 * The `T` column is the published [GameRightRail.shots], never the sum of the cells. A feed that omits a period would
 * otherwise leave the card claiming fewer shots than the game had, and the two statements on the same card would
 * disagree. A period that did not arrive is an em dash, not a zero.
 */
@Composable
internal fun ShotsByPeriodTable(
    game: Game,
    rail: GameRightRail,
) {
    val periods = rail.shotsByPeriod.sortedBy { it.period }
    val hasOvertime = periods.any { it.period > 3 }
    val periodCount = if (hasOvertime) 4 else 3

    PeriodGrid(
        tags = SHOTS_TAGS,
        periodCount = periodCount,
        rows =
            listOf(
                PeriodGridRow(
                    team = game.away,
                    values = shotValues(periods, periodCount) { it.away },
                    total = rail.shots?.awayShots,
                ),
                PeriodGridRow(
                    team = game.home,
                    values = shotValues(periods, periodCount) { it.home },
                    total = rail.shots?.homeShots,
                ),
            ),
    )
}

/** One value per drawn column; null for a period that did not arrive. Overtime is the sum of every late period. */
private fun shotValues(
    periods: List<PeriodShots>,
    periodCount: Int,
    pick: (PeriodShots) -> Int,
): List<Int?> =
    (1..periodCount).map { period ->
        if (period == 4) {
            periods.filter { it.period > 3 }.takeIf { it.isNotEmpty() }?.sumOf(pick)
        } else {
            periods.firstOrNull { it.period == period }?.let(pick)
        }
    }

private val SHOTS_TAGS =
    PeriodGridTags(
        table = "detail.shots.table",
        headerPrefix = "detail.shots.header",
        cellPrefix = "detail.shots",
        crestPrefix = "detail.shots.crest",
        logoPrefix = "detail.logo.shots",
    )
