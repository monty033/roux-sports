package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeagueSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeagueRankingTest {
    @Test
    fun theBestGoalsAgainstRanksFirstWhileTheBestGoalsForAlsoRanksFirst() {
        val league =
            league(
                club("Best", goalsForPerGame = 4.0, goalsAgainstPerGame = 2.0),
                club("Middle", goalsForPerGame = 3.0, goalsAgainstPerGame = 3.0),
                club("Worst", goalsForPerGame = 2.0, goalsAgainstPerGame = 4.0),
            )

        val best = LeagueRanking.forClub(league, "Best")!!
        val worst = LeagueRanking.forClub(league, "Worst")!!

        // Lower goals against is better, so the club conceding least is first; higher goals for is better too.
        assertEquals(1, best.rankOf(LeagueMetric.GoalsAgainstPerGame))
        assertEquals(1, best.rankOf(LeagueMetric.GoalsForPerGame))
        assertEquals(3, worst.rankOf(LeagueMetric.GoalsAgainstPerGame))
        assertEquals(3, worst.rankOf(LeagueMetric.GoalsForPerGame))
    }

    @Test
    fun aTieSharesTheBetterRankAndTheNextRankSkips() {
        val league =
            league(
                club("A", goalsForPerGame = 3.5),
                club("B", goalsForPerGame = 3.0),
                club("C", goalsForPerGame = 3.0),
                club("D", goalsForPerGame = 2.0),
            )

        val ranks =
            listOf("A", "B", "C", "D").map { name ->
                LeagueRanking.forClub(league, name)!!.rankOf(LeagueMetric.GoalsForPerGame)
            }

        assertEquals(listOf(1, 2, 2, 4), ranks)
    }

    @Test
    fun aMissingValueGetsNoRankAndDoesNotCountAgainstAnyone() {
        val league =
            league(
                club("Has value", goalsForPerGame = 3.0),
                club("Missing", goalsForPerGame = null),
                club("Lower", goalsForPerGame = 2.0),
            )

        assertNull(LeagueRanking.forClub(league, "Missing")!!.rankOf(LeagueMetric.GoalsForPerGame))
        assertEquals(2, LeagueRanking.forClub(league, "Lower")!!.rankOf(LeagueMetric.GoalsForPerGame))
    }

    @Test
    fun theClubCountIsTheWholeLeague() {
        val league = league(club("A"), club("B"), club("C"))

        assertEquals(3, LeagueRanking.forClub(league, "A")!!.clubCount)
    }

    @Test
    fun aClubNotInTheLeagueHasNoRanking() {
        val league = league(club("A", goalsForPerGame = 3.0))

        assertNull(LeagueRanking.forClub(league, "Not in the league"))
    }

    /** Fewest penalty minutes is first: the metric counts in the lower-is-better direction. */
    @Test
    fun theFewestPenaltyMinutesRanksFirstAndTheMostRanksLast() {
        val league =
            league(
                club("Fewest", penaltyMinutes = 100),
                club("Middle", penaltyMinutes = 200),
                club("Most", penaltyMinutes = 300),
            )

        assertEquals(1, LeagueRanking.forClub(league, "Fewest")!!.rankOf(LeagueMetric.PenaltyMinutes))
        assertEquals(3, LeagueRanking.forClub(league, "Most")!!.rankOf(LeagueMetric.PenaltyMinutes))
    }

    /** Rank 1 fills the track, the last place fills 1 / clubs, and tied clubs share one length. */
    @Test
    fun aRankBarIsTheClubsShareOfTheLeagueAndTiesShareALength() {
        val league =
            league(
                club("A", penaltyMinutes = 10),
                club("B", penaltyMinutes = 20),
                club("C", penaltyMinutes = 20),
                club("D", penaltyMinutes = 40),
            )

        val fractions =
            listOf("A", "B", "C", "D").map { name ->
                LeagueRanking.forClub(league, name)!!.barFractionOf(LeagueMetric.PenaltyMinutes)
            }

        assertEquals(listOf(1f, 0.75f, 0.75f, 0.25f), fractions)
    }

    /** A club with no value has no rank, so it has no bar, and it does not count against the other clubs. */
    @Test
    fun aClubWithNoValueHasNoBar() {
        val league =
            league(
                club("Has value", penaltyMinutes = 10),
                club("Missing", penaltyMinutes = null),
            )

        assertNull(LeagueRanking.forClub(league, "Missing")!!.barFractionOf(LeagueMetric.PenaltyMinutes))
        assertEquals(1f, LeagueRanking.forClub(league, "Has value")!!.barFractionOf(LeagueMetric.PenaltyMinutes))
    }

    private fun league(vararg clubs: LeagueClub) = LeagueSummary(clubs.toList())

    private fun club(
        name: String,
        goalsForPerGame: Double? = null,
        goalsAgainstPerGame: Double? = null,
        penaltyMinutes: Int? = null,
    ) = LeagueClub(
        teamName = name,
        goalsForPerGame = goalsForPerGame,
        goalsAgainstPerGame = goalsAgainstPerGame,
        penaltyMinutes = penaltyMinutes,
    )
}
