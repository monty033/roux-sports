package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import net.montycasa.roux.data.nhl.dto.LandingDto
import net.montycasa.roux.data.nhl.dto.LandingGoalDto
import net.montycasa.roux.data.nhl.dto.LandingScoringPeriodDto
import net.montycasa.roux.data.nhl.dto.LandingStarDto
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.ThreeStar

/**
 * Turns the landing payload into the two sections it owns: period scoring and the three stars.
 *
 * A landing payload for a game that has not started carries `matchup` instead of `summary`. That is a successful fetch
 * with nothing to show, not a failure, so it maps to an empty [GameLanding] rather than to [ParseResult.Failure] - the
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
                feedJson.decodeFromString<LandingDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL landing decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL landing decoding failed: ${exception.message}")
            }

        // The id is the only field whose absence makes the payload unusable: it is what the page is about. Team
        // abbreviations are optional because a goal can be attributed by isHome alone.
        landing.id ?: return ParseResult.Failure("NHL landing mapping failed: missing id")
        val summary =
            landing.summary
                ?: return ParseResult.Success(GameLanding())

        val away = landing.awayTeam?.abbrev?.takeIf { it.isNotBlank() }
        val home = landing.homeTeam?.abbrev?.takeIf { it.isNotBlank() }

        val periods =
            summary.scoring
                .orEmpty()
                .mapNotNull { it?.toPeriodScoring(away, home) }
                .sortedBy { it.period }
        val stars =
            summary.threeStars
                .orEmpty()
                .mapNotNull { it?.toThreeStar() }
                .sortedBy { it.rank }

        return ParseResult.Success(
            GameLanding(
                periodScoring = periods,
                threeStars = stars,
            ),
        )
    }

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
}
