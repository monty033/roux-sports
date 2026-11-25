package net.montycasa.roux.data.nfl.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's NFL club schedule: `/apis/site/v2/sports/football/nfl/teams/{abbr}/schedule`.
 *
 * The payload states the bye twice over - as a top-level `byeWeek`, and as a gap in `week.number` across seventeen
 * events - and the two agree in every capture. The top-level field is the one read here, because it is the feed saying
 * so rather than the app counting.
 */
@Serializable
data class NflScheduleDto(
    val team: NflScheduleTeamDto? = null,
    val season: NflScheduleSeasonDto? = null,
    val byeWeek: Int? = null,
    val events: List<NflScheduleEventDto>? = null,
)

/** The club whose schedule this is. */
@Serializable
data class NflScheduleTeamDto(
    val id: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null,
    val name: String? = null,
    val location: String? = null,
    val logo: String? = null,
    val logoDark: String? = null,
)

@Serializable
data class NflScheduleSeasonDto(
    val year: Int? = null,
    val type: Int? = null,
    val name: String? = null,
)

@Serializable
data class NflScheduleEventDto(
    val id: String? = null,
    val date: String? = null,
    val season: NflScheduleSeasonDto? = null,
    val week: NflScheduleWeekDto? = null,
    val seasonType: NflScheduleSeasonTypeDto? = null,
    val competitions: List<NflScheduleCompetitionDto>? = null,
)

@Serializable
data class NflScheduleWeekDto(
    val number: Int? = null,
    val text: String? = null,
)

@Serializable
data class NflScheduleSeasonTypeDto(
    val type: Int? = null,
    val name: String? = null,
    val abbreviation: String? = null,
)

@Serializable
data class NflScheduleCompetitionDto(
    val status: NflScheduleStatusDto? = null,
    val competitors: List<NflScheduleCompetitorDto>? = null,
    val venue: NflScheduleVenueDto? = null,
)

@Serializable
data class NflScheduleStatusDto(
    val type: NflScheduleStatusTypeDto? = null,
)

@Serializable
data class NflScheduleStatusTypeDto(
    /** The only discriminator the feed offers: `STATUS_FINAL`, `STATUS_SCHEDULED`, `STATUS_IN_PROGRESS`, … */
    val name: String? = null,
    val state: String? = null,
    val completed: Boolean? = null,
    val detail: String? = null,
)

@Serializable
data class NflScheduleCompetitorDto(
    val homeAway: String? = null,
    val team: NflScheduleTeamDto? = null,
    val score: NflScheduleScoreDto? = null,
    val winner: Boolean? = null,
)

@Serializable
data class NflScheduleScoreDto(
    val value: Double? = null,
    val displayValue: String? = null,
)

@Serializable
data class NflScheduleVenueDto(
    val fullName: String? = null,
)
