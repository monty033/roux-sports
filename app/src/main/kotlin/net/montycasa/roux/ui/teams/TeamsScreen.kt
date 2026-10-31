@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTarget

@Composable
fun TeamsScreen(
    state: TeamsUiState,
    onToggle: (TeamKey) -> Unit,
    onRetry: () -> Unit,
    onOpenAlerts: (TeamRef) -> Unit = {},
    onCloseAlerts: () -> Unit = {},
    onSetStartAlert: (Boolean) -> Unit = {},
    onSetGoalAlert: (Boolean) -> Unit = {},
    /** Whether alerts could be delivered at all; false shows the sheet's honest, disabled state. */
    canNotify: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {},
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
                    // R5 containment measures a target's real bounds, so a row straddling the bottom of the
                    // window is a violation rather than something a scroll would fix. One row of clearance
                    // below the viewport keeps the last visible row inside the window, and leaves the list
                    // clear of the gesture bar.
                    .padding(bottom = Reach.PRIMARY_MIN_HEIGHT_DP.dp + Reach.MIN_TARGET_GAP_DP.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("teams.list"),
                verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
            ) {
                state.rows.forEach { team ->
                    val selected = team.key in state.favourites
                    val label = stringResource(R.string.teams_toggle_label, team.name ?: team.abbrev)
                    val description = stringResource(if (selected) R.string.teams_favourite else R.string.teams_not_favourite)
                    val unknown = stringResource(R.string.teams_unknown_state)
                    val openLabel = stringResource(R.string.teams_alerts_open, team.name ?: team.abbrev)
                    Row(
                        Modifier.fillMaxWidth().testTag("teams.row.${team.abbrev}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
                    ) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        // A separate target rather than a clickable row: a clickable row merges the favourite
                        // checkbox into one node, so the checkbox stops existing as its own control. Sized and
                        // spaced like the checkbox, because it is the same kind of thing - a control in a list.
                        Box(
                            Modifier
                                .size(Reach.PRIMARY_MIN_HEIGHT_DP.dp + Reach.MIN_TARGET_GAP_DP.dp)
                                .reachTarget("teams.alerts.open.${team.abbrev}")
                                .testTag("teams.alerts.open.${team.abbrev}")
                                .clickable(onClickLabel = openLabel) { onOpenAlerts(team) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.teams_alerts_button),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        // A row in a thirty-two item list is not a primary control. R1 puts primary controls in
                        // the bottom half of the screen, which for a scrolling collection would squeeze the list
                        // into that half - five teams visible instead of ten. The toggles are reached by
                        // scrolling, so they declare size and spacing and leave the vertical zone to the one fixed
                        // action on this screen.
                        val toggle =
                            Modifier
                                .size(Reach.PRIMARY_MIN_HEIGHT_DP.dp + Reach.MIN_TARGET_GAP_DP.dp)
                                .reachTarget("teams.toggle.${team.abbrev}")
                        if (state.selectionKnown) {
                            Checkbox(
                                checked = selected,
                                // Null disables the checkbox: Compose renders that as non-interactive, and a
                                // read-only store has nothing useful to do with the tap.
                                onCheckedChange =
                                    if (state.canEdit) {
                                        { onToggle(team.key) }
                                    } else {
                                        null
                                    },
                                modifier =
                                    toggle.semantics {
                                        // The name is the team, not its state. "Favourite team: X" spoken over a team
                                        // that is not a favourite reads as a lie, and saying whether it is one is
                                        // what stateDescription is for.
                                        contentDescription = label
                                        stateDescription = description
                                    },
                            )
                        } else {
                            // Nothing was read, so an unchecked box would assert "not a favourite" about teams
                            // this process never looked up. Indeterminate states what is actually true.
                            TriStateCheckbox(
                                state = ToggleableState.Indeterminate,
                                onClick = null,
                                modifier =
                                    toggle.semantics {
                                        contentDescription = label
                                        stateDescription = unknown
                                    },
                            )
                        }
                    }
                }
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
                canNotify = canNotify,
                busy = state.alertsBusy,
                onDismiss = onCloseAlerts,
                onSetStart = onSetStartAlert,
                onSetGoals = onSetGoalAlert,
                onOpenSystemSettings = onOpenNotificationSettings,
            )
        }
    }
}
