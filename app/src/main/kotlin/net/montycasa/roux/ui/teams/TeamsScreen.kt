@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.listRow
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.scores.TeamLogo
import net.montycasa.roux.ui.theme.Roux
import androidx.compose.material.icons.filled.Star as StarFilled

/** Matches the standings table's badge, so a team looks the same on every screen. */
private val TEAM_CREST_SIZE = 48.dp

/** 48 dp, the floor a target may have. The crest fills it, so the row is all crest and a name. */
private val ROW_HEIGHT = 48.dp

/**
 * 2 dp between rows.
 *
 * A list is not a row of buttons: Material draws 48 dp rows straight against each other and tells them apart by
 * content, so the 8 dp clearance the audit asks between independent controls is paid once per row and buys
 * nothing here. Both row targets declare themselves list rows, which is what lets the clearance rule stand for
 * everything else.
 */
private val ROW_GAP = 2.dp

@Composable
fun TeamsScreen(
    state: TeamsUiState,
    onToggle: (TeamKey) -> Unit,
    onRetry: () -> Unit,
    onOpenAlerts: (TeamRef) -> Unit = {},
    onCloseAlerts: () -> Unit = {},
    onSetStartAlert: (Boolean) -> Unit = {},
    onSetGoalAlert: (Boolean) -> Unit = {},
    /** Whether the runtime permission has been granted. Missing, a tap asks for it rather than greying out. */
    permissionGranted: Boolean = true,
    /** Whether the app's notifications are on at all. Off, nothing can help but the system settings screen. */
    notificationsEnabled: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {},
    onEnableNotifications: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
    /** The prompt is shown once per visit: a dialog that reappears on every recomposition is a dialog to escape. */
    promptDismissed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier
                .fillMaxSize()
                .padding(Reach.EDGE_INSET_DP.dp),
            verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
        ) {
            // The header wraps its content instead of taking a weight. Sharing the screen 50/50 with the list
            // leaves a picker that shows five of thirty-two teams under a large blank block, which is exactly
            // what an audit-only reading of this screen produced.
            Text(stringResource(R.string.teams_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.teams_instructions), style = MaterialTheme.typography.bodyLarge)
            when {
                state.rows.isEmpty() && state.isRefreshing -> Text(stringResource(R.string.teams_loading))
                state.rows.isEmpty() && state.error != null -> Text(stringResource(R.string.teams_load_error))
                state.rows.isEmpty() -> Text(stringResource(R.string.teams_empty))
                state.freshness is Freshness.Stale -> Text(stringResource(R.string.teams_stale))
            }
            // A disabled toggle with no explanation looks broken, so the reason is stated where the toggles are.
            when {
                !state.canEdit -> Text(stringResource(R.string.teams_read_only), style = MaterialTheme.typography.bodyMedium)
                state.saveFailed -> Text(stringResource(R.string.teams_save_failed), style = MaterialTheme.typography.bodyMedium)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // The clearance goes *inside* the scroll as a trailing spacer, not around the viewport: a
                    // bottom padding applied before verticalScroll shrinks the viewport, which is what left a
                    // 56 dp band of empty page between the last row and the bar - the strip that reads as a
                    // missing sub-navigation bar on this tab. Inside, the list runs to the bar and the clearance
                    // only exists at the end of the scroll, which is what it was for.
                    .verticalScroll(rememberScrollState())
                    .testTag("teams.list"),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                state.rows.forEach { team ->
                    val selected = team.key in state.favourites
                    val label = stringResource(R.string.teams_toggle_label, team.name ?: team.abbrev)
                    val description = stringResource(if (selected) R.string.teams_favourite else R.string.teams_not_favourite)
                    val unknown = stringResource(R.string.teams_unknown_state)
                    val alertsOn = team.key in state.alerting
                    val alertState = stringResource(if (alertsOn) R.string.teams_alerts_on else R.string.teams_alerts_off)
                    val openLabel = stringResource(R.string.teams_alerts_open, team.name ?: team.abbrev, alertState)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // 48 dp, the floor a target may have, rather than the 56 a primary control wants: a team
                            // is a list entry reached by scrolling, and the extra 8 dp bought nothing but three fewer
                            // teams on screen.
                            .heightIn(min = ROW_HEIGHT)
                            .testTag("teams.row.${team.abbrev}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
                    ) {
                        // The crest, as the standings table has: the same badge drawn from the same feed, so a team
                        // looks the same wherever it appears.
                        TeamLogo(team, TEAM_CREST_SIZE)
                        Text(
                            // The club's full name, city included: "Toronto Maple Leafs", not "Maple Leafs".
                            // The row is scanned by club, but dropping the city made several rows ambiguous.
                            team.name?.takeIf { it.isNotBlank() } ?: team.abbrev,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // The bell replaces the "Alerts" text button in the same slot. It is still a
                        // separate target rather than part of a clickable row: a clickable row merges the
                        // controls into one node, and the whole point of B1 is to make only the crest and
                        // name tappable. Its spoken name comes from the same string the button used.
                        Box(
                            Modifier
                                .size(ROW_HEIGHT)
                                .listRow()
                                .reachTarget("teams.alerts.open.${team.abbrev}")
                                .testTag("teams.alerts.open.${team.abbrev}")
                                .clickable { onOpenAlerts(team) }
                                .semantics { contentDescription = openLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                alertBellIcon(alertsOn),
                                contentDescription = null,
                                Modifier.size(24.dp),
                                tint =
                                    if (alertsOn) {
                                        Roux.accents.signature
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        // The star is the same favourite toggle the checkbox was, in the same slot and
                        // with the same tag. Filled means saved, outlined means not; the announcement is
                        // the checkbox's own wording, so a screen reader hears the same state either way.
                        val toggle =
                            Modifier
                                // The row height, not the 56 a primary control wants: the list gap supplies the
                                // separation, so a 56 dp target would add 8 dp of nothing to every row of a
                                // thirty-two item list.
                                .size(ROW_HEIGHT)
                                .listRow()
                                .reachTarget("teams.toggle.${team.abbrev}")
                        val star =
                            if (state.selectionKnown && state.canEdit) {
                                toggle
                                    .toggleable(value = selected, role = Role.Checkbox) { onToggle(team.key) }
                                    .semantics {
                                        // The name is the team, not its state. "Favourite team: X" spoken over a team
                                        // that is not a favourite reads as a lie, and saying whether it is one is
                                        // what stateDescription is for.
                                        contentDescription = label
                                        stateDescription = description
                                    }
                            } else {
                                // A null toggle removes the click action instead of setting a Disabled flag, so
                                // a read-only store cannot be pressed and an unknown selection cannot claim a state.
                                toggle.semantics {
                                    contentDescription = label
                                    stateDescription = if (state.selectionKnown) description else unknown
                                    disabled()
                                }
                            }
                        Box(star, contentAlignment = Alignment.Center) {
                            Icon(
                                if (selected) Icons.Filled.StarFilled else starOutline,
                                contentDescription = null,
                                Modifier.size(24.dp),
                                tint =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
                // Clearance for the last row, inside the scroll: the row below the viewport has to be reachable
                // without a target sitting under the gesture bar.
                Spacer(Modifier.height(Reach.MIN_TARGET_DP.dp + Reach.MIN_TARGET_GAP_DP.dp))
            }
            if (state.canRetry || (state.isRefreshing && state.error != null)) {
                Button(
                    onClick = onRetry,
                    enabled = !state.isRefreshing,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                            .primaryControl("teams.retry"),
                ) {
                    Text(stringResource(if (state.isRefreshing) R.string.teams_loading else R.string.teams_retry))
                }
            }
        }

        // The sheet is drawn over the list rather than replacing it: the team being configured stays visible behind
        // the scrim, which is what makes "this sheet belongs to that row" legible.
        state.alerts?.let { alerts ->
            TeamAlertsSheet(
                alerts = alerts,
                canNotify = notificationsEnabled,
                busy = state.alertsBusy,
                // Asked inside the sheet rather than in a dialog on top of it: the app has one surface for asking
                // things, and a dialog stacked on a sheet is a second thing to escape.
                showPrompt = (!permissionGranted || !notificationsEnabled) && !promptDismissed,
                onEnableNotifications = onEnableNotifications,
                onDismissPrompt = onDismissPrompt,
                onDismiss = onCloseAlerts,
                onSetStart = onSetStartAlert,
                onSetGoals = onSetGoalAlert,
                onOpenSystemSettings = onOpenNotificationSettings,
            )
        }
    }
}

/**
 * The empty star, drawn as a stroke rather than a filled silhouette.
 *
 * Material's core icon set has only the filled "star" shape: its outlined theme is the same solid star, so using
 * it for "not favorite" would leave filled and empty identical. This is the drawer's own star path with a stroke
 * and no fill, which is the smallest way to show the state without pulling in the extended icon set for one glyph.
 */
private val starOutline =
    ImageVector
        .Builder("Star outline", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M12,3.6 L14.6,8.9 L20.5,9.8 L16.2,13.9 L17.2,19.7 L12,16.9 L6.8,19.7 L7.8,13.9 L3.5,9.8 L9.4,8.9 Z"),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

/**
 * Material's ringing bell at the size and path the core icon set uses.
 *
 * `material-icons-core` ships the ordinary bell but not [NotificationsActive]. The alternative was the extended
 * icon set for one glyph; this is the same 24 dp path, drawn here so the dependency stays small.
 */
private val notificationsActive =
    ImageVector
        .Builder("NotificationsActive", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M7.58,4.08 L6.15,2.65 C3.75,4.48 2.17,7.3 2.03,10.5 H4.03 C4.18,7.85 5.54,5.53 7.58,4.08 Z " +
                    "M19.97,10.5 H21.97 C21.82,7.3 20.24,4.48 17.85,2.65 L16.43,4.08 C18.45,5.53 19.82,7.85 19.97,10.5 Z " +
                    "M18,11 C18,7.93 16.36,5.36 13.5,4.68 V4 C13.5,3.17 12.83,2.5 12,2.5 C11.17,2.5 10.5,3.17 10.5,4 V4.68 " +
                    "C7.63,5.36 6,7.92 6,11 V16 L4,18 V19 H20 V18 L18,16 V11 Z " +
                    "M12,22 C12.14,22 12.27,21.99 12.4,21.96 C13.05,21.82 13.58,21.38 13.84,20.78 C13.94,20.54 13.99,20.28 " +
                    "13.99,20 H9.99 C10,21.1 10.9,22 12,22 Z",
            ),
            fill = SolidColor(Color.Black),
        ).build()

/** The ringing glyph when a team alerts, the ordinary bell when it does not. */
internal fun alertBellIcon(alerting: Boolean): ImageVector = if (alerting) notificationsActive else Icons.Filled.Notifications
