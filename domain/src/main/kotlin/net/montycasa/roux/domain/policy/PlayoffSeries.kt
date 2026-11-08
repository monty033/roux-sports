package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.SeasonSeriesGame
import net.montycasa.roux.domain.model.SeriesStatus

/**
 * A playoff game's series line for the hero: the round when the feed names it, the game's number in the series, and
 * where the series stands once this game is played.
 */
data class SeriesLine(
    /** The feed's round title, such as `1st Round`. Null when the line came from the right rail, which does not name it. */
    val round: String?,
    /** The game's number in its series. Null only when the feed did not send it. */
    val gameNumber: Int?,
    val standing: SeriesStanding,
)

/** Where a series stands: a club leads, the clubs are tied, or a club has reached the wins needed. */
sealed interface SeriesStanding {
    /** One club has more wins and has not yet reached the wins needed. [wins] and [losses] are that club's and the other's. */
    data class Leads(
        val abbrev: String,
        val wins: Int,
        val losses: Int,
    ) : SeriesStanding

    data class Tied(
        val wins: Int,
    ) : SeriesStanding

    /** A club has reached the wins needed, so the series is over. */
    data class Wins(
        val abbrev: String,
        val wins: Int,
        val losses: Int,
    ) : SeriesStanding
}

/**
 * The playoff series line: from the score feed's `seriesStatus` when the game has one, otherwise from the right rail's
 * season series.
 *
 * The score feed's `seriesStatus` is the standing after the game, so it is worded as it stands. The right rail lists
 * games, not standings, so its standing is counted from the finished games up to and including this one, and its line
 * omits the round. A regular-season or preseason game has neither source, so it has no line.
 */
object PlayoffSeries {
    /** Every playoff round is best-of-seven, so the right rail's games need four wins to end a series. */
    const val NEEDED_TO_WIN = 4

    /** The right rail's game type for a playoff game. Regular-season head-to-head games are type 2 and never count. */
    private const val PLAYOFF_GAME_TYPE = 3

    /** The line for [game]: the score feed's series when it carries one, otherwise the right rail's season series. */
    fun forGame(
        game: Game,
        seasonSeries: List<SeasonSeriesGame>,
    ): SeriesLine? =
        game.series?.let { fromFeed(it) }
            ?: game.id.value
                .toLongOrNull()
                ?.let { fromSeasonSeries(it, seasonSeries) }

    /** The line from the score feed's series status; null when a field the line needs is missing. */
    fun fromFeed(series: SeriesStatus): SeriesLine? {
        val top = series.topSeedTeamAbbrev ?: return null
        val topWins = series.topSeedWins ?: return null
        val bottom = series.bottomSeedTeamAbbrev ?: return null
        val bottomWins = series.bottomSeedWins ?: return null
        val needed = series.neededToWin ?: return null
        return SeriesLine(
            round = series.seriesTitle,
            gameNumber = series.gameNumberOfSeries,
            standing = standing(top, topWins, bottom, bottomWins, needed),
        )
    }

    /**
     * The line from the right rail. Counts the finished playoff games up to and including [gameId] and words the standing
     * and the game number from that count. The round is omitted. Null when [gameId] is not a playoff game the rail lists,
     * or when its clubs are missing.
     */
    fun fromSeasonSeries(
        gameId: Long,
        seasonSeries: List<SeasonSeriesGame>,
    ): SeriesLine? {
        val current = seasonSeries.firstOrNull { it.gameId == gameId && it.gameType == PLAYOFF_GAME_TYPE } ?: return null
        val home = current.homeAbbrev ?: return null
        val away = current.awayAbbrev ?: return null
        val finished =
            seasonSeries.filter {
                it.gameType == PLAYOFF_GAME_TYPE && it.gameId <= gameId && it.state == GameState.Final
            }
        val wins = finished.mapNotNull { it.winnerAbbrev() }.groupingBy { it }.eachCount()
        // A game still in play is not in the count, so it is the game after the finished ones.
        val gameNumber = if (current.state == GameState.Final) finished.size else finished.size + 1
        return SeriesLine(
            round = null,
            gameNumber = gameNumber,
            standing = standing(home, wins[home] ?: 0, away, wins[away] ?: 0, NEEDED_TO_WIN),
        )
    }

    /** The club that won a finished game by score; null for a game without both scores, or a tie. */
    private fun SeasonSeriesGame.winnerAbbrev(): String? {
        val away = awayScore ?: return null
        val home = homeScore ?: return null
        return when {
            away > home -> awayAbbrev
            home > away -> homeAbbrev
            else -> null
        }
    }

    private fun standing(
        firstAbbrev: String,
        firstWins: Int,
        secondAbbrev: String,
        secondWins: Int,
        needed: Int,
    ): SeriesStanding =
        when {
            firstWins >= needed -> SeriesStanding.Wins(firstAbbrev, firstWins, secondWins)
            secondWins >= needed -> SeriesStanding.Wins(secondAbbrev, secondWins, firstWins)
            firstWins == secondWins -> SeriesStanding.Tied(firstWins)
            firstWins > secondWins -> SeriesStanding.Leads(firstAbbrev, firstWins, secondWins)
            else -> SeriesStanding.Leads(secondAbbrev, secondWins, firstWins)
        }
}
