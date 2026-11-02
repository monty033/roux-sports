@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.theme.LocalRouxAccents

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

    Column(Modifier.testTag("detail.scoring.table")) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // The crest and its gap, then the same flexible space the club names take, so each heading is centred
            // over its own column.
            Spacer(Modifier.width(SCORING_CREST + 6.dp))
            Spacer(Modifier.weight(1f).padding(end = 8.dp))
            (1..periodCount).forEach { period ->
                val label = if (period == 4) "OT" else period.toString()
                PeriodCell(label, header = true, tag = "detail.period.header.$label")
            }
            PeriodCell("T", header = true, tag = "detail.period.header.T")
        }
        TeamGoalRow(
            team = game.away,
            periods = periods,
            periodCount = periodCount,
            pick = { it.awayGoals },
            total = game.awayScore,
        )
        TeamGoalRow(
            team = game.home,
            periods = periods,
            periodCount = periodCount,
            pick = { it.homeGoals },
            total = game.homeScore,
        )
    }
}

@Composable
private fun PeriodCell(
    text: String,
    header: Boolean = false,
    tag: String? = null,
) {
    Text(
        text,
        modifier = Modifier.width(PERIOD_COLUMN.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        textAlign = TextAlign.Center,
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
        color = if (header) LocalRouxAccents.current.muted else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TeamGoalRow(
    team: TeamRef,
    periods: List<PeriodScoring>,
    periodCount: Int,
    pick: (PeriodScoring) -> Int,
    total: Int?,
) {
    val name = TeamNames.cityAndName(team).second
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.testTag("detail.scoring.crest.${team.abbrev}")) {
            TeamLogo(team = team, crestSize = SCORING_CREST, imageTag = "detail.logo.scoring.${team.abbrev}")
        }
        Spacer(Modifier.width(6.dp))
        Text(
            // The club's own name, spelled out: an abbreviation in the scoring grid makes the reader translate it back.
            name,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        (1..periodCount).forEach { period ->
            val entries =
                if (period == 4) periods.filter { it.period > 3 } else listOfNotNull(periods.firstOrNull { it.period == period })
            PeriodCell(
                text = if (entries.isEmpty()) "—" else entries.sumOf(pick).toString(),
                tag = "detail.period.${team.abbrev}.$period",
            )
        }
        PeriodCell(total?.toString() ?: "—", tag = "detail.period.${team.abbrev}.T")
    }
}

private val SCORING_CREST = 24.dp
private const val PERIOD_COLUMN = 40
