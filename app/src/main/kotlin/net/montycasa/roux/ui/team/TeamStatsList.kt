@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import net.montycasa.roux.R
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeagueSummary
import net.montycasa.roux.domain.policy.LeagueMetric
import net.montycasa.roux.domain.policy.LeagueRanking
import net.montycasa.roux.ui.detail.EmptySectionLine
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.LocalRouxAccents
import net.montycasa.roux.ui.theme.Roux
import kotlin.time.Clock
import kotlin.time.Instant

private val VALUE_WIDTH = 72.dp
private val RANK_WIDTH = 72.dp
private val BAR_HEIGHT = 4.dp

/**
 * The Team Stats tab: this club's rank among every club in the league, for eleven metrics, each with a rank bar.
 *
 * [teamName] is the club's name as the standings table spells it. That is the join key, because the league summary
 * has no abbreviation. Each case that cannot be answered gets its own line, so a club missing from the league, a club
 * still waiting on standings and a failed table never look like a table of zeros.
 */
@Composable
internal fun TeamStatsList(
    league: Loadable<LeagueSummary>?,
    teamName: String?,
    standingsLoading: Boolean,
    standingsFailed: Boolean,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            TeamStatsCard(league, teamName, standingsLoading, standingsFailed, isRefreshing, onRetry, now)
        }
    }
}

/** The card's surface, corners, padding and title match the club page's other cards. */
@Composable
private fun TeamStatsCard(
    league: Loadable<LeagueSummary>?,
    teamName: String?,
    standingsLoading: Boolean,
    standingsFailed: Boolean,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    now: Instant,
) {
    val data = league?.data
    // The table's own season decides the note, so a table that fell back to last season says so.
    val seasonLabel = pastSeasonLabel(data?.season, now, TimeZone.currentSystemDefault())
    val title =
        if (data == null) {
            stringResource(R.string.team_stats_title)
        } else {
            pluralStringResource(R.plurals.team_stats_title_count, data.clubs.size, data.clubs.size)
        }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
            .testTag("team.stats.card"),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        seasonLabel?.let { label ->
            SeasonNote(label = label, tag = "team.stats.season", padding = PaddingValues(bottom = 8.dp))
        }
        val stale = league?.error != null
        if (data == null) {
            if (stale) {
                LeagueFailure(stringResource(R.string.team_stats_error), isRefreshing, onRetry)
            } else {
                EmptySectionLine(stringResource(R.string.team_stats_loading))
            }
        } else {
            // A failed refresh keeps the last table on screen, with the failure and its retry above it.
            if (stale) {
                LeagueFailure(stringResource(R.string.team_stats_stale), isRefreshing, onRetry)
            }
            ClubRanks(data, teamName, standingsLoading, standingsFailed)
        }
    }
}

@Composable
private fun ClubRanks(
    data: LeagueSummary,
    teamName: String?,
    standingsLoading: Boolean,
    standingsFailed: Boolean,
) {
    val ranking = teamName?.let { LeagueRanking.forClub(data, it) }
    when {
        ranking != null -> {
            ClubRecord(ranking.club)
            LeagueTable(ranking)
        }

        teamName == null && standingsLoading -> {
            EmptySectionLine(stringResource(R.string.team_stats_loading))
        }

        teamName == null && standingsFailed -> {
            EmptySectionLine(stringResource(R.string.team_stats_standings_error))
        }

        else -> {
            EmptySectionLine(stringResource(R.string.team_stats_absent))
        }
    }
}

@Composable
private fun LeagueFailure(
    label: String,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isRefreshing) {
            EmptySectionLine(stringResource(R.string.team_stats_loading))
        } else {
            Text(
                stringResource(R.string.team_stats_retry),
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onRetry)
                        .testTag("team.stats.retry")
                        .reachTarget("team.stats.retry")
                        .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.fill,
            )
        }
    }
}

/** The club's own record from the same league row, so the table's ranks have their club beside them. */
@Composable
private fun ClubRecord(club: LeagueClub) {
    val record = listOf(club.wins, club.losses, club.otLosses).joinToString("-") { formatStatNumber(it) }
    Text(
        stringResource(
            R.string.team_stats_header,
            formatStatNumber(club.gamesPlayed),
            record,
            formatStatNumber(club.points),
        ),
        modifier = Modifier.padding(bottom = 8.dp).testTag("team.stats.header"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LeagueTable(ranking: LeagueRanking) {
    Column(Modifier.fillMaxWidth().testTag("team.stats.table")) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            Text(
                stringResource(R.string.team_stats_column_stat),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.team_stats_column_value),
                modifier = Modifier.width(VALUE_WIDTH),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.team_stats_column_rank),
                modifier = Modifier.width(RANK_WIDTH),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LeagueMetric.entries.forEach { metric -> LeagueStatRow(metric, ranking) }
    }
}

@Composable
private fun LeagueStatRow(
    metric: LeagueMetric,
    ranking: LeagueRanking,
) {
    val label = stringResource(metricLabel(metric))
    val value = formatLeagueValue(metric, metric.valueFor(ranking.club))
    val rank = ranking.rankOf(metric)
    // The rank cell is the spoken row: a bare "7th" would not say which metric or out of how many clubs.
    val spoken =
        if (rank == null) {
            stringResource(R.string.team_stats_rank_missing, label)
        } else {
            stringResource(R.string.team_stats_rank_spoken, label, value, ordinal(rank), ranking.clubCount)
        }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("team.stats.row.${metric.name}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f).testTag("team.stats.label"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                value,
                modifier = Modifier.width(VALUE_WIDTH).testTag("team.stats.value.${metric.name}"),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                rank?.let { ordinal(it) } ?: "—",
                modifier =
                    Modifier
                        .width(RANK_WIDTH)
                        .semantics { contentDescription = spoken }
                        .testTag("team.stats.rank.${metric.name}"),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A club with no rank for this metric has no share of the league to show, so its row draws no bar.
        ranking.barFractionOf(metric)?.let { fraction ->
            Spacer(Modifier.height(4.dp))
            RankBar(metric, fraction)
        }
    }
}

/**
 * The rank as a bar: a track the width of the row, filled to the club's share of the league. Rank 1 fills the track,
 * and the last place fills `1 / clubCount` of it. The rank cell already speaks the rank, so the bar has no content
 * description of its own.
 */
@Composable
private fun RankBar(
    metric: LeagueMetric,
    fraction: Float,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .testTag("team.stats.bar.${metric.name}"),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(Roux.accents.signature)
                .testTag("team.stats.barFill.${metric.name}"),
        )
    }
}

private fun metricLabel(metric: LeagueMetric): Int =
    when (metric) {
        LeagueMetric.GoalsForPerGame -> R.string.team_stats_metric_goals_for
        LeagueMetric.GoalsAgainstPerGame -> R.string.team_stats_metric_goals_against
        LeagueMetric.ShotsForPerGame -> R.string.team_stats_metric_shots_for
        LeagueMetric.ShotsAgainstPerGame -> R.string.team_stats_metric_shots_against
        LeagueMetric.FaceoffWinPct -> R.string.team_stats_metric_faceoff
        LeagueMetric.PowerPlayPct -> R.string.team_stats_metric_power_play
        LeagueMetric.PenaltyKillPct -> R.string.team_stats_metric_penalty_kill
        LeagueMetric.PenaltyMinutes -> R.string.team_stats_metric_penalty_minutes
        LeagueMetric.Points -> R.string.team_stats_metric_points
        LeagueMetric.PointPct -> R.string.team_stats_metric_point_pct
        LeagueMetric.TeamShutouts -> R.string.team_stats_metric_shutouts
    }
