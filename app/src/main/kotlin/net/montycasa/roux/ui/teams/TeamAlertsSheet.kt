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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.listRow
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

/** The explanatory disclosure and group headings on [SHEET_PANEL]: 7.11:1. */
internal val SHEET_MUTED = Color(0xFFA2A8B1)

/** The secondary (non-filled) action on [SHEET_PANEL]: 5.08:1. */
internal val SHEET_ACCENT = Color(0xFFFF4B12)

/**
 * The tallest a sheet may be, as a fraction of the window.
 *
 * Greater than the leagues menu's 0.56: that sheet is a list the reader scrolls, while this one has to fit its
 * whole question on screen, and a question whose answer button sits below the fold is not a question.
 *
 * Measured, not guessed: the five-switch sheet is 596 dp tall at 360 dp wide (a probe printed every row's bounds),
 * so 0.80 of a 881 dp phone fits it whole and still leaves the page's own heading and its one line of copy in view
 * above the sheet. Below roughly 745 dp of window the rows scroll under a pinned Done, which is what the scroll is
 * for; the screenshot matrix renders 320x470, so its capture shows the sheet mid-scroll and is not evidence that
 * the phone clips anything.
 */
private const val SHEET_MAX_FRACTION = 0.80f

/**
 * Air between two switches of the same group.
 *
 * Back to the reach rule's minimum: a 4 dp gap saved 20 dp of sheet and failed R4 in the audit, which is exactly what
 * that audit is for. The space for a fifth row comes from the headings instead.
 */
private val SHEET_ROW_GAP = 8.dp

/** Air above a group heading, which is what separates one run of switches from the next. */
private val SHEET_GROUP_GAP = 12.dp

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
 *
 * The rows scroll, but Done does not: it is the sheet's one way out, and at a doubled font scale on a small
 * phone it used to be the last thing below the fold.
 */
@Composable
fun TeamAlertsSheet(
    alerts: TeamAlerts,
    canNotify: Boolean,
    modifier: Modifier = Modifier,
    /** True while a switch write is in flight, so a second tap cannot race the first. */
    busy: Boolean = false,
    onDismiss: () -> Unit,
    /** Every switch writes through here, so the permission path and the write path exist once. */
    onSetAlert: (AlertKind, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    /** True when notifications cannot arrive and the user has not already declined the question. */
    showPrompt: Boolean = false,
    onEnableNotifications: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
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
                .background(SHEET_PANEL, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // fill = false so a short sheet still wraps its content; the weight only supplies a
                    // bounded height for the scroll when the rows no longer fit.
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(Reach.EDGE_INSET_DP.dp)
                    .testTag("teams.alerts.sheet"),
                // Rows inside a group sit barely apart, because the switch already carries its own 48 dp of air;
                // the separation belongs to the headings, which are given it below.
                verticalArrangement = Arrangement.spacedBy(SHEET_ROW_GAP),
            ) {
                Text(
                    stringResource(R.string.teams_alerts_title, alerts.team.name ?: alerts.team.key.abbrev),
                    style = MaterialTheme.typography.headlineSmall,
                    color = SHEET_INK,
                )

                if (showPrompt) {
                    // The question replaces the switches rather than sitting above them: a sheet that overflows
                    // its own cap has controls the user cannot reach on a surface that does not look scrollable.
                    PromptBlock(onEnable = onEnableNotifications, onDismissPrompt = onDismissPrompt)
                } else {
                    AlertGroup(stringResource(R.string.teams_alerts_group_before), "teams.alerts.group.before")
                    AlertSwitch(
                        kind = AlertKind.Upcoming,
                        label = stringResource(R.string.teams_alerts_upcoming),
                        checked = alerts.upcoming,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.upcoming",
                        onSetAlert = onSetAlert,
                    )
                    AlertSwitch(
                        kind = AlertKind.Start,
                        label = stringResource(R.string.teams_alerts_start),
                        checked = alerts.start,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.start",
                        onSetAlert = onSetAlert,
                    )

                    AlertGroup(stringResource(R.string.teams_alerts_group_during), "teams.alerts.group.during")
                    AlertSwitch(
                        kind = AlertKind.Goals,
                        label = stringResource(R.string.teams_alerts_goals),
                        checked = alerts.goals,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.goals",
                        onSetAlert = onSetAlert,
                    )
                    AlertSwitch(
                        kind = AlertKind.Intermission,
                        label = stringResource(R.string.teams_alerts_intermission),
                        checked = alerts.intermission,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.intermission",
                        onSetAlert = onSetAlert,
                    )

                    AlertGroup(stringResource(R.string.teams_alerts_group_after), "teams.alerts.group.after")
                    AlertSwitch(
                        kind = AlertKind.Final,
                        label = stringResource(R.string.teams_alerts_final),
                        checked = alerts.final,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.final",
                        onSetAlert = onSetAlert,
                    )

                    // What the app will and will not do, said where the switches are rather than in a policy page
                    // nobody opens. The promise is narrow on purpose: these are computed on the phone from the
                    // score feed, and it names every alert the five switches can turn on.
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
            }

            // The prompt brings its own two answers, and a third button is what pushed its row past the sheet's
            // height cap; the scrim still closes the sheet. Otherwise Done is outside the scroll above, so the
            // rows can move under a finger while the exit stays where it was.
            if (!showPrompt) {
                Button(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Reach.EDGE_INSET_DP.dp, vertical = Reach.MIN_TARGET_GAP_DP.dp)
                            .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                            .primaryControl("teams.alerts.done")
                            .testTag("teams.alerts.done"),
                ) {
                    Text(stringResource(R.string.teams_alerts_done))
                }
            }
        }
    }
}

/** A quiet label above a run of switches; it is a heading, not another control. */
@Composable
private fun AlertGroup(
    label: String,
    tag: String,
) {
    Text(
        label,
        Modifier.fillMaxWidth().padding(top = SHEET_GROUP_GAP).testTag(tag),
        style = MaterialTheme.typography.labelMedium,
        color = SHEET_MUTED,
    )
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

/**
 * One alert switch. The whole row is the control: a reader who taps the label has tapped the switch.
 *
 * The Switch itself takes no callback. Material draws its own toggle semantics, and giving it one here as well
 * would put two controls in the same row, so a screen reader would announce the same alert twice.
 */
@Composable
private fun AlertSwitch(
    kind: AlertKind,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onSetAlert: (AlertKind, Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Reach.MIN_TARGET_DP.dp)
            // The switches are a list: the 8 dp clearance R4 asks between unrelated controls is not
            // owed between two rows of the same sheet, and the heading already separates the groups.
            .listRow()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) { on -> onSetAlert(kind, on) }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = SHEET_INK)
        Switch(
            checked = checked,
            onCheckedChange = null,
            // `enabled`, not a null callback: Material puts the disabled semantics on the node only when the
            // component is told it is disabled, and a null callback alone leaves a switch that reads as live to a
            // screen reader while doing nothing to a tap.
            enabled = enabled,
            modifier = Modifier.heightIn(min = Reach.MIN_TARGET_DP.dp),
        )
    }
}
