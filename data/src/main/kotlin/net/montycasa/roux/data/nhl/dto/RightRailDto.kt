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
    val seasonSeries: List<SeasonSeriesGameDto?>? = null,
    /** A game that has not started ships its form and ranked season statistics instead of a linescore. */
    val last10Record: Last10RecordDto? = null,
    val teamSeasonStats: TeamSeasonStatsDto? = null,
)

@Serializable
internal data class Last10RecordDto(
    val awayTeam: Last10TeamDto? = null,
    val homeTeam: Last10TeamDto? = null,
)

@Serializable
internal data class Last10TeamDto(
    /** The last ten games' record, `5-4-1`. */
    val record: String? = null,
    val streakType: String? = null,
    val streak: Int? = null,
    /** The ten results in the order the feed lists them, which is the order the card prints them in. */
    val pastGameResults: List<PastGameResultDto?>? = null,
)

@Serializable
internal data class PastGameResultDto(
    val opponentAbbrev: String? = null,
    /** `W`, `L`, `OTL` or `SOL`, kept as the feed sends it. */
    val gameResult: String? = null,
)

@Serializable
internal data class TeamSeasonStatsDto(
    val awayTeam: SeasonStatsTeamDto? = null,
    val homeTeam: SeasonStatsTeamDto? = null,
)

@Serializable
internal data class SeasonStatsTeamDto(
    val ppPctg: Double? = null,
    val pkPctg: Double? = null,
    val faceoffWinningPctg: Double? = null,
    val goalsForPerGamePlayed: Double? = null,
    val goalsAgainstPerGamePlayed: Double? = null,
    val ppPctgRank: Int? = null,
    val pkPctgRank: Int? = null,
    val faceoffWinningPctgRank: Int? = null,
    val goalsForPerGamePlayedRank: Int? = null,
    val goalsAgainstPerGamePlayedRank: Int? = null,
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

/**
 * One game of the season series: the head-to-head games the rail lists for this matchup. A game that has not started
 * carries no scores, so each score stays optional.
 */
@Serializable
internal data class SeasonSeriesGameDto(
    val id: Long? = null,
    val gameType: Int? = null,
    /** ISO `YYYY-MM-DD`, as the feed sends it. */
    val gameDate: String? = null,
    val gameState: String? = null,
    val awayTeam: SeasonSeriesTeamDto? = null,
    val homeTeam: SeasonSeriesTeamDto? = null,
)

@Serializable
internal data class SeasonSeriesTeamDto(
    val abbrev: String? = null,
    val score: Int? = null,
)
