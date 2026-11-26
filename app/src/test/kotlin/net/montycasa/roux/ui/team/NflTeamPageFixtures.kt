package net.montycasa.roux.ui.team

import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflPlayerStatus
import net.montycasa.roux.domain.model.NflRosterPlayer
import net.montycasa.roux.domain.model.NflSeasonEntry
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.time.Instant

/**
 * A football season and roster for the screens that draw them.
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
            roster = roster(),
            rosterLoading = false,
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

    /**
     * Three players, in the order the adapter establishes: position abbreviation, then jersey, then name.
     *
     * Kader Kohou is the two-absent case at once - no jersey and no status - which is what the roster tab must not
     * fill in with "0" and "Active".
     */
    private fun roster(): NflClubRoster =
        NflClubRoster(
            team = TeamRef(BILLS, name = "Buffalo Bills"),
            players =
                listOf(
                    player(
                        id = "kohou",
                        name = "Kader Kohou",
                        jersey = null,
                        positionAbbreviation = "CB",
                        positionName = "Cornerback",
                        status = NflPlayerStatus.NotStated,
                    ),
                    player(
                        id = "durant",
                        name = "Zane Durant",
                        jersey = "71",
                        positionAbbreviation = "DT",
                        positionName = "Defensive Tackle",
                        status = NflPlayerStatus.Other("Day-To-Day"),
                    ),
                    player(
                        id = "allen",
                        name = "Josh Allen",
                        jersey = "17",
                        positionAbbreviation = "QB",
                        positionName = "Quarterback",
                        status = NflPlayerStatus.Active("Active"),
                    ),
                ),
        )

    private fun player(
        id: String,
        name: String,
        jersey: String?,
        positionAbbreviation: String,
        positionName: String,
        status: NflPlayerStatus,
    ): NflRosterPlayer =
        NflRosterPlayer(
            id = id,
            name = name,
            jersey = jersey,
            positionAbbreviation = positionAbbreviation,
            positionName = positionName,
            heightText = "6' 3\"",
            weightText = "230 lbs",
            age = 27,
            headshotUrl = null,
            status = status,
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
