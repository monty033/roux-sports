@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first
import net.montycasa.roux.R
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.ClubGameStatus
import net.montycasa.roux.domain.policy.ClubGameStatusPolicy
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.rememberClockLabel
import net.montycasa.roux.ui.detail.TeamLogo
import net.montycasa.roux.ui.theme.Roux
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import androidx.compose.foundation.layout.height as heightModifier

private val CREST_SIZE = 32.dp
private val DATE_WIDTH = 72.dp

/**
 * The club's whole season in one chronological list, with the next game centred on first layout.
 *
 * The header and tab row above this list stay put; only the season scrolls. A finished row carries
 * the result from this club's side, and the next game gets a text marker rather than a colour cue
 * alone.
 */
@Composable
internal fun TeamScheduleList(
    teamKey: TeamKey,
    schedule: Loadable<List<ClubGame>>?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onGameClick: (ClubGame) -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    when {
        schedule == null -> {
            Box(modifier.fillMaxSize())
        }

        schedule.data == null && schedule.error != null -> {
            Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                ScheduleFailure(isRefreshing = isRefreshing, onRetry = onRetry)
            }
        }

        schedule.data.isNullOrEmpty() -> {
            Text(
                stringResource(R.string.team_schedule_empty),
                modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("team.schedule.empty"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> {
            val games = schedule.data.orEmpty()
            if (schedule.error != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    ScheduleFailure(isRefreshing = isRefreshing, onRetry = onRetry)
                }
            }
            SeasonList(
                teamKey = teamKey,
                games = games,
                now = now,
                onGameClick = onGameClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SeasonList(
    teamKey: TeamKey,
    games: List<ClubGame>,
    now: Instant,
    onGameClick: (ClubGame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val model = remember(games, locale) { scheduleListModel(games, locale) }
    val listState = rememberLazyListState()
    // Saved, not merely remembered: coming back from a game page must not re-centre the season and
    // throw away where the reader was.
    var anchored by rememberSaveable(teamKey.abbrev) { mutableStateOf(false) }

    LaunchedEffect(model.anchor?.id, model.entries.size) {
        if (anchored || model.anchor == null) return@LaunchedEffect
        val index =
            model.entries.indexOfFirst { entry ->
                entry is ScheduleListEntry.Game && entry.game.id == model.anchor.id
            }
        if (index < 0) return@LaunchedEffect

        // A fixed index offset cannot centre anything: a row at index N still sits against the top.
        // Wait for the first real layout so the viewport and a row's measured height are available,
        // then scroll the anchor so its middle lands on the viewport's middle.
        val layout =
            snapshotFlow { listState.layoutInfo }
                .first { info ->
                    info.totalItemsCount > 0 &&
                        info.viewportSize.height > 0 &&
                        info.visibleItemsInfo.any { item ->
                            model.entries.getOrNull(item.index) is ScheduleListEntry.Game
                        }
                }
        val rowHeight =
            layout.visibleItemsInfo
                .firstOrNull { item -> model.entries.getOrNull(item.index) is ScheduleListEntry.Game }
                ?.size
                ?: layout.visibleItemsInfo.first().size
        val offset = -(layout.viewportSize.height / 2 - rowHeight / 2)
        listState.scrollToItem(index, offset)
        anchored = true
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("team.schedule.list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        model.entries.forEach { entry ->
            when (entry) {
                is ScheduleListEntry.Month -> {
                    stickyHeader(key = entry.key) {
                        MonthHeader(entry)
                    }
                }

                is ScheduleListEntry.Game -> {
                    item(key = "game-${entry.game.id.value}") {
                        ScheduleGameRow(
                            game = entry.game,
                            teamKey = teamKey,
                            isNext = entry.isNext,
                            now = now,
                            onClick = { onGameClick(entry.game) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(entry: ScheduleListEntry.Month) {
    Text(
        entry.label,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 10.dp, bottom = 4.dp)
                .testTag("team.schedule.month.${entry.key}"),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
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
        Spacer(Modifier.heightModifier(8.dp))
        RetryButton(onRetry = onRetry, isRefreshing = isRefreshing, retryTag = "team.schedule.retry")
    }
}

@Composable
private fun ScheduleGameRow(
    game: ClubGame,
    teamKey: TeamKey,
    isNext: Boolean,
    now: Instant,
    onClick: () -> Unit,
) {
    val tag = "team.schedule.row.${game.id.value}"
    val opponent = opponentOf(game, teamKey)
    val atHome = game.home.key == teamKey
    val status = ClubGameStatusPolicy.status(game, teamKey, now)
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
        Text(
            scheduleDayLabel(game),
            modifier = Modifier.width(DATE_WIDTH).testTag("$tag.day"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.testTag("$tag.crest")) {
            TeamLogo(team = opponent, crestSize = CREST_SIZE, imageTag = "$tag.logo")
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(
                R.string.team_schedule_matchup,
                stringResource(if (atHome) R.string.team_schedule_vs else R.string.team_schedule_at),
                opponentNickname(opponent),
            ),
            modifier = Modifier.weight(1f).testTag("$tag.name"),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (isNext) {
                Text(
                    stringResource(R.string.team_schedule_next),
                    modifier = Modifier.testTag("$tag.next"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Roux.accents.signature,
                )
            }
            Text(
                scheduleStatusLabel(status),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("$tag.state"),
            )
        }
    }
}

/** The feed's common name, with the full name only as the fallback for a payload that omits it. */
private fun opponentNickname(opponent: TeamRef): String = opponent.shortName?.takeIf { it.isNotBlank() } ?: fullTeamName(opponent)

@Composable
private fun scheduleStatusLabel(status: ClubGameStatus): String =
    when (status) {
        is ClubGameStatus.Upcoming -> {
            rememberClockLabel(status.start)
        }

        ClubGameStatus.Live -> {
            stringResource(R.string.team_schedule_live)
        }

        is ClubGameStatus.Result -> {
            stringResource(
                if (status.won) R.string.team_schedule_win else R.string.team_schedule_loss,
                status.clubScore,
                status.opponentScore,
            )
        }

        ClubGameStatus.Final -> {
            stringResource(R.string.team_schedule_final)
        }

        ClubGameStatus.Postponed -> {
            stringResource(R.string.team_schedule_postponed)
        }

        ClubGameStatus.Suspended -> {
            stringResource(R.string.team_schedule_suspended)
        }

        ClubGameStatus.Pregame -> {
            stringResource(R.string.team_schedule_pregame)
        }

        is ClubGameStatus.Other -> {
            status.raw.ifBlank { stringResource(R.string.game_row_time_tbd) }
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

/** The other club in [game], falling back to the away side if the key does not match either. */
private fun opponentOf(
    game: ClubGame,
    teamKey: TeamKey,
): TeamRef = if (game.home.key == teamKey) game.away else game.home

/** One month header or one game, in the exact order the lazy list renders them. */
internal sealed interface ScheduleListEntry {
    data class Month(
        val key: String,
        val label: String,
    ) : ScheduleListEntry

    data class Game(
        val game: ClubGame,
        val isNext: Boolean,
    ) : ScheduleListEntry
}

internal data class ScheduleListModel(
    val entries: List<ScheduleListEntry>,
    val anchor: ClubGame?,
    val nextGameId: GameId?,
)

/**
 * Orders the season by start instant and inserts a month header before each month's first game.
 *
 * The next game is the soonest game the feed has not finished; if the season is over, the most
 * recent finished game becomes the anchor so the page still opens near the end rather than at the
 * top of a finished season.
 */
internal fun scheduleListModel(
    games: List<ClubGame>,
    locale: Locale,
): ScheduleListModel {
    val ordered = games.sortedWith(chronological)
    val next = ordered.firstOrNull { !it.isFinished }
    val anchor = next ?: ordered.lastOrNull { it.isFinished }
    val nextId = next?.id
    val entries = mutableListOf<ScheduleListEntry>()
    var lastMonth: YearMonth? = null
    ordered.forEach { game ->
        val date = runCatching { LocalDate.parse(game.date) }.getOrNull()
        val month = date?.let(YearMonth::from)
        val key = month?.toString() ?: game.date.take(7)
        if (month != lastMonth) {
            val label = date?.format(DateTimeFormatter.ofPattern("MMMM", locale)) ?: game.date
            entries += ScheduleListEntry.Month(key = key, label = label)
            lastMonth = month
        }
        entries += ScheduleListEntry.Game(game = game, isNext = game.id == nextId)
    }
    return ScheduleListModel(entries = entries, anchor = anchor, nextGameId = nextId)
}

private val chronological =
    Comparator<ClubGame> { first, second ->
        val firstStart = first.startTimeUtc
        val secondStart = second.startTimeUtc
        when {
            firstStart == null && secondStart == null -> first.date.compareTo(second.date)
            firstStart == null -> 1
            secondStart == null -> -1
            else -> firstStart.compareTo(secondStart)
        }
    }
