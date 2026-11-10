@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import net.montycasa.roux.R
import net.montycasa.roux.domain.policy.LeagueCalendar
import kotlin.time.Instant

/**
 * The label for a club's numbers when they belong to a season other than the one the device is in, or null when they
 * are the current season, which needs no label.
 *
 * [season] is the season the data carries, not the one that was asked for, so a value that fell back to last season is
 * named as last season. `20252026` reads as `2025-26`.
 */
internal fun pastSeasonLabel(
    season: String?,
    now: Instant,
    zone: TimeZone,
): String? {
    if (season == null || season == LeagueCalendar.seasonId(now, zone)) return null
    return "${season.take(4)}-${season.takeLast(2)}"
}

/**
 * The muted line that names the season a list is showing, placed above the list it describes. [padding] sets the
 * inset, so a card that is already inset can line the note up with its own edge. [wording] is the sentence the label
 * goes into, so a caller that must say more than "season" (the final record) uses the same style.
 */
@Composable
internal fun SeasonNote(
    label: String,
    tag: String,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    wording: Int = R.string.team_season_label,
) {
    Text(
        stringResource(wording, label),
        modifier = Modifier.padding(padding).testTag(tag),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
