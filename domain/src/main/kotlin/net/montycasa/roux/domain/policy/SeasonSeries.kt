package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.SeasonSeriesGame

/** Where a regular-season series stands between two clubs, counted from the meetings already played. */
sealed interface SeasonSeriesStanding {
    /** One club has more wins. [wins] and [losses] are that club's and the other's. */
    data class Leads(
        val abbrev: String,
        val wins: Int,
        val losses: Int,
    ) : SeasonSeriesStanding

    /** The clubs have the same number of wins. */
    data class Tied(
        val wins: Int,
    ) : SeasonSeriesStanding

    /** No meeting has been played this season. */
    data object FirstMeeting : SeasonSeriesStanding
}

/** One finished meeting as the series card prints it: [date] is `Oct 10`, [scoreline] is `TOR 6 – COL 3 · TOR`. */
data class SeriesMeeting(
    val date: String?,
    val scoreline: String,
)

/**
 * The regular-season head-to-head: which meetings count, the order they print in, and the running standing.
 *
 * Only regular-season games (`gameType 2`) that the feed marks final count, and a game needs both scores. A meeting
 * that has not been played is not a result, so it is not in the standing and not on the card.
 */
object SeasonSeries {
    /** The right rail's game type for a regular-season game. */
    private const val REGULAR_SEASON = 2

    /**
     * The finished meetings between the two clubs, oldest first.
     *
     * A game between other clubs is ignored even if the rail lists it, because the card describes this matchup only.
     */
    fun finishedMeetings(
        games: List<SeasonSeriesGame>,
        awayAbbrev: String,
        homeAbbrev: String,
    ): List<SeasonSeriesGame> {
        val clubs = setOf(awayAbbrev, homeAbbrev)
        return games
            .filter { game ->
                game.gameType == REGULAR_SEASON &&
                    game.state == GameState.Final &&
                    game.awayScore != null &&
                    game.homeScore != null &&
                    setOfNotNull(game.awayAbbrev, game.homeAbbrev) == clubs
            }.sortedWith(compareBy<SeasonSeriesGame>({ it.gameDate }, { it.gameId }))
    }

    /**
     * The standing after every finished meeting: cumulative, not the result of the latest game.
     *
     * A series that is tied after two games reads `Tied`, whatever the most recent game was. With no meeting played the
     * answer is [SeasonSeriesStanding.FirstMeeting], so the standing is never absent.
     */
    fun standing(
        games: List<SeasonSeriesGame>,
        awayAbbrev: String,
        homeAbbrev: String,
    ): SeasonSeriesStanding {
        val meetings = finishedMeetings(games, awayAbbrev, homeAbbrev)
        if (meetings.isEmpty()) return SeasonSeriesStanding.FirstMeeting
        val wins = meetings.mapNotNull { winnerAbbrev(it) }.groupingBy { it }.eachCount()
        val awayWins = wins[awayAbbrev] ?: 0
        val homeWins = wins[homeAbbrev] ?: 0
        return when {
            awayWins == homeWins -> SeasonSeriesStanding.Tied(awayWins)
            awayWins > homeWins -> SeasonSeriesStanding.Leads(awayAbbrev, awayWins, homeWins)
            else -> SeasonSeriesStanding.Leads(homeAbbrev, homeWins, awayWins)
        }
    }

    /** The standing as the card prints it: `TOR leads 1–0`, `Tied 1–1`, or `First meeting this season`. */
    fun standingText(standing: SeasonSeriesStanding): String =
        when (standing) {
            is SeasonSeriesStanding.Leads -> "${standing.abbrev} leads ${standing.wins}–${standing.losses}"
            is SeasonSeriesStanding.Tied -> "Tied ${standing.wins}–${standing.wins}"
            SeasonSeriesStanding.FirstMeeting -> "First meeting this season"
        }

    /** One row per finished meeting, oldest first. Empty when no meeting has been played, which hides the card. */
    fun meetings(
        games: List<SeasonSeriesGame>,
        awayAbbrev: String,
        homeAbbrev: String,
    ): List<SeriesMeeting> =
        finishedMeetings(games, awayAbbrev, homeAbbrev).mapNotNull { game ->
            val away = game.awayAbbrev ?: return@mapNotNull null
            val home = game.homeAbbrev ?: return@mapNotNull null
            val awayScore = game.awayScore ?: return@mapNotNull null
            val homeScore = game.homeScore ?: return@mapNotNull null
            val winner = winnerAbbrev(game) ?: return@mapNotNull null
            SeriesMeeting(
                date = game.gameDate?.let { PreGameFormat.shortDate(it) },
                scoreline = "$away $awayScore – $home $homeScore · $winner",
            )
        }

    /** The club that won a finished game by score; null for a game without both scores, or a tie. */
    private fun winnerAbbrev(game: SeasonSeriesGame): String? {
        val away = game.awayScore ?: return null
        val home = game.homeScore ?: return null
        return when {
            away > home -> game.awayAbbrev
            home > away -> game.homeAbbrev
            else -> null
        }
    }
}
