package net.montycasa.roux.data.nfl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

private const val CAPTURE = "2026-11-18"
private const val SUNDAY_A_NAME = "nfl-scoreboard-sunday-a.json"
private const val SUNDAY_B_NAME = "nfl-scoreboard-sunday-b.json"
private const val TODAY_NAME = "nfl-scoreboard-today.json"
private const val EMPTY_DAY_NAME = "nfl-scoreboard-emptyday.json"
private const val SCOREBOARD_2055_NAME = "live/scoreboard-2055.json"
private const val SCOREBOARD_2205_NAME = "live/scoreboard-2205.json"
private const val PREGAME_SUMMARY_NAME = "nfl-summary-pregame.json"
private const val SUMMARY_2055_NAME = "live/summary-2055.json"
private const val SUMMARY_2205_NAME = "live/summary-2205.json"
private const val FINAL_SUMMARY_NAME = "nfl-summary-final.json"
private const val SCOREBOARD_LIVE_ALIAS_NAME = "nfl-scoreboard-live.json"
private const val SUMMARY_LIVE_ALIAS_NAME = "nfl-summary-live.json"

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
private val WEEK_NAMES = listOf("nfl-scoreboard-week-11.json", "nfl-scoreboard-week-12.json", "nfl-scoreboard-week-1.json")
private val SUNDAY_A = FIXTURES.getValue(SUNDAY_A_NAME)
private val SUNDAY_B = FIXTURES.getValue(SUNDAY_B_NAME)
private val TODAY = FIXTURES.getValue(TODAY_NAME)
private val EMPTY_DAY = FIXTURES.getValue(EMPTY_DAY_NAME)

private val SCOREBOARD_2055 = load(SCOREBOARD_2055_NAME)
private val SCOREBOARD_2205 = load(SCOREBOARD_2205_NAME)

/**
 * Every scoreboard payload: the four base captures, the two taken during play, and the alias the capture script
 * overwrites on the next live run. The alias is scanned too, so a new capture cannot add a state unasserted.
 */
private val SCOREBOARDS =
    FIXTURES +
        mapOf(
            SCOREBOARD_2055_NAME to SCOREBOARD_2055,
            SCOREBOARD_2205_NAME to SCOREBOARD_2205,
            SCOREBOARD_LIVE_ALIAS_NAME to load(SCOREBOARD_LIVE_ALIAS_NAME),
        )

private val SUMMARIES =
    mapOf(
        PREGAME_SUMMARY_NAME to load(PREGAME_SUMMARY_NAME),
        SUMMARY_2055_NAME to load(SUMMARY_2055_NAME),
        SUMMARY_2205_NAME to load(SUMMARY_2205_NAME),
        FINAL_SUMMARY_NAME to load(FINAL_SUMMARY_NAME),
        SUMMARY_LIVE_ALIAS_NAME to load(SUMMARY_LIVE_ALIAS_NAME),
    )
private val PREGAME_SUMMARY = SUMMARIES.getValue(PREGAME_SUMMARY_NAME)
private val SUMMARY_2055 = SUMMARIES.getValue(SUMMARY_2055_NAME)
private val SUMMARY_2205 = SUMMARIES.getValue(SUMMARY_2205_NAME)
private val FINAL_SUMMARY = SUMMARIES.getValue(FINAL_SUMMARY_NAME)

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

// --- Stage 1.3b: the live captures -------------------------------------------------

private fun JsonObject.summaryCompetition(): JsonObject =
    this["header"]
        ?.jsonObject
        ?.get("competitions")
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?: error("no header.competitions[0]")

/** Status `detail`, e.g. `Final/OT`, read from an event. */
private fun JsonObject.eventStatusDetail(): String? =
    this["status"]
        ?.jsonObject
        ?.get("type")
        ?.jsonObject
        ?.get("detail")
        ?.jsonPrimitive
        ?.content

private fun JsonObject.competitionAbbreviations(): Set<String> =
    this["competitors"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull {
            it.jsonObject["team"]
                ?.jsonObject
                ?.get("abbreviation")
                ?.jsonPrimitive
                ?.content
        }.toSet()

private fun JsonObject.competitionLinescoreCounts(): List<Int> =
    this["competitors"]?.jsonArray.orEmpty().map { it.jsonObject["linescores"]?.jsonArray?.size ?: 0 }

private fun JsonObject.competitionScores(): Set<String> =
    this["competitors"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull { it.jsonObject["score"]?.jsonPrimitive?.content }
        .toSet()

private fun JsonObject.homeAbbreviation(): String? =
    summaryCompetition()["competitors"]
        ?.jsonArray
        .orEmpty()
        .map { it.jsonObject }
        .firstOrNull { it["homeAway"]?.jsonPrimitive?.content == "home" }
        ?.get("team")
        ?.jsonObject
        ?.get("abbreviation")
        ?.jsonPrimitive
        ?.content

/** Each drive's `start.text` and `start.yardLine`, which is where the yard-line convention is visible. */
private fun JsonObject.driveStarts(): List<Pair<String, Int>> {
    val drives = this["drives"]?.jsonObject ?: return emptyList()
    val all =
        drives["previous"]?.jsonArray.orEmpty().map { it.jsonObject } +
            listOfNotNull(drives["current"]?.jsonObject)
    return all.mapNotNull { drive ->
        val start = drive["start"]?.jsonObject ?: return@mapNotNull null
        val text = start["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val yardLine = start["yardLine"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
        text to yardLine
    }
}

private fun JsonObject.summaryStatusName(): String? =
    summaryCompetition()["status"]
        ?.jsonObject
        ?.get("type")
        ?.jsonObject
        ?.get("name")
        ?.jsonPrimitive
        ?.content

private fun JsonObject.summaryHasStatusField(field: String): Boolean =
    summaryCompetition()["status"]?.jsonObject?.containsKey(field) == true

private fun JsonObject.summaryStatusField(field: String): String? =
    summaryCompetition()["status"]
        ?.jsonObject
        ?.get(field)
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

private fun JsonObject.summarySections(): Map<String, Boolean> =
    listOf("drives", "scoringPlays", "videos", "article", "predictor", "lastFiveGames", "ticketsInfo")
        .associateWith { containsKey(it) }

private fun JsonObject.headerHasPlaysKey(): Boolean = summaryCompetition().containsKey("plays")

private fun sha256Of(name: String): String {
    val bytes =
        NflScoreboardCensusTest::class.java.getResourceAsStream("/fx-nfl/$CAPTURE/$name")
            ?: error("Fixture resource not found: $name")
    return MessageDigest
        .getInstance("SHA-256")
        .digest(bytes.use { it.readBytes() })
        .joinToString("") { "%02x".format(it) }
}

private fun JsonObject.drivePlayCount(): Int {
    val drives = this["drives"]?.jsonObject ?: return 0
    val previous =
        drives["previous"]?.jsonArray?.sumOf { drive -> drive.jsonObject["plays"]?.jsonArray?.size ?: 0 } ?: 0
    val current =
        drives["current"]
            ?.jsonObject
            ?.get("plays")
            ?.jsonArray
            ?.size ?: 0
    return previous + current
}

private fun JsonObject.previousDriveCount(): Int =
    this["drives"]
        ?.jsonObject
        ?.get("previous")
        ?.jsonArray
        ?.size ?: 0

private fun JsonObject.hasCurrentDrive(): Boolean = this["drives"]?.jsonObject?.containsKey("current") == true

private fun JsonObject.linescoreCounts(): List<Int> =
    summaryCompetition()["competitors"]?.jsonArray?.map { competitor ->
        competitor.jsonObject["linescores"]?.jsonArray?.size ?: 0
    } ?: emptyList()

private fun JsonObject.inProgressEvent(): JsonObject = events().first { it.statusTriple()?.startsWith("STATUS_IN_PROGRESS|") == true }

private fun JsonObject.inProgressCompetition(): JsonObject = inProgressEvent().competitions().first()

private fun JsonObject.inProgressPeriod(): String? =
    inProgressEvent()["status"]
        ?.jsonObject
        ?.get("period")
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

private fun JsonObject.inProgressClock(): String? =
    inProgressEvent()["status"]
        ?.jsonObject
        ?.get("displayClock")
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

private fun JsonObject.situationField(field: String): String? =
    inProgressCompetition()["situation"]
        ?.jsonObject
        ?.get(field)
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

private fun JsonObject.situationCompetitionCount(): Int = competitions().count { it.containsKey("situation") }

private fun JsonObject.inProgressTeamIds(): Set<String> =
    inProgressCompetition()["competitors"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull {
            it.jsonObject["team"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content
        }.toSet()

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

    @Test
    fun onlyTheDocumentedStatesAppearAnywhereInTheCorpus() {
        // A fixture carrying STATUS_HALFTIME, STATUS_END_PERIOD, or a delayed or postponed game must update the
        // Stage 1.3b table in docs/nfl-sources.md first, or this fails. Scans all seven scoreboard payloads, the
        // two timed captures and their alias included, so the files the capture script overwrites cannot drift.
        val names =
            (
                SCOREBOARDS.values.flatMap { board ->
                    board.events().mapNotNull { it.statusTriple()?.substringBefore("|") }
                } + SUMMARIES.values.mapNotNull { it.summaryStatusName() }
            ).toSet()

        assertEquals(setOf("STATUS_SCHEDULED", "STATUS_FINAL", "STATUS_IN_PROGRESS"), names)
    }

    @Test
    fun overtimeIsObservedOnlyAsAFinishedGame() {
        // CLE @ NO, 2026-11-08, in sunday-b: period 5, detail Final/OT, five linescores per club.
        val overtime =
            SUNDAY_B.events().single { event ->
                event["status"]
                    ?.jsonObject
                    ?.get("period")
                    ?.jsonPrimitive
                    ?.content == "5"
            }
        assertEquals("STATUS_FINAL|post|true", overtime.statusTriple())
        assertEquals("Final/OT", overtime.eventStatusDetail())

        val competition = overtime.competitions().first()
        assertEquals(setOf("CLE", "NO"), competition.competitionAbbreviations())
        assertEquals(listOf(5, 5), competition.competitionLinescoreCounts())
        assertEquals(setOf("23", "22"), competition.competitionScores())

        // No in-progress event anywhere in the corpus ever reached period 5, so a live overtime is still unproven.
        val inProgressPeriods =
            SCOREBOARDS.values
                .flatMap { board ->
                    board.events().filter { it.statusTriple()?.startsWith("STATUS_IN_PROGRESS|") == true }
                }.map {
                    it["status"]
                        ?.jsonObject
                        ?.get("period")
                        ?.jsonPrimitive
                        ?.content
                }
        assertEquals(setOf("1", "3"), inProgressPeriods.toSet())

        // A scoreboard final still carries a period (4, or 5 in overtime) and a zero clock, unlike the summary,
        // so "has a period" must never be read as "in progress".
        val finalPeriods =
            SCOREBOARDS.values
                .flatMap { board ->
                    board.events().filter { it.statusTriple()?.startsWith("STATUS_FINAL|") == true }
                }.map {
                    it["status"]
                        ?.jsonObject
                        ?.get("period")
                        ?.takeUnless { p -> p is JsonNull }
                        ?.jsonPrimitive
                        ?.content
                }.toSet()
        assertEquals(setOf("4", "5"), finalPeriods)

        val finalClocks =
            SCOREBOARDS.values
                .flatMap { board ->
                    board.events().filter { it.statusTriple()?.startsWith("STATUS_FINAL|") == true }
                }.map {
                    it["status"]
                        ?.jsonObject
                        ?.get("displayClock")
                        ?.jsonPrimitive
                        ?.content
                }.toSet()
        assertEquals(setOf("0:00"), finalClocks)
    }

    @Test
    fun onlyPeriodsOneAndThreeWereObservedInProgress() {
        assertEquals("1", SCOREBOARD_2055.inProgressPeriod())
        assertEquals("1:28", SCOREBOARD_2055.inProgressClock())
        assertEquals("3", SCOREBOARD_2205.inProgressPeriod())
        assertEquals("14:12", SCOREBOARD_2205.inProgressClock())

        // Halftime, end of period, periods 2 and 4, and in-progress overtime are not observed in this corpus:
        // the game may well have passed through them, and this window did not sample it.
        val observedPeriods = setOf(SCOREBOARD_2055.inProgressPeriod(), SCOREBOARD_2205.inProgressPeriod())
        assertEquals(setOf("1", "3"), observedPeriods)
    }

    @Test
    fun situationAppearsOnlyOnTheInProgressEvent() {
        assertEquals(1, SCOREBOARD_2055.situationCompetitionCount())
        assertEquals(1, SCOREBOARD_2205.situationCompetitionCount())

        // Neither a scheduled nor a finished event carries one anywhere in the corpus.
        assertEquals(0, SUNDAY_A.situationCompetitionCount())
        assertEquals(0, SUNDAY_B.situationCompetitionCount())
        assertEquals(0, TODAY.situationCompetitionCount())
        assertEquals(0, EMPTY_DAY.situationCompetitionCount())
    }

    @Test
    fun situationFieldsMatchTheDoc() {
        val early = SCOREBOARD_2055
        assertEquals("1", early.situationField("down"))
        assertEquals("10", early.situationField("distance"))
        assertEquals("48", early.situationField("yardLine"))
        assertEquals("false", early.situationField("isRedZone"))
        assertEquals("1st & 10 at HOU 48", early.situationField("downDistanceText"))
        assertEquals("1st & 10", early.situationField("shortDownDistanceText"))
        assertEquals("3", early.situationField("homeTimeouts"))
        assertEquals("3", early.situationField("awayTimeouts"))

        val third = SCOREBOARD_2205
        assertEquals("2", third.situationField("down"))
        assertEquals("9", third.situationField("distance"))
        assertEquals("41", third.situationField("yardLine"))
        assertEquals("false", third.situationField("isRedZone"))
        assertEquals("2nd & 9 at HOU 41", third.situationField("downDistanceText"))
        assertEquals("2nd & 9", third.situationField("shortDownDistanceText"))

        // isRedZone was false in both observed states, so the true case stays unproven.
        assertEquals(setOf("false"), setOf(early.situationField("isRedZone"), third.situationField("isRedZone")))
    }

    @Test
    fun possessionIsATeamIdAndPossessionTextIsNotTheOffense() {
        // 2055: Indianapolis ("11") has the ball at Houston's 48, so possessionText names the spot, not the club.
        assertEquals("11", SCOREBOARD_2055.situationField("possession"))
        assertTrue("11" in SCOREBOARD_2055.inProgressTeamIds())
        assertEquals("HOU 48", SCOREBOARD_2055.situationField("possessionText"))

        // 2205: Houston ("34") has it at its own 41, and the possession id changes with the club.
        assertEquals("34", SCOREBOARD_2205.situationField("possession"))
        assertTrue("34" in SCOREBOARD_2205.inProgressTeamIds())
        assertEquals("HOU 41", SCOREBOARD_2205.situationField("possessionText"))
    }

    @Test
    fun summarySectionsChangeWithState() {
        val pregame =
            mapOf(
                "drives" to false,
                "scoringPlays" to false,
                "videos" to false,
                "article" to true,
                "predictor" to true,
                "lastFiveGames" to true,
                "ticketsInfo" to true,
            )
        val live =
            mapOf(
                "drives" to true,
                "scoringPlays" to true,
                "videos" to true,
                "article" to false,
                "predictor" to false,
                "lastFiveGames" to false,
                "ticketsInfo" to false,
            )
        val final =
            mapOf(
                "drives" to true,
                "scoringPlays" to true,
                "videos" to true,
                "article" to true,
                "predictor" to false,
                "lastFiveGames" to false,
                "ticketsInfo" to false,
            )

        assertEquals(pregame, PREGAME_SUMMARY.summarySections())
        assertEquals(live, SUMMARY_2055.summarySections())
        assertEquals(live, SUMMARY_2205.summarySections())
        assertEquals(final, FINAL_SUMMARY.summarySections())
    }

    @Test
    fun playsLiveUnderDrivesAndNeverOnTheHeader() {
        // The summary's header carries no plays key at all, in any state, the final included.
        SUMMARIES.values.forEach { summary -> assertFalse(summary.headerHasPlaysKey()) }

        assertEquals(0, PREGAME_SUMMARY.drivePlayCount())
        assertEquals(0, PREGAME_SUMMARY.previousDriveCount())
        assertFalse(PREGAME_SUMMARY.hasCurrentDrive())

        assertEquals(41, SUMMARY_2055.drivePlayCount())
        assertEquals(5, SUMMARY_2055.previousDriveCount())
        assertTrue(SUMMARY_2055.hasCurrentDrive())

        assertEquals(96, SUMMARY_2205.drivePlayCount())
        assertEquals(12, SUMMARY_2205.previousDriveCount())
        assertTrue(SUMMARY_2205.hasCurrentDrive())

        assertEquals(181, FINAL_SUMMARY.drivePlayCount())
        assertEquals(24, FINAL_SUMMARY.previousDriveCount())
        // A finished game carries no current drive, so that block has to be optional.
        assertFalse(FINAL_SUMMARY.hasCurrentDrive())
    }

    @Test
    fun scoreboardLinescoresCarryNumericValuesAndSummaryLinescoresDoNot() {
        // A scoreboard entry carries the number and the period it belongs to.
        val competition = SCOREBOARD_2205.competitions().first()
        val home =
            competition["competitors"]!!
                .jsonArray
                .map { it.jsonObject }
                .first { it["homeAway"]?.jsonPrimitive?.content == "home" }
        val boardEntry = home["linescores"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("value", "displayValue", "period"), boardEntry.keys)
        assertEquals("7.0", boardEntry["value"]?.jsonPrimitive?.content)
        assertEquals("7", boardEntry["displayValue"]?.jsonPrimitive?.content)
        assertEquals("1", boardEntry["period"]?.jsonPrimitive?.content)

        // A summary entry has displayValue only: the value key is absent, not null.
        val summaryEntry =
            SUMMARY_2205
                .summaryCompetition()["competitors"]!!
                .jsonArray
                .first()
                .jsonObject["linescores"]!!
                .jsonArray
                .first()
                .jsonObject
        assertEquals(setOf("displayValue"), summaryEntry.keys)
        assertEquals("7", summaryEntry["displayValue"]?.jsonPrimitive?.content)

        // Entry counts still track the periods played, not a fixed four columns.
        assertEquals(listOf(0, 0), PREGAME_SUMMARY.linescoreCounts())
        assertEquals(listOf(1, 1), SUMMARY_2055.linescoreCounts())
        assertEquals(listOf(3, 3), SUMMARY_2205.linescoreCounts())
        assertEquals(listOf(4, 4), FINAL_SUMMARY.linescoreCounts())
    }

    @Test
    fun theLiveAliasesAreByteIdenticalToTheTimedCaptures() {
        // scripts/capture-fixtures.sh overwrites these two names on every live run, so they are scanned and pinned.
        assertEquals(sha256Of(SCOREBOARD_2205_NAME), sha256Of(SCOREBOARD_LIVE_ALIAS_NAME))
        assertEquals(sha256Of(SUMMARY_2205_NAME), sha256Of(SUMMARY_LIVE_ALIAS_NAME))
        assertTrue(SCOREBOARD_LIVE_ALIAS_NAME in SCOREBOARDS.keys)
        assertTrue(SUMMARY_LIVE_ALIAS_NAME in SUMMARIES.keys)
    }

    @Test
    fun theLastPlayCarriesNoDownDistanceOrYardLine() {
        val lastPlay =
            SCOREBOARD_2205.inProgressCompetition()["situation"]!!.jsonObject["lastPlay"]!!.jsonObject
        // Eleven keys, and none of them is a live down: those live on the situation itself.
        assertEquals(
            setOf(
                "athletesInvolved",
                "drive",
                "end",
                "id",
                "probability",
                "scoreValue",
                "start",
                "statYardage",
                "team",
                "text",
                "type",
            ),
            lastPlay.keys,
        )
        assertEquals("2", SCOREBOARD_2205.situationField("down"))
        assertEquals("9", SCOREBOARD_2205.situationField("distance"))
    }

    @Test
    fun yardLineIsMeasuredFromTheHomeGoalLine() {
        // 43 drive starts across the three game payloads, and every one agrees with this rule:
        // yardLine is the distance from the HOME club's goal line, so an away club on its own 20 is 80.
        var samples = 0
        // The alias is a byte copy of the 2205 summary, so it adds no sample of its own.
        SUMMARIES.filterKeys { it != SUMMARY_LIVE_ALIAS_NAME }.forEach { (name, summary) ->
            val home = summary.homeAbbreviation()
            assertTrue("$name must name a home club", home != null)
            summary.driveStarts().forEach { (text, yardLine) ->
                val club = text.substringBefore(" ")
                val spot = text.substringAfter(" ").toInt()
                val expected = if (club == home) spot else 100 - spot
                assertEquals("$name: drive starting at $text", expected, yardLine)
                samples++
            }
        }
        assertEquals(43, samples)

        // The situation field uses the same coordinate system, seen from the away side:
        // Indianapolis at Houston's 48 is 48 yards from the home goal line.
        assertEquals("48", SCOREBOARD_2055.situationField("yardLine"))
        assertEquals("HOU 48", SCOREBOARD_2055.situationField("possessionText"))
        assertEquals("41", SCOREBOARD_2205.situationField("yardLine"))
    }

    @Test
    fun pregameAndFinalReportNoPeriodOrClock() {
        assertEquals("STATUS_SCHEDULED", PREGAME_SUMMARY.summaryStatusName())
        // Absent, not null: containsKey is the only assertion that can tell the two apart.
        assertFalse(PREGAME_SUMMARY.summaryHasStatusField("period"))
        assertFalse(PREGAME_SUMMARY.summaryHasStatusField("displayClock"))

        assertEquals("STATUS_FINAL", FINAL_SUMMARY.summaryStatusName())
        assertFalse(FINAL_SUMMARY.summaryHasStatusField("period"))
        assertFalse(FINAL_SUMMARY.summaryHasStatusField("displayClock"))

        // The in-progress summaries do carry them, and they agree with the scoreboards of the same capture.
        assertEquals("1", SUMMARY_2055.summaryStatusField("period"))
        assertEquals("1:28", SUMMARY_2055.summaryStatusField("displayClock"))
        assertEquals("3", SUMMARY_2205.summaryStatusField("period"))
        assertEquals("14:12", SUMMARY_2205.summaryStatusField("displayClock"))
    }

    /**
     * Week scoreboards exist so the schedule can be skipped by game day. A week that holds one day would hide the
     * empty days the feature is meant to skip, so each captured week must span at least two distinct game dates.
     */
    @Test
    fun everyWeekScoreboardSpansSeveralGameDays() {
        for (name in WEEK_NAMES) {
            val dates =
                load(name)
                    .events()
                    .map { event ->
                        event["date"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                            .take(10)
                    }.toSet()

            assertTrue("$name spans only ${dates.size} game day(s): $dates", dates.size >= 2)
        }
    }

    @Test
    fun theWeekCapturesAreRecordedInTheSizesLog() {
        val sizes =
            NflScoreboardCensusTest::class.java
                .getResourceAsStream("/fx-nfl/$CAPTURE/SIZES.txt")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("SIZES.txt not found for $CAPTURE")

        for (week in listOf(11, 12, 1)) {
            assertTrue("week $week is not in SIZES.txt", sizes.contains("nfl-scoreboard-week-$week.json "))
            assertTrue("week $week URL is not recorded", sizes.contains("seasontype=2&week=$week"))
        }
    }
}
