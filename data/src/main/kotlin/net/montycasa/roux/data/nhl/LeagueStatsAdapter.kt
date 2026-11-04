package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nhl.dto.LeagueStatsDto
import net.montycasa.roux.data.nhl.dto.LeagueStatsRowDto
import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeagueSummary

/**
 * Turns the league team-summary payload into the table the Team Stats tab ranks against.
 *
 * A row is dropped only when it has no club name, because a row that cannot be joined to a club page
 * is useless to it. Every number stays exactly as the feed sent it: a missing number is null, never
 * zero, and a percentage is kept as the feed's fraction. The screen does the multiplying for display.
 */
internal object LeagueStatsAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<LeagueSummary> {
        val wire =
            try {
                feedJson.decodeFromString<LeagueStatsDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL league stats decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL league stats decoding failed: ${exception.message}")
            }

        val rows = wire.data ?: return ParseResult.Failure("NHL league stats mapping failed: missing data")
        val clubs = rows.mapNotNull { it?.toClub() }
        // A league with no clubs is not a real state, and an empty table would rank every club as first.
        if (clubs.isEmpty()) {
            return ParseResult.Failure("NHL league stats mapping failed: no usable clubs")
        }
        return ParseResult.Success(LeagueSummary(clubs = clubs))
    }

    /** A blank name is the only reason to drop a row; the name is kept exactly as the feed spells it. */
    private fun LeagueStatsRowDto.toClub(): LeagueClub? {
        val name = teamFullName?.takeIf { it.isNotBlank() } ?: return null
        return LeagueClub(
            teamName = name,
            gamesPlayed = gamesPlayed,
            wins = wins,
            losses = losses,
            otLosses = otLosses,
            ties = ties,
            points = points,
            pointPct = pointPct,
            goalsForPerGame = goalsForPerGame,
            goalsAgainstPerGame = goalsAgainstPerGame,
            shotsForPerGame = shotsForPerGame,
            shotsAgainstPerGame = shotsAgainstPerGame,
            faceoffWinPct = faceoffWinPct,
            powerPlayPct = powerPlayPct,
            penaltyKillPct = penaltyKillPct,
            teamShutouts = teamShutouts,
        )
    }
}
