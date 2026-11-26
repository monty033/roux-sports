package net.montycasa.roux.data.nfl.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's NFL club roster: `/apis/site/v2/sports/football/nfl/teams/{abbr}?enable=roster`.
 *
 * Not `/teams/{abbr}/roster`: that endpoint answers 200 with a different payload (a `coach`, a season block and a
 * grouped athlete list of six entries), so reading it here would produce a roster a tenth of the size.
 *
 * `team.athletes` is the roster itself. `team.groups` is a single team-level group (`id`, `parent`, `isConference`)
 * and states no membership, so grouping rows by position can only come from each athlete's own `position` object.
 */
@Serializable
data class NflRosterDto(
    val team: NflRosterTeamDto? = null,
)

/** The club whose roster this is. */
@Serializable
data class NflRosterTeamDto(
    val id: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null,
    val name: String? = null,
    val location: String? = null,
    val shortDisplayName: String? = null,
    val nickname: String? = null,
    val logos: List<NflLogoDto>? = null,
    val athletes: List<NflRosterAthleteDto>? = null,
)

/** One player, as the roster feed states them. Every listed field is present in the captures except jersey and status. */
@Serializable
data class NflRosterAthleteDto(
    val id: String? = null,
    val uid: String? = null,
    val guid: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val fullName: String? = null,
    val displayName: String? = null,
    val shortName: String? = null,
    val slug: String? = null,
    /** The feed's own text; absent on two of Buffalo's seventy-two and six of Philadelphia's seventy-six. */
    val jersey: String? = null,
    /** Present on every captured player, always with the four fields below. */
    val position: NflRosterPositionDto? = null,
    /** Absent on twelve of Buffalo's players. A missing object says nothing about availability. */
    val status: NflRosterStatusDto? = null,
    val active: Boolean? = null,
    val age: Int? = null,
    val displayHeight: String? = null,
    val displayWeight: String? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val headshot: String? = null,
    val linked: Boolean? = null,
)

@Serializable
data class NflRosterPositionDto(
    val abbreviation: String? = null,
    val displayName: String? = null,
    val id: String? = null,
    val leaf: Boolean? = null,
    val name: String? = null,
)

/** The availability object. `type` is the only discriminator the feed offers (`active`, `day-to-day`, `news`, …). */
@Serializable
data class NflRosterStatusDto(
    val type: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val id: String? = null,
)
