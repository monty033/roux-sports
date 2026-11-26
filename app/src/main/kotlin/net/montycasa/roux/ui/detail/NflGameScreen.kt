@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.NflGameClub
import net.montycasa.roux.domain.model.NflGameDetail
import net.montycasa.roux.domain.model.NflGameStatus
import net.montycasa.roux.domain.model.NflLeaderCategory
import net.montycasa.roux.domain.model.NflScoringPlay
import net.montycasa.roux.domain.model.NflScoringQuarter
import net.montycasa.roux.domain.model.NflTeamStatComparison
import net.montycasa.roux.ui.team.kickoffLabel
import net.montycasa.roux.ui.theme.LocalRouxAccents
import java.time.ZoneId
import java.util.Locale

/**
 * Football's game page.
 *
 * It is its own page rather than hockey's with different labels: one summary endpoint carries every section, the status
 * states the final wording, and each club's overall record is printed beside its name when the feed sends one. The
 * sections appear only when their payload exists, so a scheduled game shows its hero and nothing invented beneath it.
 */
@Composable
fun NflGameScreen(
    game: Game,
    state: NflGameUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailHeader(game = game, onBack = onBack)

        val detail = state.detail.data
        if (detail == null) {
            EmptyGame(state = state, onRetry = onRetry)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                GameHero(detail = detail, game = game, zone = zone, locale = locale)

                if (detail.scoring.isNotEmpty()) {
                    ScoringByQuarter(detail.scoring)
                }
                if (detail.teamStats.isNotEmpty()) {
                    TeamStats(detail = detail)
                }
                if (detail.leaders.any { it.categories.isNotEmpty() }) {
                    Leaders(detail = detail)
                }

                if (state.detail.freshness is Freshness.Stale) {
                    Text(
                        stringResource(R.string.nfl_game_stale),
                        modifier = Modifier.padding(top = 8.dp).testTag("nflGame.stale"),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalRouxAccents.current.muted,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyGame(
    state: NflGameUiState,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (state.refreshing) {
            Text(
                stringResource(R.string.nfl_game_loading),
                modifier = Modifier.testTag("nflGame.loading"),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                stringResource(R.string.nfl_game_failed),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRetry,
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                        .testTag("nflGame.retry"),
            ) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

@Composable
private fun GameHero(
    detail: NflGameDetail,
    game: Game,
    zone: ZoneId,
    locale: Locale,
) {
    Column(
        Modifier
            .testTag("nflGame.hero")
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            statusText(detail.status, game, zone, locale),
            modifier = Modifier.testTag("nflGame.status"),
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
        )
        Spacer(Modifier.height(10.dp))
        ClubScoreLine(club = detail.away, fallback = game.awayScore, side = "away")
        Spacer(Modifier.height(6.dp))
        ClubScoreLine(club = detail.home, fallback = game.homeScore, side = "home")
    }
}

@Composable
private fun ClubScoreLine(
    club: NflGameClub,
    fallback: Int?,
    side: String,
) {
    val score = club.score ?: fallback
    val record = club.record
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("nflGame.hero.$side.club"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.testTag("nflGame.hero.$side.crest")) {
            TeamLogo(team = club.team, crestSize = HERO_CREST, imageTag = "nflGame.logo.$side")
        }
        Spacer(Modifier.width(12.dp))
        Text(
            club.team.abbrev,
            modifier = Modifier.weight(1f).testTag("nflGame.hero.$side.abbrev"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (record != null) {
            Text(
                record,
                modifier = Modifier.padding(start = 8.dp).testTag("nflGame.hero.$side.record"),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
                maxLines = 1,
            )
        }
        if (score != null) {
            Text(
                score.toString(),
                modifier = Modifier.testTag("nflGame.hero.$side.score"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The hero's one line: the feed's final wording, the live quarter and clock, or the kickoff. Formatting the kickoff
 * needs the reader's zone and locale, which is why it lives here rather than in `:domain`.
 */
@Composable
private fun statusText(
    status: NflGameStatus,
    game: Game,
    zone: ZoneId,
    locale: Locale,
): String =
    when (status) {
        is NflGameStatus.Final -> {
            status.detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.nfl_game_status_final)
        }

        is NflGameStatus.Live -> {
            val period = status.period?.takeIf { it > 0 }?.let { quarterLabel(it) }
            val clock = status.clock
            when {
                period == null -> status.detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.nfl_game_status_live)
                clock.isNullOrBlank() -> period
                else -> stringResource(R.string.nfl_game_live_with_clock, period, clock)
            }
        }

        is NflGameStatus.Scheduled -> {
            (status.startTimeUtc ?: game.startTimeUtc)?.let { kickoffLabel(it, zone, locale) }
                ?: status.detail?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.nfl_game_status_scheduled)
        }

        is NflGameStatus.Unknown -> {
            status.detail.orEmpty()
        }
    }

@Composable
private fun ScoringByQuarter(quarters: List<NflScoringQuarter>) {
    GameSection(
        title = stringResource(R.string.nfl_game_scoring_title),
        tag = "nflGame.section.scoring",
    ) {
        quarters.forEachIndexed { index, quarter ->
            if (index > 0) {
                Spacer(Modifier.height(8.dp))
            }
            Text(
                quarterLabel(quarter.number),
                modifier = Modifier.padding(top = 2.dp).testTag("nflGame.scoring.quarter.${quarter.number}"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            quarter.plays.forEachIndexed { playIndex, play ->
                ScoringRow(play = play, quarter = quarter.number, index = playIndex)
            }
        }
    }
}

@Composable
private fun ScoringRow(
    play: NflScoringPlay,
    quarter: Int,
    index: Int,
) {
    val tag = "nflGame.scoring.$quarter.$index"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            play.clock,
            modifier = Modifier.width(CLOCK_WIDTH).testTag("$tag.clock"),
            style = MaterialTheme.typography.labelMedium,
            color = LocalRouxAccents.current.muted,
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(
                play.teamAbbrev,
                modifier = Modifier.testTag("$tag.club"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                play.text,
                modifier = Modifier.testTag("$tag.text"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            runningScore(play),
            modifier = Modifier.padding(start = 8.dp).testTag("$tag.score"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun runningScore(play: NflScoringPlay): String {
    val away = play.awayScore?.toString().orEmpty()
    val home = play.homeScore?.toString().orEmpty()
    return stringResource(R.string.nfl_game_play_score, away, home)
}

@Composable
private fun TeamStats(detail: NflGameDetail) {
    GameSection(
        title = stringResource(R.string.nfl_game_stats_title),
        tag = "nflGame.section.stats",
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                detail.away.team.abbrev,
                modifier = Modifier.width(STAT_WIDTH).testTag("nflGame.stats.away"),
                style = MaterialTheme.typography.labelSmall,
                color = LocalRouxAccents.current.muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                detail.home.team.abbrev,
                modifier = Modifier.width(STAT_WIDTH).testTag("nflGame.stats.home"),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = LocalRouxAccents.current.muted,
            )
        }
        detail.teamStats.forEach { stat -> TeamStatRow(stat) }
    }
}

@Composable
private fun TeamStatRow(stat: NflTeamStatComparison) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag("nflGame.stat.${stat.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stat.away.orEmpty(),
            modifier = Modifier.width(STAT_WIDTH).testTag("nflGame.stat.${stat.name}.away"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            statLabel(stat.name),
            modifier = Modifier.weight(1f).testTag("nflGame.stat.${stat.name}.label"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
        )
        Text(
            stat.home.orEmpty(),
            modifier = Modifier.width(STAT_WIDTH).testTag("nflGame.stat.${stat.name}.home"),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun statLabel(name: String): String =
    when (name) {
        "firstDowns" -> stringResource(R.string.nfl_game_stat_first_downs)
        "totalYards" -> stringResource(R.string.nfl_game_stat_total_yards)
        "netPassingYards" -> stringResource(R.string.nfl_game_stat_passing_yards)
        "rushingYards" -> stringResource(R.string.nfl_game_stat_rushing_yards)
        "turnovers" -> stringResource(R.string.nfl_game_stat_turnovers)
        "thirdDownEff" -> stringResource(R.string.nfl_game_stat_third_down_efficiency)
        "possessionTime" -> stringResource(R.string.nfl_game_stat_time_of_possession)
        else -> name
    }

@Composable
private fun Leaders(detail: NflGameDetail) {
    GameSection(
        title = stringResource(R.string.nfl_game_leaders_title),
        tag = "nflGame.section.leaders",
    ) {
        val clubs =
            listOf(detail.away.team.abbrev, detail.home.team.abbrev)
                .mapNotNull { abbrev -> detail.leaders.firstOrNull { it.teamAbbrev.equals(abbrev, ignoreCase = true) } }
                .filter { it.categories.isNotEmpty() }
        clubs.forEachIndexed { index, club ->
            if (index > 0) {
                Spacer(Modifier.height(8.dp))
            }
            Text(
                club.teamAbbrev,
                modifier = Modifier.testTag("nflGame.leaders.${club.teamAbbrev}.club"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            club.categories.forEach { category -> LeaderLine(club.teamAbbrev, category) }
        }
    }
}

@Composable
private fun LeaderLine(
    abbrev: String,
    category: NflLeaderCategory,
) {
    val leader = category.leaders.firstOrNull() ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("nflGame.leaders.$abbrev.${category.name}"),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            category.displayName,
            modifier = Modifier.width(LEADER_CATEGORY_WIDTH).testTag("nflGame.leaders.$abbrev.${category.name}.category"),
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
        )
        Text(
            stringResource(R.string.nfl_game_leader_line, leader.athleteName, leader.displayValue),
            modifier = Modifier.weight(1f).testTag("nflGame.leaders.$abbrev.${category.name}.line"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GameSection(
    title: String,
    tag: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
            .testTag(tag),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun quarterLabel(number: Int): String =
    when {
        number in 1..REGULATION_QUARTERS -> stringResource(R.string.nfl_game_quarter, number)
        number == OVERTIME -> stringResource(R.string.nfl_game_overtime)
        else -> stringResource(R.string.nfl_game_overtime_numbered, number - REGULATION_QUARTERS)
    }

private const val REGULATION_QUARTERS = 4
private const val OVERTIME = 5

private val HERO_CREST = 38.dp
private val CLOCK_WIDTH = 42.dp
private val STAT_WIDTH = 46.dp
private val LEADER_CATEGORY_WIDTH = 96.dp
