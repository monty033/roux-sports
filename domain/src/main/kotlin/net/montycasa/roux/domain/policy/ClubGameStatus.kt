package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import kotlin.time.Instant

/**
 * What a schedule row's right-hand value should be, decided without any presentation types.
 *
 * A sealed type rather than a string keeps the UI exhaustive: a finished game cannot fall through a
 * default branch and accidentally print its start time instead of its result.
 */
sealed interface ClubGameStatus {
    /** A game that has not started, with the instant the row should format in the device's zone. */
    data class Upcoming(
        val start: Instant,
    ) : ClubGameStatus

    data object Live : ClubGameStatus

    /** A completed game with both scores, expressed from the chosen club's side. */
    data class Result(
        val won: Boolean,
        val clubScore: Int,
        val opponentScore: Int,
    ) : ClubGameStatus

    /** A completed game whose score the payload omitted; the row says Final rather than inventing 0-0. */
    data object Final : ClubGameStatus

    data object Postponed : ClubGameStatus

    data object Suspended : ClubGameStatus

    data object Pregame : ClubGameStatus

    /** The feed omitted a start time and gave no state word this policy knows. */
    data class Other(
        val raw: String,
    ) : ClubGameStatus
}

/**
 * Decides the row's right-hand value for [game] from [club]'s point of view.
 *
 * `now` is deliberately an input: a game with a start time in the future reads as a time, while a
 * game whose start has passed without the feed saying it is live reads as pregame rather than as a
 * time that is already stale. Postponed and suspended tokens win over every clock-based case.
 */
object ClubGameStatusPolicy {
    fun status(
        game: ClubGame,
        club: TeamKey,
        now: Instant,
    ): ClubGameStatus {
        val scheduleState = game.gameScheduleState?.trim()?.uppercase()
        val feedState = (game.state as? GameState.Unknown)?.raw?.trim()?.uppercase()
        if (scheduleState in POSTPONED || feedState in POSTPONED) return ClubGameStatus.Postponed
        if (scheduleState in SUSPENDED || feedState in SUSPENDED) return ClubGameStatus.Suspended

        if (game.isFinished) return finishedStatus(game, club)

        return when {
            game.state is GameState.Live -> ClubGameStatus.Live
            game.state is GameState.Pre -> ClubGameStatus.Pregame
            game.startTimeUtc != null && game.startTimeUtc > now -> ClubGameStatus.Upcoming(game.startTimeUtc)
            game.startTimeUtc != null -> ClubGameStatus.Pregame
            feedState != null && feedState.isNotBlank() -> ClubGameStatus.Other(game.state.raw.trim())
            else -> ClubGameStatus.Other("")
        }
    }

    /** Scoreboard order is the clubs', not the venue's: the club's own score always prints first. */
    private fun finishedStatus(
        game: ClubGame,
        club: TeamKey,
    ): ClubGameStatus {
        val clubAtHome = game.home.key == club
        val clubScore = if (clubAtHome) game.homeScore else game.awayScore
        val opponentScore = if (clubAtHome) game.awayScore else game.homeScore
        if (clubScore == null || opponentScore == null) return ClubGameStatus.Final
        return ClubGameStatus.Result(
            won = clubScore > opponentScore,
            clubScore = clubScore,
            opponentScore = opponentScore,
        )
    }

    private val POSTPONED = setOf("PPD", "POSTPONED")
    private val SUSPENDED = setOf("SUSP", "SUSPENDED")
}
