@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.FootballResult
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflSeasonEntry
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.resultFor
import net.montycasa.roux.ui.detail.TeamLogo
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A football club's page.
 *
 * Football's page is its own rather than a generalisation of hockey's: hockey's three tabs and its roster, stats and
 * league-table loads have no football equivalent yet, and bending that page to serve one tab would put every hockey
 * screenshot at risk for a page football has not needed. The schedule below is the whole page for now.
 */
@Composable
fun NflTeamPageScreen(
    state: NflTeamPageUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val season = state.season
    val abbreviation = state.team?.key?.abbrev ?: state.teamKey.abbrev
    val name = state.team?.name ?: abbreviation

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
