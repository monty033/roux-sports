package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * The current-roster payload.
 *
 * The three group keys are the feed's own grouping authority. The defence spelling here is
 * `defensemen`, not the boxscore's `defense`.
 */
@Serializable
internal data class RosterDto(
    val forwards: List<RosterPlayerDto?>? = null,
    val defensemen: List<RosterPlayerDto?>? = null,
    val goalies: List<RosterPlayerDto?>? = null,
)

/** Only the fields the Roster tab shows are coupled to the undocumented wire shape. */
@Serializable
internal data class RosterPlayerDto(
    val id: Long? = null,
    val headshot: String? = null,
    val firstName: LocalizedNameDto? = null,
    val lastName: LocalizedNameDto? = null,
    val sweaterNumber: Int? = null,
    val positionCode: String? = null,
)
