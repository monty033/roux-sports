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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

/** The sheet's own surface, matching the leagues menu so the two read as the same material. */
private val SHEET_PANEL = Color(0xFF1C1C1E)

/** How much of the window the scrim dims at full height; the same value the menu uses. */
private const val SCRIM_ALPHA = 0.55f

/**
 * One team's alerts, in a sheet that covers the bottom of the window.
 *
 * Deliberately not draggable. The leagues menu follows a finger because it is a list of unknown length that the
 * reader scrolls through; this is three rows of fixed height that either are or are not wanted, and a sheet that can
 * be dragged adds a second way to dismiss something that has a Done button and a scrim. It closes the way it opened.
 *
 * A disabled switch is not shown as "off": a user who has not granted the notification permission, or who silenced
 * the app, is told why the switches do nothing and is sent to the one place that can change it, rather than being
 * left tapping a control that quietly refuses.
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
                .heightIn(max = maxHeight * 0.56f)
                .background(SHEET_PANEL, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .verticalScroll(rememberScrollState())
                .padding(Reach.EDGE_INSET_DP.dp)
                .testTag("teams.alerts.sheet"),
            verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
        ) {
            Text(
                stringResource(R.string.teams_alerts_title, alerts.team.name ?: alerts.team.key.abbrev),
                style = MaterialTheme.typography.headlineSmall,
            )

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("teams.alerts.disclosure"),
            )

            if (!canNotify) {
                Text(
                    stringResource(R.string.teams_alerts_blocked),
                    style = MaterialTheme.typography.bodyMedium,
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
                    modifier = Modifier.testTag("teams.alerts.save_failed"),
                )
            }

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
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
