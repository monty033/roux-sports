package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The player landing payload.
 *
 * Every field is nullable: a missing field must survive parsing so the adapter can report it as absence rather than
 * discard the whole profile. Only the fields the profile page can show are coupled to the wire shape.
 */
@Serializable
internal data class PlayerLandingDto(
    val playerId: Long? = null,
    val firstName: LocalizedNameDto? = null,
    val lastName: LocalizedNameDto? = null,
    val position: String? = null,
    val sweaterNumber: Int? = null,
    val headshot: String? = null,
    val heightInInches: Int? = null,
    val weightInPounds: Int? = null,
    val birthDate: String? = null,
    val birthCity: LocalizedNameDto? = null,
    val birthCountry: String? = null,
    @SerialName("shootsCatches") val shoots: String? = null,
    val draftDetails: PlayerDraftDto? = null,
    val careerTotals: PlayerCareerTotalsDto? = null,
    val seasonTotals: List<PlayerStatsDto?>? = null,
)

@Serializable
internal data class PlayerDraftDto(
    val year: Int? = null,
    val teamAbbrev: String? = null,
    val round: Int? = null,
    val pickInRound: Int? = null,
    val overallPick: Int? = null,
)

@Serializable
internal data class PlayerCareerTotalsDto(
    val regularSeason: PlayerStatsDto? = null,
    val playoffs: PlayerStatsDto? = null,
)

/**
 * One totals object, shared by career lines and season rows. The season header is present only on season rows, and
 * the stat keys are the union of the skater and goalie sets; the adapter reads only the keys its position owns.
 *
 * One class rather than one per shape: the wire keys do not collide, and a single decoder cannot drift from another.
 */
@Serializable
internal data class PlayerStatsDto(
    // Season header.
    val season: Int? = null,
    val gameTypeId: Int? = null,
    val leagueAbbrev: String? = null,
    val teamName: LocalizedNameDto? = null,
    val sequence: Int? = null,
    // Skater stats.
    val gamesPlayed: Int? = null,
    val goals: Int? = null,
    val assists: Int? = null,
    val points: Int? = null,
    val plusMinus: Int? = null,
    val pim: Int? = null,
    val powerPlayGoals: Int? = null,
    val powerPlayPoints: Int? = null,
    val shorthandedGoals: Int? = null,
    val shorthandedPoints: Int? = null,
    val gameWinningGoals: Int? = null,
    val otGoals: Int? = null,
    val shots: Int? = null,
    val shootingPctg: Double? = null,
    val faceoffWinningPctg: Double? = null,
    val avgToi: String? = null,
    // Goalie stats.
    val gamesStarted: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val otLosses: Int? = null,
    val goalsAgainst: Int? = null,
    val goalsAgainstAvg: Double? = null,
    val shotsAgainst: Int? = null,
    val savePctg: Double? = null,
    val shutouts: Int? = null,
    val timeOnIce: String? = null,
)
