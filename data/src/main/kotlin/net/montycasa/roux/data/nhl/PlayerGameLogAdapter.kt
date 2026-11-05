package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nhl.dto.PlayerGameLogDto
import net.montycasa.roux.data.nhl.dto.PlayerGameRowDto
import net.montycasa.roux.domain.model.GoalieDecision
import net.montycasa.roux.domain.model.PlayerGame

/**
 * Turns one player's game-log payload into [PlayerGame] rows, in the feed's order.
 *
 * The order is kept on purpose: sorting belongs to the screen, where [newestFirst] is tested. A row with no game id is
 * dropped, because it cannot be told apart from another row. A missing number stays null, the same as the profile.
 */
internal object PlayerGameLogAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(
        json: String,
        isGoalie: Boolean,
    ): ParseResult<List<PlayerGame>> {
        val log =
            try {
                feedJson.decodeFromString<PlayerGameLogDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL player game log decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL player game log decoding failed: ${exception.message}")
            }

        val games =
            log.gameLog
                .orEmpty()
                .filterNotNull()
                .mapNotNull { row -> row.gameId?.let { row.toGame(it, isGoalie) } }
        return ParseResult.Success(games)
    }

    private fun PlayerGameRowDto.toGame(
        id: Long,
        isGoalie: Boolean,
    ): PlayerGame {
        val home =
            when (homeRoadFlag) {
                "H" -> true
                "R" -> false
                else -> null
            }
        return if (isGoalie) {
            PlayerGame.Goalie(
                gameId = id,
                gameDate = gameDate,
                opponentAbbrev = opponentAbbrev,
                isHome = home,
                teamAbbrev = teamAbbrev,
                decision = decision?.let { decisionOf(it) },
                shotsAgainst = shotsAgainst,
                goalsAgainst = goalsAgainst,
                savePercentage = savePctg,
                timeOnIceSeconds = PlayerProfileAdapter.parseIceTime(toi),
            )
        } else {
            PlayerGame.Skater(
                gameId = id,
                gameDate = gameDate,
                opponentAbbrev = opponentAbbrev,
                isHome = home,
                teamAbbrev = teamAbbrev,
                goals = goals,
                assists = assists,
                points = points,
                plusMinus = plusMinus,
                shots = shots,
                timeOnIceSeconds = PlayerProfileAdapter.parseIceTime(toi),
            )
        }
    }

    /**
     * The feed's decision codes. `OT` and `OTL` are both an overtime loss. Any other code is absent rather than guessed,
     * so the screen prints a dash.
     */
    internal fun decisionOf(code: String): GoalieDecision? =
        when (code.trim()) {
            "W" -> GoalieDecision.Win
            "L" -> GoalieDecision.Loss
            "OT", "OTL" -> GoalieDecision.OvertimeLoss
            else -> null
        }
}
