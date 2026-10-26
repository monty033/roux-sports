package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** Captured payloads guard against silently rendering a populated day as empty. */
class ScoreAdapterTest {
    @Test
    fun gamedayFinishedGamesAllHaveStoppedClocks() {
        val day = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value

        assertEquals(EXPECTED_GAMES, day.games.size)
        day.games.forEach { game ->
            assertEquals(GameState.Final, game.state)
            assertEquals("game ${game.id.value}", false, game.period?.running)
        }
    }

    @Test
    fun runningClockMapsToTrue() {
        val json =
            mutateGame(0) { game ->
                JsonObject(game + ("clock" to JsonObject(game.getValue("clock").jsonObject + ("running" to JsonPrimitive(true)))))
            }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        val game = day.games.first()
        assertEquals(true, game.period?.running)
    }

    @Test
    fun absentClockKeepsRunningNull() {
        val json = mutateGame(0) { JsonObject(it - "clock") }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(EXPECTED_GAMES, day.games.size)
        assertEquals(0, day.droppedCount)
        val period = day.games.first().period
        assertEquals(3, period?.ordinal)
        assertNull(period?.running)
    }

    @Test
    fun gamedayRetainsEveryGameAndDateNavigation() {
        val result = NhlAdapter.parseScore(Fixtures.text("score-gameday.json"))
        assertTrue(result is ParseResult.Success)
        val day = (result as ParseResult.Success).value

        assertEquals(EXPECTED_GAMES, day.games.size)
        val abbreviation = Regex("[A-Z]{3}")
        day.games.forEach { game ->
            assertTrue(abbreviation.matches(game.home.abbrev))
            assertTrue(abbreviation.matches(game.away.abbrev))
        }
        assertTrue(day.dateIndex.isNotEmpty())
        assertTrue(day.dateIndex.sumOf { it.numberOfGames } > 0)
        assertEquals(0, day.droppedCount)
    }

    @Test
    fun gamedayMatchesEveryFixtureMatchupAndScoreInOrder() {
        val day = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val expected =
            listOf(
                "2026020168|PHI|ANA|4|5",
                "2026020169|NJD|LAK|3|1",
                "2026020170|BOS|SJS|6|4",
                "2026020171|OTT|NYR|7|4",
                "2026020172|PIT|NSH|3|2",
                "2026020173|WSH|BUF|4|3",
                "2026020174|CBJ|VGK|2|1",
                "2026020175|STL|CAR|0|4",
                "2026020176|EDM|TOR|2|7",
                "2026020177|UTA|TBL|5|4",
                "2026020178|CGY|DET|4|1",
                "2026020179|SEA|MIN|1|5",
            )

        assertEquals(
            expected,
            day.games.map { "${it.id.value}|${it.home.abbrev}|${it.away.abbrev}|${it.homeScore}|${it.awayScore}" },
        )
    }

    @Test
    fun missingGameStateKeepsGameWithEmptyUnknownState() {
        val json = mutateGame(0) { JsonObject(it - "gameState") }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(EXPECTED_GAMES, day.games.size)
        assertEquals("2026020168", day.games[0].id.value)
        assertEquals(GameState.Unknown(""), day.games[0].state)
        assertEquals(0, day.droppedCount)
    }

    @Test
    fun missingGamesIsFailure() {
        val json = mutateGameday { JsonObject(it - "games") }
        val result = NhlAdapter.parseScore(json)

        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("games"))
    }

    @Test
    fun nullGamesIsFailure() {
        val json = mutateGameday { JsonObject(it + ("games" to JsonNull)) }
        val result = NhlAdapter.parseScore(json)

        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("games"))
    }

    @Test
    fun explicitEmptyGamesIsSuccessful() {
        val json = mutateGameday { JsonObject(it + ("games" to JsonArray(emptyList()))) }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals("2026-10-24", day.date)
        assertTrue(day.games.isEmpty())
        assertEquals(0, day.droppedCount)
    }

    @Test
    fun wrongShapedPeriodDescriptorOnlyRemovesThatGamesPeriod() {
        val baseline = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val json = mutateGame(0) { JsonObject(it + ("periodDescriptor" to JsonPrimitive("3"))) }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(EXPECTED_GAMES, day.games.size)
        assertEquals(0, day.droppedCount)
        assertNull(day.games.first().period)
        assertEquals(baseline.games.first().copy(period = null), day.games.first())
        assertTrue(day.games.drop(1).all { it.period != null })
        assertEquals(baseline.games.drop(1), day.games.drop(1))
    }

    @Test
    fun wrongShapedClockAndOutcomeOnlyRemoveOptionalDetails() {
        val baseline = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val json =
            mutateGame(0) {
                JsonObject(it + mapOf("clock" to JsonPrimitive("00:00"), "gameOutcome" to JsonArray(emptyList())))
            }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value
        val first = baseline.games.first()

        val expectedFirst = first.copy(period = first.period!!.copy(clock = null, running = null), finalKind = null)
        assertEquals(baseline.copy(games = listOf(expectedFirst) + baseline.games.drop(1)), day)
    }

    @Test
    fun wrongShapedTeamDropsOnlyThatGame() {
        val baseline = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val json = mutateGame(1) { JsonObject(it + ("homeTeam" to JsonPrimitive("NJD"))) }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(11, day.games.size)
        assertEquals(1, day.droppedCount)
        assertEquals(baseline.games.filterIndexed { index, _ -> index != 1 }, day.games)
    }

    @Test
    fun nonObjectGameDropsOnlyThatElement() {
        val baseline = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val json =
            mutateGameday { score ->
                JsonObject(score + ("games" to JsonArray(score.getValue("games").jsonArray + JsonPrimitive("bad game"))))
            }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(baseline.copy(droppedCount = 1), day)
    }

    @Test
    fun unknownTopLevelAndGameKeysDoNotChangeResult() {
        val baseline = NhlAdapter.parseScore(Fixtures.text("score-gameday.json"))
        val withGameKey = Json.parseToJsonElement(mutateGame(0) { JsonObject(it + ("futureGameKey" to JsonPrimitive(1))) })
        val json = JsonObject(withGameKey.jsonObject + ("futureTopLevelKey" to JsonPrimitive(true))).toString()

        assertEquals(baseline, NhlAdapter.parseScore(json))
    }

    @Test
    fun missingGameWeekKeepsGamesWithEmptyDateIndex() {
        val baseline = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val json = mutateGameday { JsonObject(it - "gameWeek") }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(EXPECTED_GAMES, day.games.size)
        assertEquals(baseline.copy(dateIndex = emptyList()), day)
    }

    @Test
    fun emptyDayIsSuccessfulAndUsesFeedDate() {
        val result = NhlAdapter.parseScore(Fixtures.text("score-emptyday.json"))
        assertTrue(result is ParseResult.Success)
        val day = (result as ParseResult.Success).value

        assertTrue(day.games.isEmpty())
        assertEquals("2026-07-15", day.date)
        assertEquals(7, day.dateIndex.size)
        assertEquals(0, day.dateIndex.sumOf { it.numberOfGames })
        assertEquals(0, day.droppedCount)
    }

    @Test
    fun malformedJsonReturnsFailureWithDiagnosticContext() {
        val malformed =
            listOf(
                Fixtures.text("score-gameday.json").dropLast(1),
                "{currentDate: \"2026-10-24\", games: []}",
                "{\"games\": \"wrong shape\"}",
                "null",
                "[]",
                "\"bare string\"",
                "",
            )
        malformed.forEach { json ->
            val result = NhlAdapter.parseScore(json)
            assertTrue(result is ParseResult.Failure)
            val reason = (result as ParseResult.Failure).reason
            assertTrue(reason.startsWith("NHL score decoding failed: "))
            assertTrue(reason.removePrefix("NHL score decoding failed: ").isNotBlank())
        }
    }

    @Test
    fun completedGamePreservesScoresOutcomeProgressAndTeamAssets() {
        val result = NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success
        val day = result.value
        val game = day.games.first()

        assertEquals("2026-10-24", day.date)
        assertEquals("2026020168", game.id.value)
        assertEquals("2026-10-24", game.date)
        assertEquals(Instant.parse("2026-10-24T17:00:00Z"), game.startTimeUtc)
        assertEquals(GameState.Final, game.state)
        assertEquals(4, game.homeScore)
        assertEquals(5, game.awayScore)
        assertEquals(FinalKind.REG, game.finalKind)
        assertEquals(PeriodInfo(3, "REG", "00:00", false, running = false), game.period)
        assertEquals("Flyers", game.home.name)
        assertEquals("https://assets.nhle.com/logos/nhl/svg/PHI_light.svg", game.home.logoUrl)
        assertTrue(day.games.all { it.home.darkLogoUrl == null && it.away.darkLogoUrl == null })
    }

    @Test
    fun unparseableStartTimeKeepsGameWithNullStartTime() {
        val baseline = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val json = mutateGame(0) { JsonObject(it + ("startTimeUTC" to JsonPrimitive("not-a-timestamp"))) }
        val day = (NhlAdapter.parseScore(json) as ParseResult.Success).value

        assertEquals(EXPECTED_GAMES, day.games.size)
        assertEquals(0, day.droppedCount)
        assertNull(day.games.first().startTimeUtc)
        assertEquals(baseline.copy(games = listOf(baseline.games.first().copy(startTimeUtc = null)) + baseline.games.drop(1)), day)
    }

    @Test
    fun futureGamesKeepAbsentScoresAndProgressNullable() {
        val result = NhlAdapter.parseScore(Fixtures.text("score-futuredate.json")) as ParseResult.Success
        val day = result.value

        assertEquals(3, day.games.size)
        assertEquals(0, day.droppedCount)
        day.games.forEach { game ->
            assertEquals(GameState.Scheduled, game.state)
            assertNull(game.homeScore)
            assertNull(game.awayScore)
            assertNull(game.period)
            assertNull(game.finalKind)
        }
    }

    @Test
    fun incompleteGamesAreCountedWithoutLosingUsableRows() {
        val json =
            """
            {
              "currentDate": "2026-10-24",
              "games": [
                {"id": 1, "gameDate": "2026-10-24", "gameState": "NEW",
                 "homeTeam": {"abbrev": "PHI", "darkLogo": "dark.svg"}, "awayTeam": {"abbrev": "ANA"}},
                {"gameDate": "2026-10-24", "gameState": "OFF",
                 "homeTeam": {"abbrev": "PHI"}, "awayTeam": {"abbrev": "ANA"}},
                {"id": 3, "gameDate": "2026-10-24", "gameState": "OFF",
                 "homeTeam": {}, "awayTeam": {"abbrev": "ANA"}},
                {},
                null
              ]
            }
            """.trimIndent()
        val result = NhlAdapter.parseScore(json) as ParseResult.Success
        val day = result.value

        assertEquals(1, day.games.size)
        assertEquals(4, day.droppedCount)
        assertTrue(day.dateIndex.isEmpty())
        val game = day.games.single()
        assertEquals(GameState.Unknown("NEW"), game.state)
        assertEquals("dark.svg", game.home.darkLogoUrl)
    }

    @Test
    fun missingCurrentDateIsAFailureRatherThanAnInventedEmptyDay() {
        assertTrue(NhlAdapter.parseScore("{}") is ParseResult.Failure)
        assertTrue(NhlAdapter.parseScore("""{"currentDate": null}""") is ParseResult.Failure)
    }

    private fun mutateGameday(transform: (JsonObject) -> JsonObject): String =
        transform(Json.parseToJsonElement(Fixtures.text("score-gameday.json")).jsonObject).toString()

    private fun mutateGame(
        index: Int,
        transform: (JsonObject) -> JsonObject,
    ): String =
        mutateGameday { score ->
            val games = score.getValue("games").jsonArray
            val mutatedGames = games.mapIndexed { current, game -> if (current == index) transform(game.jsonObject) else game }
            JsonObject(score + ("games" to JsonArray(mutatedGames)))
        }
}

// Taken with jq '.games|length' at capture time.
private const val EXPECTED_GAMES = 12
