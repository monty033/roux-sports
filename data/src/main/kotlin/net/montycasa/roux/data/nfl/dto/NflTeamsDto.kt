package net.montycasa.roux.data.nfl.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's NFL teams payload: `sports[0].leagues[0].teams[].team`.
 *
 * The feed nests the club list under a one-element `sports` and a one-element `leagues`, and wraps each club in a
 * `team` object. It states no division - that is why the grouping is composed from the standings payloads instead.
 */
@Serializable
data class NflTeamsDto(
    val sports: List<NflTeamsSportDto>? = null,
)

@Serializable
data class NflTeamsSportDto(
    val leagues: List<NflTeamsLeagueDto>? = null,
)

@Serializable
data class NflTeamsLeagueDto(
    val teams: List<NflTeamsEntryDto>? = null,
)

@Serializable
data class NflTeamsEntryDto(
    val team: NflTeamsTeamDto? = null,
)

/** The club, as the teams payload states it. */
@Serializable
data class NflTeamsTeamDto(
    val abbreviation: String? = null,
    val displayName: String? = null,
    val nickname: String? = null,
    val name: String? = null,
    val location: String? = null,
    val logos: List<NflLogoDto>? = null,
)
