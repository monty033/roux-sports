@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.NflPlayerId
import net.montycasa.roux.domain.model.NflPlayerStatus
import net.montycasa.roux.domain.model.NflRosterPlayer

/**
 * The roster tab: one row per player, in the order the adapter established.
 *
 * Each row leads with the player's photo, not his number. The number is not dropped from the app: it arrives on the
 * player's own page, which has room to state it, and it stays in the model because the roster's order uses it.
 *
 * A player the feed gives no headshot shows his initials. A player the feed gives no status prints no status at all:
 * that silence is not filled with "Active".
 *
 * Every row opens its player's page. The row's id is wrapped in [NflPlayerId] immediately, so a football player can
 * never be read as a hockey player of the same digits.
 */
@Composable
internal fun NflTeamRosterList(
    roster: NflClubRoster,
    onPlayerClick: (NflPlayerId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.testTag("nflTeam.roster")) {
        items(roster.players, key = { it.id }) { player ->
            NflRosterRow(player, onPlayerClick)
        }
    }
}

@Composable
private fun NflRosterRow(
    player: NflRosterPlayer,
    onPlayerClick: (NflPlayerId) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_ROW_HEIGHT)
                // A tap and a screen reader's click both report the player. Not `clickable`: that merges the row's
                // jersey, name and status into one semantics node, and those columns are asserted and read individually.
                .pointerInput(player.id) {
                    detectTapGestures { onPlayerClick(NflPlayerId(player.id)) }
                }.semantics {
                    role = Role.Button
                    onClick(label = player.name) {
                        onPlayerClick(NflPlayerId(player.id))
                        true
                    }
                }.padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("nflTeam.rosterRow.${player.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The photo replaces the number: the roster reads as people, and the number still arrives on the player's own
        // page, where there is room to say it. A player the feed gives no headshot keeps the space and shows initials,
        // exactly as the hockey roster does, so the names stay aligned either way.
        NflRosterHeadshot(player)
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = player.positionAbbreviation,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(POSITION_WIDTH),
            maxLines = 1,
        )
        val statusWording =
            when (val status = player.status) {
                is NflPlayerStatus.Active -> status.wording
                is NflPlayerStatus.Other -> status.wording
                NflPlayerStatus.NotStated -> null
            }
        if (statusWording != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusWording,
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (player.status is NflPlayerStatus.Other) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.testTag("nflTeam.rosterStatus.${player.id}"),
                maxLines = 1,
            )
        }
    }
}

/** A roster row's photo: the feed's headshot in a circle, with the player's initials standing in until it loads. */
@Composable
private fun NflRosterHeadshot(player: NflRosterPlayer) {
    var loaded by remember(player.headshotUrl) { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .size(HEADSHOT_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("nflTeam.rosterHeadshot.${player.id}"),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = initials(player.name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        player.headshotUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag("nflTeam.rosterHeadshot.image.${player.id}"),
                contentScale = ContentScale.Crop,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

/** The first letter of each name, at most two, for a player with no photograph yet. */
private fun initials(name: String): String =
    name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

/** The photo's diameter, matched to the hockey roster so the two leagues' lists read the same. */
private val HEADSHOT_SIZE = 40.dp

private val POSITION_WIDTH = 44.dp

/** A roster row is a reading row, not a tap target, but it still clears the app's minimum touch height. */
private val MIN_ROW_HEIGHT = 48.dp
