package net.montycasa.roux.ui.team

import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflSeasonEntry
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.time.Instant

/**
 * A football season for the screens that draw one.
 *
 * Built from the shape of the real capture rather than from the capture itself: the screens are what these fixtures
 * feed, and the captured payload is the adapter's business, proven where the adapter is tested.
 */
internal object NflTeamPageFixtures {
    val BILLS = TeamKey(League.NFL, "BUF")

    fun buffalo(): NflTeamPageUiState =
        NflTeamPageUiState(
            teamKey = BILLS,
            team = TeamRef(BILLS, name = "Buffalo Bills"),
            season = season(),
            loading = false,
        )

    private fun season(): NflClubSchedule =
        NflClubSchedule(
            seasonYear = 2026,
            team = TeamRef(BILLS, name = "Buffalo Bills"),
            byeWeek = 2,
            statedByeWeek = 2,
            entries =
                listOf(
                    NflSeasonEntry.Game(1, game(home = "HOU", away = "BUF", homeScore = 31, awayScore = 36)),
                    NflSeasonEntry.Bye(2),
                    NflSeasonEntry.Game(3, game(home = "BUF", away = "MIA", homeScore = 17, awayScore = 24)),
                    NflSeasonEntry.Game(4, game(home = "BUF", away = "KC", state = GameState.Scheduled, finished = false)),
                    NflSeasonEntry.Game(5, game(home = "NYJ", away = "BUF", homeScore = 10, awayScore = 21)),
                ),
        )

    private fun game(
        home: String,
        away: String,
        homeScore: Int? = null,
        awayScore: Int? = null,
        state: GameState = GameState.Final,
        finished: Boolean = true,
    ): ClubGame =
        ClubGame(
            id = GameId(League.NFL, "$home-$away"),
            date = "2026-09-13",
            startTimeUtc = Instant.parse("2026-09-13T17:00:00Z"),
            home = TeamRef(TeamKey(League.NFL, home)),
            away = TeamRef(TeamKey(League.NFL, away)),
            state = state,
            finished = finished,
            homeScore = homeScore,
            awayScore = awayScore,
        )
}
