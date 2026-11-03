@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.ClubScheduleSections
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.rememberClockLabel
import net.montycasa.roux.ui.detail.TeamLogo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant

private val CREST_SIZE = 32.dp

/**
 * The club's season schedule, split into what is next and what has just finished.
 *
 * The section is deliberately independent of the header: [schedule] can carry a failure while the
 * page above it still shows the club's record. A finished row says "Final" and nothing else, because
 * the endpoint this section reads has no scoreline to show.
 */
@Composable
internal fun TeamScheduleSection(
    teamKey: TeamKey,
    schedule: Loadable<List<ClubGame>>?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onGameClick: (ClubGame) -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    if (schedule == null) return

    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            stringResource(R.string.team_schedule_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("team.schedule.title"),
        )
        val games = schedule.data
        if (games == null) {
            if (schedule.error != null) {
                ScheduleFailure(isRefreshing = isRefreshing, onRetry = onRetry)
            }
            return@Column
        }

        val sections = ClubScheduleSections.of(games, now)
        if (schedule.error != null) {
            ScheduleFailure(isRefreshing = isRefreshing, onRetry = onRetry)
        }
        ScheduleGroup(
            title = stringResource(R.string.team_schedule_upcoming),
            empty = stringResource(R.string.team_schedule_no_upcoming),
            groupTag = "team.schedule.upcoming",
            rowTag = "team.schedule.upcoming.row",
            games = sections.upcoming,
            teamKey = teamKey,
            result = false,
            onGameClick = onGameClick,
        )
        Spacer(Modifier.height(12.dp))
        ScheduleGroup(
            title = stringResource(R.string.team_schedule_results),
            empty = stringResource(R.string.team_schedule_no_results),
            groupTag = "team.schedule.results",
            rowTag = "team.schedule.results.row",
            games = sections.results,
            teamKey = teamKey,
            result = true,
            onGameClick = onGameClick,
        )
    }
}

@Composable
private fun ScheduleFailure(
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.team_schedule_error),
            modifier = Modifier.testTag("team.schedule.error"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().testTag("team.schedule.retry")) {
            RetryButton(onRetry = onRetry, isRefreshing = isRefreshing, retryTag = "team.schedule.retry")
        }
    }
}

@Composable
private fun ScheduleGroup(
    title: String,
    empty: String,
    groupTag: String,
    rowTag: String,
    games: List<ClubGame>,
    teamKey: TeamKey,
    result: Boolean,
    onGameClick: (ClubGame) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag(groupTag)) {
        Text(
            title,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).testTag("$groupTag.heading"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (games.isEmpty()) {
            Text(
                empty,
                modifier = Modifier.padding(vertical = 8.dp).testTag("$groupTag.empty"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            games.forEach { game ->
                ScheduleRow(
                    game = game,
                    teamKey = teamKey,
                    result = result,
                    tag = "$rowTag.${game.id.value}",
                    onClick = { onGameClick(game) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    game: ClubGame,
    teamKey: TeamKey,
    result: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val opponent = opponentOf(game, teamKey)
    val atHome = game.home.key == teamKey
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(tag)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.testTag("$tag.crest")) {
            TeamLogo(team = opponent, crestSize = CREST_SIZE, imageTag = "$tag.logo")
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                fullTeamName(opponent),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("$tag.name"),
            )
            Text(
                stringResource(if (atHome) R.string.team_schedule_home else R.string.team_schedule_away),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("$tag.venue"),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                scheduleDayLabel(game),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("$tag.day"),
            )
            Text(
                scheduleStateLabel(game, result),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("$tag.state"),
            )
        }
    }
}

/** The other club in [game], falling back to the away side if the key does not match either. */
private fun opponentOf(
    game: ClubGame,
    teamKey: TeamKey,
): TeamRef = if (game.home.key == teamKey) game.away else game.home

/**
 * A finished row says Final. An unfinished row says its unusual state in words when the feed has
 * one, because a postponed game that merely showed a time would read as a normal upcoming fixture.
 */
@Composable
private fun scheduleStateLabel(
    game: ClubGame,
    result: Boolean,
): String {
    if (result) return stringResource(R.string.team_schedule_final)
    val scheduleState = game.gameScheduleState?.trim()?.uppercase()
    val feedState = (game.state as? GameState.Unknown)?.raw?.trim()?.uppercase()
    return when {
        scheduleState in POSTPONED || feedState in POSTPONED -> stringResource(R.string.team_schedule_postponed)
        scheduleState in SUSPENDED || feedState in SUSPENDED -> stringResource(R.string.team_schedule_suspended)
        game.state is GameState.Live -> stringResource(R.string.team_schedule_live)
        game.state is GameState.Pre -> stringResource(R.string.team_schedule_pregame)
        else -> game.startTimeUtc?.let { rememberClockLabel(it) } ?: stringResource(R.string.game_row_time_tbd)
    }
}

@Composable
private fun scheduleDayLabel(game: ClubGame): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    return remember(game.date, game.startTimeUtc, locale, zone) {
        val date =
            game.startTimeUtc?.let { instant ->
                java.time.Instant
                    .ofEpochMilli(instant.toEpochMilliseconds())
                    .atZone(zone)
                    .toLocalDate()
            } ?: runCatching { LocalDate.parse(game.date) }.getOrNull()
        date?.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)) ?: game.date
    }
}

private val POSTPONED = setOf("PPD", "POSTPONED")
private val SUSPENDED = setOf("SUSP", "SUSPENDED")
