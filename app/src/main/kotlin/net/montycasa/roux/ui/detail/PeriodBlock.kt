@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.montycasa.roux.ui.theme.LocalRouxAccents
import kotlin.math.roundToInt

/**
 * A period heading shared by the summaries that group their rows by period.
 *
 * The label is always at the left. A summary with columns can add them through [trailing]; a full-width summary passes
 * nothing, which is why there is no second heading implementation to drift from this one.
 */
@Composable
internal fun PeriodHeading(
    label: String,
    tag: String,
    topPadding: Dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = topPadding, bottom = 4.dp)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.testTag("$tag.label"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** One rule between consecutive period blocks, inset from the card edges and never above the first block. */
@Composable
internal fun PeriodDivider(tag: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DIVIDER_INSET, vertical = 8.dp)
            .height(1.dp)
            .background(LocalRouxAccents.current.muted.copy(alpha = DIVIDER_ALPHA))
            .testTag(tag),
    )
}

/**
 * The zero-height gutter that holds a row's crest.
 *
 * The crest is measured at full size and placed at [offsetPx], but the gutter reports no height, so the crest can sit
 * level with the name line without inflating the row. Both summaries use this same mechanism rather than a copy.
 */
@Composable
internal fun CrestGutter(
    tag: String,
    crestSize: Dp,
    offsetPx: Float,
    content: @Composable () -> Unit,
) {
    val crestSizePx = with(LocalDensity.current) { crestSize.toPx() }
    Box(
        Modifier
            .testTag(tag)
            .width(crestSize)
            .layout { measurable, _ ->
                val crestPx = crestSizePx.roundToInt()
                val placeable = measurable.measure(Constraints.fixed(crestPx, crestPx))
                layout(placeable.width, 0) {
                    placeable.place(0, offsetPx.roundToInt())
                }
            },
    ) {
        content()
    }
}

/**
 * How far down the crest must move to centre on the name's first line.
 *
 * Measured from the row's top and the name's top, both in root coordinates, so the same arithmetic works at every font
 * scale and keeps a wrapped name's crest on its first line rather than the middle of the block.
 */
internal fun crestOffsetPx(
    rowTopPx: Float,
    nameTopPx: Float,
    nameLineHeightPx: Float,
    crestSizePx: Float,
): Float =
    if (nameLineHeightPx > 0f) {
        (nameTopPx - rowTopPx + (nameLineHeightPx - crestSizePx) / 2f).coerceAtLeast(0f)
    } else {
        0f
    }

/** `"MM:SS"` or `"M:SS"` as elapsed seconds; null when the feed sent something the card cannot place on a clock. */
internal fun elapsedSeconds(time: String): Int? {
    val parts = time.trim().split(":")
    if (parts.size != 2) return null
    val minutes = parts[0].toIntOrNull() ?: return null
    val seconds = parts[1].toIntOrNull() ?: return null
    if (minutes < 0 || seconds !in 0..59) return null
    return minutes * 60 + seconds
}

/** The scoring card's names for a period group: regulation ordinals, then one combined overtime heading. */
internal fun periodLabel(key: Int): String =
    when (key) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "OT"
    }

/** A group's heading, with `SO` taken from the feed's own type when a shootout block carries one. */
internal fun periodBlockLabel(
    key: Int,
    periodType: String?,
): String =
    if (key <= 3) {
        periodLabel(key)
    } else if (periodType?.trim()?.uppercase() == "SO") {
        "SO"
    } else {
        "OT"
    }

internal val ROW_CREST = 44.dp
internal val CREST_GAP = 10.dp
internal val NAME_TOP_GAP = 2.dp
internal const val OVERTIME_KEY = 4
internal const val UNPARSED_ELAPSED = Int.MAX_VALUE

private val DIVIDER_INSET = 8.dp
private const val DIVIDER_ALPHA = 0.35f
