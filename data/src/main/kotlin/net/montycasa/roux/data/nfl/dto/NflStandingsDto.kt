package net.montycasa.roux.data.nfl.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's NFL standings, in the shape every level of the feed uses.
 *
 * One payload holds a list of groups: two conferences at the default level, four divisions when the request names a
 * group. The DTO therefore describes the grouping rather than the level, so one adapter reads both.
 */
@Serializable
data class NflStandingsDto(
    val children: List<NflStandingsGroupDto>? = null,
    val season: NflSeasonDto? = null,
)

/** One table: a conference, or one of its divisions. */
@Serializable
data class NflStandingsGroupDto(
    val id: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val isConference: Boolean? = null,
    val standings: NflStandingsBlockDto? = null,
)

/** The rows of one table. */
@Serializable
data class NflStandingsBlockDto(
    val entries: List<NflStandingsEntryDto>? = null,
)

/** One club's line. */
@Serializable
data class NflStandingsEntryDto(
    val team: NflStandingsTeamDto? = null,
    val stats: List<NflStandingsStatDto>? = null,
)

/** The club, as the standings block states it. */
@Serializable
data class NflStandingsTeamDto(
    val id: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val name: String? = null,
    val logos: List<NflLogoDto>? = null,
)

/**
 * One statistic.
 *
 * The feed states some figures numerically and others only as a display string, and it does not do so consistently:
 * a record appears as `value: 0.0` with `displayValue: "1-1"`, while wins arrive as `value: 8.0`. Both fields are
 * therefore carried, and the adapter reads whichever one the figure is actually stated in.
 *
 * `name` is the key to read, but the feed's names include punctuation and spaces (`vs. Conf.`), and a handful of
 * entries carry an empty name with a usable `type`, so [type] is the fallback key.
 */
@Serializable
data class NflStandingsStatDto(
    val name: String? = null,
    val type: String? = null,
    val value: Double? = null,
    val displayValue: String? = null,
)
