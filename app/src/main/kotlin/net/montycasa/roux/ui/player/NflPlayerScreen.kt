@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflGameColumn
import net.montycasa.roux.domain.model.NflGameLog
import net.montycasa.roux.domain.model.NflGameLogEntry
import net.montycasa.roux.domain.model.NflPlayerProfile
import net.montycasa.roux.domain.model.NflSeasonStat
import net.montycasa.roux.domain.model.newestFirst
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.detail.SectionCard
import net.montycasa.roux.ui.theme.LocalRouxAccents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val HEADSHOT_SIZE = 112.dp
private val LOG_LABEL_WIDTH = 138.dp
private val LOG_COLUMN_WIDTH = 58.dp
private val LOG_ROW_HEIGHT = 44.dp
private val LOG_HEADER_HEIGHT = 40.dp
private val LOG_GROUP_HEIGHT = 24.dp
private val SUMMARY_CELL_GAP = 12.dp

/**
 * A football player's page: the profile header, the feed's season summary, and the game log.
 *
 * The log is not a fixed table. Its columns and their group headings come from the feed, so a kicker's field-goal
 * columns and a quarterback's passing columns are the same code with different data. A value the feed did not state
 * is rendered as nothing at all; a real zero is rendered as `0`.
 *
 * [onRetry] reloads both requests. A failed log leaves the profile and summary above it working, because the two are
 * separate loads.
 */
@Composable
fun NflPlayerScreen(
    state: NflPlayerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state.profile
    val profile = loaded?.data
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("nflPlayer.page"),
    ) {
        PlayerBackRow(onBack = onBack)
        when {
            profile != null -> {
                PlayerBody(
                    profile = profile,
                    gameLog = state.gameLog,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }

            loaded?.error != null -> {
                PlayerFailure(isRefreshing = state.loading, onRetry = onRetry)
            }

            else -> {
                Text(
                    stringResource(R.string.nfl_player_loading),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("nflPlayer.loading"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerBackRow(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp).testTag("nflPlayer.back"),
        ) {
            Text(stringResource(R.string.nfl_player_back))
        }
    }
}

@Composable
private fun PlayerBody(
    profile: NflPlayerProfile,
    gameLog: Loadable<NflGameLog?>?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        PlayerHeader(profile = profile)
        SeasonSummary(profile = profile)
        GameLogCard(gameLog = gameLog, onRetry = onRetry)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlayerHeader(profile: NflPlayerProfile) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No headshot means no picture area at all: absent artwork is not an empty avatar.
        profile.headshotUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(HEADSHOT_SIZE)
                        .clip(CircleShape)
                        .testTag("nflPlayer.headshot"),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            profile.name,
            modifier = Modifier.testTag("nflPlayer.name"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val identity = listOfNotNull(profile.jersey, profile.positionAbbreviation).joinToString(" · ")
        if (identity.isNotEmpty()) {
            Text(
                identity,
                modifier = Modifier.padding(top = 4.dp).testTag("nflPlayer.identity"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val team = profile.team?.name ?: profile.team?.key?.abbrev
        if (team != null) {
            Text(
                team,
                modifier = Modifier.padding(top = 2.dp).testTag("nflPlayer.team"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Only the facts the feed stated; a missing one leaves no separator behind and no placeholder.
        val facts =
            listOfNotNull(
                profile.heightText,
                profile.weightText,
                profile.age?.let { stringResource(R.string.nfl_player_age, it) },
                profile.college,
                profile.experienceText,
            )
        if (facts.isNotEmpty()) {
            Text(
                facts.joinToString(stringResource(R.string.nfl_player_separator)),
                modifier = Modifier.padding(top = 2.dp).testTag("nflPlayer.facts"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SeasonSummary(profile: NflPlayerProfile) {
    val stats = profile.seasonStats
    SectionCard(
        title = profile.seasonSummaryTitle ?: stringResource(R.string.nfl_player_season_summary),
        tag = "nflPlayer.summary",
        loadable = Loadable(stats, Freshness.Fresh, null),
        failureLabel = "",
        hideWhenEmpty = { it.isEmpty() },
    ) { current ->
        SummaryGrid(stats = current)
    }
}

@Composable
private fun SummaryGrid(stats: List<NflSeasonStat>) {
    Column(Modifier.fillMaxWidth()) {
        stats.chunked(SUMMARY_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { index, stat ->
                    SummaryCell(
                        stat = stat,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = if (index == 0) 0.dp else SUMMARY_CELL_GAP),
                    )
                }
                // Keep the last row's cells on the same grid as the full rows above it.
                repeat(SUMMARY_COLUMNS - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(
    stat: NflSeasonStat,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(
            stat.displayName,
            modifier = Modifier.testTag("nflPlayer.summary.${stat.name}.label"),
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
        )
        Text(
            stat.displayValue,
            modifier = Modifier.testTag("nflPlayer.summary.${stat.name}"),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GameLogCard(
    gameLog: Loadable<NflGameLog?>?,
    onRetry: () -> Unit,
) {
    val loadable: Loadable<NflGameLog> =
        gameLog?.let { Loadable(it.data, it.freshness, it.error) }
            ?: Loadable(null, Freshness.Missing, null)
    val title =
        loadable.data?.seasonLabel?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.nfl_player_game_log)
    SectionCard(
        title = title,
        tag = "nflPlayer.log",
        loadable = loadable,
        failureLabel = stringResource(R.string.nfl_player_game_log_error),
        onRetry = onRetry,
    ) { log ->
        val games = log.games.newestFirst()
        if (games.isEmpty()) {
            Text(
                stringResource(R.string.nfl_player_game_log_empty),
                modifier = Modifier.padding(top = 6.dp).testTag("nflPlayer.log.empty"),
                style = MaterialTheme.typography.bodySmall,
                color = LocalRouxAccents.current.muted,
            )
        } else {
            GameLogTable(log = log, games = games)
        }
    }
}

/**
 * The feed's columns, drawn with a frozen game label beside a horizontally scrolling set of numbers.
 *
 * Both sides are laid out in the same vertical rhythm - group row, header row, one row per game - so a number stays
 * under its heading while the label column stays put.
 */
@Composable
private fun GameLogTable(
    log: NflGameLog,
    games: List<NflGameLogEntry>,
) {
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().testTag("nflPlayer.log.table")) {
        Column(Modifier.width(LOG_LABEL_WIDTH)) {
            Spacer(Modifier.height(LOG_GROUP_HEIGHT))
            Text(
                stringResource(R.string.nfl_player_game),
                modifier =
                    Modifier
                        .height(LOG_HEADER_HEIGHT)
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .testTag("nflPlayer.log.header.game"),
                style = MaterialTheme.typography.labelSmall,
                color = LocalRouxAccents.current.muted,
            )
            games.forEachIndexed { index, game ->
                GameLabel(game = game, index = index)
            }
        }
        Column(Modifier.weight(1f).horizontalScroll(scroll)) {
            Row {
                log.groups.forEach { group ->
                    Text(
                        group.displayName,
                        modifier =
                            Modifier
                                .width(LOG_COLUMN_WIDTH * group.columns.size)
                                .height(LOG_GROUP_HEIGHT)
                                .testTag("nflPlayer.log.group.${group.name}"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalRouxAccents.current.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row {
                log.columns.forEach { column ->
                    ColumnHeader(column)
                }
            }
            games.forEachIndexed { rowIndex, game ->
                Row {
                    log.columns.forEach { column ->
                        StatCell(
                            column = column,
                            value = game.stats[column.name],
                            rowIndex = rowIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(column: NflGameColumn) {
    Box(
        modifier = Modifier.width(LOG_COLUMN_WIDTH).height(LOG_HEADER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            column.shortLabel,
            modifier = Modifier.testTag("nflPlayer.log.header.${column.name}"),
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GameLabel(
    game: NflGameLogEntry,
    index: Int,
) {
    val parts =
        listOfNotNull(
            game.gameDate?.let { formatGameDate(it) },
            opponentLabel(game),
            game.result,
        )
    Box(
        modifier =
            Modifier
                .width(LOG_LABEL_WIDTH)
                .height(LOG_ROW_HEIGHT)
                .padding(end = 8.dp)
                .testTag("nflPlayer.log.row.$index"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            parts.joinToString(stringResource(R.string.nfl_player_separator)),
            modifier = Modifier.testTag("nflPlayer.log.label.$index"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatCell(
    column: NflGameColumn,
    value: String?,
    rowIndex: Int,
) {
    Box(
        modifier = Modifier.width(LOG_COLUMN_WIDTH).height(LOG_ROW_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        // An absent value is an empty cell, not a zero and not a dash.
        Text(
            value.orEmpty(),
            modifier = Modifier.testTag("nflPlayer.log.row.$rowIndex.${column.name}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** The opponent side of a game row: the feed's own `vs`/`@` plus the club abbreviation. */
@Composable
private fun opponentLabel(game: NflGameLogEntry): String? {
    val opponent = game.opponentAbbreviation ?: return null
    return listOfNotNull(game.atVs, opponent).joinToString(" ")
}

@Composable
private fun PlayerFailure(
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.nfl_player_error),
            modifier = Modifier.testTag("nflPlayer.error"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RetryButton(onRetry = onRetry, isRefreshing = isRefreshing, retryTag = "nflPlayer.retry")
    }
}

/** The feed's game date as the reader's locale prints a short day. Unparseable input is kept as sent. */
private fun formatGameDate(raw: String): String =
    try {
        LocalDate
            .parse(raw.take(10))
            .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    } catch (_: DateTimeParseException) {
        raw
    }

private const val SUMMARY_COLUMNS = 2
