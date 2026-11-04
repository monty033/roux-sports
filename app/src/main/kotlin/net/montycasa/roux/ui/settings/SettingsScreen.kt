@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.repo.UpcomingLead
import net.montycasa.roux.ui.reach.reachTarget

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

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The stored lead, already coerced by the repository that read it. */
    leadMinutes: Int = UpcomingLead.DEFAULT,
    onLeadMinutes: (Int) -> Unit = {},
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
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = mode == choice, role = Role.RadioButton, onClick = { onMode(choice) })
                            .reachTarget("settings.${choice.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                when (choice) {
                                    ThemeMode.System -> R.string.theme_system
                                    ThemeMode.Light -> R.string.theme_light
                                    ThemeMode.Dark -> R.string.theme_dark
                                },
                            ),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        RadioButton(selected = mode == choice, onClick = null)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_lead_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_lead_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            // The allowed set is the domain's, not a list copied here: a free number would be a fourth state the
            // alert has no meaning for, and a stored value outside the set has already read as the default.
            Column(Modifier.selectableGroup()) {
                UpcomingLead.allowed.forEach { minutes ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = leadMinutes == minutes,
                                role = Role.RadioButton,
                                onClick = { onLeadMinutes(minutes) },
                            ).reachTarget("settings.lead.$minutes")
                            .testTag("settings.lead.$minutes"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                when (minutes) {
                                    15 -> R.string.settings_lead_15
                                    30 -> R.string.settings_lead_30
                                    else -> R.string.settings_lead_60
                                },
                            ),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        RadioButton(selected = leadMinutes == minutes, onClick = null)
                    }
                }
            }
            AboutSection()
        }
    }
}
