@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PlayerMugshot
import net.montycasa.roux.domain.model.SkaterLine
import net.montycasa.roux.ui.reach.listRow
import net.montycasa.roux.ui.theme.LocalRouxAccents

/**
 * The club's three player tables, in the order the feed groups them: forwards, defencemen, goalies.
 *
 * Each block is drawn only when its own list has something in it, so an absent group takes its heading, table and
 * divider with it. The group heading is the first cell of that table's header row, and consecutive groups are separated
 * by the same rule the period summaries use rather than ad-hoc spacing.
 */
@Composable
internal fun PlayerTables(
    goalies: List<GoalieLine>,
    forwards: List<SkaterLine>,
    defense: List<SkaterLine>,
    onPlayerClick: (Long) -> Unit = {},
    /** The season the game belongs to, for the mugshot path; null draws initials instead. */
    seasonStartYear: Int? = null,
) {
    val blocks =
        buildList {
            if (forwards.isNotEmpty()) {
                add(RosterBlock("Forwards", "detail.section.forwards", SKATER_COLUMNS, forwards.map { skaterRow(it, seasonStartYear) }))
            }
            if (defense.isNotEmpty()) {
                add(RosterBlock("Defensemen", "detail.section.defense", SKATER_COLUMNS, defense.map { skaterRow(it, seasonStartYear) }))
            }
            if (goalies.isNotEmpty()) {
                add(RosterBlock("Goalies", "detail.section.goalies", GOALIE_COLUMNS, goalies.map { goalieRow(it, seasonStartYear) }))
            }
        }

    Column {
        blocks.forEachIndexed { index, block ->
            if (index > 0) PeriodDivider(tag = PLAYER_DIVIDER_TAG)
            Column(Modifier.testTag("${block.tag}.block")) {
                RosterTable(
                    tag = block.tag,
                    heading = block.heading,
                    headers = block.headers,
                    rows = block.rows,
                    onPlayerClick = onPlayerClick,
                )
            }
        }
    }
}

private data class RosterBlock(
    val heading: String,
    val tag: String,
    val headers: List<String>,
    val rows: List<PlayerRow>,
)

/** One player's line. The id is what makes the row a door: without it there is no page to open. */
private data class PlayerRow(
    val name: String,
    val values: List<String>,
    val playerId: Long?,
    val mugshotUrl: String? = null,
)

private fun skaterRow(
    line: SkaterLine,
    seasonStartYear: Int?,
): PlayerRow =
    // The name is the name; position is no longer a column, so the stats start at goals.
    PlayerRow(
        line.skater.name,
        listOf(
            line.goals.toString(),
            line.assists.toString(),
            line.points.toString(),
            if (line.plusMinus > 0) "+${line.plusMinus}" else line.plusMinus.toString(),
            line.shotsOnGoal.toString(),
            line.penaltyMinutes.toString(),
            formatIceTime(line.toiSeconds),
            line.faceoffPercent()?.let { "$it%" } ?: "—",
        ),
        line.skater.id,
        PlayerMugshot.url(line.skater.id, line.skater.teamAbbrev, seasonStartYear),
    )

private fun goalieRow(
    line: GoalieLine,
    seasonStartYear: Int?,
): PlayerRow =
    PlayerRow(
        line.goalie.name,
        listOf(
            line.shotsAgainst.toString(),
            line.saves.toString(),
            line.savePercentage()?.let { formatAverage(it * 100, 1) } ?: "—",
            line.goalsAgainstAverage()?.let { formatAverage(it, 2) } ?: "—",
        ),
        line.goalie.id,
        PlayerMugshot.url(line.goalie.id, line.goalie.teamAbbrev, seasonStartYear),
    )

private val GOALIE_COLUMNS = listOf("SA", "SV", "SV%", "GAA")
private val SKATER_COLUMNS = listOf("G", "AST", "P", "+/-", "SOG", "PIM", "TOI", "FO%")

@Composable
private fun RosterTable(
    tag: String,
    heading: String,
    headers: List<String>,
    rows: List<PlayerRow>,
    onPlayerClick: (Long) -> Unit,
) {
    // One scroll state for the whole group, so a name and its numbers stay on the same row while the columns move.
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(ROW_HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(NAME_COLUMN.dp).height(ROW_HEADER_HEIGHT),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    heading.uppercase(),
                    modifier = Modifier.testTag("$tag.heading"),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalRouxAccents.current.muted,
                    maxLines = 1,
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(ROW_HEADER_HEIGHT)
                    .horizontalScroll(scroll)
                    .testTag(tag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                headers.forEach { StatHeader(it) }
            }
        }
        rows.forEach { row ->
            // A player with an id is a door to their page; one without stays plain text rather than a dead control.
            val action =
                row.playerId?.let { id ->
                    Modifier.listRow().clickable { onPlayerClick(id) }
                } ?: Modifier
            Row(
                Modifier.fillMaxWidth().height(ROW_HEIGHT).then(action),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.width(NAME_COLUMN.dp).height(ROW_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerMugshotImage(row.mugshotUrl, name = row.name, tag = "$tag.${row.playerId ?: row.name}")
                    Spacer(Modifier.width(MUGSHOT_GAP))
                    Text(
                        row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.weight(1f).height(ROW_HEIGHT).horizontalScroll(scroll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.values.forEach { StatCell(it) }
                }
            }
        }
    }
}

private val MUGSHOT_SIZE = 28.dp
private val MUGSHOT_GAP = 6.dp
private val ROW_HEADER_HEIGHT = 22.dp
private val ROW_HEIGHT = 48.dp

/** First letter of the first and last word of a name; the same shape the roster uses. */
private fun initialsOf(name: String): String {
    val words = name.trim().split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words.first().take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/**
 * A player's mugshot, or their initials while it loads and if it never arrives.
 *
 * The image is decoration: the name beside it is the content, so it carries no description of its own.
 */
@Composable
private fun PlayerMugshotImage(
    url: String?,
    name: String,
    tag: String,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier
            .size(MUGSHOT_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("$tag.mugshot"),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                initialsOf(name),
                style = MaterialTheme.typography.labelSmall,
                color = LocalRouxAccents.current.muted,
                maxLines = 1,
            )
        }
        url?.let { src ->
            AsyncImage(
                model = src,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag("$tag.mugshot.image"),
                contentScale = ContentScale.Crop,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

@Composable
private fun StatCell(text: String) {
    Box(
        Modifier.width(STAT_COLUMN.dp).height(ROW_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatHeader(text: String) {
    Box(
        Modifier.width(STAT_COLUMN.dp).height(ROW_HEADER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
            maxLines = 1,
        )
    }
}

internal const val PLAYER_DIVIDER_TAG = "detail.players.divider"

private const val NAME_COLUMN = 136
private const val STAT_COLUMN = 42

internal fun formatIceTime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val minutes = seconds / 60
    val rest = seconds % 60
    return "$minutes:${rest.toString().padStart(2, '0')}"
}

internal fun formatAverage(
    value: Double,
    decimals: Int,
): String {
    val scale =
        when (decimals) {
            1 -> 10.0
            2 -> 100.0
            else -> 1.0
        }
    val rounded = kotlin.math.round(value * scale) / scale
    val text = rounded.toString()
    val dot = text.indexOf('.')
    if (dot < 0) return text + "." + "0".repeat(decimals)
    val have = text.length - dot - 1
    return if (have >= decimals) text.substring(0, dot + 1 + decimals) else text + "0".repeat(decimals - have)
}
