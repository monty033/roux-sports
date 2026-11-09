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

    /**
     * Parses one season's league table and stamps it with [season], the season the request asked for.
     *
     * A reply with no usable club is an empty season: the feed answered with the right shape and nothing in it,
     * which is what a season that has not started looks like. It is flagged [ParseResult.Failure.emptyPayload],
     * because an empty table would rank every club as first. A missing `data` array is a broken reply, not an
     * empty season, and is not flagged.
     */
    fun parse(
        json: String,
        season: String,
    ): ParseResult<LeagueSummary> {
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
        if (clubs.isEmpty()) {
            return ParseResult.Failure("NHL league stats mapping failed: no clubs", emptyPayload = true)
        }
        return ParseResult.Success(LeagueSummary(season = season, clubs = clubs))
    }

    /** A blank name is the only reason to drop a row; the name is kept exactly as the feed spells it. */
    private fun LeagueStatsRowDto.toClub(): LeagueClub? {
        val name = teamFullName?.takeIf { it.isNotBlank() } ?: return null
        return LeagueClub(
            teamName = name,
            teamId = teamId,
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
