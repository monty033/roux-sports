package net.montycasa.roux.data.nhl

import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeagueSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The captured payload is every club's 2026-27 regular-season totals, taken from the stats host on
 * 3 November 2026: 32 rows, one per club.
 */
class LeagueStatsAdapterTest {
    @Test
    fun theCapturedPayloadMapsAllThirtyTwoClubs() {
        assertEquals(32, parsed().clubs.size)
    }

    /** The numbers the Stats tab shows for Anaheim, read from the capture rather than from this file's author. */
    @Test
    fun anasTenValuesMatchTheCapturedRow() {
        val ana = ana()

        assertEquals(3.5, ana.goalsForPerGame!!, DELTA)
        assertEquals(3.78571, ana.goalsAgainstPerGame!!, DELTA)
        assertEquals(25.0, ana.shotsForPerGame!!, DELTA)
        assertEquals(27.57142, ana.shotsAgainstPerGame!!, DELTA)
        assertEquals(0.46979, ana.faceoffWinPct!!, DELTA)
        assertEquals(0.340425, ana.powerPlayPct!!, DELTA)
        assertEquals(0.763637, ana.penaltyKillPct!!, DELTA)
        assertEquals(13, ana.points)
        assertEquals(0.46428, ana.pointPct!!, DELTA)
        assertEquals(0, ana.teamShutouts)
    }

    @Test
    fun anasRecordComesFromTheSameRow() {
        val ana = ana()

        assertEquals(14, ana.gamesPlayed)
        assertEquals(6, ana.wins)
        assertEquals(7, ana.losses)
        assertEquals(1, ana.otLosses)
    }

    /** The feed's `ties` is null on every row. A null must stay a null, never a zero that reads as a real tie count. */
    @Test
    fun tiesNullDoesNotBecomeZeroAnywhere() {
        assertNull(ana().ties)
        assertTrue(parsed().clubs.all { it.ties == null })
    }

    @Test
    fun aMissingPercentageMapsToNullNotZero() {
        val club = parsedRow("""{"teamFullName":"Test Club","points":3}""")

        assertNull(club.faceoffWinPct)
        assertNull(club.powerPlayPct)
    }

    @Test
    fun anExplicitNullPercentageMapsToNullNotZero() {
        val club = parsedRow("""{"teamFullName":"Test Club","penaltyKillPct":null}""")

        assertNull(club.penaltyKillPct)
    }

    @Test
    fun aRowWithoutAClubNameIsDroppedAndTheRestKept() {
        val league =
            parsed(
                """{"data":[{"teamFullName":"  "},{"points":4},{"teamFullName":"Kept","points":3}]}""",
            )

        assertEquals(listOf("Kept"), league.clubs.map { it.teamName })
    }

    @Test
    fun aPayloadWithoutADataArrayIsAFailure() {
        assertTrue(LeagueStatsAdapter.parse("""{"total":32}""") is ParseResult.Failure)
    }

    @Test
    fun anEmptyDataArrayIsAFailure() {
        assertTrue(LeagueStatsAdapter.parse("""{"data":[]}""") is ParseResult.Failure)
    }

    @Test
    fun aBodyThatIsNotJsonIsAFailure() {
        assertTrue(LeagueStatsAdapter.parse("<html>maintenance</html>") is ParseResult.Failure)
    }

    private fun ana(): LeagueClub = parsed().clubs.first { it.teamName == "Anaheim Ducks" }

    private fun parsedRow(row: String): LeagueClub = parsed("""{"data":[$row]}""").clubs.single()

    private fun parsed(json: String = Fixtures.text(Fixtures.CLUB_STATS_CAPTURE, FIXTURE)): LeagueSummary {
        val result = LeagueStatsAdapter.parse(json)
        return (result as? ParseResult.Success)?.value ?: error("expected a parse, got $result")
    }

    private companion object {
        const val FIXTURE = "league-team-summary-20262027.json"
        const val DELTA = 1e-9
    }
}
