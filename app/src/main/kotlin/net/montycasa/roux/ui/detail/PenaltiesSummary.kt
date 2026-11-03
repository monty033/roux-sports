@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.PenaltyEvent

/**
 * Every penalty call, grouped by period and ordered by the feed's elapsed clock.
 *
 * There are no score columns here, so every row is full width: time, the offender and the call on the name line, then
 * the drawn and served lines only when the payload carries them. The crest still sits in the shared zero-height gutter,
 * level with the offender's name, so the card reads like the scoring summary without inventing columns to fill.
 */
@Composable
internal fun PenaltiesSummary(
    game: Game,
    landing: GameLanding,
) {
    val groups = penaltyGroups(landing.penalties)
    Column {
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) PeriodDivider(tag = "detail.penalties.divider")
            PeriodHeading(
                label = group.label,
                tag = "detail.penalties.period.${group.key}",
                topPadding = if (groupIndex == 0) 0.dp else 2.dp,
            )
            group.penalties.forEach { indexed -> PenaltyRow(game = game, indexed = indexed) }
        }
    }
}

/**
 * One call: a tight text column with the offender's crest in the row's gutter.
 *
 * The crest follows the name line rather than the time line through the shared measured offset. That matters more here
 * than on a goal row: the drawn and served lines make the text block taller, so centring on the block would float the
 * crest below the name.
 */
@Composable
private fun PenaltyRow(
    game: Game,
    indexed: IndexedPenalty,
) {
    val penalty = indexed.penalty
    val padded = indexed.index.toString().padStart(2, '0')
    val team = listOf(game.away, game.home).firstOrNull { it.abbrev == penalty.teamAbbrev }
    var rowTopPx by remember { mutableFloatStateOf(0f) }
    var nameTopPx by remember { mutableFloatStateOf(0f) }
    var nameLineHeightPx by remember { mutableFloatStateOf(0f) }
    val crestSizePx = with(LocalDensity.current) { ROW_CREST.toPx() }
    val offsetPx =
        crestOffsetPx(
            rowTopPx = rowTopPx,
            nameTopPx = nameTopPx,
            nameLineHeightPx = nameLineHeightPx,
            crestSizePx = crestSizePx,
        )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("detail.penalties.row.$padded")
            .semantics(mergeDescendants = true) {}
            .onGloballyPositioned { coordinates -> rowTopPx = coordinates.positionInRoot().y },
        verticalAlignment = Alignment.Top,
    ) {
        if (team != null) {
            CrestGutter(
                tag = "detail.penalties.row.$padded.crestGutter",
                crestSize = ROW_CREST,
                offsetPx = offsetPx,
            ) {
                Box(
                    Modifier
                        .size(ROW_CREST)
                        .testTag("detail.penalties.row.$padded.crest.${team.abbrev}"),
                ) {
                    TeamLogo(
                        team = team,
                        crestSize = ROW_CREST,
                        imageTag = "detail.logo.penalties.row.$padded.${team.abbrev}",
                    )
                }
            }
        } else {
            // No crest can be resolved, but the text stays in the same column as every other row.
            Spacer(Modifier.width(ROW_CREST))
        }
        Spacer(Modifier.width(CREST_GAP))
        Column(Modifier.weight(1f).testTag("detail.penalties.row.$padded.text")) {
            Text(
                penalty.timeInPeriod,
                modifier = Modifier.fillMaxWidth().testTag("detail.penalties.row.$padded.time"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                penaltySentence(penalty),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = NAME_TOP_GAP)
                        .onGloballyPositioned { coordinates -> nameTopPx = coordinates.positionInRoot().y }
                        .testTag("detail.penalties.row.$padded.sentence"),
                // The name line is prose, so it stays at 14 sp or above even when it wraps.
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                onTextLayout = { layout ->
                    if (layout.lineCount > 0) {
                        nameLineHeightPx = layout.getLineBottom(0) - layout.getLineTop(0)
                    }
                },
            )
            penalty.drawnBy?.let { drawnBy ->
                Text(
                    "Drawn by $drawnBy",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = DETAIL_TOP_GAP)
                            .testTag("detail.penalties.row.$padded.drawn"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            penalty.servedBy?.let { servedBy ->
                Text(
                    "Served by $servedBy",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = DETAIL_TOP_GAP)
                            .testTag("detail.penalties.row.$padded.served"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The call as one sentence: offender, em dash, the call, then its duration in parentheses.
 *
 * The space before the parentheses is non-breaking so `holding` and `(2 min)` cannot be split across lines, the same
 * rule the assist line follows for a name and its count.
 */
private fun penaltySentence(penalty: PenaltyEvent): String =
    buildString {
        append(penalty.player)
        append(" — ")
        append(penalty.call)
        penalty.durationMinutes?.let { duration ->
            // Non-breaking before and inside the parenthetical: the call and its duration stay one unit, and the
            // number cannot be separated from its unit.
            append('\u00A0')
                .append('(')
                .append(duration)
                .append('\u00A0')
                .append("min)")
        }
    }

/**
 * Periods ascending, calls inside a period in the order they happened: elapsed seconds ascending.
 *
 * Extra periods share one group, keyed 4, as they do on the scoring summary. A group's heading still comes from the
 * feed's own `periodType`, so a shootout block says `SO` rather than the fourth-period `OT`.
 *
 * Sorting parses the clock numerically, never comparing the string: `"9:30"` is 570 seconds and belongs before
 * `"14:22"`. Equal clocks keep their feed order, which is what preserves the four calls at 08:43 in period 2 and the
 * pair of fighting majors at 11:55.
 */
internal fun penaltyGroups(penalties: List<PenaltyEvent>): List<PenaltyGroup> {
    var rowIndex = 0
    return penalties
        .groupBy { penalty -> minOf(penalty.period, OVERTIME_KEY) }
        .toSortedMap()
        .map { (key, periodPenalties) ->
            val ordered =
                if (key == OVERTIME_KEY) {
                    periodPenalties.sortedWith(
                        compareBy<PenaltyEvent> { it.period }
                            .thenBy { elapsedSeconds(it.timeInPeriod) ?: UNPARSED_ELAPSED },
                    )
                } else {
                    periodPenalties.sortedBy { elapsedSeconds(it.timeInPeriod) ?: UNPARSED_ELAPSED }
                }
            PenaltyGroup(
                key = key,
                label = periodBlockLabel(key, ordered.firstOrNull()?.periodType),
                penalties = ordered.map { IndexedPenalty(index = rowIndex++, penalty = it) },
            )
        }
}

internal data class PenaltyGroup(
    val key: Int,
    val label: String,
    val penalties: List<IndexedPenalty>,
)

internal data class IndexedPenalty(
    val index: Int,
    val penalty: PenaltyEvent,
)

private val DETAIL_TOP_GAP = 1.dp
