@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.detail.TeamLogo
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.Roux
import kotlin.time.Clock
import kotlin.time.Instant

/** The same crest size the Teams picker uses, so a club looks like itself on both screens. */
private val CREST_SIZE = 56.dp

/**
 * A club's page: who the club is, what the cached standings row says about its season, and the
 * season schedule underneath it.
 *
 * The schedule is a second request, so its success or failure is kept separate from the header. A
 * club in the off-season still has a page worth reading, which is why an empty group keeps its plain
 * line instead of disappearing.
 */
@Composable
fun TeamDetailScreen(
    state: TeamDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onGameClick: (ClubGame) -> Unit = {},
    now: Instant = Clock.System.now(),
) {
    val row = state.row
    val team = row?.team ?: TeamRef(state.teamKey)
    val fullName = fullTeamName(team)
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("team.page.${state.teamKey.abbrev}"),
    ) {
        TeamDetailHeader(fullName = fullName, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            TeamCard(
                team = team,
                fullName = fullName,
                row = row,
                isLoading = state.isLoading,
            )
            if (row != null) {
                Text(
                    divisionLine(row),
                    modifier = Modifier.padding(top = 12.dp).testTag("team.division"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TeamScheduleSection(
                teamKey = state.teamKey,
                schedule = state.schedule,
                isRefreshing = state.isLoading,
                onRetry = onRetry,
                onGameClick = onGameClick,
                now = now,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TeamDetailHeader(
    fullName: String,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .testTag("team.back")
                .reachTarget("team.back"),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            fullName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun TeamCard(
    team: TeamRef,
    fullName: String,
    row: StandingRow?,
    isLoading: Boolean,
) {
    val accents = Roux.accents
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("team.header"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.testTag("team.crest")) {
                TeamLogo(team = team, crestSize = CREST_SIZE, imageTag = "team.logo")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    fullName,
                    modifier = Modifier.testTag("team.name"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    row != null -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                row.record,
                                modifier = Modifier.testTag("team.record"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                pluralStringResource(R.plurals.team_points, row.pts, row.pts),
                                modifier = Modifier.testTag("team.points"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = accents.signature,
                            )
                        }
                    }

                    isLoading -> {
                        Text(
                            stringResource(R.string.team_loading_record),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        Text(
                            stringResource(R.string.team_no_record),
                            modifier = Modifier.testTag("team.noRecord"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (row != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    rankLabel(row.conferenceSequence, R.string.team_rank_conference),
                    modifier = Modifier.testTag("team.rank.conference"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rankLabel(row.leagueSequence, R.string.team_rank_league),
                    modifier = Modifier.testTag("team.rank.league"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun rankLabel(
    rank: Int,
    @androidx.annotation.StringRes label: Int,
): String = stringResource(label, ordinal(rank))

/** A rank of zero means the feed did not place the club, so the page says nothing rather than "0th". */
internal fun ordinal(rank: Int): String {
    if (rank <= 0) return "–"
    val suffix =
        when {
            rank % 100 in 11..13 -> "th"
            rank % 10 == 1 -> "st"
            rank % 10 == 2 -> "nd"
            rank % 10 == 3 -> "rd"
            else -> "th"
        }
    return "$rank$suffix"
}

/** The full club name, place first, using the same table the Teams row and the game page already use. */
internal fun fullTeamName(team: TeamRef): String {
    val (city, name) = TeamNames.cityAndName(team)
    return if (city.isNullOrBlank()) name else "$city $name"
}

/** The feed's conference and division, kept as words rather than abbreviations. */
@Composable
internal fun divisionLine(row: StandingRow): String =
    stringResource(
        R.string.team_division_line,
        conferenceName(row.conference),
        divisionName(row.division),
    )

internal fun conferenceName(conference: Conference): String =
    when (conference) {
        Conference.Eastern -> "Eastern"
        Conference.Western -> "Western"
        is Conference.Unknown -> conference.raw.trim()
    }

internal fun divisionName(division: Division): String =
    when (division) {
        Division.Atlantic -> "Atlantic"
        Division.Metropolitan -> "Metropolitan"
        Division.Central -> "Central"
        Division.Pacific -> "Pacific"
        is Division.Unknown -> division.raw.trim()
    }
