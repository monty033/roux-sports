package net.montycasa.roux.data.nfl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.FootballSituation
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adapter's job is to turn ESPN's scoreboard into domain games without guessing. These tests read the committed
 * corpus, so a parser written against the real payloads is what is under test.
 */
class NflScoreboardAdapterTest {
    private fun parse(
        name: String,
        date: String,
        capture: String = CAPTURE,
    ): net.montycasa.roux.domain.model.ScoreDay {
        val json =
            NflScoreboardAdapterTest::class.java
                .getResourceAsStream("/fx-nfl/$capture/$name")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Fixture resource not found: $name")

        return when (val result = NflScoreboardAdapter.parseScoreboard(json, date)) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("Parse failed: ${result.reason}")
        }
    }

    @Test
    fun everyGameAndTeamIsNfl() {
        val day = parse("nfl-scoreboard-sunday-a.json", "2026-11-22")

        assertTrue(day.games.isNotEmpty())
        for (game in day.games) {
            assertEquals(League.NFL, game.id.league)
            assertEquals(League.NFL, game.home.key.league)
            assertEquals(League.NFL, game.away.key.league)
        }
    }

    @Test
    fun aScheduledGameIsScheduled() {
        // Sunday A holds only finished games. The scheduled games are in the current slate, which spans this week.
        val day = parse("nfl-scoreboard-today.json", "2026-11-19")
        val scheduled = day.games.filter { it.state == GameState.Scheduled }

        assertTrue("expected scheduled games", scheduled.isNotEmpty())
    }

    @Test
    fun aFinalGameIsFinal() {
        val day = parse("nfl-scoreboard-sunday-b.json", "2026-11-22")
        val finals = day.games.filter { it.state == GameState.Final }

        assertTrue("expected final games", finals.isNotEmpty())
    }

    @Test
    fun theOvertimeFinalIsMarkedOvertimeAndTheRegularFinalsAreNot() {
        val day = parse("nfl-scoreboard-sunday-b.json", "2026-11-22")
        val finals = day.games.filter { it.state == GameState.Final }
        val overtime = finals.filter { it.finalKind == FinalKind.OT }

        assertEquals("one overtime final in this capture", 1, overtime.size)
        assertEquals("401873039", overtime.single().id.value)
        // Every other final is regulation, not a missing kind and not overtime.
        assertEquals(finals.size - 1, finals.count { it.finalKind == null })
    }

    @Test
    fun theSecondOvertimeFinalIsMarkedToo() {
        // Week 1 holds a second overtime final, so the rule has two observations rather than one.
        val day = parse("nfl-scoreboard-week-1.json", "2026-09-10")
        val finals = day.games.filter { it.state == GameState.Final }
        val overtime = finals.filter { it.finalKind == FinalKind.OT }

        assertEquals("one overtime final in week 1", 1, overtime.size)
        assertEquals("401872923", overtime.single().id.value)
        assertEquals(finals.size - 1, finals.count { it.finalKind == null })
    }

    @Test
    fun aTieIsAFinalWithEqualScores() {
        val json =
            """
            {"events":[{"id":"999","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","score":"21","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","score":"21","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_FINAL","detail":"Final","completed":true},"period":4}}]}]}
            """.trimIndent()

        val result = NflScoreboardAdapter.parseScoreboard(json, "2026-11-22")
        val game = (result as ParseResult.Success).value.games.single()

        assertEquals(GameState.Final, game.state)
        assertEquals(21, game.homeScore)
        assertEquals(21, game.awayScore)
    }

    @Test
    fun anUnobservedStatusStaysUnknownWithItsRawName() {
        val json =
            """
            {"events":[{"id":"998","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","score":"0","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","score":"0","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_POSTPONED","detail":"Postponed","completed":false}}}]}]}
            """.trimIndent()

        val result = NflScoreboardAdapter.parseScoreboard(json, "2026-11-22")
        val game = (result as ParseResult.Success).value.games.single()

        assertEquals(GameState.Unknown("STATUS_POSTPONED"), game.state)
    }

    @Test
    fun aLiveGameCarriesThePeriodAndClockTheFeedStates() {
        val game = singleGame(LIVE_QUARTER_ONE)

        assertEquals(GameState.Live(), game.state)
        val period = requireNotNull(game.period) { "a live game must carry the period the feed stated" }
        assertEquals(1, period.ordinal)
        assertEquals("1:28", period.clock)
        assertEquals("REG", period.periodType)
        assertEquals(true, period.running)
    }

    @Test
    fun aLiveGameWithNoStatedPeriodStaysUnknown() {
        // Every in-progress game in the corpus states its period. If one ever does not, period 0 would be an invented
        // fact, so the game stays unknown rather than reading as a live game at the top of the first quarter.
        val game = singleGame(LIVE_WITHOUT_A_PERIOD)

        assertEquals(GameState.Unknown("STATUS_IN_PROGRESS"), game.state)
        assertNull("no period may be invented", game.period)
    }

    @Test
    fun theFourthQuarterIsStillRegulation() {
        // The other side of the boundary: a fourth-quarter game is regulation. Without this, a threshold that reads
        // "four or more" looks correct, because period five satisfies both.
        val game = singleGame(LIVE_QUARTER_FOUR)

        val period = requireNotNull(game.period)
        assertEquals(4, period.ordinal)
        assertEquals("REG", period.periodType)
    }

    @Test
    fun aPeriodAboveTheFourthIsOvertime() {
        val game = singleGame(LIVE_QUARTER_FIVE)

        val period = requireNotNull(game.period)
        assertEquals(5, period.ordinal)
        assertEquals("OT", period.periodType)
        assertEquals("4:12", period.clock)
    }

    @Test
    fun aRealSlateMapsItsInProgressGamesLiveWithTheQuartersTheyAreIn() {
        // The Sunday capture is real football: thirteen games, several of them being played at each snapshot.
        val day = parse("live/scoreboard-1350.json", "2026-11-22", capture = SUNDAY)

        val live = day.games.filter { it.state is GameState.Live }
        assertTrue("the 13:50 snapshot is mid-afternoon football", live.isNotEmpty())
        for (game in live) {
            val period = requireNotNull(game.period) { "${game.id.value} is live without a period" }
            assertTrue("a real quarter, not a guess", period.ordinal in 1..4)
            assertEquals(true, period.running)
            assertTrue("the feed states a running clock", period.clock?.matches(CLOCK) == true)
        }
    }

    @Test
    fun aFinishedGameInTheSameSlateCarriesNoLiveProgress() {
        val day = parse("live/scoreboard-1350.json", "2026-11-22", capture = SUNDAY)

        val finals = day.games.filter { it.state == GameState.Final }
        assertTrue("the same snapshot already holds finals", finals.isNotEmpty())
        for (final in finals) {
            // The raw payload gives a final a period and a 0:00 clock. Neither may become live play.
            assertNull("${final.id.value} is final but carries live progress", final.period)
        }
    }

    @Test
    fun theWeekMatchesTheFixture() {
        assertEquals(11, parse("nfl-scoreboard-week-11.json", "2026-11-22").week)
        assertEquals(12, parse("nfl-scoreboard-week-12.json", "2026-11-26").week)
        assertEquals(1, parse("nfl-scoreboard-week-1.json", "2026-09-10").week)
    }

    @Test
    fun todaysEventsCarryBroadcasts() {
        val day = parse("nfl-scoreboard-today.json", "2026-11-19")
        val withBroadcasts = day.games.filter { it.broadcasts.isNotEmpty() }

        assertTrue("expected broadcasts on today's games", withBroadcasts.isNotEmpty())
        assertTrue(withBroadcasts.all { it.broadcasts.all { broadcast -> broadcast.network.isNotBlank() } })
    }

    @Test
    fun anEmptyDayHasNoGamesAndNoDroppedGames() {
        val day = parse("nfl-scoreboard-emptyday.json", "2026-07-15")

        assertEquals(emptyList<Any>(), day.games)
        assertEquals(0, day.droppedCount)
    }

    @Test
    fun aMalformedEventIsDroppedAndCountedRatherThanTakingTheDayDown() {
        val json =
            """
            {"events":[
              {"id":"1","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
                {"homeAway":"home","score":"0","team":{"abbreviation":"AAA"}},
                {"homeAway":"away","score":"0","team":{"abbreviation":"BBB"}}],
                "status":{"type":{"name":"STATUS_SCHEDULED"}}}]},
              {"id":"2"}]}
            """.trimIndent()

        val result = NflScoreboardAdapter.parseScoreboard(json, "2026-11-22")
        val day = (result as ParseResult.Success).value

        assertEquals(1, day.games.size)
        assertEquals(1, day.droppedCount)
    }

    @Test
    fun anUnreadablePayloadIsAFailureRatherThanAnEmptyDay() {
        val result = NflScoreboardAdapter.parseScoreboard("not json", "2026-11-22")

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun aScoreboardWithoutEventsIsAFailure() {
        val result = NflScoreboardAdapter.parseScoreboard("""{"leagues":[]}""", "2026-11-22")

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun aKickoffWrittenToTheMinuteStillBecomesAStartTime() {
        // ESPN states a kickoff as `2026-11-22T18:00Z`, with no seconds. Dropping the time would leave a scheduled
        // game with no start, so the seconds are added back.
        val json =
            """
            {"events":[{"id":"996","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_SCHEDULED"}}}]}]}
            """.trimIndent()

        val result = NflScoreboardAdapter.parseScoreboard(json, "2026-11-22")
        val game = (result as ParseResult.Success).value.games.single()

        assertEquals("2026-11-22T18:00:00Z", game.startTimeUtc.toString())
    }

    @Test
    fun aFinalCarriesTheScoreAndTheStartTime() {
        val day = parse("nfl-scoreboard-sunday-b.json", "2026-11-22")
        val final = day.games.first { it.state == GameState.Final }

        assertTrue(final.homeScore != null)
        assertTrue(final.awayScore != null)
        assertTrue("the feed states a start time", final.startTimeUtc != null)
        // A final is not live play, even though the scoreboard carries a period and a clock.
        assertNull(final.period)
    }

    @Test
    fun aLiveGameCarriesTheDownAndTheTeamWithTheBall() {
        val day = parse("live/scoreboard-1350.json", "2026-11-22", capture = "2026-11-22")
        // The capture's second quarter: Miami has it, third and three.
        val game = day.games.first { it.away.abbrev == "MIA" && it.state is GameState.Live }

        assertEquals(
            FootballSituation(down = 3, distance = 3, possession = "MIA", redZone = false),
            game.football,
        )
    }

    @Test
    fun aSnapWithNoDownKeepsTheBallAndTheRedZone() {
        val day = parse("live/scoreboard-1350.json", "2026-11-22", capture = "2026-11-22")
        // The feed states down -1 between plays: Chicago is inside the twenty with no down pending.
        val game = day.games.first { it.home.abbrev == "CHI" && it.state is GameState.Live }

        assertEquals(
            FootballSituation(down = null, distance = null, possession = "CHI", redZone = true),
            game.football,
        )
    }

    @Test
    fun aFinalIgnoresASnapTheFeedLeftBehind() {
        // The corpus holds no final with a situation, so the case the guard exists for is built from a real one: the
        // live game's situation block is copied onto a finished game, which is what a stale snapshot looks like.
        val parsed = Json.parseToJsonElement(fixture("live/scoreboard-1350.json", capture = "2026-11-22")) as JsonObject
        val events = parsed["events"] as JsonArray
        val liveSituation =
            events
                .map { it as JsonObject }
                .first { it.competitionStatusName() == "STATUS_IN_PROGRESS" }
                .competition()
                .getValue("situation")
        val mutated =
            JsonObject(
                parsed.toMutableMap().apply {
                    put(
                        "events",
                        JsonArray(
                            events.map { event ->
                                val obj = event as JsonObject
                                if (obj.competitionStatusName() != "STATUS_FINAL") {
                                    obj
                                } else {
                                    val competition = obj.competition()
                                    JsonObject(
                                        obj.toMutableMap().apply {
                                            put(
                                                "competitions",
                                                JsonArray(
                                                    listOf(
                                                        JsonObject(
                                                            competition.toMutableMap().apply {
                                                                put("situation", liveSituation)
                                                            },
                                                        ),
                                                    ),
                                                ),
                                            )
                                        },
                                    )
                                }
                            },
                        ),
                    )
                },
            ).toString()

        val day =
            when (val result = NflScoreboardAdapter.parseScoreboard(mutated, "2026-11-22")) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> error("Parse failed: ${result.reason}")
            }

        assertTrue("the capture holds a final", day.games.any { it.state == GameState.Final })
        assertTrue(
            "a final states no snap, whatever the feed last wrote",
            day.games.filter { it.state == GameState.Final }.all { it.football == null },
        )
    }

    @Test
    fun onlyALiveGameCarriesASnap() {
        val day = parse("live/scoreboard-1350.json", "2026-11-22", capture = "2026-11-22")

        val others = day.games.filterNot { it.state is GameState.Live }
        assertTrue("the capture holds finished or scheduled games too", others.isNotEmpty())
        assertTrue(
            "a game that is not being played states no snap: ${others.filter { it.football != null }.map { it.home.abbrev }}",
            others.all { it.football == null },
        )
    }

    private fun fixture(
        name: String,
        capture: String,
    ): String =
        NflScoreboardAdapterTest::class.java
            .getResourceAsStream("/fx-nfl/$capture/$name")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Fixture resource not found: $name")

    private fun JsonObject.competition(): JsonObject = (this["competitions"] as JsonArray)[0] as JsonObject

    private fun JsonObject.competitionStatusName(): String? {
        val status = competition()["status"] as? JsonObject ?: return null
        val type = status["type"] as? JsonObject ?: return null
        return type["name"]?.jsonPrimitive?.contentOrNull
    }

    private fun singleGame(payload: String): Game {
        val result = NflScoreboardAdapter.parseScoreboard(payload.trimIndent(), "2026-11-22")
        return (result as ParseResult.Success).value.games.single()
    }

    private companion object {
        const val CAPTURE = "2026-11-18"

        /** The Sunday capture, where the in-progress games actually are. */
        const val SUNDAY = "2026-11-22"
        val CLOCK = Regex("""\d{1,2}:\d{2}""")

        /** One game being played, as the feed states it: name, period, clock. */
        val LIVE_QUARTER_ONE =
            """
            {"events":[{"id":"997","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","score":"7","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","score":"3","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_IN_PROGRESS","state":"in","detail":"1:28 - 1st Quarter","completed":false},"period":1,"displayClock":"1:28"}}]}]}
            """.trimIndent()

        /** The same live game with the period the feed owes us missing. */
        val LIVE_WITHOUT_A_PERIOD =
            """
            {"events":[{"id":"996","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","score":"7","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","score":"3","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_IN_PROGRESS","state":"in","detail":"1:28 - 1st Quarter","completed":false},"displayClock":"1:28"}}]}]}
            """.trimIndent()

        /** A fourth-quarter game: the last period this league calls regulation. */
        val LIVE_QUARTER_FOUR =
            """
            {"events":[{"id":"994","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","score":"17","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","score":"13","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_IN_PROGRESS","state":"in","detail":"6:30 - 4th Quarter","completed":false},"period":4,"displayClock":"6:30"}}]}]}
            """.trimIndent()

        /** A fifth period, which this league calls overtime. */
        val LIVE_QUARTER_FIVE =
            """
            {"events":[{"id":"995","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","score":"20","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","score":"20","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_IN_PROGRESS","state":"in","detail":"4:12 - OT","completed":false},"period":5,"displayClock":"4:12"}}]}]}
            """.trimIndent()
    }
}
