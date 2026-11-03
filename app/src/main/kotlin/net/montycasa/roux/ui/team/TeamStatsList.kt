@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.montycasa.roux.R
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.GoalieStat
import net.montycasa.roux.domain.model.SkaterStat
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.detail.PeriodDivider
import net.montycasa.roux.ui.theme.Roux
import kotlin.math.round

private val IDENTITY_WIDTH = 124.dp
private val POSITION_WIDTH = 26.dp
private val STAT_COLUMN = 32.dp
private val HEADSHOT_SIZE = 30.dp
private val HEADER_HEIGHT = 26.dp
private val ROW_HEIGHT = 48.dp

private val SKATER_COLUMNS = listOf("GP", "G", "A", "PTS", "+/-", "PIM")
private val GOALIE_COLUMNS = listOf("GP", "W", "L", "OTL", "GAA", "SV%", "SO")

/**
 * The Stats tab: the club's skaters and goalies, each table in leaderboard order.
 *
 * It owns the same loading, failure and retry states as the roster. The header and the bottom tab row
 * live above this content, so a stats failure leaves both standing.
 */
@Composable
internal fun TeamStatsList(
    stats: Loadable<ClubStats>?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        stats == null -> {
            Box(modifier.fillMaxSize())
        }

        stats.data == null && stats.error != null -> {
            Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                StatsFailure(isRefreshing = isRefreshing, onRetry = onRetry)
            }
        }

        else -> {
            val data = stats.data
            if (data == null || (data.skaters.isEmpty() && data.goalies.isEmpty())) {
                Text(
                    stringResource(R.string.team_stats_empty),
                    modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("team.stats.empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier.fillMaxSize()) {
                    if (stats.error != null) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            StatsFailure(isRefreshing = isRefreshing, onRetry = onRetry)
                        }
                    }
                    StatsTables(data, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StatsTables(
    stats: ClubStats,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("team.stats.list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "stats-skaters") {
            StatsGroup(
                heading = stringResource(R.string.team_stats_skaters),
                tag = "skaters",
                headers = SKATER_COLUMNS,
                rows =
                    stats.skaters
                        .sortedWith(
                            // Leaderboard order, not the feed's.
                            compareByDescending<SkaterStat> { it.points }
                                .thenByDescending { it.goals }
                                .thenByDescending { it.gamesPlayed }
                                .thenBy { it.name },
                        ).map { it.tableRow() },
            )
        }
        item(key = "stats-divider") {
            PeriodDivider(tag = "team.stats.divider")
        }
        item(key = "stats-goalies") {
            StatsGroup(
                heading = stringResource(R.string.team_stats_goalies),
                tag = "goalies",
                headers = GOALIE_COLUMNS,
                rows =
                    stats.goalies
                        .sortedWith(
                            // Leaderboard order, not the feed's.
                            compareByDescending<GoalieStat> { it.gamesPlayed }
                                .thenBy { it.goalsAgainstAverage ?: Double.MAX_VALUE }
                                .thenBy { it.name },
                        ).map { it.tableRow() },
            )
        }
    }
}

private data class StatsRow(
    val id: Long?,
    val name: String,
    val position: String,
    val headshotUrl: String?,
    val values: List<String>,
)

private fun SkaterStat.tableRow(): StatsRow =
    StatsRow(
        id = playerId,
        name = name,
        position = positionCode ?: "—",
        headshotUrl = headshotUrl,
        values =
            listOf(
                formatStatNumber(gamesPlayed),
                formatStatNumber(goals),
                formatStatNumber(assists),
                formatStatNumber(points),
                formatPlusMinus(plusMinus),
                formatStatNumber(penaltyMinutes),
            ),
    )

private fun GoalieStat.tableRow(): StatsRow =
    StatsRow(
        id = playerId,
        name = name,
        position = positionCode ?: "—",
        headshotUrl = headshotUrl,
        values =
            listOf(
                formatStatNumber(gamesPlayed),
                formatStatNumber(wins),
                formatStatNumber(losses),
                formatStatNumber(overtimeLosses),
                formatGoalsAgainstAverage(goalsAgainstAverage),
                formatSavePercentage(savePercentage),
                formatStatNumber(shutouts),
            ),
    )

@Composable
private fun StatsGroup(
    heading: String,
    tag: String,
    headers: List<String>,
    rows: List<StatsRow>,
) {
    Column(Modifier.fillMaxWidth().testTag("team.stats.group.$tag")) {
        Row(Modifier.fillMaxWidth()) {
            // The identity column stays put; the reason to scroll is to reach a column, never to
            // lose track of whose row it is.
            Column(Modifier.width(IDENTITY_WIDTH)) {
                Box(
                    Modifier.width(IDENTITY_WIDTH).height(HEADER_HEIGHT),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        heading,
                        modifier = Modifier.testTag("team.stats.group.$tag.label"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Roux.accents.muted,
                        maxLines = 1,
                    )
                }
                rows.forEach { row ->
                    StatsIdentity(row = row, tag = row.tag())
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .testTag("team.stats.$tag"),
            ) {
                Row {
                    StatHeader(
                        text = stringResource(R.string.team_stats_position),
                        width = POSITION_WIDTH,
                        tag = "team.stats.group.$tag.header.POS",
                    )
                    headers.forEach { header ->
                        StatHeader(
                            text = header,
                            width = STAT_COLUMN,
                            tag = "team.stats.group.$tag.header.$header",
                        )
                    }
                }
                rows.forEach { row ->
                    Row {
                        StatCell(
                            text = row.position,
                            width = POSITION_WIDTH,
                            tag = "${row.tag()}.position",
                        )
                        row.values.zip(headers).forEach { (value, header) ->
                            StatCell(
                                text = value,
                                width = STAT_COLUMN,
                                tag = "${row.tag()}.${header.lowercase()}",
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun StatsRow.tag(): String = "team.stats.player.${id ?: name}"

@Composable
private fun StatsIdentity(
    row: StatsRow,
    tag: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatsHeadshot(row = row, tag = tag)
        Spacer(Modifier.width(8.dp))
        Text(
            row.name,
            modifier = Modifier.weight(1f).testTag("$tag.name"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The player's mug, with the same lettered-circle fallback the roster uses. No silhouette is drawn:
 * a missing headshot reads as initials, not as a fake player.
 */
@Composable
private fun StatsHeadshot(
    row: StatsRow,
    tag: String,
) {
    var loaded by remember(row.headshotUrl) { mutableStateOf(false) }
    Box(
        Modifier.size(HEADSHOT_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                initials(row.name),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        row.headshotUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag("$tag.headshot"),
                contentScale = ContentScale.Crop,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

private fun initials(name: String): String =
    name
        .split(' ')
        .mapNotNull { part -> part.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")

@Composable
private fun StatHeader(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    tag: String,
) {
    Box(
        Modifier.width(width).height(HEADER_HEIGHT),
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
private fun StatCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    tag: String,
) {
    Box(
        Modifier.width(width).height(ROW_HEIGHT),
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
private fun StatsFailure(
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.team_stats_error),
            modifier = Modifier.testTag("team.stats.error"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        RetryButton(onRetry = onRetry, isRefreshing = isRefreshing, retryTag = "team.stats.retry")
    }
}

/** An absent number prints as the em dash; a real number prints as itself. */
internal fun formatStatNumber(value: Int?): String = value?.toString() ?: "—"

/** Seconds as `M:SS`; 958.7857 prints `15:58`, and a real 0 prints `0:00`. */
internal fun formatStatsIceTime(seconds: Double?): String = seconds?.let { formatStatsIceTime(it.toLong()) } ?: "—"

/** The same value as a whole number of seconds. */
internal fun formatStatsIceTime(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "—"
    val minutes = seconds / 60
    val rest = seconds % 60
    return "$minutes:${rest.toString().padStart(2, '0')}"
}

/** An alias for callers that name the column rather than the unit. */
internal fun formatTimeOnIce(seconds: Double?): String = formatStatsIceTime(seconds)

/** The same value as a whole number of seconds. */
internal fun formatTimeOnIce(seconds: Long?): String = formatStatsIceTime(seconds)

/** An alias matching the game page's existing name for the same formatting. */
internal fun formatIceTime(seconds: Double?): String = formatStatsIceTime(seconds)

/** The same value as a whole number of seconds. */
internal fun formatIceTime(seconds: Long?): String = formatStatsIceTime(seconds)

/** A shooting fraction printed as a one-decimal percentage: 0.1 is `10.0%`. */
internal fun formatShootingPercentage(fraction: Double?): String = fraction?.let { formatDecimal(it * 100.0, 1) + "%" } ?: "—"

/** An alias using the feed's spelling. */
internal fun formatShootingPctg(fraction: Double?): String = formatShootingPercentage(fraction)

/** A generic name for the same one-decimal percentage. */
internal fun formatPercentage(fraction: Double?): String = formatShootingPercentage(fraction)

/** A save fraction printed the way hockey prints it: 0.869565 is `.870`, and 0.0 is `.000`. */
internal fun formatSavePercentage(fraction: Double?): String {
    if (fraction == null) return "—"
    val text = formatDecimal(fraction, 3)
    return if (text.startsWith("0.")) text.substring(1) else text
}

/** An alias using the feed's spelling. */
internal fun formatSavePctg(fraction: Double?): String = formatSavePercentage(fraction)

/** An alias for the column label. */
internal fun formatSvPercentage(fraction: Double?): String = formatSavePercentage(fraction)

/** A goals-against average with two decimals: 3.0 is `3.00`, a real 0.0 is `0.00`. */
internal fun formatGoalsAgainstAverage(value: Double?): String = value?.let { formatDecimal(it, 2) } ?: "—"

/** An alias for the column label. */
internal fun formatGaa(value: Double?): String = formatGoalsAgainstAverage(value)

/** A signed plus/minus: `+5`, `-3`, and `0` rather than `+0`; absent prints the em dash. */
internal fun formatPlusMinus(value: Int?): String =
    when {
        value == null -> "—"
        value > 0 -> "+$value"
        else -> value.toString()
    }

private fun formatDecimal(
    value: Double,
    decimals: Int,
): String {
    val scale =
        when (decimals) {
            1 -> 10.0
            2 -> 100.0
            3 -> 1000.0
            else -> 1.0
        }
    val rounded = round(value * scale) / scale
    val text = rounded.toString()
    val dot = text.indexOf('.')
    if (dot < 0) return text + "." + "0".repeat(decimals)
    val have = text.length - dot - 1
    return if (have >= decimals) text.substring(0, dot + 1 + decimals) else text + "0".repeat(decimals - have)
}
