package net.montycasa.roux.data.nfl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.NflGameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * What the football summary adapter states, and what it refuses to invent.
 *
 * Live and scheduled cases are built here as **crafted** payloads: there was no NFL game in progress on 25 November
 * 2026, so those two states were not captured and cannot be presented as captures. Each crafted payload carries a
 * `_crafted` marker saying so.
 */
class NflSummaryAdapterTest {
    @Test
    fun anOvertimeFinalKeepsTheFeedsFinalOtDetail() {
        val detail = parse("nfl-game-401872923.json")

        assertEquals(NflGameStatus.Final("Final/OT"), detail.status)
        assertEquals("DET", detail.home.team.abbrev)
        assertEquals(31, detail.home.score)
        assertEquals("NO", detail.away.team.abbrev)
        assertEquals(30, detail.away.score)
    }

    @Test
    fun anOverallRecordIsReadFromTheTotalRecordEntry() {
        val detail = parse("nfl-game-401872923.json")

        assertEquals("1-0", detail.home.record)
        assertEquals("0-1", detail.away.record)
    }

    @Test
    fun aClubWithoutARecordIsBlankRatherThanZeroZero() {
        val withoutAwayRecord =
            mutate("nfl-game-401872923.json") { root ->
                val competition =
                    root
                        .getValue("header")
                        .jsonObject
                        .getValue("competitions")
                        .jsonArray
                        .first()
                        .jsonObject
                val competitors =
                    competition.getValue("competitors").jsonArray.map { competitor ->
                        val map = competitor.jsonObject
                        if (map.getValue("homeAway").jsonPrimitive.content != "away") {
                            JsonObject(map)
                        } else {
                            JsonObject(map.toMutableMap().apply { remove("record") })
                        }
                    }
                val header = root.getValue("header").jsonObject
                val updatedCompetition = JsonObject(competition.toMutableMap().apply { put("competitors", JsonArray(competitors)) })
                root["header"] =
                    JsonObject(
                        header.toMutableMap().apply { put("competitions", JsonArray(listOf(updatedCompetition))) },
                    )
            }

        assertNull("an absent record must stay absent", withoutAwayRecord.away.record)
        assertEquals("the other club keeps its record", "1-0", withoutAwayRecord.home.record)
    }

    @Test
    fun scoringPlaysGroupByThePeriodObjectNotAnInteger() {
        val detail = parse("nfl-game-401872923.json")

        // The payload's periods are objects: a parser that looked for an int would see no quarter at all.
        assertEquals(listOf(1, 3, 4, 5), detail.scoring.map { it.number })
        assertEquals(10, detail.scoring.sumOf { it.plays.size })

        val third = detail.scoring.first { it.number == 3 }
        assertEquals(listOf("11:14", "8:54", "4:27", "0:28"), third.plays.map { it.clock })
        assertEquals("DET", third.plays.first().teamAbbrev)
        assertTrue("the first Q3 play is Detroit's", third.plays.first().isHome)
        assertEquals("Jahmyr Gibbs 1 Yd Rush (Jake Bates Kick)", third.plays.first().text)
        assertEquals(0, third.plays.first().awayScore)
        assertEquals(14, third.plays.first().homeScore)
    }

    @Test
    fun ratioShapedAndClockShapedStatsAreKeptVerbatim() {
        val detail = parse("nfl-game-401873066.json")
        val rows = detail.teamStats.associateBy { it.name }

        assertEquals(
            listOf(
                "firstDowns",
                "totalYards",
                "netPassingYards",
                "rushingYards",
                "turnovers",
                "thirdDownEff",
                "possessionTime",
            ),
            detail.teamStats.map { it.name },
        )
        // A ratio is not averaged and a game clock is not turned into seconds.
        assertEquals("1-11", rows.getValue("thirdDownEff").away)
        assertEquals("8-14", rows.getValue("thirdDownEff").home)
        assertEquals("22:14", rows.getValue("possessionTime").away)
        assertEquals("37:46", rows.getValue("possessionTime").home)
        assertEquals("167", rows.getValue("totalYards").away)
        assertEquals("354", rows.getValue("totalYards").home)
    }

    @Test
    fun aStatMissingFromOneSideIsBlankOnThatSide() {
        val withoutAwayRushing =
            mutate("nfl-game-401873066.json") { root ->
                val boxscore = root.getValue("boxscore").jsonObject
                val teams =
                    boxscore.getValue("teams").jsonArray.map { team ->
                        val map = team.jsonObject
                        if (map.getValue("homeAway").jsonPrimitive.content != "away") {
                            JsonObject(map)
                        } else {
                            JsonObject(
                                map.toMutableMap().apply {
                                    put(
                                        "statistics",
                                        JsonArray(
                                            map.getValue("statistics").jsonArray.filterNot { stat ->
                                                stat.jsonObject["name"]?.jsonPrimitive?.content == "rushingYards"
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                root["boxscore"] =
                    JsonObject(
                        boxscore.toMutableMap().apply { put("teams", JsonArray(teams)) },
                    )
            }

        val rushing = withoutAwayRushing.teamStats.first { it.name == "rushingYards" }

        assertNull("the away side was omitted, so it must not be zero-filled", rushing.away)
        assertEquals("167", rushing.home)
    }

    @Test
    fun aClubWithNoLeadersKeepsItsEntryAndStatesNoCategories() {
        val withoutNoLeaders =
            mutate("nfl-game-401872923.json") { root ->
                val leaders =
                    root.getValue("leaders").jsonArray.map { team ->
                        val map = team.jsonObject
                        if (map
                                .getValue("team")
                                .jsonObject
                                .getValue("abbreviation")
                                .jsonPrimitive.content != "NO"
                        ) {
                            JsonObject(map)
                        } else {
                            JsonObject(map.toMutableMap().apply { put("leaders", JsonArray(emptyList())) })
                        }
                    }
                root["leaders"] = JsonArray(leaders)
            }

        val no = withoutNoLeaders.leaders.first { it.teamAbbrev == "NO" }

        assertTrue("the club is still named by the payload", no.categories.isEmpty())
        assertTrue(
            "the other club keeps its categories",
            withoutNoLeaders.leaders
                .first { it.teamAbbrev == "DET" }
                .categories
                .isNotEmpty(),
        )
    }

    @Test
    fun aCraftedLivePayloadCarriesThePeriodTheClockAndTheLiveScores() {
        val detail = parseJson(craftedLive)

        val status = detail.status
        assertTrue("the crafted payload must be live, got $status", status is NflGameStatus.Live)
        status as NflGameStatus.Live
        assertEquals(3, status.period)
        assertEquals("14:12", status.clock)
        assertEquals(17, detail.home.score)
        assertEquals(10, detail.away.score)
        assertEquals(
            1,
            detail.scoring
                .single()
                .plays.size,
        )
    }

    @Test
    fun aCraftedScheduledPayloadCarriesTheKickoffAndNoBodySections() {
        val detail = parseJson(craftedScheduled)

        val status = detail.status
        assertTrue("the crafted payload must be scheduled, got $status", status is NflGameStatus.Scheduled)
        status as NflGameStatus.Scheduled
        assertEquals(Instant.parse("2026-11-26T01:00:00Z"), status.startTimeUtc)
        assertNull("a scheduled game states no score", detail.home.score)
        assertNull(detail.away.score)
        assertTrue("there is no scoring before kickoff", detail.scoring.isEmpty())
        assertTrue("there are no team stats before kickoff", detail.teamStats.isEmpty())
        assertTrue("there are no leaders before kickoff", detail.leaders.isEmpty())
    }

    private fun parse(name: String) = parseJson(Fixtures.nflGame(name))

    private fun parseJson(json: String) =
        when (val result = NflSummaryAdapter.parse(json)) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("parse failed: ${result.reason}")
        }

    private fun mutate(
        name: String,
        change: (MutableMap<String, JsonElement>) -> Unit,
    ) = parseJson(mutateJson(name, change))

    private fun mutateJson(
        name: String,
        change: (MutableMap<String, JsonElement>) -> Unit,
    ): String {
        val root = Json.parseToJsonElement(Fixtures.nflGame(name)).jsonObject.toMutableMap()
        change(root)
        return Json.encodeToString(JsonElement.serializer(), JsonObject(root))
    }

    /**
     * Crafted, not captured: there was no NFL game in progress or scheduled at capture time. The marker records that
     * provenance in the payload itself.
     */
    private val craftedLive =
        """
        {
          "_crafted": "Synthetic fixture, not a capture: no NFL game was in progress on 2026-11-25.",
          "header": {
            "competitions": [{
              "date": "2026-11-26T01:00Z",
              "status": {
                "period": 3,
                "displayClock": "14:12",
                "type": {
                  "name": "STATUS_IN_PROGRESS",
                  "state": "in",
                  "completed": false,
                  "detail": "14:12 - 3rd Quarter"
                }
              },
              "competitors": [
                {
                  "homeAway": "home",
                  "score": "17",
                  "team": {"id": "14", "abbreviation": "LAR", "displayName": "Los Angeles Rams"}
                },
                {
                  "homeAway": "away",
                  "score": "10",
                  "team": {"id": "9", "abbreviation": "GB", "displayName": "Green Bay Packers"}
                }
              ]
            }]
          },
          "scoringPlays": [{
            "text": "Kyren Williams 7 Yd Rush (Brandon McManus Kick)",
            "awayScore": 10,
            "homeScore": 17,
            "period": {"number": 3},
            "clock": {"displayValue": "14:12"},
            "team": {"id": "14", "abbreviation": "LAR"}
          }]
        }
        """.trimIndent()

    /** Crafted, not captured: a scheduled game is synthetic here, as the brief says it must be. */
    private val craftedScheduled =
        """
        {
          "_crafted": "Synthetic fixture, not a capture: no NFL game was scheduled on 2026-11-25.",
          "header": {
            "competitions": [{
              "date": "2026-11-26T01:00Z",
              "status": {
                "type": {
                  "name": "STATUS_SCHEDULED",
                  "state": "pre",
                  "completed": false,
                  "detail": "Thu, November 26th at 8:00 PM EST"
                }
              },
              "competitors": [
                {
                  "homeAway": "home",
                  "team": {"id": "14", "abbreviation": "LAR", "displayName": "Los Angeles Rams"}
                },
                {
                  "homeAway": "away",
                  "team": {"id": "9", "abbreviation": "GB", "displayName": "Green Bay Packers"}
                }
              ]
            }]
          }
        }
        """.trimIndent()
}
