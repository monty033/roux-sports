@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.policy.DateStripItem
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.theme.Roux

/** The viewport shrinks before the pinned calendar does, matching the reference's min-width: 0. */
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
        horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateStripBar(items, selectedDate, today, onSelect, Modifier.weight(1f))
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, rule, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onOpenCalendar)
                .primaryControl("scores.calendar", frequent = true),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CalendarOutline, stringResource(R.string.calendar_open), Modifier.size(22.dp), tint = Roux.accents.signature)
        }
    }
}

internal val CalendarOutline: ImageVector =
    outlineGlyph("Calendar", "M6,4 H18 Q21,4 21,7 V19 Q21,22 18,22 H6 Q3,22 3,19 V7 Q3,4 6,4 Z M8,2 V6 M16,2 V6 M3,10 H21")

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
