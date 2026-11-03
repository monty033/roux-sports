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
    val periodDescriptor: LandingPeriodDescriptorDto? = null,
    val clock: LandingClockDto? = null,
    val awayTeam: LandingTeamDto? = null,
    val homeTeam: LandingTeamDto? = null,
    val summary: LandingSummaryDto? = null,
    val venue: LocalizedNameDto? = null,
    val venueLocation: LocalizedNameDto? = null,
    val tvBroadcasts: List<LandingBroadcastDto?>? = null,
)

@Serializable
internal data class LandingTeamDto(
    val abbrev: String? = null,
)

@Serializable
internal data class LandingSummaryDto(
    val scoring: List<LandingScoringPeriodDto?>? = null,
    val threeStars: List<LandingStarDto?>? = null,
    val penalties: List<LandingPenaltyPeriodDto?>? = null,
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
    val playerId: Long? = null,
    val name: LocalizedNameDto? = null,
    val firstName: LocalizedNameDto? = null,
    val lastName: LocalizedNameDto? = null,
    /** As the feed writes it: `"10:58"`. */
    val timeInPeriod: String? = null,
    /** `ev`, `pp` or `sh`; kept raw so an unfamiliar token cannot lose the goal. */
    val strength: String? = null,
    /** `none`, empty-net and penalty-shot variants; kept raw for the same reason as [strength]. */
    val goalModifier: String? = null,
    val goalsToDate: Int? = null,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val assists: List<LandingGoalAssistDto?>? = null,
)

@Serializable
internal data class LandingGoalAssistDto(
    val name: LocalizedNameDto? = null,
    val firstName: LocalizedNameDto? = null,
    val lastName: LocalizedNameDto? = null,
    val assistsToDate: Int? = null,
    val playerId: Long? = null,
    val sweaterNumber: Int? = null,
)

@Serializable
internal data class LandingPenaltyPeriodDto(
    val periodDescriptor: LandingPeriodDescriptorDto? = null,
    val penalties: List<LandingPenaltyDto?>? = null,
)

@Serializable
internal data class LandingPenaltyDto(
    val timeInPeriod: String? = null,
    /** `MIN` or `MAJ`; kept raw, but the card prints the duration rather than this token. */
    val type: String? = null,
    val duration: Int? = null,
    val committedByPlayer: LandingPenaltyPlayerDto? = null,
    /** An object here (`{"default":"PHI"}`), like a goal's abbreviation rather than a star's. */
    val teamAbbrev: JsonElement? = null,
    val drawnBy: LandingPenaltyPlayerDto? = null,
    /** The feed's lowercase key: `fighting`, `holding`, `unsportsmanlike-conduct`. */
    val descKey: String? = null,
    /** Often an initialled name, so it is kept as sent rather than composed with a first name. */
    val servedBy: LocalizedNameDto? = null,
)

@Serializable
internal data class LandingPenaltyPlayerDto(
    val firstName: LocalizedNameDto? = null,
    val lastName: LocalizedNameDto? = null,
    val name: LocalizedNameDto? = null,
)

@Serializable
internal data class LandingStarDto(
    val star: Int? = null,
    /** A plain string here, unlike a goal's `teamAbbrev`. */
    val teamAbbrev: String? = null,
    val name: LocalizedNameDto? = null,
    val position: String? = null,
)

@Serializable
internal data class LandingClockDto(
    val timeRemaining: String? = null,
    val inIntermission: Boolean? = null,
)

@Serializable
internal data class LandingBroadcastDto(
    val network: String? = null,
    val countryCode: String? = null,
    val market: String? = null,
)
