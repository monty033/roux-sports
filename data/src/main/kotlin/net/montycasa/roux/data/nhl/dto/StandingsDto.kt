package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/** Nullable wire fields leave completeness decisions at the adapter boundary. */
@Serializable
internal data class StandingsDto(
    val standings: List<StandingRowDto?>? = null,
)

/** Only identity, display assets, totals and table placement are consumed here. */
@Serializable
internal data class StandingRowDto(
    val teamAbbrev: NameDto? = null,
    val teamName: NameDto? = null,
    val teamLogo: String? = null,
    val teamLogoDark: String? = null,
    val gamesPlayed: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val otLosses: Int? = null,
    val points: Int? = null,
    val leagueSequence: Int? = null,
    val conferenceSequence: Int? = null,
    val divisionSequence: Int? = null,
    val wildcardSequence: Int? = null,
    val conferenceName: String? = null,
    val divisionName: String? = null,
)

/** Season history needs no obsolete rule flags. */
@Serializable
internal data class SeasonsDto(
    val seasons: List<SeasonDto?>? = null,
)

/** Standings dates can be absent independently of the season identifier. */
@Serializable
internal data class SeasonDto(
    val id: Int? = null,
    val standingsStart: String? = null,
    val standingsEnd: String? = null,
)
