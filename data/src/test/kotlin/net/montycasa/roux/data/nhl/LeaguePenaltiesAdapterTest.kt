package net.montycasa.roux.data.nhl

import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.ClubPenalties
import net.montycasa.roux.domain.model.LeaguePenalties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The captured payload is every club's 2026-27 regular-season penalty totals, taken from the stats host on
 * 4 November 2026: 32 rows, one per club.
 */
class LeaguePenaltiesAdapterTest {
    @Test
    fun theCapturedPayloadMapsAllThirtyTwoClubsWithIdAndMinutes() {
        val clubs = parsed().clubs

        assertEquals(32, clubs.size)
        assertTrue(clubs.all { it.teamId != null })
        assertTrue(clubs.all { it.penaltyMinutes != null })
    }

    /** Anaheim's total is the table's own season figure, not a per-game rate. */
    @Test
    fun anasIdAndMinutesMatchTheCapturedRow() {
        val ana = parsed().clubs.first { it.teamName == "Anaheim Ducks" }

        assertEquals(24, ana.teamId)
        assertEquals(205, ana.penaltyMinutes)
    }

    @Test
    fun aMissingMinutesNumberIsNullNotZero() {
        val club = parsedRow("""{"teamId":5,"teamFullName":"Test Club"}""")

        assertNull(club.penaltyMinutes)
    }

    @Test
    fun anExplicitNullMinutesNumberIsNullNotZero() {
        val club = parsedRow("""{"teamId":5,"teamFullName":"Test Club","penaltyMinutes":null}""")

        assertNull(club.penaltyMinutes)
    }

    @Test
    fun aRowWithNeitherIdNorNameIsDroppedAndTheRestKept() {
        val league =
            parsed(
                """{"data":[{"penaltyMinutes":4},{"teamFullName":"  "},{"teamFullName":"Kept","penaltyMinutes":3}]}""",
            )

        assertEquals(listOf(ClubPenalties(null, "Kept", 3)), league.clubs)
    }

    @Test
    fun aPayloadWithoutADataArrayIsAFailure() {
        assertTrue(LeaguePenaltiesAdapter.parse("""{"total":32}""") is ParseResult.Failure)
    }

    @Test
    fun anEmptyDataArrayIsAFailure() {
        assertTrue(LeaguePenaltiesAdapter.parse("""{"data":[]}""") is ParseResult.Failure)
    }

    @Test
    fun aBodyThatIsNotJsonIsAFailure() {
        assertTrue(LeaguePenaltiesAdapter.parse("<html>maintenance</html>") is ParseResult.Failure)
    }

    private fun parsedRow(row: String): ClubPenalties = parsed("""{"data":[$row]}""").clubs.single()

    private fun parsed(json: String = Fixtures.text(Fixtures.LEAGUE_PENALTIES_CAPTURE, FIXTURE)): LeaguePenalties {
        val result = LeaguePenaltiesAdapter.parse(json)
        return (result as? ParseResult.Success)?.value ?: error("expected a parse, got $result")
    }

    private companion object {
        const val FIXTURE = "league-team-penalties-20262027.json"
    }
}
