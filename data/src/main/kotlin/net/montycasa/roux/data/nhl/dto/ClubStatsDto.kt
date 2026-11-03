package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * The club-stats payload.
 *
 * The two groups are the feed's own structure. Unlike the roster, the wire keys here are already
 * `skaters` and `goalies`, so there is no `defensemen` spelling to translate.
 */
@Serializable
internal data class ClubStatsDto(
    val skaters: List<ClubStatsSkaterDto?>? = null,
    val goalies: List<ClubStatsGoalieDto?>? = null,
)

/**
 * Only the fields the Stats tab can show are coupled to the undocumented wire shape.
 *
 * Every numeric field is nullable: a missing number must survive parsing rather than discard the
 * whole row, so the adapter supplies its own default at the mapping boundary.
 */
@Serializable
internal data class ClubStatsSkaterDto(
    val playerId: Long? = null,
    val headshot: String? = null,
    val firstName: LocalizedNameDto? = null,
    val lastName: LocalizedNameDto? = null,
    val positionCode: String? = null,
    val gamesPlayed: Int? = null,
    val goals: Int? = null,
    val assists: Int? = null,
    val points: Int? = null,
    val plusMinus: Int? = null,
    val penaltyMinutes: Int? = null,
    val shots: Int? = null,
    val shootingPctg: Double? = null,
    val powerPlayGoals: Int? = null,
    val shorthandedGoals: Int? = null,
    val gameWinningGoals: Int? = null,
    val overtimeGoals: Int? = null,
    val faceoffWinPctg: Double? = null,
    val avgTimeOnIcePerGame: Double? = null,
    val avgShiftsPerGame: Double? = null,
)

/** The goalie half of the payload. Goalies carry no position code because the group is the role. */
@Serializable
internal data class ClubStatsGoalieDto(
    val playerId: Long? = null,
    val headshot: String? = null,
    val firstName: LocalizedNameDto? = null,
    val lastName: LocalizedNameDto? = null,
    val gamesPlayed: Int? = null,
    val gamesStarted: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val overtimeLosses: Int? = null,
    val goalsAgainst: Int? = null,
    val goalsAgainstAverage: Double? = null,
    val shotsAgainst: Int? = null,
    val saves: Int? = null,
    val savePercentage: Double? = null,
    val shutouts: Int? = null,
    val timeOnIce: Long? = null,
    val assists: Int? = null,
    val goals: Int? = null,
    val points: Int? = null,
    val penaltyMinutes: Int? = null,
)
