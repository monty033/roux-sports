@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.ui.theme.LocalRouxAccents
import kotlin.math.roundToInt

/** One comparison row, already formatted for display, with the numbers its bar is drawn from. */
internal data class StatRow(
    val label: String,
    val awayText: String,
    val homeText: String,
    val awayValue: Int,
    val homeValue: Int,
)

/** The league's category codes, in the order the design shows them. */
private val TEAM_STAT_LABELS =
    linkedMapOf(
        "sog" to "Shots",
        "faceoffWinningPctg" to "Faceoffs",
        "powerPlay" to "Power play",
        "pim" to "Penalty minutes",
        "hits" to "Hits",
        "blockedShots" to "Blocked shots",
        "giveaways" to "Giveaways",
        "takeaways" to "Takeaways",
    )

@Composable
internal fun GameStatsRows(rail: GameRightRail) {
    val rows = rail.comparisonRows()
    Column {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.awayText,
                    modifier = Modifier.width(46.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    row.label.uppercase(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalRouxAccents.current.muted,
                )
                Text(
                    row.homeText,
                    modifier = Modifier.width(46.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            SplitBar(
                awayValue = row.awayValue,
                homeValue = row.homeValue,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
            )
        }
    }
}

/**
 * The rail's own rows, ordered, with the duplicate notations folded away.
 *
 * The payload sends ten rows, and two of them are the same fact in another notation: `faceoffWins` repeats
 * `faceoffWinningPctg`, and `powerPlayPctg` repeats `powerPlay`. Drawing both would be two bars for one stat.
 *
 * Values arrive as integers, fractions of one, or `made/chances` strings depending on the row, so each is parsed as the
 * kind of number it is. A blanket integer coercion is what turned 0.56 into 56% of a bar, and turned `28/50` into
 * nothing at all.
 */
internal fun GameRightRail.comparisonRows(): List<StatRow> {
    val byCategory = teamStats.associateBy { it.label }
    return buildList {
        shots?.let {
            add(StatRow("Shots", it.awayShots.toString(), it.homeShots.toString(), it.awayShots, it.homeShots))
        }
        powerPlay?.let {
            add(
                StatRow(
                    label = "Power play",
                    awayText = "${it.awayGoals}/${it.awayChances}",
                    homeText = "${it.homeGoals}/${it.homeChances}",
                    awayValue = it.awayGoals,
                    homeValue = it.homeGoals,
                ),
            )
        }
        TEAM_STAT_LABELS.forEach { (category, label) ->
            // Shots and the power play already came from their typed rows above.
            if (category == "sog" || category == "powerPlay") return@forEach
            val stat = byCategory[category] ?: return@forEach
            val away = parseStatValue(category, stat.away) ?: return@forEach
            val home = parseStatValue(category, stat.home) ?: return@forEach
            add(
                StatRow(
                    label = label,
                    awayText = formatStatText(category, stat.away, away),
                    homeText = formatStatText(category, stat.home, home),
                    awayValue = away,
                    homeValue = home,
                ),
            )
        }
    }
}

/** The number the bar is drawn from: a fraction of one as whole percent, a fraction as its numerator, else the value. */
private fun parseStatValue(
    category: String,
    raw: String,
): Int? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    return when {
        category == "faceoffWinningPctg" -> text.toDoubleOrNull()?.let { (it * 100).roundToInt() }
        text.contains('/') -> text.substringBefore('/').trim().toIntOrNull()
        else -> text.toIntOrNull() ?: text.toDoubleOrNull()?.roundToInt()
    }
}

private fun formatStatText(
    category: String,
    raw: String,
    value: Int,
): String {
    val text = raw.trim()
    return when {
        category == "faceoffWinningPctg" -> "$value%"
        text.contains('/') -> text
        else -> value.toString()
    }
}

/** The narrowest either half of a comparison bar may be, so a zero never reads as a finished full-width bar. */
internal const val MIN_BAR_SIDE_DP = 13f

/**
 * The two halves of a comparison bar, as fractions of the track.
 *
 * Split by share of the total, then floored at [minSideDp] a side - the floor is what stops a 1-0 from drawing as a
 * solid bar. Both zero means nothing to divide: the caller draws the empty track and no fill, rather than inventing
 * 50/50.
 */
internal fun barShares(
    awayValue: Int,
    homeValue: Int,
    trackDp: Float,
    minSideDp: Float = MIN_BAR_SIDE_DP,
): Pair<Float, Float> {
    if (awayValue <= 0 && homeValue <= 0) return 0f to 0f
    if (trackDp <= 0f) return 0f to 0f
    val total = (awayValue.coerceAtLeast(0) + homeValue.coerceAtLeast(0)).toFloat()
    var away = awayValue.coerceAtLeast(0) / total * trackDp
    var home = homeValue.coerceAtLeast(0) / total * trackDp
    val floor = minOf(minSideDp, trackDp / 2f)
    if (away < floor) {
        away = floor
        home = trackDp - away
    }
    if (home < floor) {
        home = floor
        away = trackDp - home
    }
    return (away / trackDp) to (home / trackDp)
}

@Composable
private fun SplitBar(
    awayValue: Int,
    homeValue: Int,
    modifier: Modifier = Modifier,
) {
    val accents = LocalRouxAccents.current
    val track = accents.muted.copy(alpha = 0.28f)
    BoxWithConstraints(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track)
            .clearAndSetSemantics { },
    ) {
        val (awayShare, homeShare) = barShares(awayValue, homeValue, maxWidth.value)
        if (awayShare > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(awayShare)
                    .height(6.dp)
                    .background(accents.fill),
            )
        }
        if (homeShare > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(homeShare)
                    .height(6.dp)
                    .align(Alignment.CenterEnd)
                    .background(accents.muted),
            )
        }
    }
}
