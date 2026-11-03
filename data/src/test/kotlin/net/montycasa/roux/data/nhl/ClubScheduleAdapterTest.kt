package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * The captured payload is Anaheim's 20262027 club schedule, taken from the live API on 3 November
 * 2026. It carries no score fields at all, on finished games or otherwise, which is why the adapter
 * has nowhere to put one.
 */
class ClubScheduleAdapterTest {
    @Test
    fun theCapturedAnaheimSeasonHasEightyEightGames() {
        val result = NhlAdapter.parseClubSchedule(fixture)

        assertTrue("The captured payload must parse: $result", result is ParseResult.Success)
        assertEquals(88, (result as ParseResult.Success).value.size)
    }

    @Test
    fun theFirstFixtureCarriesItsIdentityStartVenueClubsAndFinishedState() {
        val games = (NhlAdapter.parseClubSchedule(fixture) as ParseResult.Success).value
        val first = games.first()

        assertEquals(GameId("2026010009"), first.id)
        assertEquals("2026-09-20", first.date)
        assertEquals(Instant.parse("2026-09-20T20:00:00Z"), first.startTimeUtc)
        assertEquals("Honda Center", first.venue)
        assertEquals("ANA", first.home.abbrev)
        assertEquals("SJS", first.away.abbrev)
        assertEquals("Anaheim Ducks", first.home.name)
        assertEquals("San Jose Sharks", first.away.name)
        assertEquals("Ducks", first.home.shortName)
        assertEquals(GameState.Final, first.state)
        assertTrue(first.finished)
        assertEquals("OK", first.gameScheduleState)
    }

    @Test
    fun futureGamesRemainScheduledAndUnfinished() {
        val games = (NhlAdapter.parseClubSchedule(fixture) as ParseResult.Success).value
        val future = games.first { it.id.value == "2026020246" }

        assertEquals(GameState.Scheduled, future.state)
        assertTrue(!future.finished)
        assertEquals("2026-11-03", future.date)
    }

    @Test
    fun anEmptyGamesArrayIsAFailureRatherThanAnEmptySeason() {
        val json = mutate { root -> JsonObject(root + ("games" to JsonArray(emptyList()))) }

        val result = NhlAdapter.parseClubSchedule(json)

        assertTrue("An empty season schedule must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("empty games"))
    }

    @Test
    fun aMissingGamesFieldIsAFailure() {
        val json = mutate { root -> JsonObject(root - "games") }

        val result = NhlAdapter.parseClubSchedule(json)

        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("games"))
    }

    @Test
    fun malformedJsonIsAFailureWithDiagnosticContext() {
        listOf(fixture.dropLast(1), "{}", "null", "[]", "\"bare string\"", "").forEach { json ->
            val result = NhlAdapter.parseClubSchedule(json)

            assertTrue("Malformed '$json' must fail: $result", result is ParseResult.Failure)
            assertTrue((result as ParseResult.Failure).reason.isNotBlank())
        }
    }

    @Test
    fun anUnusableRowIsDroppedButTheRestOfTheSeasonSurvives() {
        val json =
            mutate { root ->
                val games = root.getValue("games").jsonArray
                JsonObject(root + ("games" to JsonArray(games + JsonPrimitive("not a game"))))
            }

        val games = (NhlAdapter.parseClubSchedule(json) as ParseResult.Success).value

        assertEquals(88, games.size)
    }

    @Test
    fun aScheduleWithNoUsableRowsIsAFailure() {
        val json = mutate { root -> JsonObject(root + ("games" to JsonArray(listOf(JsonPrimitive("bad"), JsonObject(emptyMap()))))) }

        val result = NhlAdapter.parseClubSchedule(json)

        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("no usable games"))
    }

    private fun mutate(transform: (JsonObject) -> JsonObject): String = transform(Json.parseToJsonElement(fixture).jsonObject).toString()

    private companion object {
        val fixture = Fixtures.clubSchedule("club-schedule-ana-20262027.json")
    }
}
