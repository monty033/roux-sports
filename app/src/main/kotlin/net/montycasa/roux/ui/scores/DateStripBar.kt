@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.domain.policy.DateStripItem
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.theme.Roux
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle

/**
 * Renders the policy's ordered ISO dates as supplied, including empty days. The host places this
 * frequent primary picker in the reach zone; the shelf owns the edge insets.
 */
@Composable
fun DateStripBar(
    items: List<DateStripItem>,
    selectedDate: String,
    today: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val selectedRequester = remember { BringIntoViewRequester() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            key(item.date) {
                val date = remember(item.date) { LocalDate.parse(item.date) }
                val fullDate = remember(date, dateFormatter) { date.format(dateFormatter) }
                val description =
                    if (item.hasGames) {
                        pluralStringResource(R.plurals.date_strip_day_with_games, item.numberOfGames, fullDate, item.numberOfGames)
                    } else {
                        stringResource(R.string.date_strip_day_without_games, fullDate)
                    }
                val selected = item.date == selectedDate
                val foreground = if (selected) Roux.accents.onFill else MaterialTheme.colorScheme.onSurface
                val background = if (selected) Roux.accents.fill else MaterialTheme.colorScheme.surfaceVariant

                Column(
                    modifier =
                        Modifier
                            .then(if (selected) Modifier.bringIntoViewRequester(selectedRequester) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(background)
                            .border(1.dp, if (selected) Roux.accents.fill else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(item.date) })
                            .primaryControl("scores.dateChip.${item.date}", frequent = true)
                            .semantics { contentDescription = description }
                            .size(Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                            .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    if (item.date == today) {
                        Text(
                            stringResource(R.string.date_strip_today),
                            color = foreground,
                            style = dateTextStyle(12).copy(letterSpacing = 0.01.em),
                        )
                    } else {
                        Text(
                            date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale),
                            color = if (selected) foreground else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = dateTextStyle(12),
                        )
                        Text(
                            stringResource(R.string.date_strip_day_label, date.dayOfMonth),
                            color = foreground,
                            style = dateTextStyle(19).copy(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            date.month.getDisplayName(DateTextStyle.SHORT, locale),
                            color = if (selected) foreground else Roux.accents.muted,
                            style = dateTextStyle(12).copy(fontWeight = FontWeight.Bold, letterSpacing = 0.04.em),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedDate, items, locale) {
        if (items.any { it.date == selectedDate }) selectedRequester.bringIntoView()
    }
}

internal fun dateTextStyle(size: Int): TextStyle =
    TextStyle(
        fontSize = size.sp,
        lineHeight = size.sp,
        fontWeight = FontWeight.ExtraBold,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
