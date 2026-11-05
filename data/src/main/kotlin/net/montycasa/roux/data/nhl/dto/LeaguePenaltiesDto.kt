package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * The league penalties payload: one row per club under `data`, with a `total` the adapter ignores.
 *
 * As with the summary, a missing array is a failed read and a null row is one the feed did not fill in.
 */
@Serializable
internal data class LeaguePenaltiesDto(
    val data: List<LeaguePenaltyRowDto?>? = null,
)

/**
 * Only the fields the join needs are coupled to the wire shape: the club's id and name, and its penalty minutes.
 *
 * Every field is nullable so a missing number survives parsing as null rather than dropping the club.
 */
@Serializable
internal data class LeaguePenaltyRowDto(
    val teamId: Int? = null,
    val teamFullName: String? = null,
    val penaltyMinutes: Int? = null,
)
