package net.montycasa.roux.data.nfl.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's NFL athlete profile: `/apis/common/v3/sports/football/nfl/athletes/{id}`.
 *
 * This is the common v3 path, not `/apis/site/v2/.../athletes/{id}`: the latter answers 404. The `athlete` object holds
 * the person and their `statsSummary`; the top-level `season` describes the season the summary covers.
 */
@Serializable
data class NflPlayerDto(
    val athlete: NflPlayerAthleteDto? = null,
    val season: NflPlayerSeasonDto? = null,
)

/** The athlete, as the profile feed states them. */
@Serializable
data class NflPlayerAthleteDto(
    val id: String? = null,
    val displayName: String? = null,
    val fullName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    /** The feed's display jersey (`#16`), not the bare `jersey` field. */
    val displayJersey: String? = null,
    val jersey: String? = null,
    val position: NflRosterPositionDto? = null,
    val headshot: NflPlayerHeadshotDto? = null,
    val displayHeight: String? = null,
    val displayWeight: String? = null,
    val age: Int? = null,
    val college: NflCollegeDto? = null,
    val displayExperience: String? = null,
    val team: NflTeamsTeamDto? = null,
    val statsSummary: NflPlayerStatsSummaryDto? = null,
)

@Serializable
data class NflPlayerHeadshotDto(
    val href: String? = null,
    val alt: String? = null,
)

@Serializable
data class NflCollegeDto(
    val id: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val abbrev: String? = null,
)

/** The season summary heading and its statistics, in the feed's order. */
@Serializable
data class NflPlayerStatsSummaryDto(
    val displayName: String? = null,
    val statistics: List<NflPlayerStatDto>? = null,
)

/**
 * One summary statistic.
 *
 * The captured payload states `name`, `displayName` and `displayValue` directly. [stats] is modelled as well because
 * the feed has also been documented with a nested list; when it is present it is the statistics, and when it is absent
 * the entry itself is one. A missing `displayValue` is absence, not zero.
 */
@Serializable
data class NflPlayerStatDto(
    val name: String? = null,
    val displayName: String? = null,
    val displayValue: String? = null,
    val value: Double? = null,
    val stats: List<NflPlayerStatDto>? = null,
)

/** The season block, whose `year` and `displayName` are all this page reads. */
@Serializable
data class NflPlayerSeasonDto(
    val year: Int? = null,
    val displayName: String? = null,
    val type: NflPlayerSeasonTypeDto? = null,
)

@Serializable
data class NflPlayerSeasonTypeDto(
    val name: String? = null,
    val displayName: String? = null,
    val abbreviation: String? = null,
)

/**
 * ESPN's NFL athlete game log: `/apis/common/v3/sports/football/nfl/athletes/{id}/gamelog`.
 *
 * `names`, `displayNames` and `labels` are parallel arrays; `categories` partitions the `names` columns by `count`, and
 * `seasonTypes[0].categories[*].events[*].stats` is aligned to `names` by index. The top-level `events` map carries the
 * date, opponent and result for each `eventId`. Nothing here is position-specific: a quarterback and a kicker arrive
 * through the same fields with different column lists.
 */
@Serializable
data class NflGameLogDto(
    val names: List<String?>? = null,
    val displayNames: List<String?>? = null,
    val labels: List<String?>? = null,
    val categories: List<NflGameLogCategoryDto>? = null,
    val seasonTypes: List<NflGameLogSeasonTypeDto>? = null,
    val events: Map<String, NflGameLogEventDto>? = null,
)

/** One category and how many of the parallel column arrays it covers. */
@Serializable
data class NflGameLogCategoryDto(
    val name: String? = null,
    val displayName: String? = null,
    val count: Int? = null,
)

@Serializable
data class NflGameLogSeasonTypeDto(
    val displayName: String? = null,
    val categories: List<NflGameLogSeasonCategoryDto>? = null,
)

/** A season-type category; only its `event` rows are game lines. */
@Serializable
data class NflGameLogSeasonCategoryDto(
    val displayName: String? = null,
    val type: String? = null,
    val events: List<NflGameLogStatsDto>? = null,
    val totals: List<String?>? = null,
)

/** One game's stats, aligned to the payload's `names` array. */
@Serializable
data class NflGameLogStatsDto(
    val eventId: String? = null,
    val stats: List<String?>? = null,
)

/** The top-level `events` entry: the metadata a stats row does not carry. */
@Serializable
data class NflGameLogEventDto(
    val id: String? = null,
    val week: Int? = null,
    val gameDate: String? = null,
    val atVs: String? = null,
    val opponent: NflGameLogClubDto? = null,
    val team: NflGameLogClubDto? = null,
    val score: String? = null,
    val gameResult: String? = null,
)

@Serializable
data class NflGameLogClubDto(
    val id: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null,
)
