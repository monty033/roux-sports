package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The club-schedule-season payload. Only the games array is consumed here; the surrounding season
 * metadata does not change what a club page shows.
 */
@Serializable
internal data class ClubScheduleDto(
    val games: List<JsonElement?>? = null,
)

/**
 * One schedule fixture. There is deliberately no score, goals or shots field: the endpoint does
 * not carry any, so mapping one would invent data.
 */
@Serializable
internal data class ClubScheduleGameDto(
    val id: Long? = null,
    val gameDate: String? = null,
    val startTimeUTC: String? = null,
    val gameState: String? = null,
    val gameScheduleState: String? = null,
    val venue: VenueDto? = null,
    val homeTeam: ScheduleTeamDto? = null,
    val awayTeam: ScheduleTeamDto? = null,
    @Serializable(with = GameOutcomeSerializer::class)
    val gameOutcome: GameOutcomeDto? = null,
)

@Serializable
internal data class VenueDto(
    val default: String? = null,
)

/** The schedule feed names a club with both its place and its common name. */
@Serializable
internal data class ScheduleTeamDto(
    val abbrev: String? = null,
    val commonName: NameDto? = null,
    val placeName: NameDto? = null,
    val logo: String? = null,
    val darkLogo: String? = null,
)
