package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.ui.AuditStates
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scoring summary is read forwards: the period's first goal (most time remaining) is the top row, and each later
 * goal has less time and a running score that is never lower than the row above it.
 *
 * The cases are [GameLanding] objects rather than hand-built rows, so a contradictory audit fixture fails here too. The
 * short-minute case exists because "9:30" sorts before "14:22" as a string but after it as 570 against 862 seconds.
 */
class ScoringSummaryOrderTest {
    private val shortMinuteGoals =
        listOf(
            goal(period = 1, time = "5:12", away = 2, home = 1),
            goal(period = 1, time = "14:22", away = 1, home = 0),
            goal(period = 1, time = "9:30", away = 1, home = 1),
        )

    @Test
    fun `every period climbs in score while its clock falls`() {
        listOf(
            "detail.full audit" to GameLanding(goals = AuditStates.fullGoals),
            "detail.live audit" to GameLanding(goals = AuditStates.liveGoals),
            "short minutes" to GameLanding(goals = shortMinuteGoals),
        ).forEach { (name, landing) ->
            scoringGroups(landing.goals).forEach { group ->
                // An OT group folds several periods, and each period's clock starts again at its own large number.
                // Compare rows only inside one period; the group's period order is asserted by the OT UI test.
                group.goals.groupBy { it.goal.period }.forEach { (period, rows) ->
                    rows.zipWithNext().forEach { (first, next) ->
                        val firstTime =
                            requireNotNull(timeInPeriodSeconds(first.goal.timeInPeriod)) {
                                "$name ${group.label} period $period: unparseable first time"
                            }
                        val nextTime =
                            requireNotNull(timeInPeriodSeconds(next.goal.timeInPeriod)) {
                                "$name ${group.label} period $period: unparseable next time"
                            }
                        assertTrue(
                            "$name ${group.label} period $period: time rose from " +
                                "${first.goal.timeInPeriod} to ${next.goal.timeInPeriod}",
                            nextTime <= firstTime,
                        )
                        assertTrue(
                            "$name ${group.label} period $period: away score fell from " +
                                "${first.goal.awayScore} to ${next.goal.awayScore}",
                            requireNotNull(next.goal.awayScore) >= requireNotNull(first.goal.awayScore),
                        )
                        assertTrue(
                            "$name ${group.label} period $period: home score fell from " +
                                "${first.goal.homeScore} to ${next.goal.homeScore}",
                            requireNotNull(next.goal.homeScore) >= requireNotNull(first.goal.homeScore),
                        )
                    }
                }
            }
        }
    }

    private fun goal(
        period: Int,
        time: String,
        away: Int,
        home: Int,
    ) = GoalEvent(
        period = period,
        timeInPeriod = time,
        isHome = false,
        scorer = "A. Scorer",
        goalsToDate = null,
        assists = emptyList(),
        strength = GoalStrength.EvenStrength,
        modifier = GoalModifier.None,
        awayScore = away,
        homeScore = home,
    )
}
