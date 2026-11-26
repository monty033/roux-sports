package net.montycasa.roux.data.nfl

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.data.nfl.dto.NflScheduleDto
import net.montycasa.roux.data.nfl.dto.NflScheduleEventDto
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflSeasonEntry
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.time.Instant

/**
 * Turns ESPN's NFL club schedule into a football season.
 *
 * Two rules shape it, and both come from what the payload actually states:
 *
 * - **The bye comes from the games, not from the feed's `byeWeek` field.** That field is not trustworthy: Buffalo's
 *   captured schedule states week 5 while Buffalo plays in week 5 and sits out week 7. Seventeen games in eighteen
 *   weeks leave exactly one empty week, so the empty week is the bye, and the stated number is carried alongside it as
 *   evidence rather than being preferred over a game the feed also states.
 * - **A status the census never saw stays unknown.** `status.type.name` is the only discriminator the feed offers, and
 *   anything outside the observed set becomes [GameState.Unknown] rather than being guessed into scheduled or final.
 *
 * Scores are taken from each competitor's `score.value` and only on a completed game: a scheduled fixture carries
 * placeholder numbers that would read as a 0-0 result if they were trusted.
 */
internal object NflClubScheduleAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<NflClubSchedule> {
        val payload =
            try {
                feedJson.decodeFromString<NflScheduleDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL schedule decoding failed: ${exception.message}")
            }

        val team = payload.team?.toRef() ?: return ParseResult.Failure("NFL schedule mapping failed: no club")
        val events = payload.events.orEmpty()
        val games = events.mapNotNull { it.toEntry() }
        if (games.isEmpty()) {
            return ParseResult.Failure("NFL schedule mapping failed: no usable events", emptyPayload = true)
        }

        val seasonYear =
            payload.season?.year
                ?: events.firstNotNullOfOrNull { it.season?.year }
                ?: return ParseResult.Failure("NFL schedule mapping failed: no season year")

        // Seventeen games in eighteen weeks leave exactly one empty week, and that empty week is the bye.
        val played = games.mapTo(mutableSetOf()) { it.week }
        val byeWeek = (1..REGULAR_SEASON_WEEKS).firstOrNull { it !in played }
        val entries =
            if (byeWeek != null) {
                (games + NflSeasonEntry.Bye(byeWeek)).sortedBy { it.week }
            } else {
                games
            }

        return ParseResult.Success(
            NflClubSchedule(
                seasonYear = seasonYear,
                team = team,
                byeWeek = byeWeek,
                statedByeWeek = payload.byeWeek?.takeIf { it > 0 },
                entries = entries,
            ),
        )
    }

    private fun NflScheduleEventDto.toEntry(): NflSeasonEntry.Game? {
        val week = week?.number ?: return null
        val competition = competitions?.firstOrNull() ?: return null
        val competitors = competition.competitors.orEmpty()
        val home = competitors.firstOrNull { it.homeAway == "home" } ?: return null
        val away = competitors.firstOrNull { it.homeAway == "away" } ?: return null
        val id = id ?: return null
        val status = competition.status?.type
        val name = status?.name
        val state =
            when (name) {
                STATUS_FINAL -> GameState.Final
                STATUS_SCHEDULED -> GameState.Scheduled
                STATUS_PRE -> GameState.Pre
                STATUS_POSTPONED -> GameState.Unknown(name)
                STATUS_CANCELED -> GameState.Unknown(name)
                null -> GameState.Unknown("STATUS_UNKNOWN")
                else -> GameState.Unknown(name)
            }
        val completed = status?.completed == true || state == GameState.Final

        return NflSeasonEntry.Game(
            week = week,
            game =
                ClubGame(
                    id = GameId(League.NFL, id),
                    // The feed states a full instant; the day label is taken from the instant, not from the string, so
                    // a game after midnight UTC is filed on the day it is played where it is played.
                    date = date?.take(10).orEmpty(),
                    startTimeUtc = date?.toInstantOrNull(),
                    venue = competition.venue?.fullName?.takeIf { it.isNotBlank() },
                    home = home.team?.toRef() ?: TeamRef(TeamKey(League.NFL, home.team?.abbreviation.orEmpty())),
                    away = away.team?.toRef() ?: TeamRef(TeamKey(League.NFL, away.team?.abbreviation.orEmpty())),
                    state = state,
                    finished = completed,
                    homeScore = if (completed) home.score?.value?.toInt() else null,
                    awayScore = if (completed) away.score?.value?.toInt() else null,
                ),
        )
    }

    private fun net.montycasa.roux.data.nfl.dto.NflScheduleTeamDto.toRef(): TeamRef? {
        val abbreviation = abbreviation?.takeIf { it.isNotBlank() } ?: return null
        return TeamRef(
            key = TeamKey(League.NFL, abbreviation),
            name = displayName?.takeIf { it.isNotBlank() },
            shortName = name?.takeIf { it.isNotBlank() },
            logoUrl = logo?.takeIf { it.isOnArtworkHost() },
        )
    }

    /**
     * The feed writes kickoffs without seconds (`2026-09-13T17:00Z`), which the strict ISO parser rejects. The clock is
     * kept as the feed states it; only the missing `:00` is supplied, and a string that still will not parse yields
     * no instant rather than a guessed one.
     */
    private fun String.toInstantOrNull(): Instant? =
        runCatching { Instant.parse(this) }.getOrNull()
            ?: runCatching { Instant.parse(replace(SECONDLESS_KICKOFF, "$1:00Z")) }.getOrNull()

    private fun String.isOnArtworkHost(): Boolean =
        try {
            val url = Url(this)
            url.protocol == URLProtocol.HTTPS && url.host == EspnHttpClientFactory.LOGO_HOST
        } catch (_: IllegalArgumentException) {
            false
        }

    private val SECONDLESS_KICKOFF = Regex("""(T\d{2}:\d{2})Z""")

    /** An NFL regular season is eighteen weeks: seventeen played, one by. */
    private const val REGULAR_SEASON_WEEKS = 18

    private const val STATUS_FINAL = "STATUS_FINAL"
    private const val STATUS_SCHEDULED = "STATUS_SCHEDULED"
    private const val STATUS_PRE = "STATUS_PRE"
    private const val STATUS_POSTPONED = "STATUS_POSTPONED"
    private const val STATUS_CANCELED = "STATUS_CANCELED"
}
