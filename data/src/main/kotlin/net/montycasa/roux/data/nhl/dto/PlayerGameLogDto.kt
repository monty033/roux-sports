package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * The game-log payload for one player, season and game type. The rows are optional on the wire: a player with no
 * games that season has no `gameLog` key at all, which is an empty log rather than a broken one.
 */
@Serializable
internal data class PlayerGameLogDto(
    val gameLog: List<PlayerGameRowDto?>? = null,
)

/**
 * One game row. Skater and goalie rows share the id, date, team, opponent and home flag; the rest is one superset, read
 * by position. Every field is nullable so one odd row cannot take the whole log down with it.
 */
@Serializable
internal data class PlayerGameRowDto(
    val gameId: Long? = null,
    val gameDate: String? = null,
    val teamAbbrev: String? = null,
    val homeRoadFlag: String? = null,
    val opponentAbbrev: String? = null,
    val toi: String? = null,
    // Skater stats.
    val goals: Int? = null,
    val assists: Int? = null,
    val points: Int? = null,
    val plusMinus: Int? = null,
    val shots: Int? = null,
    // Goalie stats.
    val decision: String? = null,
    val gamesStarted: Int? = null,
    val shotsAgainst: Int? = null,
    val goalsAgainst: Int? = null,
    val savePctg: Double? = null,
    val shutouts: Int? = null,
)
