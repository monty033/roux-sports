package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import net.montycasa.roux.data.nhl.dto.GameDto
import net.montycasa.roux.data.nhl.dto.ScoreDto
import net.montycasa.roux.data.nhl.dto.SeasonsDto
import net.montycasa.roux.data.nhl.dto.StandingsDto
import net.montycasa.roux.data.nhl.dto.TeamDto
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.time.Instant

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

    /** Reject incomplete league tables so repositories can serve cached or stale data. */
    fun parseStandings(json: String): ParseResult<List<StandingRow>> {
        val standings =
            try {
                feedJson.decodeFromString<StandingsDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL standings decoding failed: ${exception.message}")
            }
        val wireRows =
            standings.standings ?: return ParseResult.Failure("NHL standings mapping failed: missing standings field")
        // A score day can drop and count an unusable game and still be useful. A missing team
        // makes league rankings and the wildcard cut line misleading, so fail the whole table.
        // Missing or blank required names are fatal: they leave team identity or group headings empty.
        // An unrecognised non-blank group name is survivable because Unknown preserves its label.
        val rows =
            wireRows.mapIndexed { index, row ->
                fun missing(field: String) = ParseResult.Failure("NHL standings mapping failed: row $index missing $field")

                if (row == null) return missing("row (null element)")
                val abbreviation = row.teamAbbrev?.default?.takeIf { it.isNotBlank() } ?: return missing("teamAbbrev.default")
                val gamesPlayed = row.gamesPlayed ?: return missing("gamesPlayed")
                val wins = row.wins ?: return missing("wins")
                val losses = row.losses ?: return missing("losses")
                val points = row.points ?: return missing("points")
                val leagueSequence = row.leagueSequence ?: return missing("leagueSequence")
                val conferenceSequence = row.conferenceSequence ?: return missing("conferenceSequence")
                val divisionSequence = row.divisionSequence ?: return missing("divisionSequence")
                val wildcardSequence = row.wildcardSequence ?: return missing("wildcardSequence")
                val conferenceName = row.conferenceName?.takeIf { it.isNotBlank() } ?: return missing("conferenceName")
                val divisionName = row.divisionName?.takeIf { it.isNotBlank() } ?: return missing("divisionName")
                StandingRow(
                    team =
                        TeamRef(
                            key = TeamKey(abbreviation),
                            name = row.teamName?.default,
                            logoUrl = row.teamLogo,
                            darkLogoUrl = row.teamLogoDark,
                        ),
                    gp = gamesPlayed,
                    w = wins,
                    l = losses,
                    otl = row.otLosses,
                    pts = points,
                    leagueSequence = leagueSequence,
                    conferenceSequence = conferenceSequence,
                    divisionSequence = divisionSequence,
                    wildcardSequence = wildcardSequence,
                    conference = Conference.fromFeed(conferenceName),
                    division = Division.fromFeed(divisionName),
                )
            }
        if (rows.isEmpty()) {
            return ParseResult.Failure("NHL standings mapping failed: empty standings table")
        }
        val duplicateTeamKeys =
            rows
                .groupingBy { it.team.key }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        if (duplicateTeamKeys.isNotEmpty()) {
            return ParseResult.Failure("NHL standings mapping failed: duplicate team keys=$duplicateTeamKeys")
        }
        // Require internally consistent league-wide numbering to catch gaps and collisions.
        // This does not prove completeness: a shorter table can still be numbered 1..N.
        val sequenceCounts = rows.groupingBy { it.leagueSequence }.eachCount()
        val expectedSequences = (1..rows.size).toSet()
        val missingSequences = expectedSequences - sequenceCounts.keys
        val unexpectedSequences = sequenceCounts.keys - expectedSequences
        val duplicateSequences = sequenceCounts.filterValues { it > 1 }.keys
        if (missingSequences.isNotEmpty() || unexpectedSequences.isNotEmpty() || duplicateSequences.isNotEmpty()) {
            return ParseResult.Failure(
                "NHL standings mapping failed: leagueSequence must be exactly 1..${rows.size}; " +
                    "missing=${missingSequences.sorted()}, duplicates=${duplicateSequences.sorted()}, " +
                    "out of range=${unexpectedSequences.sorted()}",
            )
        }
        // Completeness cannot be derived from the payload alone: an internally consistent
        // table short a team is indistinguishable from a smaller league. Size must come from outside it.
        if (rows.size != NhlLeagueShape.EXPECTED_TEAM_COUNT) {
            return ParseResult.Failure(
                "NHL standings mapping failed: expected ${NhlLeagueShape.EXPECTED_TEAM_COUNT} teams, " +
                    "actual ${rows.size}; league shape may have changed",
            )
        }
        // A fabricated replacement team with a distinct key, expected size and clean 1..N
        // numbering remains undetectable without pinning the roster. Accept that limit here:
        // pinning abbreviations would break on a rename or relocation.
        return ParseResult.Success(rows)
    }

    /** Preserve the feed's season order and optional standings dates. */
    fun parseSeasons(json: String): ParseResult<List<Season>> {
        val history =
            try {
                feedJson.decodeFromString<SeasonsDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL seasons decoding failed: ${exception.message}")
            }
        val wireSeasons =
            history.seasons ?: return ParseResult.Failure("NHL seasons mapping failed: missing seasons field")
        // IDs ascend over time: callers choose the latest with maxBy { it.id }; do not sort here.
        val seasons =
            wireSeasons.mapIndexed { index, season ->
                val id = season?.id ?: return ParseResult.Failure("NHL seasons mapping failed: row $index missing id")
                Season(id, start = season.standingsStart, end = season.standingsEnd)
            }
        return ParseResult.Success(seasons)
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
                    running = clock?.running,
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
            homeShots = (homeTeam.sog as? JsonPrimitive)?.intOrNull,
            awayShots = (awayTeam.sog as? JsonPrimitive)?.intOrNull,
            finalKind = FinalKind.from(gameOutcome?.lastPeriodType),
            period = period,
            // A bad or absent timestamp must never drop a game: its score is still useful.
            startTimeUtc = startTimeUTC?.let { Instant.parseOrNull(it) },
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
