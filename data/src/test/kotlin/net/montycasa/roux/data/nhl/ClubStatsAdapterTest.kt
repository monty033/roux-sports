package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.montycasa.roux.data.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The captured payload is Anaheim's club statistics for 2026-27, taken from the live API on
 * 3 November 2026: 23 skaters and 3 goalies.
 */
class ClubStatsAdapterTest {
    @Test
    fun theCapturedPayloadMapsBothGroups() {
        val stats = parsed()

        assertEquals(23, stats.skaters.size)
        assertEquals(3, stats.goalies.size)
    }

    @Test
    fun theSkatersCarryTheFieldsTheTableShows() {
        val skater = parsed().skaters.first { it.id == 8473986L }

        assertEquals("Alex Killorn", skater.name)
        assertEquals("L", skater.positionCode)
        assertEquals("https://assets.nhle.com/mugs/nhl/20262027/ANA/8473986.png", skater.headshotUrl)
        assertEquals(14, skater.gamesPlayed)
        assertEquals(2, skater.goals)
        assertEquals(3, skater.assists)
        assertEquals(5, skater.points)
        assertEquals(-3, skater.plusMinus)
        assertEquals(8, skater.penaltyMinutes)
        assertEquals(0.1, skater.shootingPctg, 0.0000001)
        assertEquals(958.7857, skater.avgTimeOnIcePerGame, 0.0000001)
    }

    @Test
    fun theGoaliesCarryTheirSeasonLineVerbatim() {
        val goalie = parsed().goalies.first { it.id == 8476316L }

        assertEquals("Laurent Brossoit", goalie.name)
        assertEquals(1, goalie.gamesPlayed)
        assertEquals(1, goalie.wins)
        assertEquals(0, goalie.losses)
        assertEquals(0, goalie.overtimeLosses)
        assertEquals(3.0, goalie.goalsAgainstAverage, 0.0000001)
        assertEquals(0.869565, goalie.savePercentage, 0.0000001)
        assertEquals(0, goalie.shutouts)
        assertEquals(3600L, goalie.timeOnIce)
    }

    @Test
    fun skatersAreOrderedByPointsThenGoalsThenGamesPlayedThenName() {
        val skaters = parsed().skaters

        assertEquals("Leo Carlsson", skaters.first().name)
        assertEquals(21, skaters.first().points)
        // A lower-points skater with more games must not outrank a higher-points one.
        assertTrue(
            "points must be the first key",
            skaters.indexOfFirst { it.points == 12 } < skaters.indexOfFirst { it.points == 10 },
        )
        skaters.zipWithNext().forEach { (above, below) ->
            assertTrue(
                "${above.name} must not outrank ${below.name}",
                compareSkaters(above, below) <= 0,
            )
        }
    }

    @Test
    fun goaliesAreOrderedByGamesPlayedThenGoalsAgainstAverage() {
        val goalies = parsed().goalies

        assertEquals("Lukas Dostal", goalies.first().name)
        assertEquals(9, goalies.first().gamesPlayed)
        goalies.zipWithNext().forEach { (above, below) ->
            assertTrue(
                "${above.name} must not outrank ${below.name}",
                above.gamesPlayed > below.gamesPlayed ||
                    (above.gamesPlayed == below.gamesPlayed && above.goalsAgainstAverage <= below.goalsAgainstAverage),
            )
        }
    }

    @Test
    fun aMissingNumericFieldIsSurvivable() {
        val json =
            mutate { root ->
                val skaters = root.getValue("skaters").jsonArray
                val first = JsonObject(skaters.first().jsonObject - "points")
                JsonObject(root + ("skaters" to JsonArray(listOf(first) + skaters.drop(1))))
            }

        val result = ClubStatsAdapter.parse(json)

        assertTrue("A missing number must not discard the payload: $result", result is ParseResult.Success)
        val stats = (result as ParseResult.Success).value
        assertEquals(23, stats.skaters.size)
        assertEquals(0, stats.skaters.first { it.id == 8473986L }.points)
    }

    @Test
    fun aMissingGroupIsAFailureRatherThanAnEmptySuccess() {
        val json = mutate { root -> JsonObject(root - "goalies") }

        val result = ClubStatsAdapter.parse(json)

        assertTrue("A missing group must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("goalies"))
    }

    @Test
    fun aMalformedGroupIsAFailure() {
        val json = mutate { root -> JsonObject(root + ("skaters" to JsonPrimitive("not a list"))) }

        val result = ClubStatsAdapter.parse(json)

        assertTrue("A malformed group must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("decoding failed"))
    }

    @Test
    fun anEmptyGroupIsNotAnHonestEmptySuccess() {
        val json = mutate { root -> JsonObject(root + ("goalies" to JsonArray(emptyList()))) }

        val result = ClubStatsAdapter.parse(json)

        assertTrue("An empty group must not masquerade as data: $result", result is ParseResult.Failure)
    }

    @Test
    fun aRowWithNoNameIsDroppedButTheRestSurvive() {
        val json =
            mutate { root ->
                val skaters = root.getValue("skaters").jsonArray
                val first = JsonObject(skaters.first().jsonObject - "firstName" - "lastName")
                JsonObject(root + ("skaters" to JsonArray(listOf(first) + skaters.drop(1))))
            }

        val stats = (ClubStatsAdapter.parse(json) as ParseResult.Success).value

        assertEquals(22, stats.skaters.size)
        assertNull(stats.skaters.firstOrNull { it.id == 8473986L })
    }

    private fun parsed() = (ClubStatsAdapter.parse(fixture) as ParseResult.Success).value

    private fun mutate(transform: (JsonObject) -> JsonObject): String = transform(Json.parseToJsonElement(fixture).jsonObject).toString()

    private fun compareSkaters(
        first: net.montycasa.roux.domain.model.SkaterStat,
        second: net.montycasa.roux.domain.model.SkaterStat,
    ): Int {
        val byPoints = second.points.compareTo(first.points)
        if (byPoints != 0) return byPoints
        val byGoals = second.goals.compareTo(first.goals)
        if (byGoals != 0) return byGoals
        val byGames = second.gamesPlayed.compareTo(first.gamesPlayed)
        if (byGames != 0) return byGames
        return first.name.compareTo(second.name)
    }

    private companion object {
        val fixture = Fixtures.clubStats("club-stats-ana-20262027.json")
    }
}
