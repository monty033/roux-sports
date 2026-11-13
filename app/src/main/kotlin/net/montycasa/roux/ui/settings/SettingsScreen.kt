@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.AccentColor
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.swatch

/** Platform interpretation belongs in UI, never in the saved preference. */
fun resolveDark(
    mode: ThemeMode,
    systemDark: Boolean,
): Boolean =
    when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

/**
 * How a setting is chosen: one row per setting, its name on the left and every option in that same row.
 *
 * Three radios down the page made a three-way choice look like a form to fill in, and the answer was always visible
 * anyway. Here the options are the row: the thing you pick is where the thing you read is.
 *
 * The theme and the accent live here. The alert lead moved to the team's alert sheet, where the alert it belongs to is
 * switched on - a lead time set in Settings, for every team at once, was neither obvious nor true.
 */
@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accent: AccentColor = AccentColor.DEFAULT,
    onAccent: (AccentColor) -> Unit = {},
) {
    Surface(modifier.fillMaxSize()) {
        // The About section makes the page longer than a small phone, so the whole page scrolls as one
        // rather than the last section becoming unreachable.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_back)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            SettingRow(label = stringResource(R.string.settings_theme), tag = "settings.theme") {
                ThemeMode.entries.forEach { choice ->
                    Segment(
                        label =
                            stringResource(
                                when (choice) {
                                    ThemeMode.System -> R.string.theme_system
                                    ThemeMode.Light -> R.string.theme_light
                                    ThemeMode.Dark -> R.string.theme_dark
                                },
                            ),
                        selected = mode == choice,
                        tag = "settings.${choice.name}",
                        onClick = { onMode(choice) },
                    )
                }
            }

            AccentRow(selected = accent, onAccent = onAccent)

            AboutSection()
        }
    }
}

/** One setting: its name, and the options as segments inside the same row. */
@Composable
private fun SettingRow(
    label: String,
    tag: String,
    segments: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("$tag.row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            // The name gives way before the options do: a wrapped label is readable, a clipped option is not.
            Modifier.weight(1f).padding(end = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            Modifier
                .selectableGroup()
                .width(SEGMENT_CONTROL_WIDTH)
                .heightIn(min = 48.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), SEGMENT_SHAPE)
                .background(Color.Transparent, SEGMENT_SHAPE),
        ) {
            segments()
        }
    }
}

/** One option inside [SettingRow]. Its whole cell is the target, and the selected one is filled. */
@Composable
private fun RowScope.Segment(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                SEGMENT_SHAPE,
            ).reachTarget(tag)
            .testTag(tag)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The accent: its name on a line of its own, then the nine colours in one row beneath it.
 *
 * Nine 48 dp targets are 432 dp, which cannot sit beside a name on a phone, so the row stacks under the name and
 * scrolls sideways on a narrow screen rather than shrinking the targets below the minimum.
 */
@Composable
private fun AccentRow(
    selected: AccentColor,
    onAccent: (AccentColor) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("settings.accent.row")) {
        Text(
            stringResource(R.string.settings_accent),
            Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccentColor.entries.forEach { choice ->
                AccentSwatch(choice = choice, selected = selected == choice, onClick = { onAccent(choice) })
            }
        }
    }
}

/** One accent as a 48 dp target: its colour inside, ringed when it is the chosen one. */
@Composable
private fun AccentSwatch(
    choice: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val name = stringResource(choice.nameRes())
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = name }
            .reachTarget("settings.accent.${choice.name}")
            .testTag("settings.accent.${choice.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .border(BorderStroke(2.dp, ring), CircleShape)
                .padding(4.dp)
                .background(choice.swatch().fill, CircleShape),
        )
    }
}

private fun AccentColor.nameRes(): Int =
    when (this) {
        AccentColor.Orange -> R.string.accent_orange
        AccentColor.Blue -> R.string.accent_blue
        AccentColor.Teal -> R.string.accent_teal
        AccentColor.Green -> R.string.accent_green
        AccentColor.Purple -> R.string.accent_purple
        AccentColor.Red -> R.string.accent_red
        AccentColor.Gold -> R.string.accent_gold
        AccentColor.Pink -> R.string.accent_pink
        AccentColor.Cyan -> R.string.accent_cyan
    }

private val SEGMENT_SHAPE = RoundedCornerShape(10.dp)

/**
 * One control width for both rows, so the two settings read as a column rather than two different widgets.
 *
 * 192 leaves each segment 64 dp - comfortably above the 48 dp target - and, more to the point, leaves the name
 * enough room to stay on one line on a 320 dp screen. At 216 the label wrapped to "Alert / lead", which reads like
 * two settings.
 */
private val SEGMENT_CONTROL_WIDTH = 192.dp
