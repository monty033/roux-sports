package net.montycasa.roux.data.repo

import net.montycasa.roux.domain.model.ClubPenalties
import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeaguePenalties
import net.montycasa.roux.domain.model.LeagueSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The join of the penalties table onto the summary: by id when both sides have one, by name only otherwise. */
class LeaguePenaltyJoinTest {
    /**
     * The summary spells Montreal with a precomposed e-acute (U+00E9); the penalties feed is given the decomposed form,
     * e plus a combining acute accent (U+0065 U+0301). The names differ as strings while the club is the same. A
     * name-only join misses this pair and an id join finds it.
     */
    @Test
    fun aClubIsMatchedByIdEvenWhenTheNameSpellingDiffers() {
        val summary = summary(LeagueClub(teamName = MONTREAL, teamId = 8))
        val penalties = penalties(ClubPenalties(teamId = 8, teamName = MONTREAL_DECOMPOSED, penaltyMinutes = 173))

        val joined = joinPenalties(summary, penalties).clubs.single()

        assertEquals(173, joined.penaltyMinutes)
    }

    @Test
    fun aClubWithNoIdIsMatchedByExactName() {
        val summary = summary(LeagueClub(teamName = "Test Club"))
        val penalties = penalties(ClubPenalties(teamId = null, teamName = "Test Club", penaltyMinutes = 42))

        assertEquals(42, joinPenalties(summary, penalties).clubs.single().penaltyMinutes)
    }

    /** When both sides have ids, the id decides: a shared name with a different id is not the same club. */
    @Test
    fun anIdMismatchIsNotAMatchEvenWithTheSameName() {
        val summary = summary(LeagueClub(teamName = "Test Club", teamId = 1))
        val penalties = penalties(ClubPenalties(teamId = 2, teamName = "Test Club", penaltyMinutes = 42))

        assertNull(joinPenalties(summary, penalties).clubs.single().penaltyMinutes)
    }

    /** The name is never normalised: the decomposed spelling alone, with no id on either side, is a miss. */
    @Test
    fun aNameWithADifferentSpellingIsAMissWhenNeitherSideHasAnId() {
        val summary = summary(LeagueClub(teamName = MONTREAL))
        val penalties = penalties(ClubPenalties(teamId = null, teamName = MONTREAL_DECOMPOSED, penaltyMinutes = 173))

        assertNull(joinPenalties(summary, penalties).clubs.single().penaltyMinutes)
    }

    @Test
    fun aClubWithNoPenaltiesRowKeepsNullMinutes() {
        val summary = summary(LeagueClub(teamName = "Test Club", teamId = 1))
        val penalties = penalties(ClubPenalties(teamId = 2, teamName = "Other", penaltyMinutes = 42))

        assertNull(joinPenalties(summary, penalties).clubs.single().penaltyMinutes)
    }

    @Test
    fun aMissingPenaltiesTableLeavesEveryClubNull() {
        val summary = summary(LeagueClub(teamName = "Test Club", teamId = 1))

        assertNull(joinPenalties(summary, null).clubs.single().penaltyMinutes)
    }

    private fun summary(vararg clubs: LeagueClub) = LeagueSummary(clubs.toList())

    private fun penalties(vararg clubs: ClubPenalties) = LeaguePenalties(clubs.toList())

    private companion object {
        /** The summary's spelling: e-acute as one precomposed code point, U+00E9. */
        const val MONTREAL = "Montréal Canadiens"

        /** The same word with e-acute as e plus a combining acute accent, U+0065 U+0301: equal to a reader, unequal as a string. */
        const val MONTREAL_DECOMPOSED = "Montréal Canadiens"
    }
}
