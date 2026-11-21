package net.montycasa.roux.data.nfl

import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.FinalKind
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
    ): net.montycasa.roux.domain.model.ScoreDay {
        val json =
            NflScoreboardAdapterTest::class.java
                .getResourceAsStream("/fx-nfl/$CAPTURE/$name")
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
    fun aLiveStatusIsNotGuessedAt() {
        // The live mapping is a separate step. Until it lands, a live game must read as unknown rather than as live.
        val json =
            """
            {"events":[{"id":"997","date":"2026-11-22T18:00Z","competitions":[{"competitors":[
              {"homeAway":"home","score":"7","team":{"abbreviation":"AAA"}},
              {"homeAway":"away","score":"3","team":{"abbreviation":"BBB"}}],
              "status":{"type":{"name":"STATUS_IN_PROGRESS","state":"in","detail":"1:28 - 1st Quarter","completed":false},"period":1,"displayClock":"1:28"}}]}]}
            """.trimIndent()

        val result = NflScoreboardAdapter.parseScoreboard(json, "2026-11-22")
        val game = (result as ParseResult.Success).value.games.single()

        assertEquals(GameState.Unknown("STATUS_IN_PROGRESS"), game.state)
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

    private companion object {
        const val CAPTURE = "2026-11-18"
    }
}
