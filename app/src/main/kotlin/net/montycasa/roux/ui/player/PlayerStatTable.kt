@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.player

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val LABEL_WIDTH = 136.dp
private val STAT_WIDTH = 56.dp

/** One stat column. [key] names the test tags; [label] is the header; [value] is the number under it. */
internal data class StatCell(
    val key: String,
    val label: String,
    val value: String,
)

/** One line of a stat table. [tag] names the line's test tags; [label] is its first column; [cells] are its numbers. */
internal data class StatRow(
    val tag: String,
    val label: String,
    val cells: List<StatCell>,
)

/**
 * A stat table: a label column that wraps, then the stat columns.
 *
 * Every stat column is the same width, and a header and the numbers under it are centred in that width, so their centre
 * lines match. The stat columns sit in the middle of the space the label column leaves. When they do not all fit, they
 * scroll sideways, and the header and every line share one scroll position so they move together.
 */
@Composable
internal fun PlayerStatTable(
    headers: List<StatCell>,
    rows: List<StatRow>,
    headerTag: String,
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.width(LABEL_WIDTH))
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
                Text(
                    row.label,
                    modifier = Modifier.width(LABEL_WIDTH).testTag("${row.tag}.label"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
 * The stat block: centred in the space beside the label. The scroller is only as wide as its columns when they fit,
 * so the centring takes effect then; when they overflow it takes the whole space and scrolls.
 */
@Composable
private fun RowScope.StatCells(
    scroll: ScrollState,
    content: @Composable () -> Unit,
) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Row(Modifier.horizontalScroll(scroll)) {
            content()
        }
    }
}
