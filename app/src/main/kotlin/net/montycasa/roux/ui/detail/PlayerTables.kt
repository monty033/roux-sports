@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.SkaterLine
import net.montycasa.roux.ui.theme.LocalRouxAccents

@Composable
internal fun PlayerTables(
    goalies: List<GoalieLine>,
    skaters: List<SkaterLine>,
) {
    Column {
        if (skaters.isNotEmpty()) {
            SubHeading("Skaters")
            RosterTable(
                tag = "detail.section.skaters",
                headers = SKATER_COLUMNS,
                rows = skaters.map { skaterRow(it) },
            )
        }
        if (goalies.isNotEmpty()) {
            Spacer(Modifier.height(if (skaters.isEmpty()) 0.dp else 14.dp))
            SubHeading("Goalies")
            RosterTable(
                tag = "detail.section.goalies",
                headers = GOALIE_COLUMNS,
                rows = goalies.map { goalieRow(it) },
            )
        }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = LocalRouxAccents.current.muted,
    )
}

private fun skaterRow(line: SkaterLine): Pair<String, List<String>> =
    // The name is the name; position is its own column so a column of surnames stays readable.
    line.skater.name to
        listOf(
            line.position,
            line.goals.toString(),
            line.assists.toString(),
            line.points.toString(),
            if (line.plusMinus > 0) "+${line.plusMinus}" else line.plusMinus.toString(),
            line.shotsOnGoal.toString(),
            line.penaltyMinutes.toString(),
            formatIceTime(line.toiSeconds),
            line.faceoffPercent()?.let { "$it%" } ?: "—",
        )

private fun goalieRow(line: GoalieLine): Pair<String, List<String>> =
    line.goalie.name to
        listOf(
            line.shotsAgainst.toString(),
            line.saves.toString(),
            line.savePercentage()?.let { formatAverage(it * 100, 1) } ?: "—",
            line.goalsAgainstAverage()?.let { formatAverage(it, 2) } ?: "—",
        )

private val GOALIE_COLUMNS = listOf("SA", "SV", "SV%", "GAA")
private val SKATER_COLUMNS = listOf("POS", "G", "A", "P", "+/-", "SOG", "PIM", "TOI", "FO%")

@Composable
private fun RosterTable(
    tag: String,
    headers: List<String>,
    rows: List<Pair<String, List<String>>>,
) {
    Row(Modifier.fillMaxWidth()) {
        // The names stay put. The reason to scroll is to reach a column, never to lose track of whose row it is.
        Column {
            Spacer(Modifier.height(ROW_HEADER_HEIGHT))
            rows.forEach { (name, _) ->
                Box(Modifier.width(NAME_COLUMN.dp).height(ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .testTag(tag),
        ) {
            Row {
                headers.forEach { StatHeader(it) }
            }
            rows.forEach { (_, values) ->
                Row {
                    values.forEach { StatCell(it) }
                }
            }
        }
    }
}

private val ROW_HEADER_HEIGHT = 22.dp
private val ROW_HEIGHT = 30.dp

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
    Text(
        text,
        modifier = Modifier.width(STAT_COLUMN.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = LocalRouxAccents.current.muted,
        maxLines = 1,
    )
}

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
