package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.SeasonSeriesGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regular-season head-to-head for a COL at TOR game. The away club is COL and the home club is TOR.
 *
 * The standing is cumulative: it reads the whole series so far, not the last game in it. That is the rule the card's
 * final line depends on, and the three-game case below is the one that tells cumulative from last-meeting-only.
 */
class SeasonSeriesTest {
    @Test
    fun theFixtureMeetingReadsAsTorontoLeadingOneNil() {
        val games = listOf(oct10TorWins)

        assertEquals(
            SeasonSeriesStanding.Leads(abbrev = "TOR", wins = 1, losses = 0),
            SeasonSeries.standing(games, awayAbbrev = "COL", homeAbbrev = "TOR"),
        )
        assertEquals("TOR leads 1–0", SeasonSeries.standingText(SeasonSeries.standing(games, "COL", "TOR")))
    }

    @Test
    fun theMeetingLineNamesBothScoresAndTheWinner() {
        val meetings = SeasonSeries.meetings(listOf(oct10TorWins), awayAbbrev = "COL", homeAbbrev = "TOR")

        assertEquals(listOf(SeriesMeeting(date = "Oct 10", scoreline = "TOR 6 – COL 3 · TOR")), meetings)
    }

    @Test
    fun theStandingIsCumulativeNotTheResultOfTheLastMeeting() {
        // TOR, COL, TOR: two wins to one. Reading only the last game would say TOR leads 1–0, which is the wrong answer.
        val games = listOf(oct10TorWins, nov01ColWins, dec01TorWins)

        assertEquals(
            "TOR leads 2–1",
            SeasonSeries.standingText(SeasonSeries.standing(games, awayAbbrev = "COL", homeAbbrev = "TOR")),
        )
    }

    @Test
    fun aSeriesLevelOnWinsReadsAsTied() {
        val games = listOf(oct10TorWins, nov01ColWins)

        assertEquals(SeasonSeriesStanding.Tied(wins = 1), SeasonSeries.standing(games, "COL", "TOR"))
        assertEquals("Tied 1–1", SeasonSeries.standingText(SeasonSeries.standing(games, "COL", "TOR")))
    }

    @Test
    fun aMeetingNotYetPlayedIsFirstMeetingAndPrintsNoRow() {
        val games = listOf(upcomingGame)

        assertEquals(SeasonSeriesStanding.FirstMeeting, SeasonSeries.standing(games, "COL", "TOR"))
        assertEquals("First meeting this season", SeasonSeries.standingText(SeasonSeriesStanding.FirstMeeting))
        assertTrue(SeasonSeries.meetings(games, "COL", "TOR").isEmpty())
    }

    @Test
    fun meetingsPrintOldestFirstWhateverOrderTheFeedSendsThem() {
        val games = listOf(dec01TorWins, oct10TorWins, nov01ColWins)

        val dates = SeasonSeries.meetings(games, "COL", "TOR").map { it.date }

        assertEquals(listOf("Oct 10", "Nov 1", "Dec 1"), dates)
    }

    @Test
    fun onlyFinishedRegularSeasonMeetingsBetweenTheTwoClubsCount() {
        val playoffMeeting = oct10TorWins.copy(gameId = 9, gameType = 3)
        val otherClubs = oct10TorWins.copy(gameId = 10, awayAbbrev = "MTL", homeAbbrev = "TOR")
        val noScore = oct10TorWins.copy(gameId = 11, awayScore = null)
        val live = oct10TorWins.copy(gameId = 12, state = GameState.Live())

        val counted = SeasonSeries.finishedMeetings(listOf(playoffMeeting, otherClubs, noScore, live, oct10TorWins), "COL", "TOR")

        assertEquals(listOf(oct10TorWins.gameId), counted.map { it.gameId })
    }

    private val oct10TorWins =
        SeasonSeriesGame(
            gameId = 2026020080,
            gameType = 2,
            state = GameState.Final,
            awayAbbrev = "TOR",
            awayScore = 6,
            homeAbbrev = "COL",
            homeScore = 3,
            gameDate = "2026-10-10",
        )

    private val nov01ColWins =
        SeasonSeriesGame(
            gameId = 2026020150,
            gameType = 2,
            state = GameState.Final,
            awayAbbrev = "COL",
            awayScore = 4,
            homeAbbrev = "TOR",
            homeScore = 1,
            gameDate = "2026-11-01",
        )

    private val dec01TorWins =
        SeasonSeriesGame(
            gameId = 2026020230,
            gameType = 2,
            state = GameState.Final,
            awayAbbrev = "TOR",
            awayScore = 2,
            homeAbbrev = "COL",
            homeScore = 1,
            gameDate = "2026-12-01",
        )

    private val upcomingGame =
        SeasonSeriesGame(
            gameId = 2026020286,
            gameType = 2,
            state = GameState.Scheduled,
            awayAbbrev = "COL",
            awayScore = null,
            homeAbbrev = "TOR",
            homeScore = null,
            gameDate = "2026-11-10",
        )
}
