package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import net.montycasa.roux.data.nhl.dto.GameDto
import net.montycasa.roux.data.nhl.dto.ScoreDto
import net.montycasa.roux.data.nhl.dto.TeamDto
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef

/** A failed response must remain distinguishable from a successfully fetched empty day. */
internal sealed interface ParseResult<out T> {
    /** Carry usable domain data without exposing wire DTOs to repository code. */
    data class Success<T>(
        val value: T,
    ) : ParseResult<T>

    /** Retain diagnostic context so a broken response can be investigated. */
    data class Failure(
        val reason: String,
    ) : ParseResult<Nothing>
}

/** Keeps undocumented NHL wire shapes behind a domain-only parsing boundary. */
internal object NhlAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /** Reject malformed responses while preserving usable games from incomplete payloads. */
    fun parseScore(json: String): ParseResult<ScoreDay> {
        val score =
            try {
                feedJson.decodeFromString<ScoreDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL score decoding failed: ${exception.message}")
            }

        // currentDate identifies the requested day even when games is empty; the capture date,
        // prevDate and nextDate do not (the empty-day fixture's currentDate is 2026-07-15).
        val date =
            score.currentDate?.takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("NHL score mapping failed: missing or blank currentDate")
        val wireGames =
            score.games ?: return ParseResult.Failure("NHL score mapping failed: missing games field")
        // Isolate unusable rows; optional detail objects have their own nullable decoding boundary.
        // Keep the original list so droppedCount includes malformed elements as well as omissions.
        val games =
            wireGames.mapNotNull { element ->
                try {
                    element?.let { feedJson.decodeFromJsonElement<GameDto>(it).toGame() }
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        // Incomplete navigation entries cannot become DateIndexEntry without inventing values.
        val dateIndex =
            score.gameWeek.orEmpty().mapNotNull { entry ->
                val entryDate = entry?.date?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val numberOfGames = entry.numberOfGames ?: return@mapNotNull null
                DateIndexEntry(entryDate, numberOfGames)
            }
        return ParseResult.Success(
            ScoreDay(
                date = date,
                games = games,
                dateIndex = dateIndex,
                droppedCount = wireGames.size - games.size,
            ),
        )
    }

    /** Missing required identity or date drops a row rather than fabricating domain data. */
    private fun GameDto.toGame(): Game? {
        val gameId = id ?: return null
        val date = gameDate?.takeIf { it.isNotBlank() } ?: return null
        // An empty raw state means the feed omitted the field.
        val state = gameState ?: ""
        val home = homeTeam?.toTeamRef() ?: return null
        val away = awayTeam?.toTeamRef() ?: return null
        // A clock alone cannot establish a period ordinal; leave optional progress absent.
        val period =
            periodDescriptor?.number?.let { number ->
                PeriodInfo(
                    ordinal = number,
                    periodType = periodDescriptor.periodType,
                    clock = clock?.timeRemaining,
                    inIntermission = clock?.inIntermission ?: false,
                )
            }
        return Game(
            id = GameId(gameId.toString()),
            date = date,
            state = GameState.fromFeed(state),
            home = home,
            away = away,
            homeScore = homeTeam.score,
            awayScore = awayTeam.score,
            finalKind = FinalKind.from(gameOutcome?.lastPeriodType),
            period = period,
            startTimeUtc = startTimeUTC,
        )
    }

    /** An absent abbreviation must be visible as a dropped game rather than a broken team row. */
    private fun TeamDto.toTeamRef(): TeamRef? {
        val abbreviation = abbrev?.takeIf { it.isNotBlank() } ?: return null
        return TeamRef(
            key = TeamKey(abbreviation),
            name = name?.default,
            logoUrl = logo,
            // No score fixture carries darkLogo today (the schedule fixture does); retain it
            // so a future score capture can populate the domain asset without an adapter change.
            darkLogoUrl = darkLogo,
        )
    }
}
