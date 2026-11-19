package net.montycasa.roux.data.nfl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CAPTURE = "2026-11-18"
private const val SUNDAY_A_NAME = "nfl-scoreboard-sunday-a.json"
private const val SUNDAY_B_NAME = "nfl-scoreboard-sunday-b.json"
private const val TODAY_NAME = "nfl-scoreboard-today.json"
private const val EMPTY_DAY_NAME = "nfl-scoreboard-emptyday.json"

private fun load(name: String): JsonObject {
    val path = "/fx-nfl/$CAPTURE/$name"
    val stream =
        NflScoreboardCensusTest::class.java.getResourceAsStream(path)
            ?: error("Fixture resource not found: $path")
    return Json.parseToJsonElement(stream.use { it.readBytes().toString(Charsets.UTF_8) }).jsonObject
}

private val FIXTURES =
    mapOf(
        SUNDAY_A_NAME to load(SUNDAY_A_NAME),
        SUNDAY_B_NAME to load(SUNDAY_B_NAME),
        TODAY_NAME to load(TODAY_NAME),
        EMPTY_DAY_NAME to load(EMPTY_DAY_NAME),
    )
private val SUNDAY_A = FIXTURES.getValue(SUNDAY_A_NAME)
private val SUNDAY_B = FIXTURES.getValue(SUNDAY_B_NAME)
private val TODAY = FIXTURES.getValue(TODAY_NAME)
private val EMPTY_DAY = FIXTURES.getValue(EMPTY_DAY_NAME)

private fun JsonObject.events(): List<JsonObject> = this["events"]?.jsonArray?.map { it.jsonObject } ?: emptyList()

private fun JsonObject.competitions(): List<JsonObject> =
    this["events"]?.jsonArray?.flatMap { it.jsonObject.competitions() }
        ?: this["competitions"]?.jsonArray?.map { it.jsonObject }
        ?: emptyList()

private fun JsonObject.competitors(): List<JsonObject> =
    competitions().flatMap { competition ->
        competition["competitors"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
    }

private fun JsonObject.statusTriple(): String? {
    val type = this["status"]?.jsonObject?.get("type")?.jsonObject ?: return null
    val name = type["name"]?.jsonPrimitive?.content ?: return null
    val state = type["state"]?.jsonPrimitive?.content ?: return null
    val completed = type["completed"]?.jsonPrimitive?.content ?: return null
    return "$name|$state|$completed"
}

private fun JsonObject.statusTriples(): Map<String, Int> =
    events().mapNotNull { it.statusTriple() }.groupBy { it }.mapValues { it.value.size }

private fun JsonObject.presence(): Map<String, Boolean> =
    mapOf(
        "situation" to competitions().any { it.containsKey("situation") },
        "linescores" to competitors().any { it.containsKey("linescores") },
        "week.number" to events().any { it["week"]?.jsonObject?.containsKey("number") == true },
        "season.type" to events().any { it["season"]?.jsonObject?.containsKey("type") == true },
        "notes" to competitions().any { it.containsKey("notes") },
        "broadcasts" to competitions().any { it.containsKey("broadcasts") },
    )

private fun JsonObject.competitorAbbreviations(): Set<String> =
    competitors()
        .mapNotNull {
            it["team"]
                ?.jsonObject
                ?.get("abbreviation")
                ?.jsonPrimitive
                ?.content
        }.toSet()

private fun JsonObject.logoHosts(): Set<String> {
    val hosts = mutableSetOf<String>()

    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                for ((key, value) in element) {
                    if (key == "logos" && value is JsonArray) {
                        value.forEach { logo ->
                            (logo as? JsonObject)?.get("href")?.jsonPrimitive?.content?.let { url ->
                                hosts += url.substringAfter("://").substringBefore("/")
                            }
                        }
                    }
                    walk(value)
                }
            }

            is JsonArray -> {
                element.forEach { walk(it) }
            }

            else -> {
                Unit
            }
        }
    }
    walk(this)
    return hosts
}

private fun JsonObject.calendarWeekRanges(): List<String> {
    val calendar =
        this["leagues"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("calendar")
            ?.jsonArray
            ?: return emptyList()
    return calendar.flatMap { group ->
        group.jsonObject["entries"]?.jsonArray?.map { entry ->
            val e = entry.jsonObject
            listOf(
                e["label"]?.jsonPrimitive?.content,
                e["value"]?.jsonPrimitive?.content,
                e["startDate"]?.jsonPrimitive?.content,
                e["endDate"]?.jsonPrimitive?.content,
            ).joinToString("|")
        } ?: emptyList()
    }
}

private fun JsonObject.scores(): List<Int> = competitors().mapNotNull { it["score"]?.jsonPrimitive?.content?.toIntOrNull() }

private fun JsonObject.tieCount(): Int =
    events().count { event ->
        val scores = event.scores()
        scores.size == 2 && scores[0] == scores[1]
    }

private fun JsonObject.completedTieCount(): Int =
    events().count { event ->
        val scores = event.scores()
        event.statusTriple()?.endsWith("|true") == true && scores.size == 2 && scores[0] == scores[1]
    }

private data class LateGame(
    val requestedDay: String?,
    val utcDay: String,
    val eventId: String,
    val date: String,
)

/**
 * Read-only census of the four committed ESPN scoreboard fixtures (Stage 1.3a).
 *
 * Every expected literal below is copied from the "Stage 1.3a census" tables in `docs/nfl-sources.md`.
 * This test recomputes the same numbers straight from the fixture JSON, so the document and the corpus
 * cannot drift apart: changing one count in either place by one fails that test. No parser and no domain
 * model are involved — this records what the bytes contain, not how to read them.
 */
class NflScoreboardCensusTest {
    @Test
    fun statusTriplesMatchTheDoc() {
        // events[].status.type -- competition-level status carries the same triples (24/26/26/0 in total).
        assertEquals(mapOf("STATUS_FINAL|post|true" to 12), SUNDAY_A.statusTriples())
        assertEquals(mapOf("STATUS_FINAL|post|true" to 13), SUNDAY_B.statusTriples())
        assertEquals(mapOf("STATUS_SCHEDULED|pre|false" to 13), TODAY.statusTriples())
        assertEquals(emptyMap<String, Int>(), EMPTY_DAY.statusTriples())
    }

    @Test
    fun fieldPresenceMatchesTheDoc() {
        val finalGame =
            mapOf(
                "situation" to false,
                "linescores" to true,
                "week.number" to true,
                "season.type" to true,
                "notes" to true,
                "broadcasts" to true,
            )
        val scheduledGame = finalGame + ("linescores" to false)
        val noEvents =
            mapOf(
                "situation" to false,
                "linescores" to false,
                "week.number" to false,
                "season.type" to false,
                "notes" to false,
                "broadcasts" to false,
            )

        assertEquals(finalGame, SUNDAY_A.presence())
        assertEquals(finalGame, SUNDAY_B.presence())
        assertEquals(scheduledGame, TODAY.presence())
        assertEquals(noEvents, EMPTY_DAY.presence())
    }

    @Test
    fun competitorAbbreviationsMatchTheDoc() {
        assertEquals(
            setOf(
                "ARI",
                "ATL",
                "BUF",
                "CAR",
                "CIN",
                "CLE",
                "DAL",
                "DET",
                "GB",
                "HOU",
                "IND",
                "JAX",
                "KC",
                "LAR",
                "LV",
                "MIA",
                "MIN",
                "NE",
                "NO",
                "NYJ",
                "PIT",
                "SEA",
                "SF",
                "TEN",
            ),
            SUNDAY_A.competitorAbbreviations(),
        )
        assertEquals(
            setOf(
                "ARI",
                "ATL",
                "CAR",
                "CHI",
                "CIN",
                "CLE",
                "DAL",
                "DEN",
                "DET",
                "GB",
                "HOU",
                "IND",
                "KC",
                "LAC",
                "LAR",
                "LV",
                "MIA",
                "NE",
                "NO",
                "NYG",
                "NYJ",
                "PHI",
                "SEA",
                "SF",
                "TB",
                "WSH",
            ),
            SUNDAY_B.competitorAbbreviations(),
        )
        assertEquals(
            setOf(
                "ARI",
                "BAL",
                "BUF",
                "CAR",
                "CHI",
                "CIN",
                "DAL",
                "DEN",
                "DET",
                "HOU",
                "IND",
                "JAX",
                "KC",
                "LAC",
                "LV",
                "MIA",
                "MIN",
                "NO",
                "NYG",
                "NYJ",
                "PHI",
                "PIT",
                "SF",
                "TB",
                "TEN",
                "WSH",
            ),
            TODAY.competitorAbbreviations(),
        )
        assertEquals(emptySet<String>(), EMPTY_DAY.competitorAbbreviations())

        // The union is all 32 clubs, including every abbreviation that collides with an NHL club.
        val all =
            SUNDAY_A.competitorAbbreviations() +
                SUNDAY_B.competitorAbbreviations() +
                TODAY.competitorAbbreviations()
        assertEquals(32, all.size)
        assertTrue(
            setOf("PHI", "PIT", "BUF", "DAL", "DET", "MIN", "SEA", "CAR", "WSH", "CHI").all { it in all },
        )
    }

    @Test
    fun logoHostsAreExactlyEspnCdn() {
        val expected = setOf("a.espncdn.com")
        assertEquals(expected, SUNDAY_A.logoHosts())
        assertEquals(expected, SUNDAY_B.logoHosts())
        assertEquals(expected, TODAY.logoHosts())
        assertEquals(expected, EMPTY_DAY.logoHosts())
    }

    @Test
    fun calendarWeekRangesMatchTheDoc() {
        val expected =
            listOf(
                "Hall of Fame Weekend|1|2026-08-06T07:00Z|2026-08-13T06:59Z",
                "Preseason Week 1|2|2026-08-13T07:00Z|2026-08-20T06:59Z",
                "Preseason Week 2|3|2026-08-20T07:00Z|2026-08-27T06:59Z",
                "Preseason Week 3|4|2026-08-27T07:00Z|2026-09-06T06:59Z",
                "Week 1|1|2026-09-06T07:00Z|2026-09-16T06:59Z",
                "Week 2|2|2026-09-16T07:00Z|2026-09-23T06:59Z",
                "Week 3|3|2026-09-23T07:00Z|2026-09-30T06:59Z",
                "Week 4|4|2026-09-30T07:00Z|2026-10-07T06:59Z",
                "Week 5|5|2026-10-07T07:00Z|2026-10-14T06:59Z",
                "Week 6|6|2026-10-14T07:00Z|2026-10-21T06:59Z",
                "Week 7|7|2026-10-21T07:00Z|2026-10-28T06:59Z",
                "Week 8|8|2026-10-28T07:00Z|2026-11-04T07:59Z",
                "Week 9|9|2026-11-04T08:00Z|2026-11-11T07:59Z",
                "Week 10|10|2026-11-11T08:00Z|2026-11-18T07:59Z",
                "Week 11|11|2026-11-18T08:00Z|2026-11-25T07:59Z",
                "Week 12|12|2026-11-25T08:00Z|2026-12-02T07:59Z",
                "Week 13|13|2026-12-02T08:00Z|2026-12-09T07:59Z",
                "Week 14|14|2026-12-09T08:00Z|2026-12-16T07:59Z",
                "Week 15|15|2026-12-16T08:00Z|2026-12-23T07:59Z",
                "Week 16|16|2026-12-23T08:00Z|2026-12-30T07:59Z",
                "Week 17|17|2026-12-30T08:00Z|2027-01-06T07:59Z",
                "Week 18|18|2027-01-06T08:00Z|2027-01-13T07:59Z",
                "Wild Card|1|2027-01-13T08:00Z|2027-01-20T07:59Z",
                "Divisional Round|2|2027-01-20T08:00Z|2027-01-27T07:59Z",
                "Conference Championship|3|2027-01-27T08:00Z|2027-02-03T07:59Z",
                "Super Bowl|4|2027-02-03T08:00Z|2027-02-16T07:59Z",
            )
        // One league calendar, identical in all four captures.
        assertEquals(expected, SUNDAY_A.calendarWeekRanges())
        assertEquals(expected, SUNDAY_B.calendarWeekRanges())
        assertEquals(expected, TODAY.calendarWeekRanges())
        assertEquals(expected, EMPTY_DAY.calendarWeekRanges())
    }

    @Test
    fun lateGameDateIsAgainstTheRequestedDay() {
        val expected =
            mapOf(
                SUNDAY_A_NAME to LateGame("2026-11-15", "2026-11-16", "401873064", "2026-11-16T01:20Z"),
                SUNDAY_B_NAME to LateGame("2026-11-08", "2026-11-09", "401873050", "2026-11-09T01:20Z"),
                // No dates= parameter: the current-week response starts with the Thursday late game.
                TODAY_NAME to LateGame(null, "2026-11-20", "401873066", "2026-11-20T01:15Z"),
            )
        expected.forEach { (name, late) ->
            val event = FIXTURES.getValue(name).events().first { it["id"]?.jsonPrimitive?.content == late.eventId }
            assertEquals(name, late.date, event["date"]?.jsonPrimitive?.content)
            assertEquals(name, late.utcDay, late.date.substringBefore("T"))
            if (late.requestedDay != null) {
                // The late game is kicked off on the requested day but stamped on the following UTC day.
                assertTrue(name, late.utcDay != late.requestedDay)
            }
        }
        assertTrue(EMPTY_DAY.events().isEmpty())
    }

    @Test
    fun tieCountsMatchTheDoc() {
        // Equal scores are counted as a tie. Today's 13 are unplayed 0-0 skeletons, not final ties.
        assertEquals(0, SUNDAY_A.tieCount())
        assertEquals(0, SUNDAY_B.tieCount())
        assertEquals(13, TODAY.tieCount())
        assertEquals(0, EMPTY_DAY.tieCount())

        assertEquals(0, SUNDAY_A.completedTieCount())
        assertEquals(0, SUNDAY_B.completedTieCount())
        assertEquals(0, TODAY.completedTieCount())
        assertEquals(0, EMPTY_DAY.completedTieCount())
    }
}
