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

    private fun league(vararg clubs: LeagueClub) = LeagueSummary(clubs.toList())

    private fun club(
        name: String,
        goalsForPerGame: Double? = null,
        goalsAgainstPerGame: Double? = null,
    ) = LeagueClub(
        teamName = name,
        goalsForPerGame = goalsForPerGame,
        goalsAgainstPerGame = goalsAgainstPerGame,
    )
}
