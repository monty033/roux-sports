@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.LocalRouxAccents

/**
 * Every goal, grouped the way a period is read: periods ascending, the period's first goal first inside each.
 *
 * The clock runs down, so the first goal of a period has the most time remaining and the last has the least; the card
 * reads forwards through the period. All overtime periods share one `OT` group, the same way the period tables share one
 * `OT` column - there is no useful distinction between the fourth and fifth extra period on a scoring summary, and two
 * headers would pretend there was.
 */
@Composable
internal fun ScoringSummary(
    game: Game,
    landing: GameLanding,
) {
    Column {
        ScoreHeader(game = game)
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

/**
 * Names the two fixed score columns before any goal is listed.
 *
 * The crest and abbreviation are stacked above the column so neither has to share the row with the other: at font
 * scale 2.0 three letters are already the widest thing the column can hold, which is why full club names are not used.
 */
@Composable
private fun ScoreHeader(game: Game) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Spacer(Modifier.weight(1f))
        ScoreColumnHeader(team = game.away, side = "away")
        Spacer(Modifier.width(SCORE_DASH_COLUMN))
        ScoreColumnHeader(team = game.home, side = "home")
    }
}

@Composable
private fun ScoreColumnHeader(
    team: TeamRef,
    side: String,
) {
    Column(
        modifier = Modifier.width(SCORE_COLUMN).testTag("detail.scoring.header.$side"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.testTag("detail.scoring.header.$side.crest")) {
            TeamLogo(
                team = team,
                crestSize = HEADER_CREST,
                imageTag = "detail.logo.scoring.header.$side",
            )
        }
        Text(
            team.abbrev,
            modifier = Modifier.fillMaxWidth().testTag("detail.scoring.header.$side.abbrev"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalRouxAccents.current.muted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScoringRow(
    game: Game,
    indexed: IndexedGoal,
) {
    val goal = indexed.goal
    val padded = indexed.index.toString().padStart(2, '0')
    val scoringTeam = if (goal.isHome) game.home else game.away
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("detail.scoring.row.$padded")
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.timeRemaining,
                modifier = Modifier.width(TIME_COLUMN),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
            )
            Box(Modifier.testTag("detail.scoring.row.$padded.crest.${scoringTeam.abbrev}")) {
                TeamLogo(
                    team = scoringTeam,
                    crestSize = ROW_CREST,
                    imageTag = "detail.logo.scoring.row.$padded.${scoringTeam.abbrev}",
                )
            }
            Spacer(Modifier.width(CREST_GAP))
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
                    modifier = Modifier.padding(start = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalRouxAccents.current.muted,
                )
            }
            strengthTags(goal).forEach { tag -> StrengthTag(tag) }
            ScoreCell(
                value = goal.awayScore?.toString() ?: MISSING_SCORE,
                tag = "detail.scoring.row.$padded.away",
            )
            Text(
                "–",
                modifier = Modifier.width(SCORE_DASH_COLUMN),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalRouxAccents.current.muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            ScoreCell(
                value = goal.homeScore?.toString() ?: MISSING_SCORE,
                tag = "detail.scoring.row.$padded.home",
            )
        }
        Text(
            assistLine(game = game, goal = goal),
            modifier = Modifier.padding(start = TIME_COLUMN + ROW_CREST + CREST_GAP, top = 2.dp),
            // labelLarge rather than labelMedium: an assist line is often a full sentence of names, and this repo
            // requires prose to be at least 14 sp.
            style = MaterialTheme.typography.labelLarge,
            color = LocalRouxAccents.current.muted,
        )
    }
}

/** One fixed score column: right-aligned so a row of digits lines up down the whole card. */
@Composable
private fun ScoreCell(
    value: String,
    tag: String,
) {
    Text(
        value,
        modifier = Modifier.width(SCORE_COLUMN).testTag(tag),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
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
 * Periods ascending, goals inside a period in the order they happened: elapsed seconds ascending.
 *
 * The feed writes elapsed time, so the first goal of a period has the smallest elapsed value and the last has the
 * largest. The card shows the matching time-remaining label, which therefore falls down the card. Ordering uses the
 * parsed [GoalEvent.elapsedSeconds], never the raw string: `"9:30"` is 570 seconds and belongs after `"14:22"`.
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
                            .thenBy { it.elapsedSeconds ?: UNPARSED_ELAPSED },
                    )
                } else {
                    periodGoals.sortedBy { it.elapsedSeconds ?: UNPARSED_ELAPSED }
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

internal data class ScoringGroup(
    val key: Int,
    val label: String,
    val goals: List<IndexedGoal>,
)

internal data class IndexedGoal(
    val index: Int,
    val goal: GoalEvent,
)

private val TIME_COLUMN = 38.dp
private val ROW_CREST = PERIOD_CREST
private val HEADER_CREST = PERIOD_CREST
private val CREST_GAP = 2.dp
private val SCORE_COLUMN = 34.dp
private val SCORE_DASH_COLUMN = 10.dp
private const val OVERTIME_KEY = 4
private const val MISSING_SCORE = "—"

/** Sorts an unparseable elapsed time after every real goal, never before the opening faceoff. */
private const val UNPARSED_ELAPSED = Int.MAX_VALUE
