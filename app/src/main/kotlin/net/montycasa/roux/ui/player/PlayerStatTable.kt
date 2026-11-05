@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.player

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.listRow

private val LABEL_WIDTH = 136.dp
private val STAT_WIDTH = 56.dp

/** The air a fitted stat column keeps beyond its widest text, so neighbouring numbers never touch. */
internal val STAT_COLUMN_GAP = 8.dp

/** The cells across one row of a [StatGrid]. */
internal const val STAT_GRID_COLUMNS = 4

/** One stat column. [key] names the test tags; [label] is the header; [value] is the number under it. */
internal data class StatCell(
    val key: String,
    val label: String,
    val value: String,
)

/**
 * One line of a stat table. [tag] names the line's test tags; [label] is its first column, the frozen one. [cells] are
 * its numbers. [gameId], when set, makes the line a button that reports the game; a line with no game id is plain text.
 */
internal data class StatRow(
    val tag: String,
    val label: String,
    val cells: List<StatCell>,
    val gameId: Long? = null,
)

/**
 * A stat grid: cells of a small label above their value, [STAT_GRID_COLUMNS] to a row, wrapping down the card. Each
 * value is tagged `<tagPrefix>.<key>` and its label `<tagPrefix>.<key>.label`.
 */
@Composable
internal fun StatGrid(
    cells: List<StatCell>,
    tagPrefix: String,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        cells.chunked(STAT_GRID_COLUMNS).forEach { rowCells ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                rowCells.forEach { cell ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            cell.label,
                            modifier = Modifier.testTag("$tagPrefix.${cell.key}.label"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            cell.value,
                            modifier = Modifier.testTag("$tagPrefix.${cell.key}"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                repeat(STAT_GRID_COLUMNS - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Each stat column's width when the column is only as wide as its content: the widest of its header and its numbers as
 * the table prints them, plus [STAT_COLUMN_GAP]. A header and the numbers under it share the column, so they stay centred
 * on one another. [rows] holds each line's cells in the same order as [headers].
 */
@Composable
internal fun fittedStatWidths(
    headers: List<StatCell>,
    rows: List<List<StatCell>>,
    measurer: TextMeasurer,
): List<Dp> {
    val headerStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    return headers.mapIndexed { index, header ->
        val headerPx = measurer.measure(header.label, headerStyle).size.width
        val valuePx = rows.maxOfOrNull { cells -> measurer.measure(cells[index].value, valueStyle).size.width } ?: 0
        with(density) { maxOf(headerPx, valuePx).toDp() } + STAT_COLUMN_GAP
    }
}

/**
 * A stat table: a frozen label column that wraps, then the stat columns.
 *
 * A header and the numbers under it are centred in their column, so their centre lines match. Each column is
 * [statWidths]'s width, or the fixed width when none is given. The stat columns sit in the middle of the space the label
 * column leaves. When they do not all fit, they scroll sideways, and the header and every line share one scroll position
 * so they move together. The label column is [labelWidth] wide and does not scroll; a caller that sizes it to its content
 * passes that width in.
 *
 * A line with a game id is a 48 dp button that reports that id to [onRowClick]. A line with no id is plain text.
 */
@Composable
internal fun PlayerStatTable(
    headers: List<StatCell>,
    rows: List<StatRow>,
    headerTag: String,
    labelWidth: Dp = LABEL_WIDTH,
    statWidths: List<Dp>? = null,
    onRowClick: ((Long) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val widths = statWidths ?: headers.map { STAT_WIDTH }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.width(labelWidth))
            StatCells(scroll) {
                headers.forEachIndexed { index, header ->
                    Text(
                        header.label,
                        modifier = Modifier.width(widths[index]).testTag("$headerTag.header.${header.key}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        rows.forEach { row ->
            Row(
                Modifier.rowModifier(row, onRowClick).testTag(row.tag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(labelWidth).testTag("${row.tag}.frozen")) {
                    Text(
                        row.label,
                        modifier = Modifier.testTag("${row.tag}.label"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatCells(scroll) {
                    row.cells.forEachIndexed { index, cell ->
                        Text(
                            cell.value,
                            modifier = Modifier.width(widths[index]).testTag("${row.tag}.${cell.key}"),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A line's own modifier. A line with a game id and a handler is a 48 dp row that reads as a button to a screen reader,
 * and a list row like the other tappable rows. Any other line keeps the table's spacing and takes no tap.
 */
@Composable
private fun Modifier.rowModifier(
    row: StatRow,
    onRowClick: ((Long) -> Unit)?,
): Modifier {
    val id = row.gameId
    if (id == null || onRowClick == null) return fillMaxWidth().padding(vertical = 4.dp)
    return fillMaxWidth()
        .heightIn(min = Reach.MIN_TARGET_DP.dp)
        .listRow()
        .clickable(role = Role.Button) { onRowClick(id) }
}

/**
 * The stat block: centred in the space beside the label when its columns fit. When they overflow, the row is as wide as
 * its columns and starts at the label's edge, so the first column is reachable by scrolling. Centring a wider-than-space
 * block would push its left end under the frozen label.
 */
@Composable
private fun RowScope.StatCells(
    scroll: ScrollState,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.weight(1f)) {
        Row(
            Modifier.widthIn(min = maxWidth).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}
