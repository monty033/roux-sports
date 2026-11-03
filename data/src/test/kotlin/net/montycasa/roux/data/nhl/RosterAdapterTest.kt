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
 * The captured payload is Anaheim's current roster, taken from the live API on 3 November 2026.
 * Its defence key is `defensemen`, while the boxscore feed calls the same group `defense`.
 */
class RosterAdapterTest {
    @Test
    fun theCapturedRosterMapsItsThreeFeedLists() {
        val roster = parsed()

        assertEquals(16, roster.forwards.size)
        assertEquals(8, roster.defense.size)
        assertEquals(3, roster.goalies.size)
    }

    @Test
    fun eachGroupIsOrderedBySweaterNumberAscending() {
        val roster = parsed()

        listOf(roster.forwards, roster.defense, roster.goalies).forEach { group ->
            val numbers = group.map { it.sweaterNumber }
            assertEquals(numbers.sortedBy { it }, numbers)
        }
    }

    @Test
    fun thePlayersCarryTheFieldsTheRosterTabShows() {
        val player = parsed().forwards.first { it.id == 8484153L }

        assertEquals("Leo Carlsson", player.name)
        assertEquals(91, player.sweaterNumber)
        assertEquals("C", player.positionCode)
        assertEquals("https://assets.nhle.com/mugs/nhl/20262027/ANA/8484153.png", player.headshotUrl)
    }

    @Test
    fun aMissingHeadshotOrSweaterNumberIsSurvivable() {
        val json =
            mutate { root ->
                val forwards = root.getValue("forwards").jsonArray
                val first = JsonObject(forwards.first().jsonObject - "headshot" - "sweaterNumber")
                JsonObject(root + ("forwards" to JsonArray(listOf(first) + forwards.drop(1))))
            }

        val roster = (RosterAdapter.parse(json) as ParseResult.Success).value
        val player = roster.forwards.first { it.id == 8484153L }

        assertNull(player.headshotUrl)
        assertNull(player.sweaterNumber)
        assertEquals("Leo Carlsson", player.name)
    }

    @Test
    fun aMissingGroupIsAFailureRatherThanAnEmptySuccess() {
        val json = mutate { root -> JsonObject(root - "defensemen") }

        val result = RosterAdapter.parse(json)

        assertTrue("A missing group must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("defensemen"))
    }

    @Test
    fun aMalformedGroupIsAFailure() {
        val json = mutate { root -> JsonObject(root + ("goalies" to JsonPrimitive("not a list"))) }

        val result = RosterAdapter.parse(json)

        assertTrue("A malformed group must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("decoding failed"))
    }

    @Test
    fun aNonEmptyGroupWithNoUsablePlayersIsAFailure() {
        val json = mutate { root -> JsonObject(root + ("forwards" to JsonArray(listOf(JsonObject(emptyMap()))))) }

        val result = RosterAdapter.parse(json)

        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("forwards"))
    }

    private fun parsed() = (RosterAdapter.parse(fixture) as ParseResult.Success).value

    private fun mutate(transform: (JsonObject) -> JsonObject): String = transform(Json.parseToJsonElement(fixture).jsonObject).toString()

    private companion object {
        val fixture = Fixtures.roster("roster-ana-current.json")
    }
}
