package net.montycasa.roux.data.nfl

import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.FootballResult
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflSeasonEntry
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.resultFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The season a club's schedule describes, read from three real captures.
 *
 * The three were chosen because the bye falls in a different place in each, and because one of them disproves the
 * feed's own bye field - see [theGamesDecideTheByeWhenTheFeedFieldDisagrees].
 */
class NflClubScheduleAdapterTest {
    private fun schedule(club: String): NflClubSchedule =
        when (val result = NflClubScheduleAdapter.parse(fixture(club))) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("Parse failed: ${result.reason}")
        }

    private fun fixture(club: String): String =
        NflClubScheduleAdapterTest::class.java
            .getResourceAsStream("/fx-nfl/$CAPTURE/nfl-schedule-$club.json")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Fixture resource not found: nfl-schedule-$club.json")

    private fun NflClubSchedule.games(): List<NflSeasonEntry.Game> = entries.filterIsInstance<NflSeasonEntry.Game>()

    private fun NflClubSchedule.bye(): NflSeasonEntry.Bye? = entries.filterIsInstance<NflSeasonEntry.Bye>().singleOrNull()

    @Test
    fun aSeasonIsSeventeenGamesAndOneBye() {
        for (club in CLUBS) {
            val season = schedule(club)
            assertEquals("$club games", 17, season.games().size)
            assertEquals("$club rows", 18, season.entries.size)
            assertTrue("$club has exactly one bye", season.bye() != null)
            assertEquals("$club season year", 2026, season.seasonYear)
            assertEquals("$club is a football club", League.NFL, season.team.key.league)
        }
    }

    @Test
    fun theByeIsTheWeekNoGameOccupies() {
        // The three captures put the bye in three different weeks, so a hardcoded week cannot pass.
        assertEquals(7, schedule("buf").byeWeek)
        assertEquals(8, schedule("sf").byeWeek)
        assertEquals(14, schedule("ari").byeWeek)
    }

    @Test
    fun theGamesDecideTheByeWhenTheFeedFieldDisagrees() {
        // Buffalo's payload states byeWeek 5 - and also states a Buffalo game in week 5. The games are the evidence:
        // the season's bye is the empty week, and the stated number is kept so the disagreement is not tidied away.
        val buffalo = schedule("buf")

        assertEquals("what the feed's own field says", 5, buffalo.statedByeWeek)
        assertEquals("what the games say", 7, buffalo.byeWeek)
        assertTrue(
            "week 5 must really hold a game for this to be a disagreement",
            buffalo.games().any { it.week == 5 },
        )
        // The other two captures agree, so this is the feed being wrong once, not the field meaning something else.
        assertEquals(8, schedule("sf").statedByeWeek)
        assertEquals(8, schedule("sf").byeWeek)
        assertEquals(14, schedule("ari").statedByeWeek)
        assertEquals(14, schedule("ari").byeWeek)
    }

    @Test
    fun theByeRowSitsWhereTheSeasonWasEmpty() {
        val entries = schedule("buf").entries
        val bye = entries.indexOfFirst { it is NflSeasonEntry.Bye }

        assertEquals("the bye row is week 7", 7, entries[bye].week)
        assertEquals(6, entries[bye - 1].week)
        assertEquals(8, entries[bye + 1].week)
    }

    @Test
    fun aFinishedGameIsWonOrLostFromThisClubsSide() {
        val buffalo = schedule("buf")
        val week1 = buffalo.games().first { it.week == 1 }
        // Captured as Houston 31, Buffalo 36, with Buffalo away.
        assertEquals("BUF", week1.game.away.key.abbrev)
        assertEquals(FootballResult.Win, week1.game.resultFor(TeamKey(League.NFL, "BUF")))
        assertEquals(FootballResult.Loss, week1.game.resultFor(TeamKey(League.NFL, "HOU")))
    }

    @Test
    fun aSeasonHasBothWinsAndLossesToProveTheSidesAreNotSwapped() {
        val results = schedule("buf").games().map { it.game.resultFor(TeamKey(League.NFL, "BUF")) }

        assertTrue("the capture must contain a win", results.any { it == FootballResult.Win })
        assertTrue("the capture must contain a loss", results.any { it == FootballResult.Loss })
    }

    @Test
    fun anUnplayedGameStatesNoScoreAndNoResult() {
        val week12 = schedule("buf").games().first { it.week == 12 }

        assertEquals(GameState.Scheduled, week12.game.state)
        assertNull("a scheduled game has no home score", week12.game.homeScore)
        assertNull("a scheduled game has no away score", week12.game.awayScore)
        assertEquals(FootballResult.Unplayed, week12.game.resultFor(TeamKey(League.NFL, "BUF")))
    }

    @Test
    fun aPlaceholderScoreOnAnUnplayedGameIsNotAScore() {
        // The captures leave a scheduled game's score out entirely, so they cannot prove the guard that drops it. This
        // payload states one anyway - the way a fixture can carry 0-0 before kickoff - and the season must not read it.
        val json =
            """
            {"team":{"abbreviation":"BUF","displayName":"Buffalo Bills"},
             "season":{"year":2026},"byeWeek":3,
             "events":[
               {"id":"1","date":"2026-09-13T17:00Z","week":{"number":1},
                "competitions":[{"status":{"type":{"name":"STATUS_SCHEDULED","completed":false}},
                  "competitors":[{"homeAway":"home","team":{"abbreviation":"HOU"},"score":{"value":0.0}},
                                 {"homeAway":"away","team":{"abbreviation":"BUF"},"score":{"value":0.0}}]}]},
               {"id":"2","date":"2026-09-20T17:00Z","week":{"number":2},
                "competitions":[{"status":{"type":{"name":"STATUS_FINAL","completed":true}},
                  "competitors":[{"homeAway":"home","team":{"abbreviation":"BUF"},"score":{"value":20.0}},
                                 {"homeAway":"away","team":{"abbreviation":"MIA"},"score":{"value":20.0}}]}]}
             ]}
            """.trimIndent()

        val season =
            when (val result = NflClubScheduleAdapter.parse(json)) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> error("Parse failed: ${result.reason}")
            }

        val scheduled = season.games().first { it.week == 1 }.game
        assertNull("a placeholder 0 must not become a score", scheduled.homeScore)
        assertNull(scheduled.awayScore)
        assertEquals(FootballResult.Unplayed, scheduled.resultFor(TeamKey(League.NFL, "BUF")))
        // The final beside it is level, and that IS a tie.
        assertEquals(
            FootballResult.Tie,
            season
                .games()
                .first { it.week == 2 }
                .game
                .resultFor(TeamKey(League.NFL, "BUF")),
        )
    }

    @Test
    fun aKickoffIsAnInstantAndTheDayIsItsOwnString() {
        val week1 = schedule("buf").games().first { it.week == 1 }

        // The capture states 2026-09-13T17:00Z: a Sunday afternoon kickoff, kept as the feed wrote the day.
        assertEquals("2026-09-13", week1.game.date)
        assertEquals("2026-09-13T17:00:00Z", week1.game.startTimeUtc.toString())
    }

    @Test
    fun aGameCarriesBothClubsAndItsVenue() {
        val week1 = schedule("buf").games().first { it.week == 1 }

        assertEquals("HOU", week1.game.home.key.abbrev)
        assertEquals("BUF", week1.game.away.key.abbrev)
        assertEquals(League.NFL, week1.game.home.key.league)
        assertEquals(League.NFL, week1.game.away.key.league)
        assertEquals("Reliant Stadium", week1.game.venue)
    }

    @Test
    fun aCrestOffTheAllowedHostIsDroppedRatherThanRequested() {
        for (game in schedule("buf").games()) {
            for (club in listOf(game.game.home, game.game.away)) {
                val logo = club.logoUrl ?: continue
                assertTrue("${club.key.abbrev} crest is off the allowed host: $logo", logo.startsWith("https://a.espncdn.com/"))
            }
        }
    }

    @Test
    fun aTieIsItsOwnOutcomeRatherThanALoss() {
        // No tie is in these captures - three clubs, forty-eight finals, none level. The domain rule is pinned here on
        // a game built for it, so the day the feed sends one the table already knows what it is.
        val club = TeamKey(League.NFL, "BUF")
        val tied = game(home = 20, away = 20)

        assertEquals(FootballResult.Tie, tied.resultFor(club))
        assertEquals(FootballResult.Tie, tied.resultFor(TeamKey(League.NFL, "HOU")))
    }

    @Test
    fun aScheduledGameWithPlaceholderNumbersIsNotATie() {
        // The feed's scheduled games carry no score at all; if they ever carried 0-0 the state still settles it.
        val unplayed = game(home = 0, away = 0, state = GameState.Scheduled, finished = false)

        assertEquals(FootballResult.Unplayed, unplayed.resultFor(TeamKey(League.NFL, "BUF")))
    }

    @Test
    fun anEmptyPayloadIsReportedAsEmptyRatherThanGarbled() {
        val result = NflClubScheduleAdapter.parse("""{"team":{"abbreviation":"BUF"},"events":[]}""")

        assertTrue("an empty season is an empty payload: $result", result is ParseResult.Failure && result.emptyPayload)
    }

    @Test
    fun aMalformedPayloadFails() {
        val result = NflClubScheduleAdapter.parse("""{"events": "not a list"}""")

        assertTrue("garbage is a failure, not an empty season: $result", result is ParseResult.Failure)
    }

    private fun game(
        home: Int,
        away: Int,
        state: GameState = GameState.Final,
        finished: Boolean = true,
    ): ClubGame =
        ClubGame(
            id = GameId(League.NFL, "test"),
            date = "2026-11-25",
            home = TeamRef(TeamKey(League.NFL, "HOU")),
            away = TeamRef(TeamKey(League.NFL, "BUF")),
            state = state,
            finished = finished,
            homeScore = home,
            awayScore = away,
        )

    private companion object {
        const val CAPTURE = "2026-11-25"
        val CLUBS = listOf("buf", "sf", "ari")
    }
}
