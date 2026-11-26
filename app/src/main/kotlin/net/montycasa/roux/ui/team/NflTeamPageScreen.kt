@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.FootballResult
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflSeasonEntry
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.resultFor
import net.montycasa.roux.ui.common.SubNavigationRow
import net.montycasa.roux.ui.detail.TeamLogo
import net.montycasa.roux.ui.nav.NavSwipe
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A football club's page.
 *
 * Football's page is its own rather than a generalisation of hockey's: hockey's roster, stats and league-table loads
 * have no football equivalent yet. It opens on the schedule, which is what a reader arriving from the teams list saw
 * before the roster tab existed, and the roster is the second page behind the same bottom strip.
 *
 * The two destinations are a pager, exactly like the club page's tabs, so a swipe moves one destination at a time and
 * the strip follows the drag. Each page loads its own request, so one failing does not take the other away.
 */
@Composable
fun NflTeamPageScreen(
    state: NflTeamPageUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onRosterRetry: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val season = state.season
    val abbreviation = state.team?.key?.abbrev ?: state.teamKey.abbrev
    val name = state.team?.name ?: abbreviation
    val pager = rememberPagerState { NflTeamTab.entries.size }
    val scope = rememberCoroutineScope()
    // During a swipe the strip follows the page the pager is heading to, not the one it is leaving.
    val shownTab = NflTeamTab.entries[if (pager.isScrollInProgress) pager.targetPage else pager.currentPage]
    val tabState = rememberSaveableStateHolder()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("nflTeam.back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nfl_team_back))
            }
            // The crest the teams list and the scores rows draw: one badge from one feed, so a club looks the same
            // everywhere it appears. With artwork off it falls back to the abbreviation, as those do.
            TeamLogo(team = state.team ?: TeamRef(state.teamKey), crestSize = CREST_SIZE)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("nflTeam.name"),
                )
                Text(
                    text = listOfNotNull(state.record, season?.seasonYear?.toString()).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("nflTeam.record"),
                )
            }
        }

        HorizontalDivider()

        HorizontalPager(
            state = pager,
            // Off, horizontal drags belong to the pager; on, the shell's pager would claim them instead.
            userScrollEnabled = !NavSwipe.TURNS_PAGES,
            // The neighbouring tab is composed ahead of the drag, so a swipe reveals a finished page.
            beyondViewportPageCount = 1,
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = tween(durationMillis = TAB_SETTLE_MILLIS, easing = FastOutSlowInEasing),
                ),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("nflTeam.pager"),
        ) { page ->
            val tab = NflTeamTab.entries[page]
            tabState.SaveableStateProvider(tab) {
                when (tab) {
                    NflTeamTab.Schedule -> {
                        ScheduleTab(
                            state = state,
                            season = season,
                            onRetry = onRetry,
                            zone = zone,
                            locale = locale,
                        )
                    }

                    NflTeamTab.Roster -> {
                        RosterTab(
                            state = state,
                            onRetry = onRosterRetry,
                        )
                    }
                }
            }
        }

        // The page's own second level sits at the bottom, where the app's other navigation lives.
        NflTeamTabRow(
            selected = shownTab,
            onSelect = { tab -> scope.launch { pager.animateScrollToPage(tab.ordinal) } },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

/** The football club page's second level. Schedule is first, so a page opening on it has not changed for its reader. */
internal enum class NflTeamTab {
    Schedule,
    Roster,
}

@Composable
private fun NflTeamTabRow(
    selected: NflTeamTab,
    onSelect: (NflTeamTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubNavigationRow(
        entries = NflTeamTab.entries,
        selected = selected,
        onSelect = onSelect,
        tagPrefix = "nflTeam.tab",
        modifier = modifier,
        label = { tab ->
            stringResource(
                when (tab) {
                    NflTeamTab.Schedule -> R.string.nfl_team_tab_schedule
                    NflTeamTab.Roster -> R.string.nfl_team_tab_roster
                },
            )
        },
    )
}

@Composable
private fun ScheduleTab(
    state: NflTeamPageUiState,
    season: NflClubSchedule?,
    onRetry: () -> Unit,
    zone: ZoneId,
    locale: Locale,
) {
    when {
        season != null -> {
            ScheduleList(season = season, teamKey = state.teamKey, zone = zone, locale = locale)
        }

        state.loading -> {
            Text(
                text = stringResource(R.string.nfl_team_loading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp).testTag("nflTeam.loading"),
            )
        }

        else -> {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.nfl_team_failed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 4.dp).testTag("nflTeam.retry"),
                ) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}

@Composable
private fun RosterTab(
    state: NflTeamPageUiState,
    onRetry: () -> Unit,
) {
    val roster = state.roster
    when {
        roster != null -> {
            NflTeamRosterList(roster = roster, modifier = Modifier.fillMaxSize())
        }

        state.rosterLoading -> {
            Text(
                text = stringResource(R.string.nfl_team_roster_loading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp).testTag("nflTeam.rosterLoading"),
            )
        }

        else -> {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.nfl_team_roster_failed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 4.dp).testTag("nflTeam.rosterRetry"),
                ) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}

@Composable
private fun ScheduleList(
    season: NflClubSchedule,
    teamKey: TeamKey,
    zone: ZoneId,
    locale: Locale,
) {
    LazyColumn(Modifier.fillMaxSize().testTag("nflTeam.schedule")) {
        items(season.entries, key = { entry -> "${entry.week}-${entry::class.simpleName}" }) { entry ->
            when (entry) {
                is NflSeasonEntry.Bye -> ByeRow(entry.week)
                is NflSeasonEntry.Game -> GameRow(entry, teamKey, zone, locale)
            }
        }
    }
}

@Composable
private fun ByeRow(week: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("nflTeam.bye"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.nfl_team_week, week),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(WEEK_WIDTH),
        )
        Text(
            text = stringResource(R.string.nfl_team_bye),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GameRow(
    entry: NflSeasonEntry.Game,
    teamKey: TeamKey,
    zone: ZoneId,
    locale: Locale,
) {
    val game = entry.game
    val home = game.home.key == teamKey
    val opponent = if (home) game.away.key.abbrev else game.home.key.abbrev
    val result = game.resultFor(teamKey)
    val detail =
        when (result) {
            FootballResult.Win -> stringResource(R.string.nfl_team_win, game.ownScore(teamKey), game.opponentScore(teamKey))
            FootballResult.Loss -> stringResource(R.string.nfl_team_loss, game.ownScore(teamKey), game.opponentScore(teamKey))
            FootballResult.Tie -> stringResource(R.string.nfl_team_tie, game.ownScore(teamKey), game.opponentScore(teamKey))
            FootballResult.Unplayed -> game.startTimeUtc?.let { kickoffLabel(it, zone, locale) }.orEmpty()
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("nflTeam.game.${entry.week}")
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.nfl_team_week, entry.week),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(WEEK_WIDTH),
        )
        Text(
            text = if (home) stringResource(R.string.nfl_team_vs, opponent) else stringResource(R.string.nfl_team_at, opponent),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color =
                when (result) {
                    FootballResult.Win -> MaterialTheme.colorScheme.primary
                    FootballResult.Loss -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.testTag("nflTeam.result.${entry.week}"),
        )
    }
}

/** `Sun, Sep 13 · 1:00 PM` in the reader's own zone, which is the only place the kickoff means anything. */
internal fun kickoffLabel(
    instant: kotlin.time.Instant,
    zone: ZoneId,
    locale: Locale,
): String {
    val local =
        java.time.Instant
            .ofEpochMilli(instant.toEpochMilliseconds())
            .atZone(zone)
    val day = local.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    val time = local.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    return "$day · $time"
}

private fun net.montycasa.roux.domain.model.ClubGame.ownScore(teamKey: TeamKey): Int =
    (if (home.key == teamKey) homeScore else awayScore) ?: 0

private fun net.montycasa.roux.domain.model.ClubGame.opponentScore(teamKey: TeamKey): Int =
    (if (home.key == teamKey) awayScore else homeScore) ?: 0

private val WEEK_WIDTH = 72.dp

private val CREST_SIZE = 44.dp

/** The same settle the player and hockey-club page tabs use, so the three screens feel like one app. */
private const val TAB_SETTLE_MILLIS = 250
