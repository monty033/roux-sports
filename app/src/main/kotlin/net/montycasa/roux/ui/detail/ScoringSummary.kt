@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.ui.theme.LocalRouxAccents

/**
 * Every goal, grouped the way a period is read: periods ascending, the period's first goal first inside each.
 *
 * The feed's clock is elapsed time, so it climbs down the card as the period goes on, and the running score climbs with
 * it. All overtime periods share one `OT` group, the same way the period tables share one `OT` column - there is no
 * useful distinction between the fourth and fifth extra period on a scoring summary, and two headers would pretend
 * there was.
 */
@Composable
internal fun ScoringSummary(
    game: Game,
    landing: GameLanding,
) {
    val groups = scoringGroups(landing.goals)
    Column {
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) PeriodDivider()
            PeriodHeading(game = game, group = group)
            group.goals.forEach { indexed -> ScoringRow(game = game, indexed = indexed) }
        }
    }
}

/**
 * A period heading names the clubs it belongs to.
 *
 * The ordinal sits at the left in the primary colour and the two abbreviations sit over the score columns, which
 * replaces the single header above the first period. Headings never become row indices.
 */
@Composable
private fun PeriodHeading(
    game: Game,
    group: ScoringGroup,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = if (group.key == 1) 0.dp else 2.dp, bottom = 4.dp)
                .testTag("detail.scoring.period.${group.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.label,
            modifier = Modifier.testTag("detail.scoring.period.${group.key}.label"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        ColumnAbbreviation(teamAbbrev = game.away.abbrev, side = "away", group = group)
        ColumnAbbreviation(teamAbbrev = game.home.abbrev, side = "home", group = group)
    }
}

@Composable
private fun ColumnAbbreviation(
    teamAbbrev: String,
    side: String,
    group: ScoringGroup,
) {
    Text(
        teamAbbrev,
        modifier =
            Modifier
                .width(SCORE_COLUMN)
                .testTag("detail.scoring.period.${group.key}.$side.abbrev"),
        style = MaterialTheme.typography.labelMedium,
        color = LocalRouxAccents.current.muted,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

/** One rule between consecutive period blocks, inset from the card edges and never above the first block. */
@Composable
private fun PeriodDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DIVIDER_INSET, vertical = 8.dp)
            .height(1.dp)
            .background(LocalRouxAccents.current.muted.copy(alpha = DIVIDER_ALPHA))
            .testTag("detail.scoring.divider"),
    )
}

/**
 * A goal row: the scoring club's crest at the far left, then a two-line text column, then the fixed score columns.
 *
 * The score columns top-align with the time line rather than the middle of the text block, so the numbers stay on one
 * reading line while the sentence beneath wraps to whatever height it needs.
 */
@Composable
private fun ScoringRow(
    game: Game,
    indexed: IndexedGoal,
) {
    val goal = indexed.goal
    val padded = indexed.index.toString().padStart(2, '0')
    val scoringTeam = if (goal.isHome) game.home else game.away
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("detail.scoring.row.$padded")
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.testTag("detail.scoring.row.$padded.crest.${scoringTeam.abbrev}")) {
            TeamLogo(
                team = scoringTeam,
                crestSize = ROW_CREST,
                imageTag = "detail.logo.scoring.row.$padded.${scoringTeam.abbrev}",
            )
        }
        Spacer(Modifier.width(CREST_GAP))
        Column(Modifier.weight(1f)) {
            Text(
                goal.timeInPeriod,
                modifier = Modifier.testTag("detail.scoring.row.$padded.time"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                goalSentence(goal),
                modifier = Modifier.padding(top = 2.dp).testTag("detail.scoring.row.$padded.sentence"),
                // The sentence is prose, so it stays at 14 sp or above even when it wraps.
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        ScoreCell(
            value = goal.awayScore?.toString() ?: MISSING_SCORE,
            emphasised = !goal.isHome,
            tag = "detail.scoring.row.$padded.away",
        )
        ScoreCell(
            value = goal.homeScore?.toString() ?: MISSING_SCORE,
            emphasised = goal.isHome,
            tag = "detail.scoring.row.$padded.home",
        )
    }
}

/** One fixed score column; the side that just changed is bold in the primary colour. */
@Composable
private fun ScoreCell(
    value: String,
    emphasised: Boolean,
    tag: String,
) {
    Box(
        modifier = Modifier.width(SCORE_COLUMN).then(if (emphasised) Modifier.testTag("$tag.emphasised") else Modifier),
        contentAlignment = Alignment.TopEnd,
    ) {
        Text(
            value,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasised) MaterialTheme.colorScheme.primary else LocalRouxAccents.current.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * The goal as one sentence: full name, season goals, strength in prose, then the assists.
 *
 * The feed already carries full first and last names; the adapter composes them, so this only lays the sentence out. An
 * unassisted goal stops before the assists fragment rather than printing an empty parenthetical.
 */
private fun goalSentence(goal: GoalEvent): String =
    buildString {
        append(goal.scorer)
        goal.goalsToDate?.let { seasonGoals -> append(' ').append(seasonGoals) }
        strengthPhrases(goal).forEach { phrase -> append(' ').append(phrase) }
        if (goal.assists.isNotEmpty()) {
            append(" (Assists: ")
            goal.assists.forEachIndexed { index, assist ->
                if (index > 0) append(", ")
                append(assist.name)
                assist.assistsToDate?.let { count -> append(' ').append(count) }
            }
            append(')')
        }
    }

/** Strength and modifier in words, in that order; even strength and unknown carry no phrase. */
private fun strengthPhrases(goal: GoalEvent): List<String> =
    buildList {
        when (goal.strength) {
            GoalStrength.PowerPlay -> add("(power play)")
            GoalStrength.ShortHanded -> add("(short-handed)")
            GoalStrength.EvenStrength, GoalStrength.Unknown -> Unit
        }
        when (goal.modifier) {
            GoalModifier.EmptyNet -> add("(empty net)")
            GoalModifier.PenaltyShot -> add("(penalty shot)")
            GoalModifier.None, GoalModifier.Other -> Unit
        }
    }

/**
 * Periods ascending, goals inside a period in the order they happened: elapsed seconds ascending.
 *
 * The feed writes elapsed time, so the first goal of a period has the smallest value and the last has the largest; the
 * times climb down the card. Ordering parses [GoalEvent.timeInPeriod] numerically, never comparing the raw string:
 * `"9:30"` is 570 seconds and belongs before `"14:22"`.
 *
 * Overtime is folded into one group keyed 4. Its goals run oldest extra period first, then elapsed ascending, so the
 * last goal of the game is the bottom row of the card.
 */
internal fun scoringGroups(goals: List<GoalEvent>): List<ScoringGroup> {
    var rowIndex = 0
    return goals
        .groupBy { goal -> minOf(goal.period, OVERTIME_KEY) }
        .toSortedMap()
        .map { (key, periodGoals) ->
            val ordered =
                if (key == OVERTIME_KEY) {
                    periodGoals.sortedWith(
                        compareBy<GoalEvent> { it.period }
                            .thenBy { elapsedSeconds(it.timeInPeriod) ?: UNPARSED_ELAPSED },
                    )
                } else {
                    periodGoals.sortedBy { elapsedSeconds(it.timeInPeriod) ?: UNPARSED_ELAPSED }
                }
            ScoringGroup(
                key = key,
                label = periodLabel(key),
                goals = ordered.map { IndexedGoal(index = rowIndex++, goal = it) },
            )
        }
}

/** `"MM:SS"` or `"M:SS"` as elapsed seconds; null when the feed sent something the card cannot place on a clock. */
private fun elapsedSeconds(time: String): Int? {
    val parts = time.trim().split(":")
    if (parts.size != 2) return null
    val minutes = parts[0].toIntOrNull() ?: return null
    val seconds = parts[1].toIntOrNull() ?: return null
    if (minutes < 0 || seconds !in 0..59) return null
    return minutes * 60 + seconds
}

private fun periodLabel(key: Int): String =
    when (key) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "OT"
    }

internal data class ScoringGroup(
    val key: Int,
    val label: String,
    val goals: List<IndexedGoal>,
)

internal data class IndexedGoal(
    val index: Int,
    val goal: GoalEvent,
)

private val ROW_CREST = 44.dp
private val CREST_GAP = 10.dp
private val SCORE_COLUMN = 34.dp
private val DIVIDER_INSET = 8.dp
private const val DIVIDER_ALPHA = 0.35f
private const val OVERTIME_KEY = 4
private const val MISSING_SCORE = "—"

/** Sorts an unparseable elapsed time after every real goal, never before the opening faceoff. */
private const val UNPARSED_ELAPSED = Int.MAX_VALUE
