package net.montycasa.roux.domain.model

import kotlin.time.Instant

/**
 * One fixture in a club's season schedule, as the schedule endpoint describes it.
 *
 * The schedule payload carries goals on the team objects (`homeTeam.score` and `awayTeam.score`) for
 * finished games, but not on the game object. Future games carry neither score, so both fields are
 * nullable and are never defaulted to zero.
 *
 * [finished] is separate from [state] because the feed can say both `FINAL` and `OFF` for a completed
 * game. The schedule endpoint also carries [gameScheduleState], which can mark a game postponed or
 * suspended while its ordinary [state] still looks scheduled.
 */
data class ClubGame(
    val id: GameId,
    /** The feed's ISO `YYYY-MM-DD` game date, kept for the day label and for tests of same-day ordering. */
    val date: String,
    val startTimeUtc: Instant? = null,
    val venue: String? = null,
    val home: TeamRef,
    val away: TeamRef,
    val state: GameState,
    /** True when the feed marks this game complete, including `OFF` as well as `FINAL`. */
    val finished: Boolean = state == GameState.Final,
    /** The raw `gameScheduleState` token, retained so a postponement can be shown in words. */
    val gameScheduleState: String? = null,
    /** The home club's goals; null on a fixture the feed has not completed. */
    val homeScore: Int? = null,
    /** The away club's goals; null on a fixture the feed has not completed. */
    val awayScore: Int? = null,
) {
    val isFinished: Boolean get() = finished || state == GameState.Final
}
