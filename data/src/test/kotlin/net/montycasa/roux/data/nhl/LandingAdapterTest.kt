package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalAssist
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.PeriodScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The landing payload is the only source of period scoring and the three stars, so these tests are about which of those
 * two survive a payload that is missing something - and about the two traps the real capture carries: a goal's
 * `teamAbbrev` is an object while a star's is a string, and a backup goalie can appear with a 00:00 line.
 */
class LandingAdapterTest {
    @Test
    fun scheduledVenueAndBroadcastsKeepFeedOrder() {
        val landing =
            successOf(
                """{"id":1,"gameState":"FUT",
            "venue":{"default":" Centre Bell "},"venueLocation":{"default":" Montreal ","fr":"Montréal"},
            "tvBroadcasts":[{"id":131,"network":" TSN2 ","countryCode":" CA ","market":" H ","sequenceNumber":112},
            {"network":"NHLN","countryCode":"US","market":"N"}]}""",
            )
        assertEquals(
            net.montycasa.roux.domain.model
                .Venue("Centre Bell", "Montreal"),
            landing.venue,
        )
        assertEquals(
            listOf(
                net.montycasa.roux.domain.model
                    .Broadcast("TSN2", "CA", "H"),
                net.montycasa.roux.domain.model
                    .Broadcast("NHLN", "US", "N"),
            ),
            landing.broadcasts,
        )
    }

    @Test
    fun absentGameInfoIsEmpty() {
        val landing = successOf("""{"id":1}""")
        assertNull(landing.venue)
        assertTrue(landing.broadcasts.isEmpty())
    }

    @Test
    fun malformedBroadcastsLoseOnlyTheirOwnEntry() {
        val landing =
            successOf(
                """{"id":1,"venue":{"default":" "},"tvBroadcasts":[
            {"network":"TSN2"},{"countryCode":"CA"},42,null,{"network":{}},
            {"network":" "},{"network":"NHLN","countryCode":" ","market":" "}]}""",
            )
        assertNull(landing.venue)
        assertEquals(
            listOf(
                net.montycasa.roux.domain.model
                    .Broadcast("TSN2", null, null),
                net.montycasa.roux.domain.model
                    .Broadcast("NHLN", null, null),
            ),
            landing.broadcasts,
        )
    }

    @Test
    fun liveMomentSurvivesWithoutSummary() {
        val landing =
            successOf(
                """{"id":1,"gameState":"LIVE","periodDescriptor":{"number":3,"periodType":"REG"},
                "clock":{"timeRemaining":"12:34","inIntermission":false}}""",
            )
        assertEquals(PeriodInfo(3, "REG", "12:34", false), landing.period)
        assertEquals(GameState.Live(), landing.state)
    }

    @Test
    fun intermissionSurvivesZeroOrMissingClockTime() {
        listOf("\"timeRemaining\":\"00:00\",", "").forEach { time ->
            val landing =
                successOf(
                    """{"id":1,"gameState":"LIVE","periodDescriptor":{"number":2,"periodType":"REG"},
                    "clock":{$time"inIntermission":true}}""",
                )
            assertEquals(PeriodInfo(2, "REG", if (time.isEmpty()) null else "00:00", true), landing.period)
        }
    }

    @Test
    fun finalKindRequiresFinalState() {
        listOf("FINAL", "OFF", "LIVE").forEach { state ->
            listOf("OT" to FinalKind.OT, "SO" to FinalKind.SO, "REG" to FinalKind.REG, null to FinalKind.REG).forEach { (type, kind) ->
                val descriptor = type?.let { "\"periodDescriptor\":{\"number\":4,\"periodType\":\"$it\"}," }.orEmpty()
                val landing = successOf("""{"id":1,$descriptor"gameState":"$state"}""")
                assertEquals("$state / $type", if (state == "LIVE") null else kind, landing.finalKind)
            }
        }
    }

    @Test
    fun missingMomentKeepsScoring() {
        val root = fixtureJson().filterKeys { it !in setOf("periodDescriptor", "clock") }
        val landing = successOf(JsonObject(root + ("gameState" to JsonPrimitive("LIVE"))).toString())
        assertNull(landing.period)
        assertNull(landing.finalKind)
        assertEquals(4, landing.periodScoring.size)
        assertEquals(PeriodScoring(1, 3, 0), landing.periodScoring.first())
    }

    @Test
    fun malformedMomentKeepsScoringAndDoesNotInventValues() {
        val root = fixtureJson()
        val malformed =
            Json
                .parseToJsonElement(
                    """{"periodDescriptor":{"number":"oops","periodType":[]},
                "clock":{"timeRemaining":"bad","inIntermission":{}},"gameState":{}}""",
                ).jsonObject
        val landing = successOf(JsonObject(root + malformed).toString())
        assertNull(landing.period)
        assertNull(landing.state)
        assertNull(landing.finalKind)
        assertEquals(4, landing.periodScoring.size)

        val validNumber = Json.parseToJsonElement("""{"number":2,"periodType":"REG"}""")
        val partial = successOf(JsonObject(root + malformed + ("periodDescriptor" to validNumber)).toString())
        assertEquals(PeriodInfo(2, "REG", null), partial.period)
    }

    @Test
    fun capturedFinalMapsFourPeriodsIncludingOvertime() {
        val landing = successOf(Fixtures.text(FIXTURE))
        assertEquals(
            listOf(
                PeriodScoring(period = 1, awayGoals = 3, homeGoals = 0),
                PeriodScoring(period = 2, awayGoals = 0, homeGoals = 1),
                PeriodScoring(period = 3, awayGoals = 0, homeGoals = 2),
                PeriodScoring(period = 4, awayGoals = 1, homeGoals = 0),
            ),
            landing.periodScoring,
        )
    }

    @Test
    fun capturedFinalMapsThreeStarsInRankOrder() {
        val landing = successOf(Fixtures.text(FIXTURE))
        assertEquals(listOf(1, 2, 3), landing.threeStars.map { it.rank })
        assertEquals(listOf("K. Johnson", "C. Talbot", "J. Drysdale"), landing.threeStars.map { it.player.name })
        assertEquals(listOf("CBJ", "CBJ", "PHI"), landing.threeStars.map { it.player.teamAbbrev })
    }

    /**
     * A game that has not started carries `matchup` instead of `summary`. That is the normal shape for most of the day,
     * so it must read as a successful fetch with nothing to draw - not as a broken response the page would apologise
     * for.
     */
    @Test
    fun realNotStartedPayloadSucceedsWithNoSections() {
        // 22 KB of matchup, season comparisons and ticket links: a two-field stub would pass while the decoder choked on
        // any of the other keys, so the fixture is the test.
        val landing = successOf(Fixtures.text("gc-fut-landing.json"))
        assertEquals(emptyList<PeriodScoring>(), landing.periodScoring)
        assertEquals(emptyList<Any>(), landing.threeStars)
    }

    @Test
    fun minimalNotStartedPayloadAlsoSucceeds() {
        val landing = successOf("""{"id":2026020199,"gameState":"FUT"}""")
        assertEquals(emptyList<PeriodScoring>(), landing.periodScoring)
        assertEquals(emptyList<Any>(), landing.threeStars)
    }

    @Test
    fun emptyObjectFailsWithoutThrowing() {
        val result = LandingAdapter.parse("{}")
        assertTrue("expected Failure, got $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("missing id"))
    }

    @Test
    fun malformedJsonFailsWithoutThrowing() {
        val result = LandingAdapter.parse("""{"id":2026020188,"summary":""")
        assertTrue("expected Failure, got $result", result is ParseResult.Failure)
    }

    @Test
    fun removingScoringLeavesStars() {
        val landing = successOf(withoutSummaryNode("scoring"))
        assertEquals(emptyList<PeriodScoring>(), landing.periodScoring)
        assertEquals(3, landing.threeStars.size)
    }

    @Test
    fun removingStarsLeavesScoring() {
        val landing = successOf(withoutSummaryNode("threeStars"))
        assertEquals(4, landing.periodScoring.size)
        assertEquals(emptyList<Any>(), landing.threeStars)
    }

    @Test
    fun removingSummaryRemovesBoth() {
        val landing = successOf(withoutTopLevelKey("summary"))
        assertEquals(emptyList<PeriodScoring>(), landing.periodScoring)
        assertEquals(emptyList<Any>(), landing.threeStars)
    }

    /** `isHome` is the field that decides the side; without it the abbreviation still has to work. */
    @Test
    fun goalWithoutIsHomeFallsBackToObjectAbbreviation() {
        val landing = successOf(clearingGoalsField("isHome"))
        assertEquals(4, landing.periodScoring.size)
        assertEquals(PeriodScoring(period = 1, awayGoals = 3, homeGoals = 0), landing.periodScoring.first())
    }

    /**
     * The score feed writes `teamAbbrev` as a plain string and the landing payload writes it as an object. The adapter
     * accepts both, so a payload that switches shape is still attributed rather than silently counted as nobody.
     */
    @Test
    fun goalWithBareStringAbbreviationIsStillAttributed() {
        val landing = successOf(rewritingGoalAbbrevAsString())
        assertEquals(4, landing.periodScoring.size)
        assertEquals(PeriodScoring(period = 1, awayGoals = 3, homeGoals = 0), landing.periodScoring.first())
    }

    /**
     * A period with one unattributable goal is dropped rather than published short. A partial count would disagree with
     * the score printed above it and nothing on screen would explain the difference.
     */
    @Test
    fun unattributableGoalDropsOnlyItsOwnPeriod() {
        val landing = successOf(clearingGoalAttributionForPeriod(2))
        assertEquals(listOf(1, 3, 4), landing.periodScoring.map { it.period })
        assertEquals(3, landing.threeStars.size)
    }

    @Test
    fun periodsComeBackSortedByNumber() {
        val landing = successOf(reversingScoringOrder())
        assertEquals(listOf(1, 2, 3, 4), landing.periodScoring.map { it.period })
    }

    @Test
    fun powerPlayGoalKeepsEveryPublishedField() {
        val landing =
            successOf(
                """{"id":1,"summary":{"scoring":[{"periodDescriptor":{"number":1},"goals":[{
                "isHome":false,"teamAbbrev":{"default":"CBJ"},"name":{"default":"K. Marchenko"},
                "timeInPeriod":"10:58","strength":"pp","goalModifier":"none","goalsToDate":12,
                "awayScore":2,"homeScore":1,"assists":[
                  {"name":{"default":"J. Voracek"},"assistsToDate":4,"playerId":8478460,"sweaterNumber":93},
                  {"name":{"default":"Z. Werenski"},"assistsToDate":9,"playerId":8478461,"sweaterNumber":8}]}]}]}}""",
            )
        assertEquals(
            GoalEvent(
                period = 1,
                timeInPeriod = "10:58",
                isHome = false,
                scorer = "K. Marchenko",
                goalsToDate = 12,
                assists = listOf(GoalAssist("J. Voracek", 4), GoalAssist("Z. Werenski", 9)),
                strength = GoalStrength.PowerPlay,
                modifier = GoalModifier.None,
                awayScore = 2,
                homeScore = 1,
            ),
            landing.goals.single(),
        )
    }

    @Test
    fun shortHandedEmptyNetGoalKeepsBothFacts() {
        val landing =
            successOf(
                """{"id":1,"summary":{"scoring":[{"periodDescriptor":{"number":2},"goals":[{
                "isHome":true,"name":{"default":"N. Cates"},"strength":"sh","goalModifier":"empty-net",
                "timeInPeriod":"18:01","awayScore":3,"homeScore":2}]}]}}""",
            )
        val goal = landing.goals.single()
        assertEquals(GoalStrength.ShortHanded, goal.strength)
        assertEquals(GoalModifier.EmptyNet, goal.modifier)
    }

    @Test
    fun unrecognisedStrengthKeepsTheGoalAsUnknown() {
        val landing =
            successOf(
                """{"id":1,"summary":{"scoring":[{"periodDescriptor":{"number":1},"goals":[{
                "isHome":false,"name":{"default":"A. Fantilli"},"strength":"five-on-three",
                "timeInPeriod":"07:00"}]}]}}""",
            )
        assertEquals(GoalStrength.Unknown, landing.goals.single().strength)
        assertEquals("A. Fantilli", landing.goals.single().scorer)
    }

    @Test
    fun goalWithoutPeriodOrScorerIsDroppedWhileItsNeighboursSurvive() {
        val landing =
            successOf(
                """{"id":1,"summary":{"scoring":[
                {"periodDescriptor":{"number":1},"goals":[
                  {"isHome":false,"name":{"default":"Good One"},"timeInPeriod":"10:00"},
                  {"isHome":false,"timeInPeriod":"09:00"},
                  {"isHome":false,"name":{"default":"  "},"timeInPeriod":"08:00"},
                  null,
                  {"isHome":false,"name":{"default":"Good Two"},"timeInPeriod":"07:00"}]},
                {"periodDescriptor":{},"goals":[{"isHome":true,"name":{"default":"No Period"}}]},
                {"periodDescriptor":{"number":2},"goals":[{"isHome":true,"name":{"default":"Good Three"},"timeInPeriod":"11:00"}]}]}}""",
            )
        assertEquals(listOf("Good One", "Good Two", "Good Three"), landing.goals.map { it.scorer })
        assertEquals(listOf(1, 1, 2), landing.goals.map { it.period })
    }

    @Test
    fun capturedFinalGoalsKeepTheirOwnRunningScoresAndSeasonTotals() {
        val landing = successOf(Fixtures.text(FIXTURE))
        assertEquals(7, landing.goals.size)
        assertEquals("A. Fantilli", landing.goals.first().scorer)
        assertEquals(1, landing.goals.first().awayScore)
        assertEquals(0, landing.goals.first().homeScore)
        assertEquals(4, landing.goals.first().goalsToDate)
        assertEquals(1, landing.goals.first().period)
        assertEquals(
            2,
            landing.goals
                .first()
                .assists.size,
        )
        // The last goal's running score is the final score, but it is that goal's own fields, not the game's.
        assertEquals(4, landing.goals.last().awayScore)
        assertEquals(3, landing.goals.last().homeScore)
    }

    private fun successOf(json: String): GameLanding {
        val result = LandingAdapter.parse(json)
        assertTrue("expected Success, got $result", result is ParseResult.Success)
        return (result as ParseResult.Success).value
    }

    private fun fixtureJson(): JsonObject = Json.parseToJsonElement(Fixtures.text(FIXTURE)).jsonObject

    private fun JsonObject.summary(): JsonObject = this["summary"]!!.jsonObject

    /** Rebuilds the fixture with one node removed from inside `summary`. */
    private fun withoutSummaryNode(node: String): String {
        val fixture = fixtureJson()
        val summary = JsonObject(fixture.summary().filterKeys { it != node })
        return JsonObject(fixture + ("summary" to summary)).toString()
    }

    private fun withoutTopLevelKey(key: String): String = JsonObject(fixtureJson().filterKeys { it != key }).toString()

    /** Removes a field from every goal in `summary.scoring[].goals[]`, leaving the rest of each goal intact. */
    private fun clearingGoalsField(field: String): String {
        val fixture = fixtureJson()
        val summary =
            JsonObject(
                fixture.summary() + ("scoring" to mapScoring(fixture) { goal -> JsonObject(goal.filterKeys { it != field }) }),
            )
        return JsonObject(fixture + ("summary" to summary)).toString()
    }

    /** Strips every side signal from one period's goals, leaving that period unattributable and the others intact. */
    private fun clearingGoalAttributionForPeriod(periodNumber: Int): String {
        val fixture = fixtureJson()
        val scoring =
            fixture.summary()["scoring"]!!.jsonArray.map { period ->
                val periodObject = period.jsonObject
                val number = periodObject["periodDescriptor"]!!.jsonObject["number"]!!.jsonPrimitive.int
                if (number != periodNumber) {
                    periodObject
                } else {
                    JsonObject(
                        periodObject +
                            (
                                "goals" to
                                    JsonArray(
                                        periodObject["goals"]!!.jsonArray.map {
                                            JsonObject(it.jsonObject.filterKeys { key -> key != "isHome" && key != "teamAbbrev" })
                                        },
                                    )
                            ),
                    )
                }
            }
        val summary = JsonObject(fixture.summary() + ("scoring" to JsonArray(scoring)))
        return JsonObject(fixture + ("summary" to summary)).toString()
    }

    private fun rewritingGoalAbbrevAsString(): String {
        val fixture = fixtureJson()
        val summary = JsonObject(fixture.summary() + ("scoring" to mapScoring(fixture) { goal -> withoutIsHome(goal) }))
        return JsonObject(fixture + ("summary" to summary)).toString()
    }

    /** Drops `isHome` and flattens `{"default":"CBJ"}` into `"CBJ"` - the score feed's shape. */
    private fun withoutIsHome(goal: JsonObject): JsonObject {
        val flat =
            buildJsonObject {
                goal.forEach { (key, value) ->
                    when (key) {
                        "isHome" -> {
                            Unit
                        }

                        "teamAbbrev" -> {
                            put("teamAbbrev", JsonPrimitive(value.jsonObject["default"]!!.jsonPrimitive.content))
                        }

                        else -> {
                            put(key, value)
                        }
                    }
                }
            }
        return flat
    }

    private fun reversingScoringOrder(): String {
        val fixture = fixtureJson()
        val reversed = fixture.summary()["scoring"]!!.jsonArray.reversed()
        val summary = JsonObject(fixture.summary() + ("scoring" to JsonArray(reversed)))
        return JsonObject(fixture + ("summary" to summary)).toString()
    }

    private fun mapScoring(
        fixture: JsonObject,
        transform: (JsonObject) -> JsonObject,
    ): JsonArray =
        JsonArray(
            fixture.summary()["scoring"]!!.jsonArray.map { period ->
                val periodObject = period.jsonObject
                JsonObject(
                    periodObject +
                        ("goals" to JsonArray(periodObject["goals"]!!.jsonArray.map { transform(it.jsonObject) })),
                )
            },
        )

    private companion object {
        const val FIXTURE = "gc-final-landing.json"
    }
}
