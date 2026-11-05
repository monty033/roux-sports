package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * The league team-summary payload: one row per club under `data`, with a `total` the adapter ignores.
 *
 * The array is nullable and its rows are too: a missing array is a failed read, and a null row is one
 * the feed did not fill in, which the adapter drops.
 */
@Serializable
internal data class LeagueStatsDto(
    val data: List<LeagueStatsRowDto?>? = null,
)

/**
 * Only the fields the Team Stats tab can show are coupled to the undocumented wire shape.
 *
 * Every field is nullable, including the club's name's neighbours: a missing number must survive
 * parsing as null rather than dropping the whole club, and the adapter does not supply zeros.
 * `ties` is always null in the feed today; it is named so a null tie is asserted rather than assumed.
 */
@Serializable
internal data class LeagueStatsRowDto(
    val teamFullName: String? = null,
    val teamId: Int? = null,
    val gamesPlayed: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val otLosses: Int? = null,
    val ties: Int? = null,
    val points: Int? = null,
    val pointPct: Double? = null,
    val goalsForPerGame: Double? = null,
    val goalsAgainstPerGame: Double? = null,
    val shotsForPerGame: Double? = null,
    val shotsAgainstPerGame: Double? = null,
    val faceoffWinPct: Double? = null,
    val powerPlayPct: Double? = null,
    val penaltyKillPct: Double? = null,
    val teamShutouts: Int? = null,
)
