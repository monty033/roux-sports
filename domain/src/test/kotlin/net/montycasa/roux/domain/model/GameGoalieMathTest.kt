package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two derived goaltender numbers, and the cases where there is no honest number to give.
 *
 * Both are computed rather than read - the boxscore publishes goals against and ice time, not an average or a
 * percentage - so the arithmetic and its refusals are worth pinning here rather than in the screen.
 *
 * The fixture values are real: C. Talbot played 62:22 and allowed 3 in the captured CBJ-PHI game.
 */
class GameGoalieMathTest {
    private fun goalie(
        saves: Int,
        shotsAgainst: Int,
        goalsAgainst: Int,
        toiSeconds: Long,
    ) = GoalieLine(
        goalie = PlayerRef(name = "C. Talbot", teamAbbrev = "CBJ"),
        saves = saves,
        shotsAgainst = shotsAgainst,
        goalsAgainst = goalsAgainst,
        toiSeconds = toiSeconds,
    )

    @Test
    fun goalsAgainstAverageIsGoalsPerSixtyMinutesOfThatGoalieSIceTime() {
        // 3 goals in 62:22 (3742 s): 3 * 3600 / 3742 = 2.886...
        val average = goalie(saves = 29, shotsAgainst = 32, goalsAgainst = 3, toiSeconds = 3742).goalsAgainstAverage()
        assertEquals(2.89, average!!, 0.005)
    }

    /**
     * The denominator is the goalie's own ice time, not the game length. Same goals over 40 minutes must produce a
     * worse average, or the number would flatter a goalie who was pulled early.
     */
    @Test
    fun theSameGoalsOverLessIceTimeIsAWorseAverage() {
        val full = goalie(saves = 20, shotsAgainst = 23, goalsAgainst = 3, toiSeconds = 3600).goalsAgainstAverage()!!
        val pulled = goalie(saves = 12, shotsAgainst = 15, goalsAgainst = 3, toiSeconds = 2400).goalsAgainstAverage()!!
        assertEquals(3.0, full, 0.0001)
        assertEquals(4.5, pulled, 0.0001)
    }

    /** A goalie who dressed but never played has no average; zero would claim a shutout he did not earn. */
    @Test
    fun noIceTimeMeansNoAverage() {
        assertNull(goalie(saves = 0, shotsAgainst = 0, goalsAgainst = 0, toiSeconds = 0).goalsAgainstAverage())
    }

    /** A shutout is a real 0.00 - distinct from the null above. */
    @Test
    fun aShutoutIsZeroNotNull() {
        assertEquals(0.0, goalie(saves = 30, shotsAgainst = 30, goalsAgainst = 0, toiSeconds = 3600).goalsAgainstAverage()!!, 0.0)
    }

    @Test
    fun savePercentageIsSavesOverShotsFaced() {
        assertEquals(29.0 / 32.0, goalie(saves = 29, shotsAgainst = 32, goalsAgainst = 3, toiSeconds = 3742).savePercentage()!!, 0.0001)
    }

    /** Facing no shots is not 0% - it is no information, and the page shows a dash instead. */
    @Test
    fun noShotsFacedMeansNoPercentage() {
        assertNull(goalie(saves = 0, shotsAgainst = 0, goalsAgainst = 0, toiSeconds = 600).savePercentage())
    }

    @Test
    fun faceoffPercentageRoundsToWholeNumbers() {
        val centre = skater(faceoffFraction = 0.533333)
        assertEquals(53, centre.faceoffPercent())
    }

    @Test
    fun aSkatersLostDrawIsZeroPercentNotAbsent() {
        assertEquals(0, skater(faceoffFraction = 0.0).faceoffPercent())
    }

    @Test
    fun aSkatersWhoNeverTookADrawHasNoPercentage() {
        assertNull(skater(faceoffFraction = null).faceoffPercent())
    }

    private fun skater(faceoffFraction: Double?) =
        SkaterLine(
            skater = PlayerRef(name = "C. Coyle", teamAbbrev = "CBJ"),
            position = "C",
            goals = 0,
            assists = 0,
            points = 0,
            plusMinus = 0,
            shotsOnGoal = 0,
            penaltyMinutes = 0,
            toiSeconds = 1176,
            faceoffFraction = faceoffFraction,
        )
}
