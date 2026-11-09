@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import net.montycasa.roux.R
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.GoalieStat
import net.montycasa.roux.domain.model.RosterPlayer
import net.montycasa.roux.domain.model.SkaterStat
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.detail.PeriodDivider
import net.montycasa.roux.ui.reach.listRow
import net.montycasa.roux.ui.theme.Roux
import kotlin.time.Clock
import kotlin.time.Instant

private val IDENTITY_WIDTH = 168.dp
private val HEADSHOT_SIZE = 40.dp
private val STAT_COLUMN = 42.dp
private val HEADER_HEIGHT = 26.dp
private val ROW_MIN_HEIGHT = 48.dp

private val SKATER_COLUMNS = listOf("GP", "G", "AST", "PTS", "+/-", "PIM")
private val GOALIE_COLUMNS = listOf("GP", "W", "L", "OTL", "GAA", "SV%", "SO")

/**
 * The Players tab: the roster's identity and the stats feed's numbers, one row per player.
 *
 * The two sources fail independently, so the table renders whichever has arrived. A failure from
 * one is a line above the table; only two failures take the tab over and offer a retry.
 */
@Composable
internal fun TeamPlayersList(
    roster: Loadable<ClubRoster>?,
    stats: Loadable<ClubStats>?,
    isRosterRefreshing: Boolean,
    isStatsRefreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayerClick: (Long) -> Unit = {},
    now: Instant = Clock.System.now(),
) {
    val rosterData = roster?.data
    val statsData = stats?.data
    val rosterFailed = roster != null && rosterData == null && roster.error != null
    val statsFailed = stats != null && statsData == null && stats.error != null
    val anyFailed = rosterFailed || statsFailed

    when {
        rosterFailed && statsFailed -> {
            Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                PlayersFailure(
                    isRefreshing = isRosterRefreshing || isStatsRefreshing,
                    onRetry = onRetry,
                )
            }
        }

        rosterData == null && statsData == null -> {
            // Nothing has arrived yet, or only one source has failed while the other is still in
            // flight. There is no content to put under an error line yet.
            Box(modifier.fillMaxSize())
        }

        else -> {
            val groups = playerGroups(rosterData, statsData)
            Column(modifier.fillMaxSize()) {
                if (anyFailed) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        PlayersFailure(
                            isRefreshing = isRosterRefreshing || isStatsRefreshing,
                            onRetry = onRetry,
                        )
                    }
                }
                // The numbers are the stats feed's, so the season named is the stats' season, not the roster's.
                pastSeasonLabel(statsData?.season, now, TimeZone.currentSystemDefault())?.let { label ->
                    SeasonNote(label = label, tag = "team.players.season")
                }
                if (groups.all { it.rows.isEmpty() }) {
                    Text(
                        stringResource(R.string.team_players_empty),
                        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("team.players.empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PlayersTables(groups, onPlayerClick, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PlayersTables(
    groups: List<PlayerGroupRows>,
    onPlayerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("team.players.list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        groups.forEachIndexed { index, group ->
            item(key = "players-${group.group.tag}") {
                PlayersGroup(group, onPlayerClick)
            }
            if (index < groups.lastIndex) {
                item(key = "players-divider-${group.group.tag}") {
                    PeriodDivider(tag = "team.players.divider")
                }
            }
        }
    }
}

@Composable
private fun PlayersGroup(
    group: PlayerGroupRows,
    onPlayerClick: (Long) -> Unit,
) {
    val scroll = rememberScrollState()
    val primaryTag = "team.players.group.${group.group.tag}"
    val alias = if (group.group == PlayerGroup.Defensemen) "team.players.group.defensemen" else null
    if (alias == null) {
        PlayersGroupContent(
            group = group,
            scroll = scroll,
            onPlayerClick = onPlayerClick,
            modifier = Modifier.fillMaxWidth().testTag(primaryTag),
        )
    } else {
        Box(Modifier.testTag(alias)) {
            PlayersGroupContent(
                group = group,
                scroll = scroll,
                onPlayerClick = onPlayerClick,
                modifier = Modifier.fillMaxWidth().testTag(primaryTag),
            )
        }
    }
}

@Composable
private fun PlayersGroupContent(
    group: PlayerGroupRows,
    scroll: androidx.compose.foundation.ScrollState,
    onPlayerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(group.group.labelRes())
    val columns = groupColumns(group.group)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(IDENTITY_WIDTH).heightIn(min = HEADER_HEIGHT).padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    label,
                    modifier = Modifier.testTag("team.players.group.${group.group.tag}.label"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Roux.accents.muted,
                    maxLines = 1,
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = HEADER_HEIGHT)
                    .horizontalScroll(scroll)
                    .testTag("team.players.numbers.${group.group.tag}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEach { column ->
                    PlayersHeaderCell(
                        text = column,
                        tag = "team.players.group.${group.group.tag}.header.$column",
                    )
                }
            }
        }
        group.rows.forEach { row ->
            PlayersRow(
                row = row,
                group = group.group,
                columns = columns,
                scroll = scroll,
                onPlayerClick = onPlayerClick,
            )
        }
    }
}

@Composable
private fun PlayersRow(
    row: PlayerTableRow,
    group: PlayerGroup,
    columns: List<String>,
    scroll: androidx.compose.foundation.ScrollState,
    onPlayerClick: (Long) -> Unit,
) {
    val tag = row.tag()
    val playerId = row.playerId
    val openLabel = stringResource(R.string.team_players_open_player, row.name)
    // A row with no id has no player page to open, so it stays a plain row rather than a control that does nothing.
    // The label replaces the row's text for a screen reader: the name, then the action, instead of every stat in turn.
    val rowModifier = Modifier.fillMaxWidth().testTag(tag)
    val tappable =
        if (playerId == null) {
            rowModifier
        } else {
            rowModifier
                .listRow()
                .clickable { onPlayerClick(playerId) }
                .semantics { contentDescription = openLabel }
        }
    Row(tappable, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .width(IDENTITY_WIDTH)
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerHeadshot(row = row, tag = tag)
            Spacer(Modifier.width(6.dp))
            Text(
                row.name,
                modifier = Modifier.weight(1f).testTag("$tag.name"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                softWrap = true,
            )
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = ROW_MIN_HEIGHT)
                .horizontalScroll(scroll)
                .testTag("$tag.numbers"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.statValues(group).forEachIndexed { index, value ->
                PlayersStatCell(
                    text = value,
                    tag = "$tag.${columns[index].lowercase()}",
                )
            }
        }
    }
}

@Composable
private fun PlayerHeadshot(
    row: PlayerTableRow,
    tag: String,
) {
    var loaded by remember(row.headshotUrl) { mutableStateOf(false) }
    Box(
        Modifier
            .size(HEADSHOT_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("$tag.headshot"),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                initials(row.name),
                modifier = Modifier.testTag("$tag.initials"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        row.headshotUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag("$tag.headshot.image"),
                contentScale = ContentScale.Crop,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

@Composable
private fun PlayersHeaderCell(
    text: String,
    tag: String,
) {
    Box(
        Modifier.width(STAT_COLUMN).heightIn(min = HEADER_HEIGHT),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text,
            modifier = Modifier.testTag(tag),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall,
            color = Roux.accents.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayersStatCell(
    text: String,
    tag: String,
) {
    Box(
        Modifier.width(STAT_COLUMN).heightIn(min = ROW_MIN_HEIGHT),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text,
            modifier = Modifier.testTag(tag),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayersFailure(
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.team_players_error),
            modifier = Modifier.testTag("team.players.error"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        RetryButton(onRetry = onRetry, isRefreshing = isRefreshing, retryTag = "team.players.retry")
    }
}

private fun initials(name: String): String =
    name
        .split(' ')
        .mapNotNull { part -> part.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")

private fun groupColumns(group: PlayerGroup): List<String> = if (group == PlayerGroup.Goalies) GOALIE_COLUMNS else SKATER_COLUMNS

private fun PlayerGroup.labelRes(): Int =
    when (this) {
        PlayerGroup.Forwards -> R.string.team_players_forwards
        PlayerGroup.Defensemen -> R.string.team_players_defensemen
        PlayerGroup.Goalies -> R.string.team_players_goalies
    }

private fun PlayerTableRow.statValues(group: PlayerGroup): List<String> =
    if (group == PlayerGroup.Goalies) {
        listOf(
            formatStatNumber(goalie?.gamesPlayed),
            formatStatNumber(goalie?.wins),
            formatStatNumber(goalie?.losses),
            formatStatNumber(goalie?.overtimeLosses),
            formatGoalsAgainstAverage(goalie?.goalsAgainstAverage),
            formatSavePercentage(goalie?.savePercentage),
            formatStatNumber(goalie?.shutouts),
        )
    } else {
        listOf(
            formatStatNumber(skater?.gamesPlayed),
            formatStatNumber(skater?.goals),
            formatStatNumber(skater?.assists),
            formatStatNumber(skater?.points),
            formatPlusMinus(skater?.plusMinus),
            formatStatNumber(skater?.penaltyMinutes),
        )
    }

internal enum class PlayerGroup(
    val tag: String,
) {
    Forwards("forwards"),
    Defensemen("defense"),
    Goalies("goalies"),
}

internal data class PlayerTableRow(
    val playerId: Long?,
    val name: String,
    val sweaterNumber: Int?,
    val headshotUrl: String?,
    val positionCode: String?,
    val skater: SkaterStat? = null,
    val goalie: GoalieStat? = null,
) {
    fun tag(): String = "team.players.player.${playerId ?: name}"
}

internal data class PlayerGroupRows(
    val group: PlayerGroup,
    val rows: List<PlayerTableRow>,
)

/**
 * Joins the roster and the stats feed on the feed's player id. Name is never a key: two players can
 * share one, and matching by name would merge them.
 *
 * Roster rows keep the roster's own group. Stats-only rows are used only when the roster is absent,
 * where they are grouped from their position code and carry no sweater number, so they sort last in
 * their group.
 */
internal fun playerGroups(
    roster: ClubRoster?,
    stats: ClubStats?,
): List<PlayerGroupRows> {
    val rows = PlayerGroup.entries.associateWith { mutableListOf<PlayerTableRow>() }
    val skatersById =
        stats
            ?.skaters
            .orEmpty()
            .mapNotNull { line -> line.playerId?.let { it to line } }
            .toMap()
    val goaliesById =
        stats
            ?.goalies
            .orEmpty()
            .mapNotNull { line -> line.playerId?.let { it to line } }
            .toMap()
    val claimedIds = mutableSetOf<Long>()

    rosterGroups(roster).forEach { (group, players) ->
        players.forEach { player ->
            player.id?.let { claimedIds += it }
            val skater = if (group == PlayerGroup.Goalies) null else player.id?.let { skatersById[it] }
            val goalie = if (group == PlayerGroup.Goalies) player.id?.let { goaliesById[it] } else null
            rows.getValue(group) +=
                PlayerTableRow(
                    playerId = player.id,
                    name = player.name,
                    sweaterNumber = player.sweaterNumber,
                    headshotUrl = player.headshotUrl ?: skater?.headshotUrl ?: goalie?.headshotUrl,
                    positionCode = player.positionCode ?: skater?.positionCode ?: goalie?.positionCode,
                    skater = skater,
                    goalie = goalie,
                )
        }
    }

    // With a roster present the rows come from the roster alone: a player in the season stats who is not on it may have
    // been traded away or sent down, and he belongs only on the club he is currently on. Without a roster the stats are
    // all there is, so they stand in rather than leaving the tab blank.
    if (roster == null) {
        stats?.skaters.orEmpty().forEach { line ->
            if (line.playerId == null || line.playerId !in claimedIds) {
                rows.getValue(groupForSkaterPosition(line.positionCode)) +=
                    PlayerTableRow(
                        playerId = line.playerId,
                        name = line.name,
                        sweaterNumber = null,
                        headshotUrl = line.headshotUrl,
                        positionCode = line.positionCode,
                        skater = line,
                    )
            }
        }
        stats?.goalies.orEmpty().forEach { line ->
            if (line.playerId == null || line.playerId !in claimedIds) {
                rows.getValue(PlayerGroup.Goalies) +=
                    PlayerTableRow(
                        playerId = line.playerId,
                        name = line.name,
                        sweaterNumber = null,
                        headshotUrl = line.headshotUrl,
                        positionCode = line.positionCode ?: "G",
                        goalie = line,
                    )
            }
        }
    }

    return PlayerGroup.entries.map { group ->
        PlayerGroupRows(group = group, rows = rows.getValue(group).sortedWith(playerOrder))
    }
}

private fun rosterGroups(roster: ClubRoster?): List<Pair<PlayerGroup, List<RosterPlayer>>> =
    listOf(
        PlayerGroup.Forwards to roster?.forwards.orEmpty(),
        PlayerGroup.Defensemen to roster?.defense.orEmpty(),
        PlayerGroup.Goalies to roster?.goalies.orEmpty(),
    )

private fun groupForSkaterPosition(code: String?): PlayerGroup =
    when (code?.trim()?.uppercase()) {
        "D" -> PlayerGroup.Defensemen
        else -> PlayerGroup.Forwards
    }

/** Sweater number ascending; a player with no number last, then by name. */
private val playerOrder: Comparator<PlayerTableRow> =
    compareBy<PlayerTableRow> { it.sweaterNumber ?: Int.MAX_VALUE }
        .thenBy { it.name }
