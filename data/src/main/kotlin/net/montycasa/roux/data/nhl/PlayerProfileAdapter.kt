package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nhl.dto.PlayerLandingDto
import net.montycasa.roux.data.nhl.dto.PlayerStatsDto
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerDraft
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals

/**
 * Turns the player landing payload into a [PlayerProfile].
 *
 * The skater and goalie stat sets carry different keys, so the position decides which set is read. A goalie is the
 * only position with its own set. A missing number stays null all the way to the screen, where it prints as an em
 * dash; it is never turned into zero, because zero is a real value.
 */
internal object PlayerProfileAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<PlayerProfile> {
        val landing =
            try {
                feedJson.decodeFromString<PlayerLandingDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL player landing decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL player landing decoding failed: ${exception.message}")
            }

        val playerId = landing.playerId ?: return ParseResult.Failure("NHL player landing mapping failed: missing playerId")
        val position =
            landing.position?.trim()?.takeIf { it.isNotEmpty() }
                ?: return ParseResult.Failure("NHL player landing mapping failed: missing position")
        val firstName =
            landing.firstName?.default?.takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("NHL player landing mapping failed: missing first name")
        val lastName =
            landing.lastName?.default?.takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("NHL player landing mapping failed: missing last name")

        val isGoalie = position == GOALIE_POSITION

        return ParseResult.Success(
            PlayerProfile(
                playerId = playerId,
                firstName = firstName,
                lastName = lastName,
                position = position,
                sweaterNumber = landing.sweaterNumber,
                headshotUrl = landing.headshot?.takeIf { it.isNotBlank() },
                heightInches = landing.heightInInches,
                weightPounds = landing.weightInPounds,
                birthDate = landing.birthDate?.takeIf { it.isNotBlank() },
                birthCity = landing.birthCity?.default?.takeIf { it.isNotBlank() },
                birthCountry = landing.birthCountry?.takeIf { it.isNotBlank() },
                shoots = landing.shoots?.takeIf { it.isNotBlank() },
                draft =
                    landing.draftDetails?.let { draft ->
                        PlayerDraft(
                            year = draft.year,
                            teamAbbrev = draft.teamAbbrev,
                            round = draft.round,
                            pickInRound = draft.pickInRound,
                            overallPick = draft.overallPick,
                        )
                    },
                career =
                    PlayerCareer(
                        regularSeason = landing.careerTotals?.regularSeason?.totals(isGoalie),
                        playoffs = landing.careerTotals?.playoffs?.totals(isGoalie),
                    ),
                seasons =
                    landing.seasonTotals.orEmpty().filterNotNull().map { row ->
                        PlayerSeasonLine(
                            season = row.season,
                            gameTypeId = row.gameTypeId,
                            leagueAbbrev = row.leagueAbbrev?.trim()?.takeIf { it.isNotEmpty() },
                            teamName = row.teamName?.default?.takeIf { it.isNotBlank() },
                            sequence = row.sequence,
                            totals = row.totals(isGoalie),
                        )
                    },
            ),
        )
    }

    private fun PlayerStatsDto.totals(isGoalie: Boolean): PlayerTotals = if (isGoalie) goalieTotals() else skaterTotals()

    private fun PlayerStatsDto.skaterTotals(): PlayerTotals.Skater =
        PlayerTotals.Skater(
            gamesPlayed = gamesPlayed,
            goals = goals,
            assists = assists,
            points = points,
            plusMinus = plusMinus,
            penaltyMinutes = pim,
            powerPlayGoals = powerPlayGoals,
            powerPlayPoints = powerPlayPoints,
            shorthandedGoals = shorthandedGoals,
            shorthandedPoints = shorthandedPoints,
            gameWinningGoals = gameWinningGoals,
            overtimeGoals = otGoals,
            shots = shots,
            shootingPctg = shootingPctg,
            faceoffWinningPctg = faceoffWinningPctg,
            avgTimeOnIceSeconds = parseIceTime(avgToi),
        )

    private fun PlayerStatsDto.goalieTotals(): PlayerTotals.Goalie =
        PlayerTotals.Goalie(
            gamesPlayed = gamesPlayed,
            gamesStarted = gamesStarted,
            wins = wins,
            losses = losses,
            overtimeLosses = otLosses,
            goalsAgainst = goalsAgainst,
            goalsAgainstAverage = goalsAgainstAvg,
            shotsAgainst = shotsAgainst,
            savePercentage = savePctg,
            shutouts = shutouts,
            timeOnIceSeconds = parseIceTime(timeOnIce),
            goals = goals,
            assists = assists,
            penaltyMinutes = pim,
        )

    /**
     * `MM:SS` as whole seconds: `11:12` is 672. Anything else, including seconds of 60 or more, is absent rather than a
     * guess. Minutes may run past sixty, as a goalie's season total does (`7716:44`).
     */
    internal fun parseIceTime(text: String?): Long? {
        val parts = text?.split(':') ?: return null
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val seconds = parts[1].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        return minutes * 60 + seconds
    }

    private const val GOALIE_POSITION = "G"
}
