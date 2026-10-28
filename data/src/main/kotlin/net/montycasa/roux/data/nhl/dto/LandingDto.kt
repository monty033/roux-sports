package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The landing endpoint, as it actually arrives.
 *
 * Only the fields the page needs are modelled; `ignoreUnknownKeys` covers the rest, which is most of it - a landing
 * payload carries venues, broadcasts, ticket links, replay URLs and a whole matchup section for games that have not
 * started.
 */
@Serializable
internal data class LandingDto(
    val id: Long? = null,
    val gameState: String? = null,
    val awayTeam: LandingTeamDto? = null,
    val homeTeam: LandingTeamDto? = null,
    val summary: LandingSummaryDto? = null,
)

@Serializable
internal data class LandingTeamDto(
    val abbrev: String? = null,
)

@Serializable
internal data class LandingSummaryDto(
    val scoring: List<LandingScoringPeriodDto?>? = null,
    val threeStars: List<LandingStarDto?>? = null,
)

@Serializable
internal data class LandingScoringPeriodDto(
    val periodDescriptor: LandingPeriodDescriptorDto? = null,
    val goals: List<LandingGoalDto?>? = null,
)

@Serializable
internal data class LandingPeriodDescriptorDto(
    val number: Int? = null,
    /** REG, OT or SO. Kept raw: the UI decides how to label a period, not the wire. */
    val periodType: String? = null,
)

@Serializable
internal data class LandingGoalDto(
    /**
     * Which side scored. This is the reliable field: [teamAbbrev] arrives as an object here
     * (`{"default":"CBJ"}`) while the same name is a plain string on the score feed and on a three star, so comparing it
     * as a string fails silently and attributes every goal to nobody.
     */
    val isHome: Boolean? = null,
    /** Declared as [JsonElement] because it is not a string in this payload; see [isHome]. */
    val teamAbbrev: JsonElement? = null,
)

@Serializable
internal data class LandingStarDto(
    val star: Int? = null,
    /** A plain string here, unlike a goal's `teamAbbrev`. */
    val teamAbbrev: String? = null,
    val name: LocalizedNameDto? = null,
    val position: String? = null,
)
