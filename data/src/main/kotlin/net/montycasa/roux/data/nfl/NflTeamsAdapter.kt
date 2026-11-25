package net.montycasa.roux.data.nfl

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.data.nfl.dto.NflTeamsDto
import net.montycasa.roux.data.nfl.dto.NflTeamsTeamDto
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflTeam
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef

/**
 * Turns ESPN's NFL teams payload into domain clubs.
 *
 * The feed's order and words are kept: nothing is sorted and no nickname, name or division is composed from other
 * fields. A field the feed leaves out stays null.
 *
 * The logo is the one decision made here. The client allows exactly one image host, so a crest URL on any other host
 * would be a request the boundary refuses - and a refusal that surfaces as a blocked-host failure for a picture. The
 * first logo is the club's own default crest; if it is not on that host the club is drawn with none, rather than
 * walking down the list for a later logo that happens to be.
 */
internal object NflTeamsAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<List<NflTeam>> {
        val payload =
            try {
                feedJson.decodeFromString<NflTeamsDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL teams decoding failed: ${exception.message}")
            }

        val entries =
            payload.sports
                ?.firstOrNull()
                ?.leagues
                ?.firstOrNull()
                ?.teams ?: return ParseResult.Failure("NFL teams mapping failed: missing teams field")

        // Abbreviation is the club's identity. Two entries sharing one would be one favorite toggle for two rows, so the
        // first stands and the repeat is dropped; the feed has never sent one, and a silent merge would hide it if it did.
        val clubs =
            entries
                .mapNotNull { it.team?.toClub() }
                .distinctBy { it.team.key }

        return if (clubs.isEmpty()) {
            ParseResult.Failure("NFL teams mapping failed: no usable teams", emptyPayload = true)
        } else {
            ParseResult.Success(clubs)
        }
    }

    private fun NflTeamsTeamDto.toClub(): NflTeam? {
        val abbreviation = abbreviation?.takeIf { it.isNotBlank() } ?: return null
        val fullName = displayName?.takeIf { it.isNotBlank() }
        val nickname = (nickname ?: name)?.takeIf { it.isNotBlank() }
        val crest =
            logos
                .orEmpty()
                .firstOrNull()
                ?.href
                ?.takeIf { it.isOnArtworkHost() }

        return NflTeam(
            team =
                TeamRef(
                    key = TeamKey(League.NFL, abbreviation),
                    name = fullName,
                    shortName = nickname,
                    logoUrl = crest,
                ),
            fullName = fullName,
            nickname = nickname,
            location = location?.takeIf { it.isNotBlank() },
            logoUrl = crest,
        )
    }

    private fun String.isOnArtworkHost(): Boolean =
        try {
            val url = Url(this)
            url.protocol == URLProtocol.HTTPS && url.host == EspnHttpClientFactory.LOGO_HOST
        } catch (_: IllegalArgumentException) {
            false
        }
}
