@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.policy.DateStripItem
import net.montycasa.roux.ui.reach.Reach

/** A fixed-height shelf with a centred date trigger and day arrows at either edge. */
@Composable
internal fun DateShelf(
    items: List<DateStripItem>,
    selectedDate: String,
    today: String,
    onSelect: (String) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = MaterialTheme.colorScheme.outline
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("scores.shelf")
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind { drawLine(rule, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(horizontal = Reach.EDGE_INSET_DP.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateStripBar(items, selectedDate, today, onSelect, Modifier.fillMaxWidth(), onOpenCalendar)
    }
}

internal fun outlineGlyph(
    name: String,
    path: String,
): ImageVector =
    ImageVector
        .Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
