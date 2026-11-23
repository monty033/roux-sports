package net.montycasa.roux.data.nfl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nfl.dto.NflCompetitionDto
import net.montycasa.roux.data.nfl.dto.NflCompetitorDto
import net.montycasa.roux.data.nfl.dto.NflEventDto
import net.montycasa.roux.data.nfl.dto.NflScoreboardDto
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.Broadcast
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.FootballSituation
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.time.Instant

/**
 * Turns ESPN's NFL scoreboard into domain games.
 *
 * The mapping follows what the census recorded in `docs/nfl-sources.md` rather than what the feed's field names
 * suggest. Two rules matter most:
 *
 * - `status.type.name` is the only safe state discriminator. The feed's `state` and `completed` fields describe the
 *   same life differently, and only the name was consistent across every state the corpus holds.
 * - A scoreboard final carries a period of 4 or 5 and a clock of `0:00`, so a final is never mistaken for live play.
 */
internal object NflScoreboardAdapter {
    /** The status names the census observed. Anything else stays [GameState.Unknown] rather than being guessed at. */
    private const val STATUS_FINAL = "STATUS_FINAL"
    private const val STATUS_SCHEDULED = "STATUS_SCHEDULED"

    /**
     * A scheduled game is one the feed has not started. The feed also has a pregame state that this adapter does
     * not yet distinguish: the corpus holds no pregame sample, so the two cannot be told apart from evidence.
     */
    private const val STATUS_PRE = "STATUS_PRE"
    private const val STATUS_IN_PROGRESS = "STATUS_IN_PROGRESS"

    /** Four quarters; a period above this one is overtime. */
    private const val NFL_REGULATION_PERIODS = 4

    /** A kickoff written to the minute, with no seconds: `2026-11-22T18:00Z`. */
    private val MINUTE_PRECISION = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}Z""")

    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /**
     * Parse a scoreboard into a day of games.
     *
     * The date comes from the first event rather than from a request parameter: the feed's week scoreboard has no
     * single day, and the date a caller asked for is the date it already knows.
     */
    fun parseScoreboard(
        json: String,
        requestedDate: String,
    ): ParseResult<ScoreDay> {
        val scoreboard =
            try {
                feedJson.decodeFromString<NflScoreboardDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL scoreboard decoding failed: ${exception.message}")
            }

        val wireEvents = scoreboard.events ?: return ParseResult.Failure("NFL scoreboard mapping failed: missing events field")
        val week = scoreboard.week?.number

        val games =
            wireEvents.mapNotNull { event ->
                try {
                    event.toGame(requestedDate)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

        return ParseResult.Success(
            ScoreDay(
                date = requestedDate,
                games = games,
                // ESPN's scoreboard carries no date index the way the NHL feed does, so there is none to report.
                dateIndex = emptyList(),
                droppedCount = wireEvents.size - games.size,
                week = week,
            ),
        )
    }

    /**
     * The kickoff time, when the feed states one that can be read.
     *
     * ESPN writes a kickoff as `2026-11-22T18:00Z`, with no seconds, which an ISO instant parser rejects. The
     * seconds are added back rather than the time being dropped, so a scheduled game still knows when it starts.
     */
    private fun String?.toInstantOrNull(): Instant? {
        val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val withSeconds =
            if (MINUTE_PRECISION.matches(raw)) "${raw.dropLast(1)}:00Z" else raw

        return runCatching { Instant.parse(withSeconds) }.getOrNull()
    }

    /** One event into a game, or null when the event cannot be read. */
    private fun NflEventDto.toGame(requestedDate: String): Game? {
        val id = id?.takeIf { it.isNotBlank() } ?: return null
        val competition = competitions?.firstOrNull() ?: return null
        val competitors = competition.competitors ?: return null
        val home = competitors.firstOrNull { it.homeAway == "home" } ?: return null
        val away = competitors.firstOrNull { it.homeAway == "away" } ?: return null
        val statusName =
            competition.status
                ?.type
                ?.name
                ?.takeIf { it.isNotBlank() } ?: return null
        val livePeriod = if (statusName == STATUS_IN_PROGRESS) competition.livePeriod() else null

        return Game(
            id = GameId(League.NFL, id),
            date = date?.take(10)?.takeIf { it.isNotBlank() } ?: requestedDate,
            state = statusName.toGameState(livePeriod),
            period = livePeriod,
            home = home.toTeamRef(),
            away = away.toTeamRef(),
            homeScore = home.score?.toIntOrNull(),
            awayScore = away.score?.toIntOrNull(),
            finalKind = competition.finalKind(statusName),
            startTimeUtc = date.toInstantOrNull(),
            broadcasts = competition.toBroadcasts(),
            football = competition.toFootballSituation(competitors, livePeriod),
        )
    }

    /**
     * The state, from the status name alone.
     *
     * A live game needs more than its name: the feed states the period and clock on the same competition, and a live
     * name with no real period is not a fact worth inventing, so [livePeriod] decides whether this can be live at
     * all. Finals stay finals whatever their period and clock say - the census found a final carrying period 4 and
     * `0:00`, which is exactly what a live game never looks like.
     */
    private fun String.toGameState(livePeriod: PeriodInfo?): GameState =
        when (this) {
            STATUS_FINAL -> GameState.Final
            STATUS_SCHEDULED -> GameState.Scheduled
            STATUS_PRE -> GameState.Pre
            STATUS_IN_PROGRESS -> if (livePeriod == null) GameState.Unknown(this) else GameState.Live()
            else -> GameState.Unknown(this)
        }

    /**
     * The period and clock of a game being played, or null when the feed states no period.
     *
     * `running` is true for a mapped live game because an in-progress status carrying a period is the strongest
     * statement this feed makes; it has no running flag of its own. `inIntermission` stays false and is deliberately
     * not derived: the captured corpus holds no halftime or period-break payload, so reading a halt out of a clock
     * would be a guess. That state is unverified here, not supported.
     */
    private fun NflCompetitionDto.livePeriod(): PeriodInfo? {
        val period = status?.period?.takeIf { it > 0 } ?: return null
        return PeriodInfo(
            ordinal = period,
            periodType = if (period > NFL_REGULATION_PERIODS) "OT" else "REG",
            clock = status?.displayClock?.trim()?.takeIf { it.isNotEmpty() },
            inIntermission = false,
            running = true,
        )
    }

    /**
     * The snap a live game is on, or null when there is nothing honest to draw.
     *
     * Only a live game carries one: a final keeps whatever `situation` the feed last wrote, and that is a snapshot of
     * a game that is over. Within a live game the feed stops stating a down between plays - after a score, on a
     * kickoff, during a timeout - so a down outside 1-4 is dropped and the distance with it, while the possession and
     * the red-zone flag still stand. Possession arrives as a team id, so it is resolved against this same payload's
     * competitors; an id that names neither side is dropped rather than shown as a bare number.
     */
    private fun NflCompetitionDto.toFootballSituation(
        competitors: List<NflCompetitorDto>,
        livePeriod: PeriodInfo?,
    ): FootballSituation? {
        if (livePeriod == null) return null
        val wire = situation ?: return null
        val possession =
            wire.possession
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { id -> competitors.firstOrNull { it.id == id } }
                ?.team
                ?.abbreviation
                ?.takeIf { it.isNotBlank() }
        val down = wire.down?.takeIf { it in 1..4 }
        val distance = if (down == null) null else wire.distance?.takeIf { it >= 0 }
        val redZone = wire.isRedZone == true
        if (possession == null && down == null) return null

        return FootballSituation(down = down, distance = distance, possession = possession, redZone = redZone)
    }

    /**
     * Whether a finished game went to overtime.
     *
     * The census found exactly one overtime final: it carried period 5 and a status detail of `Final/OT`, while every
     * regular final carried period 4 and a detail of `Final`. The detail is the marker the feed states; the period is
     * the corroboration. A note is not used here: the notes that exist in the corpus are announcements, not results.
     */
    private fun NflCompetitionDto.finalKind(statusName: String): FinalKind? {
        if (statusName != STATUS_FINAL) return null
        val detail =
            status
                ?.type
                ?.detail
                ?.trim()
                .orEmpty()
        val period = status?.period
        return when {
            detail.equals("Final/OT", ignoreCase = true) -> FinalKind.OT
            detail.equals("Final", ignoreCase = true) && period != null && period > 4 -> FinalKind.OT
            else -> null
        }
    }

    /** The two sides of a game, each identified by its NFL team. */
    private fun NflCompetitorDto.toTeamRef(): TeamRef {
        val team = team
        val abbreviation = team?.abbreviation?.takeIf { it.isNotBlank() } ?: return TeamRef(TeamKey(League.NFL, ""))
        val logos = team.logos.orEmpty()
        val light = logos.firstOrNull { it.rel?.contains("full") == true && it.href?.endsWith(".png") == true }
        val dark = logos.firstOrNull { it.rel?.contains("dark") == true && it.href?.endsWith(".png") == true }

        return TeamRef(
            key = TeamKey(League.NFL, abbreviation),
            name = team.displayName ?: team.name,
            shortName = team.shortDisplayName,
            logoUrl = light?.href ?: team.logo,
            darkLogoUrl = dark?.href,
        )
    }

    /**
     * The broadcasts, which the census found on the competition rather than on the event.
     *
     * The corpus carries the network as a list of names and the scope as a market ("national"). The domain model
     * wants a network, a country and a market, so the first name becomes the network and the market is carried
     * through. An entry with no usable name is decoration that cannot be shown, so it drops on its own.
     */
    private fun NflCompetitionDto.toBroadcasts(): List<Broadcast> =
        broadcasts.orEmpty().mapNotNull { broadcast ->
            val network =
                broadcast.names?.firstOrNull { it.isNotBlank() }
                    ?: broadcast.media?.shortName
                    ?: broadcast.type?.shortName
                    ?: return@mapNotNull null
            Broadcast(
                network = network.trim(),
                country = null,
                market = broadcast.market?.trim(),
            )
        }
}
