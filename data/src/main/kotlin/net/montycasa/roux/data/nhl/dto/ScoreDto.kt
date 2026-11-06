package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Nullable wire fields let the adapter decide which omissions prevent a usable scoreboard. */
@Serializable
internal data class ScoreDto(
    val currentDate: String? = null,
    val prevDate: String? = null,
    val nextDate: String? = null,
    val gameWeek: List<DateIndexDto?>? = null,
    val games: List<JsonElement?>? = null,
)

/** Incomplete navigation entries can be skipped without losing otherwise usable games. */
@Serializable
internal data class DateIndexDto(
    val date: String? = null,
    val numberOfGames: Int? = null,
)

/** Only fields consumed by the score adapter are coupled to the undocumented feed. */
@Serializable
internal data class GameDto(
    val id: Long? = null,
    val gameDate: String? = null,
    val startTimeUTC: String? = null,
    val gameState: String? = null,
    val homeTeam: TeamDto? = null,
    val awayTeam: TeamDto? = null,
    @Serializable(with = GameOutcomeSerializer::class)
    val gameOutcome: GameOutcomeDto? = null,
    @Serializable(with = PeriodDescriptorSerializer::class)
    val periodDescriptor: PeriodDescriptorDto? = null,
    @Serializable(with = ClockSerializer::class)
    val clock: ClockDto? = null,
    // A playoff series snapshot; a malformed object must not cost the game its score.
    @Serializable(with = SeriesStatusSerializer::class)
    val seriesStatus: SeriesStatusDto? = null,
    // The day feed carries broadcast listings beside the score; the same shape the landing page uses.
    val tvBroadcasts: List<LandingBroadcastDto?>? = null,
)

/** Scores and display assets may be absent independently of the team's identity. */
@Serializable
internal data class TeamDto(
    val name: NameDto? = null,
    val abbrev: String? = null,
    val score: Int? = null,
    // Keep malformed optional shot counts from invalidating the team's identity or game.
    val sog: JsonElement? = null,
    val logo: String? = null,
    val darkLogo: String? = null,
)

/** Preserve the default feed name without choosing another locale for the caller. */
@Serializable
internal data class NameDto(
    val default: String? = null,
)

/** The outcome is separate from the current period, so final-kind mapping uses its own token. */
@Serializable
internal data class GameOutcomeDto(
    val lastPeriodType: String? = null,
    val otPeriods: Int? = null,
)

/**
 * A playoff game's series snapshot, from the score feed's `seriesStatus`.
 *
 * Only the fields the app words are modelled; unmodelled keys are ignored like every other feed node.
 */
@Serializable
internal data class SeriesStatusDto(
    val round: Int? = null,
    val seriesTitle: String? = null,
    val neededToWin: Int? = null,
    val topSeedTeamAbbrev: String? = null,
    val topSeedWins: Int? = null,
    val bottomSeedTeamAbbrev: String? = null,
    val bottomSeedWins: Int? = null,
    val gameNumberOfSeries: Int? = null,
)

/** Future games need not supply a period descriptor. */
@Serializable
internal data class PeriodDescriptorDto(
    val number: Int? = null,
    val periodType: String? = null,
)

/** Preserve display clock text rather than reconstructing it from seconds. */
@Serializable
internal data class ClockDto(
    val timeRemaining: String? = null,
    val inIntermission: Boolean? = null,
    val running: Boolean? = null,
)
