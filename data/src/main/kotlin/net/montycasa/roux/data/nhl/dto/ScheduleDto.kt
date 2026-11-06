package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * A weekly schedule response.
 *
 * Only the fields the opening-night walk reads are modelled: the days in the block and the date the
 * next block starts. Every other node is ignored like the rest of the undocumented feed.
 */
@Serializable
internal data class ScheduleDto(
    val nextStartDate: String? = null,
    val gameWeek: List<ScheduleDayDto?>? = null,
)

/** A day carries its games only to expose each game's type; the schedule itself is not rendered here. */
@Serializable
internal data class ScheduleDayDto(
    val date: String? = null,
    val games: List<ScheduleGameDto?>? = null,
)

/** The feed's game type: 1 preseason, 2 regular season, 3 playoffs. */
@Serializable
internal data class ScheduleGameDto(
    val gameType: Int? = null,
)
