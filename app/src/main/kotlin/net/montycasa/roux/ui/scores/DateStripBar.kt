@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.policy.DateStripItem
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Renders the policy's ordered ISO dates as supplied, including empty days. The host places this
 * frequent primary picker in the reach zone; this component owns target sizes, gaps and edge insets.
 */
@Composable
fun DateStripBar(
    items: List<DateStripItem>,
    selectedDate: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val selectedRequester = remember { BringIntoViewRequester() }

    // Keep the viewport itself clear of the back-gesture edges, including after scrolling.
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Reach.EDGE_INSET_DP.dp)
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
                val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant

                Column(
                    modifier =
                        Modifier
                            .then(if (selected) Modifier.bringIntoViewRequester(selectedRequester) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(background)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(item.date) })
                            .primaryControl("scores.dateChip.${item.date}", frequent = true)
                            .semantics { contentDescription = description }
                            .sizeIn(minWidth = Reach.PRIMARY_MIN_HEIGHT_DP.dp, minHeight = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                            .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(R.string.date_strip_day_label, date.dayOfMonth),
                        modifier = Modifier.clearAndSetSemantics { },
                        color = foreground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (item.hasGames) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    // Filled versus hollow is a second, non-colour signal, even on the selected day.
                    Box(
                        Modifier
                            .size(6.dp)
                            .then(
                                if (item.hasGames) {
                                    Modifier.background(foreground, CircleShape)
                                } else {
                                    Modifier.border(1.dp, foreground, CircleShape)
                                },
                            ),
                    )
                }
            }
        }
    }

    LaunchedEffect(selectedDate, items, locale) {
        if (items.any { it.date == selectedDate }) selectedRequester.bringIntoView()
    }
}
