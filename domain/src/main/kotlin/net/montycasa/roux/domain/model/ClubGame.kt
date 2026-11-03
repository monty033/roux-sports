package net.montycasa.roux.domain.model

import kotlin.time.Instant

/**
 * One fixture in a club's season schedule, as the schedule endpoint describes it.
 *
 * The club-schedule-season payload carries no goals or scoreline on any game, finished or not, so
 * there is deliberately no score field here. A nullable score that could never be filled in would
 * invite the UI to reserve space for it and imply the app had simply failed to read it.
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
) {
    val isFinished: Boolean get() = finished || state == GameState.Final
}
