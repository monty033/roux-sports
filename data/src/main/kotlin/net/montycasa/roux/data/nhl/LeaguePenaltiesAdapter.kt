package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nhl.dto.LeaguePenaltiesDto
import net.montycasa.roux.data.nhl.dto.LeaguePenaltyRowDto
import net.montycasa.roux.domain.model.ClubPenalties
import net.montycasa.roux.domain.model.LeaguePenalties

/**
 * Turns the league penalties payload into the penalty minutes the join reads onto each club.
 *
 * A row is dropped only when it has neither a club id nor a name, because a row that cannot be matched to
 * any club is useless here. A missing number is null, never zero: a zero would rank a club with no reported
 * penalties as the least-penalised club in the league.
 */
internal object LeaguePenaltiesAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<LeaguePenalties> {
        val wire =
            try {
                feedJson.decodeFromString<LeaguePenaltiesDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL league penalties decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL league penalties decoding failed: ${exception.message}")
            }

        val rows = wire.data ?: return ParseResult.Failure("NHL league penalties mapping failed: missing data")
        val clubs = rows.mapNotNull { it?.toClub() }
        if (clubs.isEmpty()) {
            return ParseResult.Failure("NHL league penalties mapping failed: no usable clubs")
        }
        return ParseResult.Success(LeaguePenalties(clubs = clubs))
    }

    private fun LeaguePenaltyRowDto.toClub(): ClubPenalties? {
        val name = teamFullName?.takeIf { it.isNotBlank() }
        if (teamId == null && name == null) return null
        return ClubPenalties(teamId = teamId, teamName = name.orEmpty(), penaltyMinutes = penaltyMinutes)
    }
}
