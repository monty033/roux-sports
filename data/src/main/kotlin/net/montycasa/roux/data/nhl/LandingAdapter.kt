package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import net.montycasa.roux.data.nhl.dto.LandingBroadcastDto
import net.montycasa.roux.data.nhl.dto.LandingClockDto
import net.montycasa.roux.data.nhl.dto.LandingDto
import net.montycasa.roux.data.nhl.dto.LandingGoalDto
import net.montycasa.roux.data.nhl.dto.LandingPenaltyDto
import net.montycasa.roux.data.nhl.dto.LandingPenaltyPeriodDto
import net.montycasa.roux.data.nhl.dto.LandingPeriodDescriptorDto
import net.montycasa.roux.data.nhl.dto.LandingScoringPeriodDto
import net.montycasa.roux.data.nhl.dto.LandingStarDto
import net.montycasa.roux.data.nhl.dto.LocalizedNameDto
import net.montycasa.roux.domain.model.Broadcast
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalAssist
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.domain.model.PenaltyEvent
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.ThreeStar
import net.montycasa.roux.domain.model.Venue

/**
 * Maps the landing moment, period scoring and three stars.
 *
 * A landing payload for a game that has not started carries `matchup` instead of `summary`. That is a successful fetch
 * with nothing to show, not a failure, so it maps to a [GameLanding] with empty sections rather than to [ParseResult.Failure] - the
 * page then simply has no scoring or stars to draw.
 */
internal object LandingAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<GameLanding> {
        val landing =
            try {
                // Decode optional moment values separately: malformed moment fields must not lose the page.
                val root =
                    feedJson.parseToJsonElement(json) as? JsonObject
                        ?: return ParseResult.Failure("NHL landing decoding failed: expected object")
                val descriptor = root["periodDescriptor"] as? JsonObject
                val clock = root["clock"] as? JsonObject
                feedJson
                    .decodeFromJsonElement<LandingDto>(
                        JsonObject(root.filterKeys { it !in setOf("periodDescriptor", "clock", "gameState", "tvBroadcasts") }),
                    ).copy(
                        tvBroadcasts =
                            (root["tvBroadcasts"] as? JsonArray)?.mapNotNull { entry ->
                                runCatching { feedJson.decodeFromJsonElement<LandingBroadcastDto>(entry) }.getOrNull()
                            },
                        gameState = root["gameState"].stringOrNull(),
                        periodDescriptor =
                            descriptor?.let {
                                LandingPeriodDescriptorDto(
                                    number = (it["number"] as? JsonPrimitive)?.intOrNull?.takeIf { number -> number > 0 },
                                    periodType = it["periodType"].stringOrNull(),
                                )
                            },
                        clock =
                            clock?.let {
                                LandingClockDto(
                                    timeRemaining =
                                        it["timeRemaining"].stringOrNull()?.takeIf { time ->
                                            time.matches(Regex("[0-9]{2}:[0-5][0-9]"))
                                        },
                                    inIntermission = (it["inIntermission"] as? JsonPrimitive)?.booleanOrNull,
                                )
                            },
                    )
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL landing decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL landing decoding failed: ${exception.message}")
            }

        // The id is the only field whose absence makes the payload unusable: it is what the page is about. Team
        // abbreviations are optional because a goal can be attributed by isHome alone.
        landing.id ?: return ParseResult.Failure("NHL landing mapping failed: missing id")
        val summary = landing.summary
        val state = landing.gameState?.let(GameState::fromFeed)

        val away = landing.awayTeam?.abbrev?.takeIf { it.isNotBlank() }
        val home = landing.homeTeam?.abbrev?.takeIf { it.isNotBlank() }

        val periods =
            summary
                ?.scoring
                .orEmpty()
                .mapNotNull { it?.toPeriodScoring(away, home) }
                .sortedBy { it.period }
        val stars =
            summary
                ?.threeStars
                .orEmpty()
                .mapNotNull { it?.toThreeStar() }
                .sortedBy { it.rank }
        val goals =
            summary
                ?.scoring
                .orEmpty()
                .flatMap { rawPeriod ->
                    val period = rawPeriod ?: return@flatMap emptyList()
                    // A goal with no period cannot be placed, and one with no scorer cannot be attributed. Both are
                    // dropped alone: unlike a period count, a goal list stays honest when it is short.
                    val number = period.periodDescriptor?.number?.takeIf { it > 0 } ?: return@flatMap emptyList()
                    period.goals.orEmpty().mapNotNull { it?.toGoalEvent(number, away, home) }
                }
        val penalties =
            summary
                ?.penalties
                .orEmpty()
                .flatMap { rawPeriod ->
                    val period = rawPeriod ?: return@flatMap emptyList()
                    // A penalty without a period has no heading to sit under, and one without an offender cannot be
                    // attributed. Both are dropped alone: the rest of the calls are still true.
                    val descriptor = period.periodDescriptor ?: return@flatMap emptyList()
                    val number = descriptor.number?.takeIf { it > 0 } ?: return@flatMap emptyList()
                    period.penalties.orEmpty().mapNotNull { it?.toPenaltyEvent(number, descriptor.periodType.clean()) }
                }

        return ParseResult.Success(
            GameLanding(
                venue =
                    landing.venue
                        ?.default
                        .clean()
                        ?.let { Venue(it, landing.venueLocation?.default.clean()) },
                broadcasts =
                    landing.tvBroadcasts.orEmpty().mapNotNull { broadcast ->
                        broadcast?.network.clean()?.let { Broadcast(it, broadcast?.countryCode.clean(), broadcast?.market.clean()) }
                    },
                periodScoring = periods,
                threeStars = stars,
                goals = goals,
                penalties = penalties,
                state = state,
                period =
                    landing.periodDescriptor?.number?.let {
                        PeriodInfo(
                            ordinal = it,
                            periodType = landing.periodDescriptor.periodType,
                            clock = landing.clock?.timeRemaining,
                            inIntermission = landing.clock?.inIntermission ?: false,
                        )
                    },
                finalKind =
                    if (state == GameState.Final) {
                        if (landing.periodDescriptor?.periodType == null) {
                            FinalKind.REG
                        } else {
                            FinalKind.from(landing.periodDescriptor.periodType)
                        }
                    } else {
                        null
                    },
            ),
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /**
     * Counts a period's goals by side.
     *
     * Returns null if any goal cannot be attributed. Publishing a period that is short a goal is worse than publishing
     * none: the section would show a total that disagrees with the score above it, and nothing on screen would say why.
     */
    private fun LandingScoringPeriodDto.toPeriodScoring(
        awayAbbrev: String?,
        homeAbbrev: String?,
    ): PeriodScoring? {
        val number = periodDescriptor?.number ?: return null
        var awayGoals = 0
        var homeGoals = 0
        for (goal in goals.orEmpty()) {
            val side = goal?.side(awayAbbrev, homeAbbrev) ?: return null
            when (side) {
                Side.Away -> awayGoals++
                Side.Home -> homeGoals++
            }
        }
        return PeriodScoring(period = number, awayGoals = awayGoals, homeGoals = homeGoals)
    }

    /**
     * The feed's full first and last name, which is what the card prints.
     *
     * Returns null when either half is absent, letting the caller fall back to the payload's abbreviated `name.default`
     * rather than inventing a surname.
     */
    private fun fullName(
        first: LocalizedNameDto?,
        last: LocalizedNameDto?,
    ): String? {
        val firstPart = first?.default.clean() ?: return null
        val lastPart = last?.default.clean() ?: return null
        return "$firstPart $lastPart"
    }

    /**
     * One goal, with its own season totals and running score.
     *
     * Returns null when the goal cannot be placed (no side) or attributed (no name). Everything else is kept even when
     * it is unfamiliar: an unknown strength becomes [GoalStrength.Unknown] so the goal still appears, and a missing
     * score stays null rather than being guessed from the goal before it.
     */
    private fun LandingGoalDto.toGoalEvent(
        period: Int,
        awayAbbrev: String?,
        homeAbbrev: String?,
    ): GoalEvent? {
        val side = side(awayAbbrev, homeAbbrev) ?: return null
        val scorer = fullName(firstName, lastName) ?: name?.default.clean() ?: return null
        val mappedAssists =
            assists.orEmpty().mapNotNull { assist ->
                val entry = assist ?: return@mapNotNull null
                val assistName =
                    fullName(entry.firstName, entry.lastName) ?: entry.name?.default.clean()
                        ?: return@mapNotNull null
                GoalAssist(name = assistName, assistsToDate = entry.assistsToDate)
            }
        return GoalEvent(
            period = period,
            timeInPeriod = timeInPeriod.clean().orEmpty(),
            isHome = side == Side.Home,
            scorer = scorer,
            goalsToDate = goalsToDate,
            assists = mappedAssists,
            strength = strength.toStrength(),
            modifier = goalModifier.toModifier(),
            awayScore = awayScore,
            homeScore = homeScore,
        )
    }

    /**
     * One penalty call, with the feed's `descKey` turned into the prose the card prints.
     *
     * The offender's full name is required: a row with no name cannot say who sat. The drawn and served names are
     * optional and stay optional, which is what lets the card omit those lines rather than print an empty label.
     */
    private fun LandingPenaltyDto.toPenaltyEvent(
        period: Int,
        periodType: String?,
    ): PenaltyEvent? {
        val offender =
            fullName(committedByPlayer?.firstName, committedByPlayer?.lastName)
                ?: committedByPlayer?.name?.default.clean()
                ?: return null
        val drawn =
            fullName(drawnBy?.firstName, drawnBy?.lastName)
                ?: drawnBy?.name?.default.clean()
        return PenaltyEvent(
            period = period,
            periodType = periodType,
            timeInPeriod = timeInPeriod.clean().orEmpty(),
            teamAbbrev = teamAbbrev?.abbrevText().orEmpty(),
            player = offender,
            call = penaltyCall(descKey),
            durationMinutes = duration?.takeIf { it > 0 },
            drawnBy = drawn,
            servedBy = servedBy?.default.clean(),
        )
    }

    /**
     * The card's prose for a call.
     *
     * The observed keys are listed explicitly because the feed treats them as translation keys, not display text.
     * An unfamiliar key is not dropped: hyphens become spaces, so a new call still reads as words instead of leaking
     * a wire token into the sentence.
     */
    private fun penaltyCall(descKey: String?): String {
        val key =
            descKey
                ?.trim()
                ?.lowercase()
                .orEmpty()
                .ifEmpty { "penalty" }
        return PENALTY_CALLS[key] ?: key.replace('-', ' ')
    }

    private fun String?.toStrength(): GoalStrength =
        when (this?.trim()?.lowercase()) {
            "ev" -> GoalStrength.EvenStrength
            "pp" -> GoalStrength.PowerPlay
            "sh" -> GoalStrength.ShortHanded
            else -> GoalStrength.Unknown
        }

    private fun String?.toModifier(): GoalModifier =
        when (this?.trim()?.lowercase()) {
            null, "", "none" -> GoalModifier.None
            "empty-net" -> GoalModifier.EmptyNet
            "penalty-shot" -> GoalModifier.PenaltyShot
            else -> GoalModifier.Other
        }

    /** [isHome] first, because it is unambiguous; the abbreviation is a fallback for a payload that omits it. */
    private fun LandingGoalDto.side(
        awayAbbrev: String?,
        homeAbbrev: String?,
    ): Side? {
        isHome?.let { return if (it) Side.Home else Side.Away }
        val abbrev = teamAbbrev?.abbrevText() ?: return null
        return when (abbrev) {
            homeAbbrev -> Side.Home
            awayAbbrev -> Side.Away
            else -> null
        }
    }

    /** Reads a `teamAbbrev` whether it arrives as `"CBJ"` or as `{"default":"CBJ"}`. */
    private fun kotlinx.serialization.json.JsonElement.abbrevText(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> (this["default"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }

    private fun LandingStarDto.toThreeStar(): ThreeStar? {
        val rank = star ?: return null
        val playerName = name?.default?.takeIf { it.isNotBlank() } ?: return null
        return ThreeStar(
            rank = rank,
            player = PlayerRef(name = playerName, teamAbbrev = teamAbbrev.orEmpty()),
        )
    }

    private enum class Side { Away, Home }

    /** The observed `descKey` values, spelled the way the card reads them. */
    private val PENALTY_CALLS =
        mapOf(
            "fighting" to "fighting",
            "holding" to "holding",
            "hooking" to "hooking",
            "interference" to "interference",
            "roughing" to "roughing",
            "tripping" to "tripping",
            "unsportsmanlike-conduct" to "unsportsmanlike conduct",
        )
}
