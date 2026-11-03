package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class ClubGameStatusTest {
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val toronto = TeamKey("TOR")
    private val boston = TeamKey("BOS")

    @Test
    fun aFutureGameShowsItsStartTime() {
        val status = status(game(state = GameState.Scheduled, start = "2026-11-05T20:00:00Z"))

        assertEquals(ClubGameStatus.Upcoming(Instant.parse("2026-11-05T20:00:00Z")), status)
    }

    @Test
    fun aLiveGameSaysLiveEvenAfterItsStartHasPassed() {
        val status = status(game(state = GameState.Live(), start = "2026-11-03T11:00:00Z"))

        assertEquals(ClubGameStatus.Live, status)
    }

    @Test
    fun aHomeWinPrintsTheClubsScoreFirst() {
        val status =
            status(
                game(
                    state = GameState.Final,
                    finished = true,
                    home = toronto,
                    away = boston,
                    homeScore = 5,
                    awayScore = 3,
                ),
            )

        assertEquals(ClubGameStatus.Result(won = true, clubScore = 5, opponentScore = 3), status)
    }

    @Test
    fun anAwayWinByFiveToThreeStillReadsAsAWin() {
        // The venue must not pick the winner: the club's score is the away score here.
        val status =
            status(
                game(
                    state = GameState.Final,
                    finished = true,
                    home = boston,
                    away = toronto,
                    homeScore = 3,
                    awayScore = 5,
                ),
            )

        assertEquals(ClubGameStatus.Result(won = true, clubScore = 5, opponentScore = 3), status)
    }

    @Test
    fun aLossPrintsTheClubsScoreFirst() {
        val status =
            status(
                game(
                    state = GameState.Final,
                    finished = true,
                    home = toronto,
                    away = boston,
                    homeScore = 1,
                    awayScore = 3,
                ),
            )

        assertEquals(ClubGameStatus.Result(won = false, clubScore = 1, opponentScore = 3), status)
    }

    @Test
    fun aFinishedGameWithoutAScoreSaysFinalRatherThanZeroZero() {
        val status =
            status(
                game(
                    state = GameState.Final,
                    finished = true,
                    homeScore = null,
                    awayScore = 3,
                ),
            )

        assertEquals(ClubGameStatus.Final, status)
    }

    @Test
    fun postponedAndSuspendedStayWordsEvenWhenTheStartLooksFuture() {
        assertEquals(
            ClubGameStatus.Postponed,
            status(game(state = GameState.Scheduled, start = "2026-11-05T20:00:00Z", scheduleState = "PPD")),
        )
        assertEquals(
            ClubGameStatus.Suspended,
            status(game(state = GameState.Scheduled, start = "2026-11-05T20:00:00Z", scheduleState = "SUSP")),
        )
    }

    @Test
    fun aPregameStateKeepsItsWordBeforeTheClockReachesTheStart() {
        val status = status(game(state = GameState.Pre, start = "2026-11-05T20:00:00Z"))

        assertEquals(ClubGameStatus.Pregame, status)
    }

    private fun status(game: ClubGame): ClubGameStatus = ClubGameStatusPolicy.status(game, toronto, now)

    private fun game(
        state: GameState,
        start: String? = null,
        finished: Boolean = false,
        home: TeamKey = toronto,
        away: TeamKey = boston,
        homeScore: Int? = null,
        awayScore: Int? = null,
        scheduleState: String? = null,
    ): ClubGame =
        ClubGame(
            id = GameId("2026020001"),
            date = "2026-11-03",
            startTimeUtc = start?.let(Instant::parse),
            home = TeamRef(home),
            away = TeamRef(away),
            state = state,
            finished = finished,
            gameScheduleState = scheduleState,
            homeScore = homeScore,
            awayScore = awayScore,
        )
}
