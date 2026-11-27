package net.montycasa.roux.data.nfl

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.data.nfl.dto.NflGameLogDto
import net.montycasa.roux.data.nfl.dto.NflPlayerDto
import net.montycasa.roux.data.nfl.dto.NflPlayerStatDto
import net.montycasa.roux.data.nfl.dto.NflTeamsTeamDto
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflGameColumn
import net.montycasa.roux.domain.model.NflGameLog
import net.montycasa.roux.domain.model.NflGameLogEntry
import net.montycasa.roux.domain.model.NflGameStatGroup
import net.montycasa.roux.domain.model.NflPlayerId
import net.montycasa.roux.domain.model.NflPlayerProfile
import net.montycasa.roux.domain.model.NflPlayerSeason
import net.montycasa.roux.domain.model.NflSeasonStat
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef

/**
 * Turns ESPN's football player payloads into the player page's model.
 *
 * The two parsers are deliberately independent: the profile and the game log are separate requests, so a malformed log
 * can never prevent the profile from rendering and vice versa.
 *
 * The game log's columns are built from the feed alone. The top-level `names`, `displayNames` and `labels` arrays are
 * parallel, and `categories[].count` partitions them; no position is consulted and no column list is written here. A
 * kicker therefore renders his field-goal columns with exactly the same code as a quarterback's passing ones.
 */
internal object NflPlayerAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parseProfile(json: String): ParseResult<NflPlayerProfile> {
        val payload =
            try {
                feedJson.decodeFromString<NflPlayerDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL player profile decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NFL player profile decoding failed: ${exception.message}")
            }

        val athlete = payload.athlete ?: return ParseResult.Failure("NFL player profile mapping failed: no athlete")
        val id =
            athlete.id?.trim()?.takeIf { it.isNotEmpty() }
                ?: return ParseResult.Failure("NFL player profile mapping failed: no player id")
        val name =
            athlete.displayName?.takeIf { it.isNotBlank() }
                ?: athlete.fullName?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(
                    athlete.firstName?.takeIf { it.isNotBlank() },
                    athlete.lastName?.takeIf { it.isNotBlank() },
                ).joinToString(" ").takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("NFL player profile mapping failed: no player name")

        val position = athlete.position
        return ParseResult.Success(
            NflPlayerProfile(
                playerId = NflPlayerId(id),
                name = name,
                positionAbbreviation = position?.abbreviation?.trim()?.takeIf { it.isNotEmpty() },
                positionName = position?.displayName?.trim()?.takeIf { it.isNotEmpty() },
                jersey = athlete.displayJersey?.trim()?.takeIf { it.isNotEmpty() },
                headshotUrl = athlete.headshot?.href?.takeIf { it.isOnArtworkHost() },
                heightText = athlete.displayHeight?.takeIf { it.isNotBlank() },
                weightText = athlete.displayWeight?.takeIf { it.isNotBlank() },
                age = athlete.age,
                college =
                    athlete.college
                        ?.name
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                experienceText = athlete.displayExperience?.trim()?.takeIf { it.isNotEmpty() },
                team = athlete.team?.toRef(),
                season = payload.season?.toSeason(),
                seasonSummaryTitle =
                    athlete.statsSummary
                        ?.displayName
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                seasonStats =
                    athlete.statsSummary
                        ?.statistics
                        .orEmpty()
                        .flatMap { it.toSeasonStats() },
            ),
        )
    }

    fun parseGameLog(json: String): ParseResult<NflGameLog> {
        val payload =
            try {
                feedJson.decodeFromString<NflGameLogDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL player game log decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NFL player game log decoding failed: ${exception.message}")
            }

        val names = payload.names.orEmpty()
        val displayNames = payload.displayNames.orEmpty()
        val labels = payload.labels.orEmpty()
        val columns =
            names.indices.mapNotNull { index ->
                val name = names.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val displayName =
                    displayNames.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: name
                NflGameColumn(
                    name = name,
                    displayName = displayName,
                    shortLabel = labels.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: displayName,
                )
            }
        val columnsByName = columns.associateBy { it.name }
        val groups = columns.toGroups(payload.categories.orEmpty())

        val seasonType = payload.seasonTypes.orEmpty().firstOrNull()
        val eventCategory =
            seasonType
                ?.categories
                .orEmpty()
                .firstOrNull { it.type == EVENT_TYPE && !it.events.isNullOrEmpty() }
                ?: seasonType?.categories.orEmpty().firstOrNull { !it.events.isNullOrEmpty() }
        val eventMetadata = payload.events.orEmpty()
        val games =
            eventCategory
                ?.events
                .orEmpty()
                .mapNotNull { row ->
                    val eventId = row.eventId?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val meta = eventMetadata[eventId]
                    NflGameLogEntry(
                        eventId = eventId,
                        week = meta?.week,
                        gameDate = meta?.gameDate?.takeIf { it.isNotBlank() },
                        atVs = meta?.atVs?.trim()?.takeIf { it.isNotEmpty() },
                        opponentAbbreviation =
                            meta
                                ?.opponent
                                ?.abbreviation
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() },
                        teamAbbreviation =
                            meta
                                ?.team
                                ?.abbreviation
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() },
                        score = meta?.score?.trim()?.takeIf { it.isNotEmpty() },
                        result = meta?.gameResult?.trim()?.takeIf { it.isNotEmpty() },
                        stats =
                            row.stats
                                .orEmpty()
                                .mapIndexedNotNull { index, raw ->
                                    val columnName =
                                        names.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
                                            ?: return@mapIndexedNotNull null
                                    // A dash is the feed's way of stating no value; zero is a value and stays "0".
                                    val value =
                                        raw?.trim()?.takeIf { it.isNotEmpty() && it != ABSENT_VALUE }
                                            ?: return@mapIndexedNotNull null
                                    if (columnName !in columnsByName) return@mapIndexedNotNull null
                                    columnName to value
                                }.toMap(),
                    )
                }

        return ParseResult.Success(
            NflGameLog(
                seasonLabel = seasonType?.displayName?.trim()?.takeIf { it.isNotEmpty() },
                groups = groups,
                games = games,
            ),
        )
    }

    /**
     * The heading a column group is drawn under.
     *
     * The feed's own label is used whenever it is a label: "Passing" and "Rushing" arrive capitalized and are printed
     * as they are. Some categories arrive as a bare identifier instead — a kicker's groups are stated as `fieldgoals`
     * and `pats` — and a table headed "fieldgoals" reads as a bug, so those few keys get a spelled heading. This
     * renames a heading only: which columns exist, and how many, still comes from the feed's counts.
     */
    private fun headingFor(
        name: String,
        stated: String,
    ): String {
        if (stated.isNotEmpty() && stated != name) return stated
        return when (name) {
            "fieldgoals" -> "Field Goals"
            "pats" -> "Extra Points"
            "defense" -> "Defense"
            "returns" -> "Returns"
            "kicking" -> "Kicking"
            "punting" -> "Punting"
            "receiving" -> "Receiving"
            "passing" -> "Passing"
            "rushing" -> "Rushing"
            else -> stated.ifEmpty { name }
        }
    }

    /**
     * Partitions the columns by the feed's category counts. The counts are the only grouping statement in the payload.
     *
     * A count that runs past the available columns is clamped rather than trusted; a column left over after the last
     * category joins that category rather than disappearing, because a table that silently drops a stat is worse than
     * one whose last heading covers one more column.
     */
    private fun List<NflGameColumn>.toGroups(
        categories: List<net.montycasa.roux.data.nfl.dto.NflGameLogCategoryDto>,
    ): List<NflGameStatGroup> {
        if (isEmpty()) return emptyList()
        if (categories.isEmpty()) {
            return listOf(NflGameStatGroup(name = "", displayName = "", columns = this))
        }

        val groups = mutableListOf<NflGameStatGroup>()
        var cursor = 0
        for (category in categories) {
            if (cursor >= size) break
            val count = (category.count ?: 0).coerceAtLeast(0)
            val end = (cursor + count).coerceAtMost(size)
            if (end > cursor) {
                groups +=
                    NflGameStatGroup(
                        name =
                            category.name
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                .orEmpty(),
                        displayName =
                            headingFor(
                                name = category.name?.trim().orEmpty(),
                                stated = category.displayName?.trim().orEmpty(),
                            ),
                        columns = subList(cursor, end).toList(),
                    )
                cursor = end
            }
        }
        if (cursor < size) {
            val remaining = subList(cursor, size).toList()
            val last = groups.lastOrNull()
            if (last == null) {
                groups += NflGameStatGroup(name = "", displayName = "", columns = remaining)
            } else {
                groups[groups.lastIndex] = last.copy(columns = last.columns + remaining)
            }
        }
        return groups
    }

    /** One summary entry may hold its own nested list; otherwise the entry is the statistic. */
    private fun NflPlayerStatDto.toSeasonStats(): List<NflSeasonStat> =
        stats.orEmpty().flatMap { it.toSeasonStats() }.ifEmpty { listOfNotNull(toSeasonStat()) }

    private fun NflPlayerStatDto.toSeasonStat(): NflSeasonStat? {
        val value = displayValue?.takeIf { it.isNotBlank() } ?: return null
        val label =
            displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: name?.trim()?.takeIf { it.isNotEmpty() }
                ?: return null
        return NflSeasonStat(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: label,
            displayName = label,
            displayValue = value,
        )
    }

    private fun net.montycasa.roux.data.nfl.dto.NflPlayerSeasonDto.toSeason(): NflPlayerSeason =
        NflPlayerSeason(
            year = year,
            displayName =
                displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: type?.displayName?.trim()?.takeIf { it.isNotEmpty() },
        )

    private fun NflTeamsTeamDto.toRef(): TeamRef? {
        val abbreviation = abbreviation?.trim()?.takeIf { it.isNotEmpty() } ?: return null
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

    private fun String.isOnArtworkHost(): Boolean =
        try {
            val url = Url(this)
            url.protocol == URLProtocol.HTTPS && url.host == EspnHttpClientFactory.LOGO_HOST
        } catch (_: IllegalArgumentException) {
            false
        }

    /** The feed's marker for "no value", which must never reach the screen as text. */
    private const val ABSENT_VALUE = "-"

    /** The season-type category whose rows are games rather than an aggregate. */
    private const val EVENT_TYPE = "event"
}
