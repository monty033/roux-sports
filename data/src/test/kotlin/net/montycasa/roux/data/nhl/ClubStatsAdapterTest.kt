package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.montycasa.roux.data.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val skater = parsed().skaters.first { it.playerId == 8473986L }

        assertEquals("Alex Killorn", skater.name)
        assertEquals("L", skater.positionCode)
        assertEquals("https://assets.nhle.com/mugs/nhl/20262027/ANA/8473986.png", skater.headshotUrl)
        assertEquals(14, skater.gamesPlayed)
        assertEquals(2, skater.goals)
        assertEquals(3, skater.assists)
        assertEquals(5, skater.points)
        assertEquals(-3, skater.plusMinus)
        assertEquals(8, skater.penaltyMinutes)
        assertEquals(0.1, requireNotNull(skater.shootingPctg), 0.0000001)
        assertEquals(958.7857, requireNotNull(skater.avgTimeOnIcePerGame), 0.0000001)
    }

    @Test
    fun theGoaliesCarryTheirSeasonLineVerbatim() {
        val goalie = parsed().goalies.first { it.playerId == 8476316L }

        assertEquals("Laurent Brossoit", goalie.name)
        assertEquals(1, goalie.gamesPlayed)
        assertEquals(1, goalie.wins)
        assertEquals(0, goalie.losses)
        assertEquals(0, goalie.overtimeLosses)
        assertEquals(3.0, requireNotNull(goalie.goalsAgainstAverage), 0.0000001)
        assertEquals(0.869565, requireNotNull(goalie.savePercentage), 0.0000001)
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
        val order =
            compareByDescending<net.montycasa.roux.domain.model.SkaterStat> { it.points }
                .thenByDescending { it.goals }
                .thenByDescending { it.gamesPlayed }
                .thenBy { it.name }
        skaters.zipWithNext().forEach { (above, below) ->
            assertTrue(
                "${above.name} must not outrank ${below.name}",
                order.compare(above, below) <= 0,
            )
        }
    }

    @Test
    fun goaliesAreOrderedByGamesPlayedThenGoalsAgainstAverage() {
        val goalies = parsed().goalies

        assertEquals("Lukas Dostal", goalies.first().name)
        assertEquals(9, goalies.first().gamesPlayed)
        val order =
            compareByDescending<net.montycasa.roux.domain.model.GoalieStat> { it.gamesPlayed }
                .thenBy { it.goalsAgainstAverage ?: Double.MAX_VALUE }
                .thenBy { it.name }
        goalies.zipWithNext().forEach { (above, below) ->
            assertTrue(
                "${above.name} must not outrank ${below.name}",
                order.compare(above, below) <= 0,
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

        val result = ClubStatsAdapter.parse(json, SEASON)

        assertTrue("A missing number must not discard the payload: $result", result is ParseResult.Success)
        val stats = (result as ParseResult.Success).value
        assertEquals(23, stats.skaters.size)
        assertNull("an absent number must stay absent, not become zero", stats.skaters.first { it.playerId == 8473986L }.points)
    }

    @Test
    fun anAbsentGoalieNumberIsNullRatherThanZero() {
        val json =
            mutate { root ->
                val goalies = root.getValue("goalies").jsonArray
                val first = JsonObject(goalies.first().jsonObject - "goalsAgainstAverage" - "savePercentage")
                JsonObject(root + ("goalies" to JsonArray(listOf(first) + goalies.drop(1))))
            }

        val result = ClubStatsAdapter.parse(json, SEASON)

        assertTrue("An absent number must not discard the payload: $result", result is ParseResult.Success)
        val goalie = (result as ParseResult.Success).value.goalies.first { it.playerId == 8476316L }
        assertNull(goalie.goalsAgainstAverage)
        assertNull(goalie.savePercentage)
    }

    @Test
    fun aMissingGroupIsAFailureRatherThanAnEmptySuccess() {
        val json = mutate { root -> JsonObject(root - "goalies") }

        val result = ClubStatsAdapter.parse(json, SEASON)

        assertTrue("A missing group must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("goalies"))
    }

    @Test
    fun aMalformedGroupIsAFailure() {
        val json = mutate { root -> JsonObject(root + ("skaters" to JsonPrimitive("not a list"))) }

        val result = ClubStatsAdapter.parse(json, SEASON)

        assertTrue("A malformed group must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("decoding failed"))
    }

    @Test
    fun anEmptyGroupIsNotAnHonestEmptySuccess() {
        val json = mutate { root -> JsonObject(root + ("goalies" to JsonArray(emptyList()))) }

        val result = ClubStatsAdapter.parse(json, SEASON)

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

        val stats = (ClubStatsAdapter.parse(json, SEASON) as ParseResult.Success).value

        assertEquals(22, stats.skaters.size)
        assertNull(stats.skaters.firstOrNull { it.playerId == 8473986L })
    }

    @Test
    fun theParsedValueNamesTheSeasonItWasAskedFor() {
        assertEquals(SEASON, parsed().season)

        val earlier = (ClubStatsAdapter.parse(fixture, "20252026") as ParseResult.Success).value

        assertEquals("20252026", earlier.season)
    }

    @Test
    fun bothGroupsWithNoRowsIsAnEmptySeasonThatMayRollBack() {
        val json = mutate { root -> JsonObject(root + mapOf("skaters" to JsonArray(emptyList()), "goalies" to JsonArray(emptyList()))) }

        val result = ClubStatsAdapter.parse(json, SEASON)

        assertTrue("An empty season must not masquerade as data: $result", result is ParseResult.Failure)
        assertTrue("An empty season must be flagged so the repository can roll back", (result as ParseResult.Failure).emptyPayload)
    }

    @Test
    fun oneEmptyGroupIsAFailureButNotAnEmptySeason() {
        val json = mutate { root -> JsonObject(root + ("goalies" to JsonArray(emptyList()))) }

        val result = ClubStatsAdapter.parse(json, SEASON)

        assertTrue("One empty group beside rows is a broken reply: $result", result is ParseResult.Failure)
        assertFalse((result as ParseResult.Failure).emptyPayload)
    }

    @Test
    fun aMalformedReplyIsNotAnEmptySeason() {
        val result = ClubStatsAdapter.parse("""{"skaters":"nope"}""", SEASON)

        assertTrue(result is ParseResult.Failure)
        assertFalse((result as ParseResult.Failure).emptyPayload)
    }

    private fun parsed() = (ClubStatsAdapter.parse(fixture, SEASON) as ParseResult.Success).value

    private fun mutate(transform: (JsonObject) -> JsonObject): String = transform(Json.parseToJsonElement(fixture).jsonObject).toString()

    private companion object {
        const val SEASON = "20262027"
        val fixture = Fixtures.clubStats("club-stats-ana-20262027.json")
    }
}
