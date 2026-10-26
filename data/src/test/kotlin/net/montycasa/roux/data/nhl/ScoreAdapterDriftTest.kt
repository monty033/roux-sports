package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** Whole-day comparisons pin the exact permitted deltas when the score feed drifts. */
class ScoreAdapterDriftTest {
    @Test
    fun missingStateKeepsEveryGameWithOnlyAnEmptyUnknownState() {
        val json = mutateGameday(0) { JsonObject(it - "gameState") }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value
        val expectedGames = listOf(baseline.games.first().copy(state = GameState.Unknown(""))) + baseline.games.drop(1)

        assertEquals(12, day.games.size)
        assertEquals(0, day.droppedCount)
        assertEquals(baseline.copy(games = expectedGames), day)
    }

    @Test
    fun missingHomeTeamDropsOnlyThatGameAndPreservesTheRemainingOrder() {
        val json = mutateGameday(1) { JsonObject(it - "homeTeam") }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value
        val expectedGames = baseline.games.filterIndexed { index, _ -> index != 1 }

        assertEquals(11, day.games.size)
        assertEquals(1, day.droppedCount)
        assertEquals(expectedGames.map { it.id }, day.games.map { it.id })
        assertEquals(baseline.copy(games = expectedGames, droppedCount = 1), day)
    }

    @Test
    fun unknownFieldsLeaveTheEntireDayUnchanged() {
        val json =
            mutateGameday { score ->
                val games = score.getValue("games").jsonArray
                val first = JsonObject(games.first().jsonObject + ("futureGameKey" to JsonPrimitive(true)))
                JsonObject(
                    score +
                        mapOf(
                            "futureTopLevelKey" to JsonPrimitive("future"),
                            "games" to JsonArray(listOf(first) + games.drop(1)),
                        ),
                )
            }

        assertEquals(baseline, (NhlAdapter.parseScore(json) as ParseResult.Success).value)
    }

    @Test
    fun missingNavigationOnlyEmptiesTheDateIndex() {
        val json = mutateGameday { JsonObject(it - "gameWeek") }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(12, day.games.size)
        assertEquals(baseline.copy(dateIndex = emptyList()), day)
    }

    @Test
    fun unmodeledPeriodLeavesTheEntireDayUnchanged() {
        // GameDto does not model the period scalar, only periodDescriptor. This mutation is
        // structurally a no-op and does not demonstrate scalar coercion or descriptor tolerance.
        val json = mutateGameday(0) { JsonObject(it + ("period" to JsonPrimitive("3"))) }

        assertEquals(baseline, (NhlAdapter.parseScore(json) as ParseResult.Success).value)
    }

    @Test
    fun stringPeriodDescriptorOnlyRemovesThatGamesPeriod() {
        val json = mutateGameday(0) { JsonObject(it + ("periodDescriptor" to JsonPrimitive("3"))) }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value
        val expectedGames = listOf(baseline.games.first().copy(period = null)) + baseline.games.drop(1)

        assertNotNull(baseline.games.first().period)
        assertEquals(baseline.copy(games = expectedGames), day)
    }

    @Test
    fun arrayPeriodDescriptorOnlyRemovesThatGamesPeriod() {
        val json = mutateGameday(0) { JsonObject(it + ("periodDescriptor" to JsonArray(listOf(JsonPrimitive(3))))) }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value
        val expectedGames = listOf(baseline.games.first().copy(period = null)) + baseline.games.drop(1)

        assertNotNull(baseline.games.first().period)
        assertEquals(baseline.copy(games = expectedGames), day)
    }

    @Test
    fun truncatedDocumentsReturnFailure() {
        val bytes = fixture.toByteArray(Charsets.UTF_8)
        val truncated =
            listOf(
                "last byte missing" to bytes.copyOf(bytes.size - 1),
                "only 40 percent retained" to bytes.copyOf(bytes.size * 40 / 100),
            )

        truncated.forEach { (label, input) ->
            val result = NhlAdapter.parseScore(input.toString(Charsets.UTF_8))
            assertTrue(label, result is ParseResult.Failure)
        }
    }

    @Test
    fun everyHostileInputReturnsAParseResult() {
        val random = Random(1407)
        val randomText = buildString { repeat(100 * 1024) { append(random.nextInt(256).toChar()) } }
        val inputs =
            listOf(
                "empty string" to "",
                "whitespace only" to " \t\r\n",
                "null" to "null",
                "empty array" to "[]",
                "number array" to "[1,2,3]",
                "bare JSON string" to "\"garbage\"",
                "empty object" to "{}",
                "object games" to """{"games":{}}""",
                "numeric games" to """{"games":42}""",
                "array game" to """{"games":[[]]}""",
                "null game" to """{"games":[null]}""",
                "numeric date" to """{"currentDate":5,"games":[]}""",
                "deeply nested garbage" to
                    ("""{"currentDate":"2026-10-24","games":[""" + "[".repeat(512) + "0" + "]".repeat(512) + "]}"),
                "100 KB random bytes as text (seed 1407)" to randomText,
            )

        inputs.forEach { (label, input) ->
            val result =
                try {
                    NhlAdapter.parseScore(input)
                } catch (throwable: Throwable) {
                    fail("Input '$label' escaped with ${throwable.javaClass.name}: ${throwable.message}")
                    return@forEach
                }
            assertTrue("Input '$label' must return a ParseResult", result is ParseResult.Success || result is ParseResult.Failure)
        }
    }

    private fun mutateGameday(
        gameIndex: Int? = null,
        transform: (JsonObject) -> JsonObject,
    ): String {
        val score = Json.parseToJsonElement(fixture).jsonObject
        if (gameIndex == null) return transform(score).toString()
        val games = score.getValue("games").jsonArray
        val mutatedGames = games.mapIndexed { index, game -> if (index == gameIndex) transform(game.jsonObject) else game }
        return JsonObject(score + ("games" to JsonArray(mutatedGames))).toString()
    }

    private companion object {
        val fixture = Fixtures.text("score-gameday.json")
        val baseline = (NhlAdapter.parseScore(fixture) as ParseResult.Success).value
    }
}
