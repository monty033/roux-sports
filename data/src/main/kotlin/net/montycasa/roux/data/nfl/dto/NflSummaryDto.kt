package net.montycasa.roux.data.nfl.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's NFL game summary. Only the fields this stage reads are described; `ignoreUnknownKeys` lets the rest pass
 * through, including the payloads' large drives, odds, injuries and win-probability blocks.
 */
@Serializable
data class NflSummaryDto(
    val header: NflSummaryHeaderDto? = null,
    val scoringPlays: List<NflScoringPlayDto>? = null,
    val boxscore: NflBoxscoreDto? = null,
    val leaders: List<NflLeadersTeamDto>? = null,
)

@Serializable
data class NflSummaryHeaderDto(
    val competitions: List<NflSummaryCompetitionDto>? = null,
)

/** The summary competition carries the date and status at this level, unlike a scoreboard event's competition. */
@Serializable
data class NflSummaryCompetitionDto(
    val date: String? = null,
    val status: NflStatusDto? = null,
    val competitors: List<NflCompetitorDto>? = null,
)

/**
 * One scoring play. `period` is an object (`{ "number": 1 }`), not a number, which is why it gets its own type.
 * Scores here are JSON numbers, unlike the competitor's string score.
 */
@Serializable
data class NflScoringPlayDto(
    val id: String? = null,
    val text: String? = null,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val period: NflScoringPeriodDto? = null,
    val clock: NflScoringClockDto? = null,
    val team: NflTeamDto? = null,
)

@Serializable
data class NflScoringPeriodDto(
    val number: Int? = null,
)

@Serializable
data class NflScoringClockDto(
    val displayValue: String? = null,
)

@Serializable
data class NflBoxscoreDto(
    val teams: List<NflBoxscoreTeamDto>? = null,
)

@Serializable
data class NflBoxscoreTeamDto(
    val team: NflTeamDto? = null,
    val homeAway: String? = null,
    val statistics: List<NflBoxscoreStatDto>? = null,
)

/**
 * One team statistic. `value` is deliberately not modelled: the feed mixes numbers and `"-"` in that field, and this
 * page prints `displayValue` rather than deriving anything from the numeric form.
 */
@Serializable
data class NflBoxscoreStatDto(
    val name: String? = null,
    val displayValue: String? = null,
    val label: String? = null,
)

@Serializable
data class NflLeadersTeamDto(
    val team: NflTeamDto? = null,
    val leaders: List<NflLeaderCategoryDto>? = null,
)

@Serializable
data class NflLeaderCategoryDto(
    val name: String? = null,
    val displayName: String? = null,
    val leaders: List<NflLeaderDto>? = null,
)

@Serializable
data class NflLeaderDto(
    val displayValue: String? = null,
    val athlete: NflLeaderAthleteDto? = null,
)

@Serializable
data class NflLeaderAthleteDto(
    val id: String? = null,
    val displayName: String? = null,
    val headshot: NflLeaderHeadshotDto? = null,
)

@Serializable
data class NflLeaderHeadshotDto(
    val href: String? = null,
)
