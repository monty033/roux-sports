package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nhl.dto.RosterDto
import net.montycasa.roux.data.nhl.dto.RosterPlayerDto
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.RosterPlayer

/** Keeps the undocumented roster shape behind a domain-only parsing boundary. */
internal object RosterAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<ClubRoster> {
        val roster =
            try {
                feedJson.decodeFromString<RosterDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL roster decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL roster decoding failed: ${exception.message}")
            }

        val forwardsWire = roster.forwards ?: return ParseResult.Failure("NHL roster mapping failed: missing forwards")
        val defenseWire = roster.defensemen ?: return ParseResult.Failure("NHL roster mapping failed: missing defensemen")
        val goaliesWire = roster.goalies ?: return ParseResult.Failure("NHL roster mapping failed: missing goalies")

        val forwards = forwardsWire.players() ?: return failure("forwards")
        val defense = defenseWire.players() ?: return failure("defensemen")
        val goalies = goaliesWire.players() ?: return failure("goalies")
        return ParseResult.Success(ClubRoster(forwards = forwards, defense = defense, goalies = goalies))
    }

    private fun failure(group: String): ParseResult<ClubRoster> =
        ParseResult.Failure("NHL roster mapping failed: $group has no usable players")

    /**
     * Maps one feed list, dropping only rows with no name to show.
     *
     * A group the feed sent as a non-empty list but whose every row was unusable is a failure: the
     * page would otherwise present a broken payload as an honest empty group.
     */
    private fun List<RosterPlayerDto?>.players(): List<RosterPlayer>? {
        val players = mapNotNull { it?.toPlayer() }
        if (isNotEmpty() && players.isEmpty()) return null
        // The feed's order is not documented as meaningful; a roster is read by number.
        return players.sortedWith(compareBy({ it.sweaterNumber ?: Int.MAX_VALUE }, { it.name }))
    }

    private fun RosterPlayerDto.toPlayer(): RosterPlayer? {
        val first = firstName?.default?.trim()?.takeIf { it.isNotEmpty() }
        val last = lastName?.default?.trim()?.takeIf { it.isNotEmpty() }
        val name = listOfNotNull(first, last).joinToString(" ")
        if (name.isBlank()) return null
        return RosterPlayer(
            id = id,
            name = name,
            sweaterNumber = sweaterNumber,
            positionCode = positionCode?.trim()?.takeIf { it.isNotEmpty() },
            headshotUrl = headshot?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
