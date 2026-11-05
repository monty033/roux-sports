@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.player

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LABEL_WIDTH = 136.dp
private val STAT_WIDTH = 56.dp

/** The cells across one row of a [StatGrid]. */
internal const val STAT_GRID_COLUMNS = 4

/** One stat column. [key] names the test tags; [label] is the header; [value] is the number under it. */
internal data class StatCell(
    val key: String,
    val label: String,
    val value: String,
)

/**
 * One line of a stat table. [tag] names the line's test tags; [label] is its first column; [cells] are its numbers.
 * [detail], when set, is a second line under [label] in the same frozen column, such as a game's opponent.
 */
internal data class StatRow(
    val tag: String,
    val label: String,
    val cells: List<StatCell>,
    val detail: String? = null,
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
 * A stat table: a frozen label column that wraps, then the stat columns.
 *
 * Every stat column is the same width, and a header and the numbers under it are centred in that width, so their centre
 * lines match. The stat columns sit in the middle of the space the label column leaves. When they do not all fit, they
 * scroll sideways, and the header and every line share one scroll position so they move together. The label column is
 * [labelWidth] wide and does not scroll; a caller that sizes it to its content passes that width in.
 */
@Composable
internal fun PlayerStatTable(
    headers: List<StatCell>,
    rows: List<StatRow>,
    headerTag: String,
    labelWidth: Dp = LABEL_WIDTH,
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.width(labelWidth))
            StatCells(scroll) {
                headers.forEach { header ->
                    Text(
                        header.label,
                        modifier = Modifier.width(STAT_WIDTH).testTag("$headerTag.header.${header.key}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(row.tag)) {
                Column(Modifier.width(labelWidth).testTag("${row.tag}.frozen")) {
                    Text(
                        row.label,
                        modifier = Modifier.testTag("${row.tag}.label"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.detail?.let { detail ->
                        Text(
                            detail,
                            modifier = Modifier.testTag("${row.tag}.detail"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatCells(scroll) {
                    row.cells.forEach { cell ->
                        Text(
                            cell.value,
                            modifier = Modifier.width(STAT_WIDTH).testTag("${row.tag}.${cell.key}"),
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
