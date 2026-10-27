@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.ui.theme.Roux
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Picks immediately, so every dismissal preserves the same date as DONE. No calendar fetch is needed. */
@Composable
internal fun CalendarMonthSheet(
    selectedDate: String,
    today: String,
    dateIndex: List<DateIndexEntry>,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    bottomInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    var displayedMonth by rememberSaveable { mutableStateOf(selectedDate.take(7)) }
    // The sheet holds a draft, and only DONE applies it. Tapping a day or TODAY moves the draft; dismissing the sheet -
    // scrim, back or the dialog dismiss action - throws the draft away and leaves the scores where they were. That is
    // what makes TODAY and DONE two different buttons rather than decoration.
    var draft by rememberSaveable { mutableStateOf(selectedDate) }
    val month = YearMonth.parse(displayedMonth)
    val locale = LocalConfiguration.current.locales[0]
    val title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    val dismissLabel = stringResource(R.string.calendar_dismiss)
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
    val weeks = (first.dayOfWeek.value % 7 + month.lengthOfMonth() + 6) / 7
    val games = dateIndex.filter { it.numberOfGames > 0 }.map { it.date }.toSet()
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().testTag("calendar.overlay")) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF08090B).copy(alpha = 0.55f))
                .clickable(interactionSource = null, indication = null, onClickLabel = dismissLabel, onClick = onDismiss)
                .testTag("calendar.scrim"),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .dropShadow(
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    Shadow(radius = 50.dp, color = Color.Black.copy(alpha = 0.45f), offset = DpOffset(0.dp, (-18).dp)),
                ).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                // Consume blank-space taps so they cannot reach the dismissing scrim underneath.
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics {
                    dialog()
                    paneTitle = title
                    dismiss(dismissLabel) {
                        onDismiss()
                        true
                    }
                }.testTag("calendar.sheet")
                .windowInsetsPadding(bottomInsets)
                .padding(top = 10.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(40.dp, 4.dp).background(Roux.accents.muted.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(false) { displayedMonth = month.minusMonths(1).toString() }
                Text(
                    title,
                    Modifier.weight(1f).testTag("calendar.title"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = dateTextStyle(16).copy(letterSpacing = (-0.01).em),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                MonthArrow(true) { displayedMonth = month.plusMonths(1).toString() }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Text(it, color = Roux.accents.muted, style = dateTextStyle(9).copy(letterSpacing = 0.1.em))
                    }
                }
            }
            repeat(weeks) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { day ->
                        val date = start.plusDays((week * 7 + day).toLong())
                        CalendarDay(
                            date,
                            date.toString() == draft,
                            YearMonth.from(date) != month,
                            date.toString() in games,
                            Modifier.weight(1f),
                        ) {
                            draft = date.toString()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarAction(stringResource(R.string.calendar_today), false, Modifier.weight(1f)) {
                    displayedMonth = today.take(7)
                    draft = today
                }
                CalendarAction(stringResource(R.string.calendar_done), true, Modifier.weight(1f)) {
                    onApply(draft)
                    onDismiss()
                }
            }
            // The shell supplies its navigation inset only when this overlay sits outside Scaffold.
            // An already inset screen supplies zero; neither path invents a second 24 dp strip.
        }
    }
}

@Composable
private fun MonthArrow(
    next: Boolean,
    onClick: () -> Unit,
) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            if (next) NextMonth else PreviousMonth,
            stringResource(if (next) R.string.calendar_next else R.string.calendar_previous),
            Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    selected: Boolean,
    muted: Boolean,
    hasGames: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ink =
        if (selected) {
            Roux.accents.onFill
        } else if (muted) {
            Roux.accents.muted
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Roux.accents.fill else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) }
            .testTag("calendar.day.$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = ink,
            style =
                dateTextStyle(14).copy(
                    fontWeight =
                        if (selected) {
                            FontWeight.ExtraBold
                        } else if (muted) {
                            FontWeight.Medium
                        } else {
                            FontWeight.SemiBold
                        },
                ),
        )
        if (hasGames) {
            Box(
                Modifier
                    .size(
                        4.dp,
                    ).background(
                        (if (selected) Roux.accents.onFill else Roux.accents.signature).copy(alpha = 0.85f),
                        CircleShape,
                    ).testTag("calendar.dot.$date"),
            )
        }
    }
}

@Composable
private fun CalendarAction(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(if (primary) Roux.accents.fill else Color.Transparent)
            .border(1.dp, if (primary) Roux.accents.fill else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Roux.accents.onFill else MaterialTheme.colorScheme.onSurface,
            style = dateTextStyle(12).copy(letterSpacing = 0.1.em),
        )
    }
}

private val PreviousMonth = outlineGlyph("Previous month", "M15,6 L9,12 L15,18")
private val NextMonth = outlineGlyph("Next month", "M9,6 L15,12 L9,18")
