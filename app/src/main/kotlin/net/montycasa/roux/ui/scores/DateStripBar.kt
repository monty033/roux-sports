@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.domain.policy.DateStripItem
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Orientation for the scores pager; buttons only step through the supplied ordered dates. */
@Composable
fun DateStripBar(
    items: List<DateStripItem>,
    selectedDate: String,
    today: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.parse(selectedDate)
    val index = items.indexOfFirst { it.date == selectedDate }
    val style = dateTextStyle(12)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val todayLabel = stringResource(R.string.date_strip_today)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onSelect(items[index - 1].date) },
            enabled = index > 0,
            modifier =
                Modifier
                    .size(
                        Reach.PRIMARY_MIN_HEIGHT_DP.dp,
                    ).testTag("scores.previous")
                    .primaryControl("scores.previous", frequent = true),
        ) {
            Icon(outlineGlyph("Previous day", "M15,5 L8,12 L15,19"), "Previous day")
        }
        BoxWithConstraints(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = 112.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("scores.dateLabel")
                .semantics(mergeDescendants = true) {
                    stateDescription =
                        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                },
        ) {
            val monthDay = date.format(DateTimeFormatter.ofPattern("MMM d", locale))
            val candidates =
                if (selectedDate ==
                    today
                ) {
                    listOf("$todayLabel, $monthDay", todayLabel)
                } else {
                    listOf(date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)), monthDay, date.dayOfMonth.toString())
                }
            val width = with(density) { maxWidth.roundToPx() }
            val label = candidates.firstOrNull { measurer.measure(it, style, softWrap = false).size.width <= width } ?: candidates.last()
            Text(label, Modifier.fillMaxWidth(), style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        }
        IconButton(
            onClick = { onSelect(items[index + 1].date) },
            enabled = index >= 0 && index < items.lastIndex,
            modifier = Modifier.size(Reach.PRIMARY_MIN_HEIGHT_DP.dp).testTag("scores.next").primaryControl("scores.next", frequent = true),
        ) {
            Icon(outlineGlyph("Next day", "M9,5 L16,12 L9,19"), "Next day")
        }
    }
}

internal fun dateTextStyle(size: Int): TextStyle =
    TextStyle(
        fontSize = size.sp,
        letterSpacing = 0.sp,
        lineHeight = size.sp,
        fontWeight = FontWeight.ExtraBold,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
