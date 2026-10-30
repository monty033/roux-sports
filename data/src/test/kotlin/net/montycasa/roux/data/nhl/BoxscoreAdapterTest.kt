package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.SkaterLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The boxscore's job on this page is narrow and easy to get wrong in one specific way: every game lists four goalies and
 * two of them never played.
 */
class BoxscoreAdapterTest {
    @Test
    fun capturedFinalKeepsTheTwoGoaliesWhoPlayed() {
        val goalies = successOf(Fixtures.text(FIXTURE)).goalies
        assertEquals(2, goalies.size)
        assertEquals(listOf("C. Talbot", "D. Vladar"), goalies.map { it.goalie.name })
        assertEquals(listOf("CBJ", "PHI"), goalies.map { it.goalie.teamAbbrev })
        assertEquals(listOf(29, 11), goalies.map { it.saves })
        assertEquals(listOf(32, 15), goalies.map { it.shotsAgainst })
    }

    /**
     * The backups are named and dressed and sit in the same list with zeros. They are excluded, and the exclusion is by
     * the flag that says who started - not by position in the list, because the home side arrives with its backup
     * first and its starter second.
     */
    @Test
    fun backupsWithZeroTimeAreExcluded() {
        val names = successOf(Fixtures.text(FIXTURE)).goalies.map { it.goalie.name }
        assertTrue("a backup leaked into the goalie lines: $names", "J. Greaves" !in names)
        assertTrue("a backup leaked into the goalie lines: $names", "J. Woll" !in names)
    }

    /** Home side first in the payload, away side first on the page. */
    @Test
    fun awayGoalieComesFirstEvenWhenThePayloadListsHomeFirst() {
        val goalies = successOf(Fixtures.text(FIXTURE)).goalies
        assertEquals("CBJ", goalies.first().goalie.teamAbbrev)
    }

    /** Without the flag, ice time decides: a goalie with no time on ice did not play. */
    @Test
    fun missingStarterFlagFallsBackToIceTime() {
        val goalies = successOf(withoutStarterFlags()).goalies
        assertEquals(listOf("C. Talbot", "D. Vladar"), goalies.map { it.goalie.name })
    }

    @Test
    fun noIceTimeAtAllMeansNoGoalieSection() {
        val goalies = successOf(zeroingIceTime()).goalies
        assertEquals(emptyList<GoalieLine>(), goalies)
    }

    /**
     * A game that has not started has no playerByGameStats node at all. Pre-game is the normal state for most of the
     * day, so this is a successful fetch with nothing to draw.
     */
    @Test
    fun realNotStartedBoxscoreSucceedsWithNoGoalies() {
        val goalies = successOf(Fixtures.text("gc-fut-boxscore.json")).goalies
        assertEquals(emptyList<GoalieLine>(), goalies)
    }

    /**
     * Skaters live in two lists per side - `forwards` and `defense`. Reading only `forwards` would drop six defencemen
     * per team and nothing in the payload would complain, so the count is asserted, not just "greater than zero".
     */
    @Test
    fun capturedFinalHasEverySkaterFromBothSides() {
        val skaters = successOf(Fixtures.text(FIXTURE)).skaters
        assertEquals(36, skaters.size)
        assertEquals(18, skaters.count { it.skater.teamAbbrev == "CBJ" })
        assertEquals(18, skaters.count { it.skater.teamAbbrev == "PHI" })
        assertTrue(
            "no defenceman was parsed: ${skaters.map { it.position }.toSet()}",
            skaters.any { it.position == "D" },
        )
    }

    @Test
    fun skaterLineCarriesTheColumnsThePageDraws() {
        val coyle = successOf(Fixtures.text(FIXTURE)).skaters.first { it.skater.name == "C. Coyle" }
        assertEquals("CBJ", coyle.skater.teamAbbrev)
        assertEquals("C", coyle.position)
        assertEquals(0, coyle.goals)
        assertEquals(0, coyle.points)
        assertEquals(19 * 60 + 36, coyle.toiSeconds)
        assertEquals(53, coyle.faceoffPercent())
    }

    /** Ice time is "19:36" text and becomes seconds, because the page formats it and maths needs a number. */
    @Test
    fun iceTimeIsParsedFromMinutesAndSeconds() {
        val johnson = successOf(Fixtures.text(FIXTURE)).skaters.first { it.skater.name == "K. Johnson" }
        assertEquals(2, johnson.goals)
        assertEquals(3, johnson.points)
        assertTrue("K. Johnson has no ice time", johnson.toiSeconds > 0)
    }

    /**
     * The captured payload always sends a faceoff figure, using 0.0 for skaters who never took a draw - so 0% and "no
     * draws" are not distinguishable from this field alone, and the page must not invent a distinction.
     */
    @Test
    fun capturedPayloadSendsAFaceoffFigureForEverySkater() {
        val skaters = successOf(Fixtures.text(FIXTURE)).skaters
        assertTrue("a skater arrived with no faceoff figure", skaters.all { it.faceoffPercent() != null })
        assertTrue("nobody took a draw at all", skaters.any { it.faceoffPercent() == 0 })
    }

    /** Older or partial payloads omit the key; that absence is preserved as null rather than turned into 0%. */
    @Test
    fun anAbsentFaceoffKeyStaysNull() {
        val skaters = successOf(withoutOneFaceoffFigure()).skaters
        val stripped = skaters.first { it.skater.name == "C. Coyle" }
        assertNull(stripped.faceoffPercent())
    }

    /** The goalie line now carries the two numbers the goals-against average is derived from. */
    @Test
    fun goalieLineCarriesGoalsAgainstAndIceTime() {
        val goalies = successOf(Fixtures.text(FIXTURE)).goalies
        val talbot = goalies.first { it.goalie.name == "C. Talbot" }
        assertEquals(3, talbot.goalsAgainst)
        assertEquals(62 * 60 + 22, talbot.toiSeconds)
        assertEquals(2.89, talbot.goalsAgainstAverage()!!, 0.005)
        assertEquals(29.0 / 32.0, talbot.savePercentage()!!, 0.0001)
    }

    @Test
    fun startedGameReportsNoSkatersEither() {
        assertEquals(emptyList<SkaterLine>(), successOf(Fixtures.text("gc-fut-boxscore.json")).skaters)
    }

    @Test
    fun emptyObjectFailsWithoutThrowing() {
        val result = BoxscoreAdapter.parse("{}")
        assertTrue("expected Failure, got $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("missing id"))
    }

    @Test
    fun malformedJsonFailsWithoutThrowing() {
        assertTrue(BoxscoreAdapter.parse("""{"id":2026020188,"playerByGame""") is ParseResult.Failure)
    }

    private fun successOf(json: String): GameBoxscore {
        val result = BoxscoreAdapter.parse(json)
        assertTrue("expected Success, got $result", result is ParseResult.Success)
        return (result as ParseResult.Success).value
    }

    private fun fixture(): JsonObject = Json.parseToJsonElement(Fixtures.text(FIXTURE)).jsonObject

    /** Rebuilds the fixture with all four goalies transformed, leaving every other node untouched. */
    private fun rewriteGoalies(transform: (JsonObject) -> JsonObject): String {
        val root = fixture()
        val players = root["playerByGameStats"]!!.jsonObject
        val rewritten =
            JsonObject(
                players.mapValues { (_, side) ->
                    val sideObject = side.jsonObject
                    val goalies = JsonArray(sideObject["goalies"]!!.jsonArray.map { transform(it.jsonObject) })
                    JsonObject(sideObject + ("goalies" to goalies))
                },
            )
        return JsonObject(root + ("playerByGameStats" to rewritten)).toString()
    }

    private fun withoutStarterFlags(): String = rewriteGoalies { goalie -> JsonObject(goalie.filterKeys { it != "starter" }) }

    /** Strips the faceoff figure from each side's first forward - C. Coyle and the Flyers' first forward. */
    private fun withoutOneFaceoffFigure(): String {
        val root = fixture()
        val players = root["playerByGameStats"]!!.jsonObject
        val rewritten =
            JsonObject(
                players.mapValues { (_, side) ->
                    val sideObject = side.jsonObject
                    val forwards =
                        JsonArray(
                            sideObject["forwards"]!!.jsonArray.mapIndexed { index, element ->
                                if (index == 0) {
                                    JsonObject(element.jsonObject.filterKeys { it != "faceoffWinningPctg" })
                                } else {
                                    element
                                }
                            },
                        )
                    JsonObject(sideObject + ("forwards" to forwards))
                },
            )
        return JsonObject(root + ("playerByGameStats" to rewritten)).toString()
    }

    private fun zeroingIceTime(): String =
        rewriteGoalies { goalie ->
            JsonObject(goalie + ("toi" to JsonPrimitive("00:00")) + ("starter" to JsonPrimitive(false)))
        }

    /** A nameless goalie is dropped rather than drawn with a blank where the name goes. */
    @Test
    fun goalieWithoutANameIsSkippedRatherThanRenderedBlank() {
        val goalies =
            successOf(
                rewriteGoalies { goalie ->
                    if (goalie.named("C. Talbot")) JsonObject(goalie.filterKeys { it != "name" }) else goalie
                },
            ).goalies
        assertEquals(listOf("D. Vladar"), goalies.map { it.goalie.name })
    }

    /** Some payloads repeat a goalie; a duplicate is one line, not two rows of the same numbers. */
    @Test
    fun duplicateGoalieEntriesCollapseToOneLine() {
        val goalies = successOf(duplicatingAwayStarter()).goalies
        assertEquals(listOf("C. Talbot", "D. Vladar"), goalies.map { it.goalie.name })
    }

    /** An unparseable ice time is no ice time: the goalie stays out rather than being credited with a period. */
    @Test
    fun unparseableIceTimeCountsAsNotPlayed() {
        val goalies = successOf(corruptingStarterIceTime()).goalies
        assertEquals(listOf("D. Vladar"), goalies.map { it.goalie.name })
    }

    private fun duplicatingAwayStarter(): String {
        val root = fixture()
        val players = root["playerByGameStats"]!!.jsonObject
        val away = players["awayTeam"]!!.jsonObject
        val goalies = away["goalies"]!!.jsonArray
        val withDuplicate = JsonArray(goalies + listOf(goalies.first { it.jsonObject["starter"] == JsonPrimitive(true) }))
        val rewritten =
            JsonObject(
                players + ("awayTeam" to JsonObject(away + ("goalies" to withDuplicate))),
            )
        return JsonObject(root + ("playerByGameStats" to rewritten)).toString()
    }

    private fun corruptingStarterIceTime(): String =
        rewriteGoalies { goalie ->
            val withoutFlag = JsonObject(goalie.filterKeys { it != "starter" })
            // Two colon-separated parts with a non-numeric minute, so the value reaches the minutes parse rather than
            // being rejected on shape alone.
            if (goalie.named("C. Talbot")) JsonObject(withoutFlag + ("toi" to JsonPrimitive("xx:30"))) else withoutFlag
        }

    /** Both sides have a starter, so a mutation has to name the one it means. */
    private fun JsonObject.named(name: String): Boolean =
        this["name"]
            ?.jsonObject
            ?.get("default")
            ?.jsonPrimitive
            ?.content == name

    private companion object {
        const val FIXTURE = "gc-final-boxscore.json"
    }
}
