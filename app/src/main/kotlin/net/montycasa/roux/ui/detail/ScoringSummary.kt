@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.ui.theme.LocalRouxAccents

/**
 * Every goal, grouped the way a period is read: periods ascending, newest goal first inside each.
 *
 * All overtime periods share one `OT` group, the same way the period tables share one `OT` column - there is no useful
 * distinction between the fourth and fifth extra period on a scoring summary, and two headers would pretend there was.
 */
@Composable
internal fun ScoringSummary(
    game: Game,
    landing: GameLanding,
) {
    Column {
        scoringGroups(landing.goals).forEach { group ->
            Text(
                group.label,
                modifier =
                    Modifier
                        .padding(top = if (group.key == 1) 0.dp else 10.dp, bottom = 2.dp)
                        .testTag("detail.scoring.period.${group.key}"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
                color = LocalRouxAccents.current.muted,
            )
            group.goals.forEach { indexed -> ScoringRow(game = game, indexed = indexed) }
        }
    }
}

@Composable
private fun ScoringRow(
    game: Game,
    indexed: IndexedGoal,
) {
    val goal = indexed.goal
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("detail.scoring.row.${indexed.index.toString().padStart(2, '0')}")
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.timeInPeriod,
                modifier = Modifier.width(TIME_COLUMN),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
            )
            Text(
                goal.scorer,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            goal.goalsToDate?.let { seasonGoals ->
                Text(
                    "($seasonGoals)",
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalRouxAccents.current.muted,
                )
            }
            strengthTags(goal).forEach { tag -> StrengthTag(tag) }
            Text(
                runningScore(goal),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            assistLine(game = game, goal = goal),
            modifier = Modifier.padding(start = TIME_COLUMN, top = 2.dp),
            // labelLarge rather than labelMedium: an assist line is often a full sentence of names, and this repo
            // requires prose to be at least 14 sp.
            style = MaterialTheme.typography.labelLarge,
            color = LocalRouxAccents.current.muted,
        )
    }
}

@Composable
private fun StrengthTag(tag: String) {
    Text(
        tag,
        modifier =
            Modifier
                .padding(start = 4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** `PP`, `SH`, `EN`, `PS`; strength before modifier, so a short-handed empty-net goal reads `SH EN`. */
private fun strengthTags(goal: GoalEvent): List<String> =
    buildList {
        when (goal.strength) {
            GoalStrength.PowerPlay -> add("PP")
            GoalStrength.ShortHanded -> add("SH")
            GoalStrength.EvenStrength, GoalStrength.Unknown -> Unit
        }
        when (goal.modifier) {
            GoalModifier.EmptyNet -> add("EN")
            GoalModifier.PenaltyShot -> add("PS")
            GoalModifier.None, GoalModifier.Other -> Unit
        }
    }

/** The running score after this goal, away first. A missing half is a dash, never a number the feed did not send. */
private fun runningScore(goal: GoalEvent): String {
    val away = goal.awayScore ?: return MISSING_SCORE
    val home = goal.homeScore ?: return MISSING_SCORE
    return "$away–$home"
}

/** The scoring club and its assists; the season count is printed only when the feed published one for that assist. */
private fun assistLine(
    game: Game,
    goal: GoalEvent,
): String {
    val club = if (goal.isHome) game.home.abbrev else game.away.abbrev
    if (goal.assists.isEmpty()) return "$club · Unassisted"
    val assists =
        goal.assists.joinToString(", ") { assist ->
            assist.assistsToDate?.let { count -> "${assist.name} ($count)" } ?: assist.name
        }
    return "$club · A: $assists"
}

/**
 * Periods ascending, goals inside a period newest first.
 *
 * Overtime is folded into one group keyed 4. Its goals run latest period first, then latest clock first, which is the
 * same "newest at the top" reading a single period has.
 */
private fun scoringGroups(goals: List<GoalEvent>): List<ScoringGroup> {
    var rowIndex = 0
    return goals
        .groupBy { goal -> minOf(goal.period, OVERTIME_KEY) }
        .toSortedMap()
        .map { (key, periodGoals) ->
            val ordered =
                if (key == OVERTIME_KEY) {
                    periodGoals.sortedWith(
                        compareByDescending<GoalEvent> { it.period }.thenByDescending { it.timeInPeriod },
                    )
                } else {
                    periodGoals.sortedByDescending { it.timeInPeriod }
                }
            ScoringGroup(
                key = key,
                label = periodLabel(key),
                goals = ordered.map { IndexedGoal(index = rowIndex++, goal = it) },
            )
        }
}

private fun periodLabel(key: Int): String =
    when (key) {
        1 -> "1ST"
        2 -> "2ND"
        3 -> "3RD"
        else -> "OT"
    }

private data class ScoringGroup(
    val key: Int,
    val label: String,
    val goals: List<IndexedGoal>,
)

private data class IndexedGoal(
    val index: Int,
    val goal: GoalEvent,
)

private val TIME_COLUMN = 46.dp
private const val OVERTIME_KEY = 4
private const val MISSING_SCORE = "—"
