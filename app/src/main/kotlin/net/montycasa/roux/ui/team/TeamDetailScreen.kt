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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import net.montycasa.roux.ui.common.SubNavigationRow
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.detail.TeamLogo
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.Roux
import kotlin.time.Clock
import kotlin.time.Instant

/** The same crest size the Teams picker uses, so a club looks like itself on both screens. */
private val CREST_SIZE = 56.dp

/**
 * A club's page: who the club is, its record, and a sub-navigation that opens on the season schedule.
 *
 * Only the tab's content list scrolls: the back row, club card and tab row stay put. Each tab owns
 * its own request, so one failing does not take the header or the other tab away.
 */
@Composable
fun TeamDetailScreen(
    state: TeamDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onPlayersRetry: () -> Unit = {},
    /** Superseded callbacks kept for callers compiled against the two old tabs. */
    onRosterRetry: (() -> Unit)? = null,
    onStatsRetry: (() -> Unit)? = null,
    onGameClick: (ClubGame) -> Unit = {},
    now: Instant = Clock.System.now(),
) {
    val row = state.row
    val team = row?.team ?: TeamRef(state.teamKey)
    val fullName = fullTeamName(team)
    // Tab selection is local view state. It is not a route, so Back leaves the club page rather than
    // walking through tabs the reader visited.
    var selectedTab by rememberSaveable { mutableStateOf(TeamTab.Schedule) }
    // Each tab keeps its own saved list state: switching to Players and back restores the schedule's
    // scroll position instead of re-anchoring or scrolling to the top.
    val tabState = rememberSaveableStateHolder()
    val retryPlayers: () -> Unit = {
        onPlayersRetry()
        onRosterRetry?.invoke()
        onStatsRetry?.invoke()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("team.page.${state.teamKey.abbrev}"),
    ) {
        TeamDetailHeader(fullName = fullName, onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
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
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            tabState.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    TeamTab.Schedule -> {
                        TeamScheduleList(
                            teamKey = state.teamKey,
                            schedule = state.schedule,
                            isRefreshing = state.isLoading,
                            onRetry = onRetry,
                            onGameClick = onGameClick,
                            now = now,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TeamTab.Players -> {
                        TeamPlayersList(
                            roster = state.roster,
                            stats = state.stats,
                            isRosterRefreshing = state.isRosterLoading,
                            isStatsRefreshing = state.isStatsLoading,
                            onRetry = retryPlayers,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TeamTab.TeamStats -> {
                        // Team Stats is the next PR; this branch keeps the switch exhaustive.
                    }
                }
            }
        }
        // The page's own second level sits at the bottom, where the app's other navigation lives, so the thumb
        // reaches it without crossing the list and the header keeps the height the card needs.
        TeamTabRow(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

/** The club page's own second level. Players is live; Team Stats remains a coming-soon seat. */
internal enum class TeamTab {
    Schedule,
    Players,
    TeamStats,
}

@Composable
private fun TeamTabRow(
    selected: TeamTab,
    onSelect: (TeamTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubNavigationRow(
        entries = TeamTab.entries,
        selected = selected,
        onSelect = onSelect,
        tagPrefix = "team.tab",
        modifier = modifier,
        enabled = { it != TeamTab.TeamStats },
        label = { tab ->
            stringResource(
                when (tab) {
                    TeamTab.Schedule -> R.string.team_tab_schedule
                    TeamTab.Players -> R.string.team_tab_players
                    TeamTab.TeamStats -> R.string.team_tab_team_stats
                },
            )
        },
    )
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
