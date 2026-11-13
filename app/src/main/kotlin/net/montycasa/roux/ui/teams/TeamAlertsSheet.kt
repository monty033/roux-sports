@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.teams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.domain.repo.UpcomingLead
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.listRow
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.theme.Roux

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

/** The fill of a chosen "Off": a raised neutral, never the accent that means "on" everywhere else. */
internal val SHEET_OFF_FILL = Color(0xFF3A3A40)

/** The hairline above a group heading: structure, not decoration - it says a new run of rows starts here. */
internal val SHEET_RULE = Color(0xFF3A3A40)

private val SEGMENT_SHAPE = RoundedCornerShape(10.dp)

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
    /**
     * The reminder answer: a lead time in minutes, or null for "Off".
     *
     * One callback for both halves of the question because the sheet asks it once - a switch and a separate lead
     * value would be two controls for one decision, and the reader cannot tell which one owns it.
     */
    onSetReminder: (Int?) -> Unit,
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
                    ReminderBlock(
                        alerts = alerts,
                        enabled = canNotify && !busy,
                        onSetReminder = onSetReminder,
                    )

                    AlertGroup(stringResource(R.string.teams_alerts_group_during), "teams.alerts.group.during")
                    // First row of "during", because that is when it fires: the puck drops and the game is under way.
                    // Before-the-game holds only the reminder, which is the one alert that arrives ahead of time.
                    AlertSwitch(
                        kind = AlertKind.Start,
                        label = stringResource(R.string.teams_alerts_start),
                        whenText = stringResource(R.string.teams_alerts_start_when),
                        checked = alerts.start,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.start",
                        onSetAlert = onSetAlert,
                    )
                    AlertSwitch(
                        kind = AlertKind.Goals,
                        label = stringResource(R.string.teams_alerts_goals),
                        // Both teams' goals, which is what the decider counts: the name of the followed team
                        // here would promise a filter the alert does not apply.
                        whenText = stringResource(R.string.teams_alerts_goals_when),
                        checked = alerts.goals,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.goals",
                        onSetAlert = onSetAlert,
                    )
                    AlertSwitch(
                        kind = AlertKind.Intermission,
                        label = stringResource(R.string.teams_alerts_intermission),
                        whenText = stringResource(R.string.teams_alerts_intermission_when),
                        checked = alerts.intermission,
                        enabled = canNotify && !busy,
                        tag = "teams.alerts.intermission",
                        onSetAlert = onSetAlert,
                    )

                    AlertGroup(stringResource(R.string.teams_alerts_group_after), "teams.alerts.group.after")
                    AlertSwitch(
                        kind = AlertKind.Final,
                        label = stringResource(R.string.teams_alerts_final),
                        whenText = stringResource(R.string.teams_alerts_final_when),
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

/**
 * The one row that asks whether and when at once: a name, the answer spelled out, and four answers across the width.
 *
 * "Off" is an answer rather than a switch, so nothing in the sheet looks like a second alert; and the second line
 * restates the choice in words ("One alert, 30 min before each game"), so "1 hour" cannot be read as "every hour".
 */
@Composable
private fun ReminderBlock(
    alerts: TeamAlerts,
    enabled: Boolean,
    onSetReminder: (Int?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("teams.alerts.reminder")) {
        Text(
            stringResource(R.string.teams_alerts_reminder),
            style = MaterialTheme.typography.bodyLarge,
            color = SHEET_INK,
        )
        Text(
            if (alerts.upcoming) {
                stringResource(R.string.teams_alerts_reminder_summary, leadLabel(alerts.leadMinutes))
            } else {
                stringResource(R.string.teams_alerts_reminder_off_summary)
            },
            Modifier.padding(top = 2.dp, bottom = 8.dp).testTag("teams.alerts.reminder.summary"),
            style = MaterialTheme.typography.bodySmall,
            color = SHEET_MUTED,
        )
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
        ) {
            ReminderSegment(
                label = stringResource(R.string.teams_alerts_reminder_off),
                selected = !alerts.upcoming,
                accentWhenSelected = false,
                enabled = enabled,
                tag = "teams.alerts.reminder.off",
                onClick = { onSetReminder(null) },
            )
            UpcomingLead.allowed.forEach { minutes ->
                ReminderSegment(
                    label = leadLabel(minutes),
                    selected = alerts.upcoming && alerts.leadMinutes == minutes,
                    accentWhenSelected = true,
                    enabled = enabled,
                    tag = "teams.alerts.reminder.$minutes",
                    onClick = { onSetReminder(minutes) },
                )
            }
        }
    }
}

/**
 * One answer of the reminder.
 *
 * A chosen time fills with the accent; Off fills neutral. Orange is this app's "on", and a default Off wearing it
 * would read as "reminders are on" from across the room.
 */
@Composable
private fun RowScope.ReminderSegment(
    label: String,
    selected: Boolean,
    accentWhenSelected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val fill =
        when {
            !selected -> Color.Transparent
            accentWhenSelected -> Roux.accents.fill
            else -> SHEET_OFF_FILL
        }
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = Reach.MIN_TARGET_DP.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .background(fill, SEGMENT_SHAPE)
            .border(BorderStroke(1.dp, if (selected) fill else SHEET_MUTED), SEGMENT_SHAPE)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            // On the accent the text is the panel's near-black (7:1); white there would be 3.3:1.
            color = if (selected && accentWhenSelected) SHEET_PANEL else SHEET_INK,
            textAlign = TextAlign.Center,
        )
    }
}

/** The lead as a sentence says it: "15 min", "30 min", "1 hour". */
@Composable
private fun leadLabel(minutes: Int): String =
    stringResource(
        when (minutes) {
            15 -> R.string.teams_alerts_reminder_15
            30 -> R.string.teams_alerts_reminder_30
            else -> R.string.teams_alerts_reminder_60
        },
    )

/**
 * A heading above a run of rows.
 *
 * Three things separate it from a row label, and all three are needed: a hairline above it starts the run, capitals
 * and letter spacing make it read as a label rather than a sentence, and the muted colour keeps it quieter than the
 * rows it introduces. A heading that is only "a smaller grey line" is what made these read as rows in the first place.
 */
@Composable
private fun AlertGroup(
    label: String,
    tag: String,
) {
    Column(Modifier.fillMaxWidth().padding(top = SHEET_GROUP_GAP).testTag(tag)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SHEET_RULE))
        Text(
            label.uppercase(),
            Modifier.padding(top = 10.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = SHEET_MUTED,
            letterSpacing = 1.sp,
        )
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
        colors = ButtonDefaults.textButtonColors(contentColor = Roux.accents.fill),
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
    /** What the alert is, in one line, under the name: the label says which alert, this says when it arrives. */
    whenText: String? = null,
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
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = SHEET_INK)
            if (whenText != null) {
                Text(whenText, style = MaterialTheme.typography.bodySmall, color = SHEET_MUTED)
            }
        }
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
