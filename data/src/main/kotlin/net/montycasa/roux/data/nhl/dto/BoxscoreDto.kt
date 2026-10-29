package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * The boxscore endpoint, as it actually arrives.
 *
 * A game that has not started returns this object **without** `playerByGameStats` at all - not with an empty goalie
 * list - so the absence of that node is an ordinary pre-game state and maps to an empty boxscore rather than a failure.
 */
@Serializable
internal data class BoxscoreDto(
    val id: Long? = null,
    val awayTeam: BoxscoreTeamDto? = null,
    val homeTeam: BoxscoreTeamDto? = null,
    val playerByGameStats: BoxscorePlayersDto? = null,
)

@Serializable
internal data class BoxscoreTeamDto(
    val abbrev: String? = null,
)

@Serializable
internal data class BoxscorePlayersDto(
    val awayTeam: BoxscoreTeamPlayersDto? = null,
    val homeTeam: BoxscoreTeamPlayersDto? = null,
)

@Serializable
internal data class BoxscoreTeamPlayersDto(
    val goalies: List<BoxscoreGoalieDto?>? = null,
    /**
     * Skaters arrive as two lists, not one: `forwards` then `defense`. Both are needed - a page that reads only
     * `forwards` loses every defenceman, and none of the other keys in the payload hint that they exist.
     */
    val forwards: List<BoxscoreSkaterDto?>? = null,
    val defense: List<BoxscoreSkaterDto?>? = null,
)

@Serializable
internal data class BoxscoreSkaterDto(
    val playerId: Long? = null,
    val name: LocalizedNameDto? = null,
    val position: String? = null,
    val goals: Int? = null,
    val assists: Int? = null,
    val points: Int? = null,
    val plusMinus: Int? = null,
    val sog: Int? = null,
    val pim: Int? = null,
    /** "19:36". A skater always has some, so no fallback is needed here. */
    val toi: String? = null,
    /**
     * A fraction 0..1, not a percentage, and absent for skaters who never took a draw. The payload also sends 0.0 for
     * some players, which is a real "lost every draw they took" - kept as-is, because null and 0 mean different things.
     */
    val faceoffWinningPctg: Double? = null,
)

@Serializable
internal data class BoxscoreGoalieDto(
    val playerId: Long? = null,
    val name: LocalizedNameDto? = null,
    val saves: Int? = null,
    val shotsAgainst: Int? = null,
    val goalsAgainst: Int? = null,
    /** "62:22". Also the field that separates a goalie who played from a backup listed at 00:00. */
    val toi: String? = null,
    /** W, L or O once the game is over; null before it starts and for a backup who never played. */
    val decision: String? = null,
    /** Flagged on the goalie who started. Absent on some payloads, which is why [toi] is the fallback. */
    val starter: Boolean? = null,
)
