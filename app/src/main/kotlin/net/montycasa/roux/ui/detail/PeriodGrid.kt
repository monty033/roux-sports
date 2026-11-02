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
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.theme.LocalRouxAccents

/**
 * The tags one period grid uses, so two tables can share the drawing without sharing semantics.
 *
 * The prefixes rather than whole strings keep the column suffix in one place: a fix to a cell tag is a fix to every
 * table built on [PeriodGrid].
 */
internal data class PeriodGridTags(
    val table: String,
    val headerPrefix: String,
    val cellPrefix: String,
    val crestPrefix: String,
    val logoPrefix: String,
)

/**
 * One club's line in a period grid.
 *
 * [values] is in column order and holds one entry per period, null when that period did not arrive. [total] is the
 * published total, which is not required to equal the sum of [values].
 */
internal data class PeriodGridRow(
    val team: TeamRef,
    val values: List<Int?>,
    val total: Int?,
)

/**
 * A club row per period, with the published total last.
 *
 * The goals card and the shots card are read the same way - across the periods, both clubs, one official total - so
 * they draw the same grid. Keeping the headings, the crest, the club name and the fixed column width here is the
 * point: a change to alignment or to the total column cannot land in one table and miss the other.
 *
 * A period above regulation shares the single `OT` column, matching how the league combines overtimes in a summary.
 */
@Composable
internal fun PeriodGrid(
    tags: PeriodGridTags,
    periodCount: Int,
    rows: List<PeriodGridRow>,
) {
    Column(Modifier.testTag(tags.table)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // The crest and its gap, then the same flexible space the club names take, so each heading is centred
            // over its own column.
            Spacer(Modifier.width(PERIOD_CREST + PERIOD_GAP))
            Spacer(Modifier.weight(1f).padding(end = PERIOD_NAME_GAP))
            (1..periodCount).forEach { period ->
                val label = if (period == 4) "OT" else period.toString()
                PeriodCell(label, header = true, tag = "${tags.headerPrefix}.$label")
            }
            PeriodCell("T", header = true, tag = "${tags.headerPrefix}.T")
        }
        rows.forEach { row ->
            PeriodTeamRow(row = row, periodCount = periodCount, tags = tags)
        }
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
private fun PeriodTeamRow(
    row: PeriodGridRow,
    periodCount: Int,
    tags: PeriodGridTags,
) {
    val name = TeamNames.cityAndName(row.team).second
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.testTag("${tags.crestPrefix}.${row.team.abbrev}")) {
            TeamLogo(team = row.team, crestSize = PERIOD_CREST, imageTag = "${tags.logoPrefix}.${row.team.abbrev}")
        }
        Spacer(Modifier.width(PERIOD_GAP))
        Text(
            // The club's own name, spelled out: an abbreviation in the grid makes the reader translate it back.
            name,
            modifier = Modifier.weight(1f).padding(end = PERIOD_NAME_GAP),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        (1..periodCount).forEach { period ->
            PeriodCell(
                text = row.values.getOrNull(period - 1)?.toString() ?: "—",
                tag = "${tags.cellPrefix}.${row.team.abbrev}.$period",
            )
        }
        PeriodCell(row.total?.toString() ?: "—", tag = "${tags.cellPrefix}.${row.team.abbrev}.T")
    }
}

internal val PERIOD_CREST = 24.dp
private val PERIOD_GAP = 6.dp
private val PERIOD_NAME_GAP = 8.dp
internal const val PERIOD_COLUMN = 40
