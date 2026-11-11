@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.LeagueSummary
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.ui.common.SubNavigationRow
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.detail.TeamLogo
import net.montycasa.roux.ui.nav.NavSwipe
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.Roux
import kotlin.time.Clock
import kotlin.time.Instant

/** The same crest size the Teams picker uses, so a club looks like itself on both screens. */
private val CREST_SIZE = 56.dp

/** The same settle the player page's tabs use, so the two screens feel like one app. */
private const val TAB_SETTLE_MILLIS = 250

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
    /** Re-reads the league table behind the Team Stats tab only. */
    onLeagueStatsRetry: () -> Unit = {},
    /** Superseded callbacks kept for callers compiled against the two old tabs. */
    onRosterRetry: (() -> Unit)? = null,
    onStatsRetry: (() -> Unit)? = null,
    onGameClick: (ClubGame) -> Unit = {},
    onPlayerClick: (Long) -> Unit = {},
    now: Instant = Clock.System.now(),
) {
    val row = state.row
    val team = row?.team ?: TeamRef(state.teamKey)
    val fullName = fullTeamName(team)
    // Last season's record stands in only for a clean standings answer with no row: a failed request is not an empty table.
    val lastSeason =
        if (row == null && !state.isLoading && state.error == null) {
            previousSeasonRecord(state.leagueStats?.data, fullName, now, TimeZone.currentSystemDefault())
        } else {
            null
        }
    // The three tabs are pages, so a horizontal swipe moves between them the same way the player and game pages do.
    // Each page keeps its own saved list state through the holder below, so leaving a tab and coming back restores its
    // scroll position instead of re-anchoring or scrolling to the top.
    val pager = rememberPagerState { TeamTab.entries.size }
    val scope = rememberCoroutineScope()
    // During a swipe the strip follows the page the pager is heading to, not the one it is leaving, so it never lags
    // behind the finger.
    val shownTab = TeamTab.entries[if (pager.isScrollInProgress) pager.targetPage else pager.currentPage]
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
                lastSeason = lastSeason,
                isLoading = state.isLoading,
                standingsFailed = state.error != null,
                leagueLoading = state.isLeagueStatsLoading,
            )
        }
        HorizontalPager(
            state = pager,
            // Off, horizontal drags belong to the pager; on, the shell's pager would claim them instead.
            userScrollEnabled = !NavSwipe.TURNS_PAGES,
            // The neighbouring tab is composed ahead of the drag, so a swipe reveals a finished page, not an empty one.
            beyondViewportPageCount = 1,
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = tween(durationMillis = TAB_SETTLE_MILLIS, easing = FastOutSlowInEasing),
                ),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("team.pager"),
        ) { page ->
            val tab = TeamTab.entries[page]
            tabState.SaveableStateProvider(tab) {
                when (tab) {
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
                            onPlayerClick = onPlayerClick,
                            modifier = Modifier.fillMaxSize(),
                            now = now,
                        )
                    }

                    TeamTab.TeamStats -> {
                        TeamStatsList(
                            league = state.leagueStats,
                            teamName = row?.team?.name,
                            standingsLoading = state.isLoading,
                            standingsFailed = state.error != null && row == null,
                            isRefreshing = state.isLeagueStatsLoading,
                            onRetry = onLeagueStatsRetry,
                            modifier = Modifier.fillMaxSize(),
                            now = now,
                        )
                    }
                }
            }
        }
        // The page's own second level sits at the bottom, where the app's other navigation lives, so the thumb
        // reaches it without crossing the list and the header keeps the height the card needs.
        TeamTabRow(
            selected = shownTab,
            onSelect = { tab -> scope.launch { pager.animateScrollToPage(tab.ordinal) } },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

/** The club page's own second level. Schedule, Players and Team Stats are all live. */
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
    lastSeason: PastSeasonRecord?,
    isLoading: Boolean,
    standingsFailed: Boolean,
    leagueLoading: Boolean,
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

                    // Before a season starts the table is empty, so the record is last season's final, labelled as such
                    // and with no places: places are only ever read from the current standings row.
                    lastSeason != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    lastSeason.record,
                                    modifier = Modifier.testTag("team.record"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                lastSeason.points?.let { pts ->
                                    Text(
                                        pluralStringResource(R.plurals.team_points, pts, pts),
                                        modifier = Modifier.testTag("team.points"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = accents.signature,
                                    )
                                }
                            }
                            SeasonNote(
                                label = lastSeason.label,
                                tag = "team.lastSeason",
                                wording = R.string.team_last_season_label,
                                padding = PaddingValues(0.dp),
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

                    // A failed standings load says nothing about this club either way, so it must not claim the table
                    // lacks the record. The Team Stats tab carries the error itself.
                    standingsFailed -> {
                        Unit
                    }

                    // The league table behind the fallback is still arriving, so the header says it is loading rather
                    // than that the club has no record.
                    leagueLoading -> {
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
            TeamPlaces(row)
        }
    }
}

/**
 * The club's place in its division, conference and league, from the row's own sequences, on one centred line.
 *
 * A place the feed did not number (a sequence of zero or less) is left out rather than printed as "0th". Each place
 * is its own text so a phrase never splits, and the line wraps onto a new centred line instead of truncating.
 */
@Composable
private fun TeamPlaces(row: StandingRow) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        if (row.divisionSequence > 0) {
            PlaceText(
                stringResource(R.string.team_place_division, ordinal(row.divisionSequence), divisionName(row.division)),
                "team.place.division",
            )
        }
        if (row.conferenceSequence > 0) {
            PlaceText(
                stringResource(
                    R.string.team_place_conference,
                    ordinal(row.conferenceSequence),
                    conferenceShortName(row.conference),
                ),
                "team.place.conference",
            )
        }
        if (row.leagueSequence > 0) {
            PlaceText(stringResource(R.string.team_place_league, ordinal(row.leagueSequence)), "team.place.league")
        }
    }
}

@Composable
private fun PlaceText(
    text: String,
    tag: String,
) {
    Text(
        text,
        modifier = Modifier.testTag(tag),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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

/**
 * A club's record from the season before, for a header whose current standings have no row yet.
 *
 * [label] names the season the numbers belong to, read from the table itself, so it can never say the current season.
 */
internal data class PastSeasonRecord(
    val label: String,
    val record: String,
    val points: Int?,
)

/**
 * The club's line in last season's league table, or null when the table is not the season before today's, has no line
 * for the club by its full name, or leaves its wins, losses or overtime losses unnumbered.
 */
internal fun previousSeasonRecord(
    league: LeagueSummary?,
    fullName: String,
    now: Instant,
    zone: TimeZone,
): PastSeasonRecord? {
    if (league == null) return null
    if (league.season != LeagueCalendar.previousSeasonId(LeagueCalendar.seasonId(now, zone))) return null
    val club = league.clubs.firstOrNull { it.teamName == fullName } ?: return null
    val wins = club.wins ?: return null
    val losses = club.losses ?: return null
    val otLosses = club.otLosses ?: return null
    val label = pastSeasonLabel(league.season, now, zone) ?: return null
    return PastSeasonRecord(label = label, record = "$wins-$losses-$otLosses", points = club.points)
}

/** The conference as the places line prints it: East or West, or the feed's own word when it is neither. */
internal fun conferenceShortName(conference: Conference): String =
    when (conference) {
        Conference.Eastern -> "East"
        Conference.Western -> "West"
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
