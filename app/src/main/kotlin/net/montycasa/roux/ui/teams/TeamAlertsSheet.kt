@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl

/**
 * The sheet's own surface, matching the leagues menu so the two read as the same material.
 *
 * The panel is fixed dark in BOTH themes, so every piece of text the sheet draws takes its foreground from the
 * constants below rather than from the theme. Without that, light-theme onSurface (near-black) lands on this panel
 * at about 1.1:1. Internal so ContrastAndTypeTest measures these exact values.
 */
internal val SHEET_PANEL = Color(0xFF1C1C1E)

/** Title, switch labels and warnings on [SHEET_PANEL]: 14.67:1. */
internal val SHEET_INK = Color(0xFFEDEEF1)

/** The explanatory disclosure on [SHEET_PANEL]: 7.11:1. */
internal val SHEET_MUTED = Color(0xFFA2A8B1)

/** The secondary (non-filled) action on [SHEET_PANEL]: 5.08:1. */
internal val SHEET_ACCENT = Color(0xFFFF4B12)

/**
 * The tallest a sheet may be, as a fraction of the window.
 *
 * Greater than the leagues menu's 0.56: that sheet is a list the reader scrolls, while this one has to fit its
 * whole question on screen, and a question whose answer button sits below the fold is not a question.
 */
private const val SHEET_MAX_FRACTION = 0.75f

/** How much of the window the scrim dims at full height; the same value the menu uses. */
private const val SCRIM_ALPHA = 0.55f

/**
 * One team's alerts, in a sheet that covers the bottom of the window.
 *
 * Deliberately not draggable. The leagues menu follows a finger because it is a list of unknown length that the
 * reader scrolls through; this is a fixed run of rows that either are or are not wanted, and a drag would add a
 * second way to dismiss something that already has a scrim and a Done button.
 *
 * When notifications cannot arrive at all, the sheet opens on that question instead of on the switches: the
 * switches are the reason it matters, and a control that quietly refuses is how an app looks broken.
 */
@Composable
fun TeamAlertsSheet(
    alerts: TeamAlerts,
    canNotify: Boolean,
    /** True while a switch write is in flight, so a second tap cannot race the first. */
    busy: Boolean = false,
    onDismiss: () -> Unit,
    onSetStart: (Boolean) -> Unit,
    onSetGoals: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    /** True when notifications cannot arrive and the user has not already declined the question. */
    showPrompt: Boolean = false,
    onEnableNotifications: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("teams.alerts.host")) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SHEET_PANEL.copy(alpha = SCRIM_ALPHA))
                .pointerInput(Unit) {
                    // The scrim takes its own taps and swallows drags: the page must not scroll behind an open sheet.
                    detectTapGestures { onDismiss() }
                }.testTag("teams.alerts.scrim"),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * SHEET_MAX_FRACTION)
                .background(SHEET_PANEL, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .verticalScroll(rememberScrollState())
                .padding(Reach.EDGE_INSET_DP.dp)
                .testTag("teams.alerts.sheet"),
            verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
        ) {
            Text(
                stringResource(R.string.teams_alerts_title, alerts.team.name ?: alerts.team.key.abbrev),
                style = MaterialTheme.typography.headlineSmall,
                color = SHEET_INK,
            )

            if (showPrompt) {
                // The question replaces the switches rather than sitting above them: a sheet that overflows its own
                // cap has controls the user cannot reach on a surface that does not look scrollable.
                PromptBlock(onEnable = onEnableNotifications, onDismissPrompt = onDismissPrompt)
            } else {
                AlertSwitch(
                    label = stringResource(R.string.teams_alerts_start),
                    checked = alerts.start,
                    enabled = canNotify && !busy,
                    tag = "teams.alerts.start",
                    onChange = onSetStart,
                )
                AlertSwitch(
                    label = stringResource(R.string.teams_alerts_goals),
                    checked = alerts.goals,
                    enabled = canNotify && !busy,
                    tag = "teams.alerts.goals",
                    onChange = onSetGoals,
                )

                // What the app will and will not do, said where the switches are rather than in a policy page nobody
                // opens. The promise is narrow on purpose: these are computed on the phone from the score feed.
                Text(
                    stringResource(R.string.teams_alerts_disclosure),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SHEET_MUTED,
                    modifier = Modifier.testTag("teams.alerts.disclosure"),
                )

                if (!canNotify) {
                    Text(
                        stringResource(R.string.teams_alerts_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SHEET_INK,
                        modifier = Modifier.testTag("teams.alerts.blocked"),
                    )
                    Button(
                        onClick = onOpenSystemSettings,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                                .primaryControl("teams.alerts.settings")
                                .testTag("teams.alerts.settings"),
                    ) {
                        Text(stringResource(R.string.teams_alerts_open_settings))
                    }
                }

                if (alerts.saveFailed) {
                    Text(
                        stringResource(R.string.teams_alerts_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SHEET_INK,
                        modifier = Modifier.testTag("teams.alerts.save_failed"),
                    )
                }
            }

            // The prompt brings its own two answers, and a third button is what pushed its row past the sheet's
            // height cap; the scrim still closes the sheet.
            if (!showPrompt) {
                Button(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                            .primaryControl("teams.alerts.done"),
                ) {
                    Text(stringResource(R.string.teams_alerts_done))
                }
            }
        }
    }
}

/**
 * The question the sheet opens on when nothing could be delivered: what is off, why it matters, and the way out.
 *
 * Stacked rather than side by side. A weight-based row inside a sheet measured against a fractional height cap
 * resolved the button to zero width on a small window, which is a real trap for a control that must be tappable.
 */
@Composable
private fun PromptBlock(
    onEnable: () -> Unit,
    onDismissPrompt: () -> Unit,
) {
    Text(
        stringResource(R.string.teams_alerts_prompt_title),
        Modifier.fillMaxWidth().testTag("teams.alerts.prompt.title"),
        style = MaterialTheme.typography.titleMedium,
        color = SHEET_INK,
    )
    Text(
        stringResource(R.string.teams_alerts_prompt_body),
        Modifier.fillMaxWidth().testTag("teams.alerts.prompt.body"),
        style = MaterialTheme.typography.bodyMedium,
        color = SHEET_INK,
    )
    Button(
        onClick = onEnable,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                .testTag("teams.alerts.prompt.enable"),
    ) {
        Text(stringResource(R.string.teams_alerts_prompt_enable))
    }
    TextButton(
        onClick = onDismissPrompt,
        modifier = Modifier.fillMaxWidth().heightIn(min = Reach.MIN_TARGET_DP.dp).testTag("teams.alerts.prompt.later"),
        // A TextButton is drawn in primary, which is the light-theme #C93A06 (3.31:1 on this panel).
        colors = ButtonDefaults.textButtonColors(contentColor = SHEET_ACCENT),
    ) {
        Text(stringResource(R.string.teams_alerts_prompt_later))
    }
}

@Composable
private fun AlertSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().testTag("$tag.row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = SHEET_INK)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            // `enabled`, not a null callback: Material puts the disabled semantics on the node only when the
            // component is told it is disabled, and a null callback alone leaves a switch that reads as live to a
            // screen reader while doing nothing to a tap.
            enabled = enabled,
            modifier =
                Modifier
                    .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                    .semantics {
                        // The switch's name is the alert, and its state is spoken as on or off by the control.
                        contentDescription = label
                    }.testTag(tag),
        )
    }
}
