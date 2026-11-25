package net.montycasa.roux.data.nfl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nfl.dto.NflStandingsDto
import net.montycasa.roux.data.nfl.dto.NflStandingsEntryDto
import net.montycasa.roux.data.nfl.dto.NflStandingsStatDto
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflStandingRow
import net.montycasa.roux.domain.model.NflStandingTable
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.math.roundToInt

/**
 * Turns ESPN's NFL standings into domain tables.
 *
 * The mapping keeps the feed's order and the feed's words. Standings arrive already ranked, so the adapter sorts
 * nothing: a table that arrived out of order is a feed problem worth seeing, not one to paper over with a comparator
 * whose tie-breaks this app would have to invent.
 *
 * Two figures need care, and the census in `docs/nfl-sources.md` is why:
 *
 * - A record is stated as a display string, not a number. `divisionRecord` carries `value: 0.0` and
 *   `displayValue: "1-1"`, so reading the numeric field would state every division record as zero.
 * - A missing figure stays missing. A stat the feed omits becomes null and is drawn blank, never zero: a club with no
 *   points and a club whose points the feed did not state are different facts.
 */
internal object NflStandingsAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /** Reads one standings payload, at either level: two conference tables, or four division tables. */
    fun parse(json: String): ParseResult<List<NflStandingTable>> {
        val standings =
            try {
                feedJson.decodeFromString<NflStandingsDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL standings decoding failed: ${exception.message}")
            }

        val groups = standings.children ?: return ParseResult.Failure("NFL standings mapping failed: missing children field")
        val tables =
            groups.mapNotNull { group ->
                val name = group.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // A group with no rows is how the feed answers a level it does not publish for that group; carrying
                // an empty table would put a heading on the screen with nothing under it.
                val rows =
                    group.standings
                        ?.entries
                        .orEmpty()
                        .mapNotNull { it.toRow() }
                if (rows.isEmpty()) null else NflStandingTable(name = name, rows = rows)
            }

        return if (tables.isEmpty()) {
            ParseResult.Failure("NFL standings mapping failed: no usable rows", emptyPayload = true)
        } else {
            ParseResult.Success(tables)
        }
    }

    private fun NflStandingsEntryDto.toRow(): NflStandingRow? {
        val team = team ?: return null
        val abbreviation = team.abbreviation?.takeIf { it.isNotBlank() } ?: return null
        val stats = stats.orEmpty()
        val logos = team.logos.orEmpty()
        val light = logos.firstOrNull { it.rel?.contains("full") == true && it.href?.endsWith(".png") == true }
        val dark = logos.firstOrNull { it.rel?.contains("dark") == true && it.href?.endsWith(".png") == true }

        return NflStandingRow(
            team =
                TeamRef(
                    key = TeamKey(League.NFL, abbreviation),
                    name = team.displayName ?: team.name,
                    shortName = team.shortDisplayName,
                    logoUrl = light?.href,
                    darkLogoUrl = dark?.href,
                ),
            wins = stats.count("wins"),
            losses = stats.count("losses"),
            ties = stats.count("ties"),
            winPercent = stats.display("winPercent"),
            pointsFor = stats.count("pointsFor"),
            pointsAgainst = stats.count("pointsAgainst"),
            // The feed states the same figure twice under two names; either is the feed's own total.
            differential = stats.count("differential") ?: stats.count("pointDifferential"),
            streak = stats.display("streak"),
            divisionRecord = stats.display("divisionRecord"),
            conferenceRecord = stats.display("vs. Conf.") ?: stats.display("vsconf"),
            playoffSeed = stats.count("playoffSeed")?.takeIf { it > 0 },
        )
    }

    /** A stat by name, falling back to its type where the feed left the name empty. */
    private fun List<NflStandingsStatDto>.stat(key: String): NflStandingsStatDto? =
        firstOrNull { it.name == key } ?: firstOrNull { it.type == key }

    /** A count, rounded from the feed's double. Null where the feed stated no number. */
    private fun List<NflStandingsStatDto>.count(key: String): Int? = stat(key)?.value?.roundToInt()

    /** A display string, kept verbatim. Null where the feed stated nothing to print. */
    private fun List<NflStandingsStatDto>.display(key: String): String? = stat(key)?.displayValue?.takeIf { it.isNotBlank() }
}
