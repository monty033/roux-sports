package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import net.montycasa.roux.data.nhl.dto.ClubScheduleDto
import net.montycasa.roux.data.nhl.dto.ClubScheduleGameDto
import net.montycasa.roux.data.nhl.dto.GameDto
import net.montycasa.roux.data.nhl.dto.ScheduleDto
import net.montycasa.roux.data.nhl.dto.ScheduleTeamDto
import net.montycasa.roux.data.nhl.dto.ScoreDto
import net.montycasa.roux.data.nhl.dto.SeasonsDto
import net.montycasa.roux.data.nhl.dto.SeriesStatusDto
import net.montycasa.roux.data.nhl.dto.StandingsDto
import net.montycasa.roux.data.nhl.dto.TeamDto
import net.montycasa.roux.domain.model.Broadcast
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.ScheduleBlock
import net.montycasa.roux.domain.model.ScheduleDay
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.SeriesStatus
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
                // Absent or blank navigation dates are absence, not an invented day: the off-season
                // rule reads them as "no next game" rather than comparing against an empty string.
                previousGameDate = score.prevDate?.takeIf { it.isNotBlank() },
                nextGameDate = score.nextDate?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /**
     * A club's season skater and goalie statistics.
     *
     * Kept on the single adapter entry point as well as on [ClubStatsAdapter] so callers that already
     * use `NhlAdapter.parse…` do not need a second vocabulary.
     */
    fun parseClubStats(json: String): ParseResult<ClubStats> = ClubStatsAdapter.parse(json)

    /**
     * One week of the league schedule, reduced to the dates and game types opening night needs.
     *
     * A missing `gameWeek` is a failure; an individual day without a usable date is skipped, because
     * the walk only needs the days that can be placed on the calendar.
     */
    fun parseSchedule(json: String): ParseResult<ScheduleBlock> {
        val schedule =
            try {
                feedJson.decodeFromString<ScheduleDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL schedule decoding failed: ${exception.message}")
            }
        val wireDays = schedule.gameWeek ?: return ParseResult.Failure("NHL schedule mapping failed: missing gameWeek field")
        val days =
            wireDays.mapNotNull { day ->
                val date = day?.date?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ScheduleDay(date, day.games.orEmpty().mapNotNull { it?.gameType })
            }
        return ParseResult.Success(
            ScheduleBlock(
                days = days,
                nextStartDate = schedule.nextStartDate?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /**
     * A club's season schedule.
     *
     * An empty games array is a failure here, unlike a score day where an off day is a legitimate
     * empty slate: every club has a season schedule, so zero games means the response is not the
     * one that was asked for. Individual unusable rows are dropped, but if none survive that is the
     * same failure.
     */
    fun parseClubSchedule(json: String): ParseResult<List<ClubGame>> {
        val schedule =
            try {
                feedJson.decodeFromString<ClubScheduleDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL club schedule decoding failed: ${exception.message}")
            }
        val wireGames =
            schedule.games ?: return ParseResult.Failure("NHL club schedule mapping failed: missing games field")
        if (wireGames.isEmpty()) {
            return ParseResult.Failure("NHL club schedule mapping failed: empty games list")
        }
        val games =
            wireGames.mapNotNull { element ->
                try {
                    element?.let { feedJson.decodeFromJsonElement<ClubScheduleGameDto>(it).toClubGame() }
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        if (games.isEmpty()) {
            return ParseResult.Failure("NHL club schedule mapping failed: no usable games")
        }
        return ParseResult.Success(games)
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
                            // Place-free club name. A blank common name is absence, not a name.
                            shortName = row.teamCommonName?.default?.takeIf { it.isNotBlank() },
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

    /** A schedule row needs an id, a date and both clubs; optional details stay optional. */
    private fun ClubScheduleGameDto.toClubGame(): ClubGame? {
        val gameId = id ?: return null
        val gameDate = gameDate?.takeIf { it.isNotBlank() } ?: return null
        val home = homeTeam?.toScheduleTeamRef() ?: return null
        val away = awayTeam?.toScheduleTeamRef() ?: return null
        val rawState = gameState ?: ""
        val state = GameState.fromFeed(rawState)
        val scheduleState = gameScheduleState?.takeIf { it.isNotBlank() }
        // `OFF` is the feed's other finished token; gameOutcome alone also marks a completed game.
        val finished =
            state == GameState.Final ||
                gameOutcome != null ||
                rawState.trim().equals("OFF", ignoreCase = true)
        return ClubGame(
            id = GameId(gameId.toString()),
            date = gameDate,
            startTimeUtc = startTimeUTC?.let { Instant.parseOrNull(it) },
            venue = venue?.default?.takeIf { it.isNotBlank() },
            home = home,
            away = away,
            state = state,
            finished = finished,
            gameScheduleState = scheduleState,
            homeScore = homeTeam.score,
            awayScore = awayTeam.score,
        )
    }

    /** A club's full schedule name is its place followed by its common name. */
    private fun ScheduleTeamDto.toScheduleTeamRef(): TeamRef? {
        val abbreviation = abbrev?.takeIf { it.isNotBlank() } ?: return null
        val common = commonName?.default?.takeIf { it.isNotBlank() }
        val place = placeName?.default?.takeIf { it.isNotBlank() }
        val fullName =
            listOfNotNull(place, common)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
        return TeamRef(
            key = TeamKey(abbreviation),
            name = fullName,
            shortName = common,
            logoUrl = logo,
            darkLogoUrl = darkLogo,
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
            // Broadcasts are decoration, never identity: an unreadable entry drops on its own.
            broadcasts =
                tvBroadcasts.orEmpty().mapNotNull { broadcast ->
                    broadcast?.network?.let { network ->
                        Broadcast(
                            network.trim(),
                            broadcast.countryCode?.trim(),
                            broadcast.market?.trim(),
                        )
                    }
                },
            otPeriods = gameOutcome?.otPeriods,
            // A malformed status was already decoded to null at the optional-object boundary.
            series = seriesStatus?.toSeriesStatus(),
        )
    }

    /** An all-null status object says nothing about a series, so it is absent rather than an empty value. */
    private fun SeriesStatusDto.toSeriesStatus(): SeriesStatus? {
        val status =
            SeriesStatus(
                round = round,
                seriesTitle = seriesTitle?.takeIf { it.isNotBlank() },
                neededToWin = neededToWin,
                topSeedTeamAbbrev = topSeedTeamAbbrev?.takeIf { it.isNotBlank() },
                topSeedWins = topSeedWins,
                bottomSeedTeamAbbrev = bottomSeedTeamAbbrev?.takeIf { it.isNotBlank() },
                bottomSeedWins = bottomSeedWins,
                gameNumberOfSeries = gameNumberOfSeries,
            )
        return status.takeIf { it != SeriesStatus() }
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
