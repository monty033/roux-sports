package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The right-rail endpoint, as it actually arrives.
 *
 * Two shapes matter here and they are not the same: a started game carries `linescore`, `shotsByPeriod` and a populated
 * `teamGameStats`, while a game that has not started carries none of those and ships `teamSeasonStats` and `last10Record`
 * instead. So every node below is optional for a reason.
 */
@Serializable
internal data class RightRailDto(
    val linescore: LinescoreDto? = null,
    val shotsByPeriod: List<PeriodShotsDto?>? = null,
    val teamGameStats: List<TeamStatRowDto?>? = null,
)

@Serializable
internal data class LinescoreDto(
    val byPeriod: List<LinescorePeriodDto?>? = null,
    val totals: LinescoreTotalsDto? = null,
)

@Serializable
internal data class LinescorePeriodDto(
    val periodDescriptor: PeriodDescriptorDto? = null,
    val away: Int? = null,
    val home: Int? = null,
)

@Serializable
internal data class LinescoreTotalsDto(
    val away: Int? = null,
    val home: Int? = null,
)

@Serializable
internal data class PeriodShotsDto(
    val periodDescriptor: PeriodDescriptorDto? = null,
    val away: Int? = null,
    val home: Int? = null,
)

/**
 * One row of the comparison table, e.g. `{"category": "powerPlay", "awayValue": "0/1", "homeValue": "0/2"}`.
 *
 * The values are [JsonElement] rather than a number because the same field carries integers, decimals and strings like
 * `"0/1"` and `"28/50"` depending on the category.
 */
@Serializable
internal data class TeamStatRowDto(
    val category: String? = null,
    val awayValue: JsonElement? = null,
    val homeValue: JsonElement? = null,
)
