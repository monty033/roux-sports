package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.PowerPlaySummary
import net.montycasa.roux.domain.model.ShotTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The right rail states shots and power play twice each, in different shapes, and a started game's two statements are
 * not required to agree. These tests pin which statement wins and what happens when it is missing.
 */
class RightRailAdapterTest {
    @Test
    fun capturedFinalReadsShotsFromTheTotalsRow() {
        assertEquals(ShotTotals(awayShots = 15, homeShots = 32), successOf(Fixtures.text(FIXTURE)).shots)
    }

    @Test
    fun capturedFinalReadsPowerPlayGoalsOverChances() {
        assertEquals(
            PowerPlaySummary(awayGoals = 0, awayChances = 1, homeGoals = 0, homeChances = 2),
            successOf(Fixtures.text(FIXTURE)).powerPlay,
        )
    }

    /** The per-period array is the fallback, and it is only used when the totals row is gone. */
    @Test
    fun missingSogRowFallsBackToCountingPeriods() {
        val rail = successOf(withoutRow("sog"))
        assertEquals(ShotTotals(awayShots = 15, homeShots = 32), rail.shots)
    }

    /** The totals row wins even when the periods disagree with it - that is the whole point of preferring it. */
    @Test
    fun totalsRowWinsOverPeriodsThatDisagree() {
        val rail = successOf(withPeriodShots(away = 99, home = 99))
        assertEquals(ShotTotals(awayShots = 15, homeShots = 32), rail.shots)
    }

    @Test
    fun missingPeriodsAndRowMeansNoShots() {
        assertNull(successOf(rootWithout("teamGameStats", "shotsByPeriod")).shots)
    }

    /**
     * Half a power-play line is not printable, so it is dropped. Inventing a zero for the half that failed to parse
     * would put a wrong number on screen with nothing to distinguish it from a right one.
     */
    @Test
    fun malformedPowerPlayIsDroppedRatherThanHalfParsed() {
        assertNull(successOf(withPowerPlay(away = "1", home = "0/2")).powerPlay)
    }

    @Test
    fun malformedHomeSideAlsoDropsTheWholeLine() {
        assertNull(successOf(withPowerPlay(away = "1/3", home = "x/2")).powerPlay)
    }

    /**
     * The feed writes some numbers as strings, so a string `sog` still counts. The value is deliberately not what the
     * per-period fallback would produce - a coincidental match would pass whether or not the row was read at all.
     */
    @Test
    fun sogAsStringStillCounts() {
        val rail = successOf(rewriteRow("sog") { it.with("awayValue" to JsonPrimitive("20"), "homeValue" to JsonPrimitive("41")) })
        assertEquals(ShotTotals(awayShots = 20, homeShots = 41), rail.shots)
    }

    /** A game that has not started has no linescore, no shots and no comparison table at all. */
    @Test
    fun realNotStartedRailHasNeitherSection() {
        val rail = successOf(Fixtures.text("gc-fut-right-rail.json"))
        assertNull(rail.shots)
        assertNull(rail.powerPlay)
    }

    @Test
    fun emptyObjectSucceedsWithNoSections() {
        val rail = successOf("{}")
        assertNull(rail.shots)
        assertNull(rail.powerPlay)
    }

    @Test
    fun malformedJsonFailsWithoutThrowing() {
        assertTrue(RightRailAdapter.parse("""{"teamGameStats":""") is ParseResult.Failure)
    }

    private fun successOf(json: String): GameRightRail {
        val result = RightRailAdapter.parse(json)
        assertTrue("expected Success, got $result", result is ParseResult.Success)
        return (result as ParseResult.Success).value
    }

    private fun root(): JsonObject = Json.parseToJsonElement(Fixtures.text(FIXTURE)).jsonObject

    private fun rootWithout(vararg keys: String): String = JsonObject(root().filterKeys { it !in keys }).toString()

    /** `JsonObject + Pair` yields a plain Map, so every edit has to be wrapped back into a JsonObject. */
    private fun JsonObject.with(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(this + pairs)

    private fun rows(): List<JsonObject> = root()["teamGameStats"]!!.jsonArray.map { it.jsonObject }

    private fun withRows(rows: List<JsonObject>): String = JsonObject(root().with("teamGameStats" to JsonArray(rows))).toString()

    private fun withoutRow(category: String): String = withRows(rows().filterNot { it["category"] == JsonPrimitive(category) })

    private fun rewriteRow(
        category: String,
        transform: (JsonObject) -> JsonObject,
    ): String = withRows(rows().map { if (it["category"] == JsonPrimitive(category)) transform(it) else it })

    private fun withPowerPlay(
        away: String,
        home: String,
    ): String =
        rewriteRow("powerPlay") {
            it.with("awayValue" to JsonPrimitive(away), "homeValue" to JsonPrimitive(home))
        }

    private fun withPeriodShots(
        away: Int,
        home: Int,
    ): String {
        val fixture = root()
        val periods =
            fixture["shotsByPeriod"]!!.jsonArray.map {
                it.jsonObject.with("away" to JsonPrimitive(away), "home" to JsonPrimitive(home))
            }
        return JsonObject(fixture.with("shotsByPeriod" to JsonArray(periods))).toString()
    }

    private companion object {
        const val FIXTURE = "gc-final-right-rail.json"
    }
}
