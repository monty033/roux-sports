@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.montycasa.roux.R
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.RosterPlayer
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.detail.PeriodDivider
import net.montycasa.roux.ui.theme.Roux

private val NUMBER_WIDTH = 30.dp
private val HEADSHOT_SIZE = 36.dp
private val POSITION_WIDTH = 28.dp
private val ROW_MIN_HEIGHT = 52.dp

/** The Roster tab: the feed's three groups, each ordered by sweater number by the adapter. */
@Composable
internal fun TeamRosterList(
    roster: Loadable<ClubRoster>?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        roster == null -> {
            Box(modifier.fillMaxSize())
        }

        roster.data == null && roster.error != null -> {
            Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                RosterFailure(isRefreshing = isRefreshing, onRetry = onRetry)
            }
        }

        else -> {
            val data = roster.data
            if (data == null ||
                (data.forwards.isEmpty() && data.defense.isEmpty() && data.goalies.isEmpty())
            ) {
                Text(
                    stringResource(R.string.team_roster_empty),
                    modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("team.roster.empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier.fillMaxSize()) {
                    if (roster.error != null) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            RosterFailure(isRefreshing = isRefreshing, onRetry = onRetry)
                        }
                    }
                    RosterGroups(data, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun RosterGroups(
    roster: ClubRoster,
    modifier: Modifier = Modifier,
) {
    val sections =
        listOf(
            RosterSection(stringResource(R.string.team_roster_forwards), "forwards", roster.forwards),
            RosterSection(stringResource(R.string.team_roster_defense), "defense", roster.defense),
            RosterSection(stringResource(R.string.team_roster_goalies), "goalies", roster.goalies),
        )
    LazyColumn(
        modifier = modifier.testTag("team.roster.list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "header-${section.tag}") {
                RosterGroupHeader(section)
            }
            section.players.forEach { player ->
                item(key = "player-${player.id ?: player.name}") {
                    RosterRow(player = player)
                }
            }
            if (sectionIndex < sections.lastIndex) {
                item(key = "divider-${section.tag}") {
                    PeriodDivider(tag = "team.roster.divider")
                }
            }
        }
    }
}

private data class RosterSection(
    val label: String,
    val tag: String,
    val players: List<RosterPlayer>,
)

@Composable
private fun RosterGroupHeader(section: RosterSection) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .padding(top = 8.dp)
            .testTag("team.roster.group.${section.tag}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(NUMBER_WIDTH + 8.dp + HEADSHOT_SIZE + 10.dp))
        Text(
            section.label,
            modifier = Modifier.weight(1f).testTag("team.roster.group.${section.tag}.label"),
            style = MaterialTheme.typography.labelSmall,
            color = Roux.accents.muted,
            maxLines = 1,
        )
        Text(
            stringResource(R.string.team_roster_position),
            modifier = Modifier.width(POSITION_WIDTH),
            style = MaterialTheme.typography.labelSmall,
            color = Roux.accents.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun RosterRow(player: RosterPlayer) {
    val tag = "team.roster.player.${player.id ?: player.name}"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            player.sweaterNumber?.toString() ?: "–",
            modifier = Modifier.width(NUMBER_WIDTH).testTag("$tag.number"),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = Roux.accents.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        PlayerHeadshot(player = player)
        Spacer(Modifier.width(10.dp))
        Text(
            player.name,
            modifier = Modifier.weight(1f).testTag("$tag.name"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            player.positionCode ?: "–",
            modifier = Modifier.width(POSITION_WIDTH).testTag("$tag.position"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/**
 * The player's mug, with the same kind of lettered circle TeamLogo uses while artwork is absent.
 * No silhouette is drawn: a missing headshot reads as initials, not as a fake player.
 */
@Composable
private fun PlayerHeadshot(player: RosterPlayer) {
    var loaded by remember(player.headshotUrl) { mutableStateOf(false) }
    val tag = "team.roster.headshot.${player.id ?: player.name}"
    Box(
        Modifier.size(HEADSHOT_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                initials(player.name),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        player.headshotUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag(tag),
                contentScale = ContentScale.Crop,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

private fun initials(name: String): String =
    name
        .split(' ')
        .mapNotNull { part -> part.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")

@Composable
private fun RosterFailure(
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.team_roster_error),
            modifier = Modifier.testTag("team.roster.error"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        RetryButton(onRetry = onRetry, isRefreshing = isRefreshing, retryTag = "team.roster.retry")
    }
}
