package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.SeasonSeriesGame
import net.montycasa.roux.domain.model.SeriesStatus
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayoffSeriesTest {
    /**
     * The 2025-26 first-round series between BUF and BOS as the right rail lists it, in feed order. BUF won it 4–2. The
     * rail's `seasonSeriesWins` gives only that final result, so each game's line has to be counted up to that game.
     */
    private val rail =
        listOf(
            playoff(2025030111, away = "BOS", awayScore = 3, home = "BUF", homeScore = 4),
            playoff(2025030112, away = "BOS", awayScore = 4, home = "BUF", homeScore = 2),
            playoff(2025030113, away = "BUF", awayScore = 3, home = "BOS", homeScore = 1),
            playoff(2025030114, away = "BUF", awayScore = 6, home = "BOS", homeScore = 1),
            playoff(2025030115, away = "BOS", awayScore = 2, home = "BUF", homeScore = 1),
            playoff(2025030116, away = "BUF", awayScore = 4, home = "BOS", homeScore = 1),
        )

    @Test
    fun aFeedStandingIsWordedAsItStoodAfterThatGame() {
        val line = PlayoffSeries.fromFeed(feed(topAbbrev = "BUF", topWins = 1, bottomAbbrev = "BOS", bottomWins = 0, game = 1))

        assertEquals(SeriesLine("1st Round", 1, SeriesStanding.Leads("BUF", 1, 0)), line)
    }

    /** The top seed is the first club the feed names, and its wins belong to it, not to the other seed. */
    @Test
    fun aFeedStandingKeepsEachSeedsWinsWithItsClub() {
        val line = PlayoffSeries.fromFeed(feed(topAbbrev = "BUF", topWins = 0, bottomAbbrev = "BOS", bottomWins = 1, game = 1))

        assertEquals(SeriesLine("1st Round", 1, SeriesStanding.Leads("BOS", 1, 0)), line)
    }

    @Test
    fun aTiedFeedStandingReadsTied() {
        val line = PlayoffSeries.fromFeed(feed(topAbbrev = "BUF", topWins = 2, bottomAbbrev = "BOS", bottomWins = 2, game = 4))

        assertEquals(SeriesLine("1st Round", 4, SeriesStanding.Tied(2)), line)
    }

    @Test
    fun aClubAtTheWinsNeededReadsAsWinningTheSeries() {
        val line = PlayoffSeries.fromFeed(feed(topAbbrev = "BUF", topWins = 4, bottomAbbrev = "BOS", bottomWins = 1, game = 5))

        assertEquals(SeriesLine("1st Round", 5, SeriesStanding.Wins("BUF", 4, 1)), line)
    }

    @Test
    fun aFeedStandingMissingItsWinsNeededIsNoLine() {
        val series =
            SeriesStatus(
                seriesTitle = "1st Round",
                topSeedTeamAbbrev = "BUF",
                topSeedWins = 1,
                bottomSeedTeamAbbrev = "BOS",
                bottomSeedWins = 0,
                gameNumberOfSeries = 1,
            )

        assertNull(PlayoffSeries.fromFeed(series))
    }

    @Test
    fun theFeedsOwnLineIsUsedWhenTheGameHasOne() {
        val withFeed = gameOf(2025030111L, series = feed(topAbbrev = "BUF", topWins = 1, bottomAbbrev = "BOS", bottomWins = 0, game = 1))

        assertEquals(SeriesLine("1st Round", 1, SeriesStanding.Leads("BUF", 1, 0)), PlayoffSeries.forGame(withFeed, rail))
    }

    @Test
    fun aGameWithoutAFeedStandingReadsTheRightRailFromItsOwnCount() {
        assertEquals(SeriesLine(null, 2, SeriesStanding.Tied(1)), PlayoffSeries.forGame(gameOf(2025030112L), rail))
    }

    /** Game 1 reads the standing after game 1. The later finished games in the rail must not change it. */
    @Test
    fun railGameOneLeadsOneNilAndIgnoresTheLaterGames() {
        assertEquals(SeriesLine(null, 1, SeriesStanding.Leads("BUF", 1, 0)), PlayoffSeries.fromSeasonSeries(2025030111L, rail))
    }

    @Test
    fun railGameTwoTiesTheSeriesOneEach() {
        assertEquals(SeriesLine(null, 2, SeriesStanding.Tied(1)), PlayoffSeries.fromSeasonSeries(2025030112L, rail))
    }

    /** The sixth game closes the series 4–2 for BUF, and that is the one line that reads a club winning. */
    @Test
    fun railGameSixReadsTheClinchedSeries() {
        assertEquals(SeriesLine(null, 6, SeriesStanding.Wins("BUF", 4, 2)), PlayoffSeries.fromSeasonSeries(2025030116L, rail))
    }

    /** A game still in play is not a finished game, so it adds nothing to the count and its number is the next one. */
    @Test
    fun aLiveGameIsNotCountedAsFinished() {
        val series =
            listOf(
                playoff(2025030111, away = "BOS", awayScore = 3, home = "BUF", homeScore = 4),
                playoff(2025030112, away = "BOS", awayScore = 4, home = "BUF", homeScore = 2, state = GameState.Live()),
            )

        assertEquals(SeriesLine(null, 2, SeriesStanding.Leads("BUF", 1, 0)), PlayoffSeries.fromSeasonSeries(2025030112L, series))
    }

    @Test
    fun aRegularSeasonGameHasNoSeriesLine() {
        val regular = listOf(playoff(2025020001, away = "BOS", awayScore = 3, home = "BUF", homeScore = 4, gameType = 2))

        assertNull(PlayoffSeries.fromSeasonSeries(2025020001L, regular))
        assertNull(PlayoffSeries.forGame(gameOf(2025020001L), regular))
    }

    @Test
    fun aGameTheRailDoesNotListHasNoSeriesLine() {
        assertNull(PlayoffSeries.fromSeasonSeries(2025030199L, rail))
    }

    private fun feed(
        topAbbrev: String,
        topWins: Int,
        bottomAbbrev: String,
        bottomWins: Int,
        game: Int,
    ) = SeriesStatus(
        round = 1,
        seriesTitle = "1st Round",
        neededToWin = 4,
        topSeedTeamAbbrev = topAbbrev,
        topSeedWins = topWins,
        bottomSeedTeamAbbrev = bottomAbbrev,
        bottomSeedWins = bottomWins,
        gameNumberOfSeries = game,
    )

    private fun gameOf(
        id: Long,
        series: SeriesStatus? = null,
    ) = Game(
        id = GameId(id.toString()),
        date = "2026-04-19",
        state = GameState.Final,
        home = TeamRef(TeamKey("BUF")),
        away = TeamRef(TeamKey("BOS")),
        series = series,
    )

    private fun playoff(
        id: Long,
        away: String,
        awayScore: Int,
        home: String,
        homeScore: Int,
        state: GameState = GameState.Final,
        gameType: Int = 3,
    ) = SeasonSeriesGame(
        gameId = id,
        gameType = gameType,
        state = state,
        awayAbbrev = away,
        awayScore = awayScore,
        homeAbbrev = home,
        homeScore = homeScore,
    )
}
