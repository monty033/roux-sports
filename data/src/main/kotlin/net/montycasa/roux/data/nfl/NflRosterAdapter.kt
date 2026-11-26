package net.montycasa.roux.data.nfl

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.data.nfl.dto.NflRosterAthleteDto
import net.montycasa.roux.data.nfl.dto.NflRosterDto
import net.montycasa.roux.data.nfl.dto.NflRosterStatusDto
import net.montycasa.roux.data.nfl.dto.NflRosterTeamDto
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.NflPlayerStatus
import net.montycasa.roux.domain.model.NflRosterPlayer
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef

/**
 * Turns ESPN's NFL club roster into a football roster.
 *
 * Two rules shape it, both from what the captures actually state:
 *
 * - **A player's position is their own.** The team-level `groups` object is one group with no membership, so reading
 *   it as offensive/defensive units would invent a grouping the payload does not carry.
 * - **A missing status is not active.** Only a stated `status.type` becomes an answer; a player with no status object
 *   keeps [NflPlayerStatus.NotStated] even when the same player is `active: false`.
 *
 * The list is ordered here, once: position abbreviation, then jersey number ascending with unnumbered players last,
 * then name. That makes "the roster is grouped by position" a property of the list the screen renders rather than a
 * sentence in a comment.
 */
internal object NflRosterAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val rosterOrder =
        compareBy<NflRosterPlayer>(
            { it.positionAbbreviation },
            { it.jersey?.toIntOrNull() ?: Int.MAX_VALUE },
            { it.name },
        )

    fun parse(json: String): ParseResult<NflClubRoster> {
        val payload =
            try {
                feedJson.decodeFromString<NflRosterDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL roster decoding failed: ${exception.message}")
            }

        val teamDto = payload.team ?: return ParseResult.Failure("NFL roster mapping failed: no club")
        val team = teamDto.toRef() ?: return ParseResult.Failure("NFL roster mapping failed: no club")
        val athletes = teamDto.athletes ?: return ParseResult.Failure("NFL roster mapping failed: no athletes")
        val players = athletes.mapNotNull { it.toPlayer() }.sortedWith(rosterOrder)
        if (players.isEmpty()) {
            return ParseResult.Failure("NFL roster mapping failed: no usable players", emptyPayload = true)
        }
        return ParseResult.Success(NflClubRoster(team = team, players = players))
    }

    private fun NflRosterTeamDto.toRef(): TeamRef? {
        val abbreviation = abbreviation?.takeIf { it.isNotBlank() } ?: return null
        return TeamRef(
            key = TeamKey(League.NFL, abbreviation),
            name = displayName?.takeIf { it.isNotBlank() },
            shortName = (shortDisplayName ?: nickname ?: name)?.takeIf { it.isNotBlank() },
            logoUrl =
                logos
                    .orEmpty()
                    .firstOrNull()
                    ?.href
                    ?.takeIf { it.isOnArtworkHost() },
        )
    }

    private fun NflRosterAthleteDto.toPlayer(): NflRosterPlayer? {
        val athleteId = id?.takeIf { it.isNotBlank() } ?: return null
        val name =
            displayName?.takeIf { it.isNotBlank() }
                ?: fullName?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(firstName?.takeIf { it.isNotBlank() }, lastName?.takeIf { it.isNotBlank() })
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }
                ?: return null
        val position = position
        return NflRosterPlayer(
            id = athleteId,
            name = name,
            jersey = jersey?.takeIf { it.isNotBlank() },
            positionAbbreviation =
                position
                    ?.abbreviation
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    .orEmpty(),
            positionName =
                position
                    ?.displayName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    .orEmpty(),
            heightText = displayHeight?.takeIf { it.isNotBlank() },
            weightText = displayWeight?.takeIf { it.isNotBlank() },
            age = age,
            headshotUrl = headshot?.takeIf { it.isOnArtworkHost() },
            status = status.toStatus(),
        )
    }

    private fun NflRosterStatusDto?.toStatus(): NflPlayerStatus {
        val status = this ?: return NflPlayerStatus.NotStated
        val statedType = status.type?.takeIf { it.isNotBlank() } ?: return NflPlayerStatus.NotStated
        val wording =
            status.name?.takeIf { it.isNotBlank() }
                ?: status.abbreviation?.takeIf { it.isNotBlank() }
                ?: statedType
        return if (statedType == STATUS_ACTIVE) {
            NflPlayerStatus.Active(wording)
        } else {
            NflPlayerStatus.Other(wording)
        }
    }

    private fun String.isOnArtworkHost(): Boolean =
        try {
            val url = Url(this)
            url.protocol == URLProtocol.HTTPS && url.host == EspnHttpClientFactory.LOGO_HOST
        } catch (_: IllegalArgumentException) {
            false
        }

    private const val STATUS_ACTIVE = "active"
}
