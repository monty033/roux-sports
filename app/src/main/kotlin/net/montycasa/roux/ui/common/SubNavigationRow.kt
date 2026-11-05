@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.Roux

/**
 * A second-level choice row: outlined cells, an accent border on the selected one, and no page chrome.
 *
 * The standings ranking strip and the club page's Schedule/Roster/Stats row are the same control, so
 * the styling lives here once. Entries can be disabled individually; a disabled entry keeps its 48 dp
 * cell, is not selectable, and says "Unavailable" to a screen reader instead of disappearing.
 *
 * @param tagPrefix the shared prefix for the group and each entry. Entries are tagged
 *   `"$tagPrefix.${entry}"`, which keeps the standings suite's existing `standings.mode.*` tags.
 * @param enabled per-entry availability. A disabled entry cannot be selected or clicked.
 * @param wrapLabels a label that does not fit one line wraps onto more lines instead of being cut off. Off, a label is one
 *   line, which is what the club and standings rows keep.
 * @param label the localized label for an entry.
 */
@Composable
fun <T> SubNavigationRow(
    entries: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    wrapLabels: Boolean = false,
    label: @Composable (T) -> String,
) {
    val unavailable = stringResource(R.string.common_unavailable)
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup()
            .reachTarget(tagPrefix),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            val entryEnabled = enabled(entry)
            val entrySelected = entryEnabled && entry == selected
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor =
                    when {
                        entrySelected -> Roux.accents.signature
                        entryEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                    },
                shape = RoundedCornerShape(12.dp),
                border =
                    BorderStroke(
                        1.dp,
                        when {
                            entrySelected -> Roux.accents.signature
                            entryEnabled -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = DISABLED_ALPHA)
                        },
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(
                            if (entryEnabled) {
                                Modifier.selectable(
                                    selected = entrySelected,
                                    enabled = true,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(entry) },
                                )
                            } else {
                                // A disabled peer stays present, keeps its cell and says why it cannot be used.
                                // Leaving a selectable action behind would let accessibility services offer a
                                // click that does nothing.
                                Modifier.semantics(mergeDescendants = true) {
                                    role = Role.RadioButton
                                    this.selected = false
                                    disabled()
                                    stateDescription = unavailable
                                }
                            },
                        ).testTag("$tagPrefix.$entry"),
            ) {
                Column(
                    Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label(entry),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = if (wrapLabels) Int.MAX_VALUE else 1,
                    )
                }
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
