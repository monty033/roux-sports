package net.montycasa.roux.data.nfl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nfl.dto.NflBoxscoreStatDto
import net.montycasa.roux.data.nfl.dto.NflBoxscoreTeamDto
import net.montycasa.roux.data.nfl.dto.NflCompetitorDto
import net.montycasa.roux.data.nfl.dto.NflLeaderAthleteDto
import net.montycasa.roux.data.nfl.dto.NflLeaderCategoryDto
import net.montycasa.roux.data.nfl.dto.NflLeadersTeamDto
import net.montycasa.roux.data.nfl.dto.NflScoringPlayDto
import net.montycasa.roux.data.nfl.dto.NflStatusDto
import net.montycasa.roux.data.nfl.dto.NflSummaryCompetitionDto
import net.montycasa.roux.data.nfl.dto.NflSummaryDto
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflGameClub
import net.montycasa.roux.domain.model.NflGameDetail
import net.montycasa.roux.domain.model.NflGameStatus
import net.montycasa.roux.domain.model.NflLeader
import net.montycasa.roux.domain.model.NflLeaderCategory
import net.montycasa.roux.domain.model.NflScoringPlay
import net.montycasa.roux.domain.model.NflScoringQuarter
import net.montycasa.roux.domain.model.NflTeamLeaders
import net.montycasa.roux.domain.model.NflTeamStatComparison
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.time.Instant

/**
 * Turns ESPN's NFL `summary` into one game page.
 *
 * The mapping follows the payload rather than its field names:
 *
 * - `status.type.name` is the state, and `status.type.detail` is the wording the hero prints (`Final`, `Final/OT`).
 * - A competitor's overall record is the `record` entry of type `total`, printed exactly as the feed words it.
 * - A scoring play's `period` is an object, so the quarter comes from `period.number`, never from the status. On a
 *   finished game the status carries no period or clock at all.
 * - Team statistics are selected by `name` and printed from `displayValue` untouched: third-down efficiency is a ratio
 *   (`"8-17"`) and possession is a clock (`"31:35"`), neither of which may be averaged or re-formatted.
 * - Leaders keep every category the feed states. The five captured categories are not a fixed schema, so nothing is
 *   named beyond what arrived.
 */
internal object NflSummaryAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private const val STATUS_FINAL = "STATUS_FINAL"
    private const val STATUS_SCHEDULED = "STATUS_SCHEDULED"
    private const val STATUS_PRE = "STATUS_PRE"
    private const val STATUS_IN_PROGRESS = "STATUS_IN_PROGRESS"

    /** A kickoff written to the minute, with no seconds: `2026-11-22T18:00Z`. */
    private val SECONDLESS_KICKOFF = Regex("""(T\d{2}:\d{2})Z""")

    /**
     * The seven statistics the page compares, in page order. Every one exists in all three 25 November captures:
     * `firstDowns`, `totalYards`, `netPassingYards`, `rushingYards`, `turnovers`, `thirdDownEff` and `possessionTime`.
     * The feed's passing row is `netPassingYards`, not `passingYards`.
     */
    private val TEAM_STAT_NAMES =
        listOf(
            "firstDowns",
            "totalYards",
            "netPassingYards",
            "rushingYards",
            "turnovers",
            "thirdDownEff",
            "possessionTime",
        )

    fun parse(json: String): ParseResult<NflGameDetail> {
        val payload =
            try {
                feedJson.decodeFromString<NflSummaryDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NFL summary decoding failed: ${exception.message}")
            }

        val competition =
            payload.header
                ?.competitions
                ?.firstOrNull()
                ?: return ParseResult.Failure("NFL summary mapping failed: missing competition")
        val competitors = competition.competitors.orEmpty()
        val home =
            competitors.firstOrNull { it.homeAway == "home" } ?: return ParseResult.Failure("NFL summary mapping failed: no home club")
        val away =
            competitors.firstOrNull { it.homeAway == "away" } ?: return ParseResult.Failure("NFL summary mapping failed: no away club")
        val status = competition.status.toStatus(competition.date)
        val scoring = competition.footballScores(payload.scoringPlays.orEmpty(), home)

        return ParseResult.Success(
            NflGameDetail(
                status = status,
                away = away.toClub(status),
                home = home.toClub(status),
                scoring = scoring,
                teamStats = payload.boxscore.comparisonRows(),
                leaders = payload.leaders.orEmpty().mapNotNull { it.toTeamLeaders() },
            ),
        )
    }

    /**
     * The lifecycle state, with the feed's detail. A scheduled game keeps its kickoff instant, and a final keeps the
     * status's own `Final`/`Final/OT` wording. Nothing is derived from a period here: the captures prove a final has no
     * period to derive from.
     */
    private fun NflStatusDto?.toStatus(date: String?): NflGameStatus {
        val wire = this
        val type = wire?.type
        val name = type?.name?.trim().orEmpty()
        val detail = type?.detail?.trim()?.takeIf { it.isNotEmpty() }
        return when (name) {
            STATUS_FINAL -> {
                NflGameStatus.Final(detail)
            }

            STATUS_IN_PROGRESS -> {
                NflGameStatus.Live(
                    detail = detail,
                    period = wire?.period?.takeIf { it > 0 },
                    clock = wire?.displayClock?.trim()?.takeIf { it.isNotEmpty() },
                )
            }

            STATUS_SCHEDULED, STATUS_PRE -> {
                NflGameStatus.Scheduled(
                    detail = detail,
                    startTimeUtc = date.toInstantOrNull(),
                )
            }

            else -> {
                NflGameStatus.Unknown(detail ?: name.takeIf { it.isNotEmpty() })
            }
        }
    }

    /**
     * A scheduled game states no score; any placeholder it might carry is not a result. A final or live game's score is
     * a string in the competitor block and becomes a number only here.
     */
    private fun NflCompetitorDto.toClub(status: NflGameStatus): NflGameClub =
        NflGameClub(
            team = toTeamRef(),
            score = if (status is NflGameStatus.Scheduled) null else score?.trim()?.toIntOrNull(),
            record = overallRecord(),
        )

    /**
     * The club's overall record, from the `total` entry in the competitor's record array. A missing array, a missing
     * total row, or a blank value is null: the hero must omit the line rather than print `0-0`.
     */
    private fun NflCompetitorDto.overallRecord(): String? =
        record
            .orEmpty()
            .firstOrNull { it.type?.trim()?.equals("total", ignoreCase = true) == true }
            ?.let { it.displayValue ?: it.summary }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun NflCompetitorDto.toTeamRef(): TeamRef {
        val team = team
        val abbreviation = team?.abbreviation?.trim()?.takeIf { it.isNotEmpty() } ?: ""
        val logos = team?.logos.orEmpty()
        val light = logos.firstOrNull { it.rel?.contains("full") == true && it.href?.endsWith(".png") == true }
        val dark = logos.firstOrNull { it.rel?.contains("dark") == true && it.href?.endsWith(".png") == true }

        return TeamRef(
            key = TeamKey(League.NFL, abbreviation),
            name = team?.displayName ?: team?.name,
            shortName = team?.shortDisplayName,
            logoUrl = light?.href ?: team?.logo,
            darkLogoUrl = dark?.href,
        )
    }

    /** The scoring plays grouped by `period.number` and ordered by quarter, keeping payload order inside each group. */
    private fun NflSummaryCompetitionDto.footballScores(
        plays: List<NflScoringPlayDto>,
        home: NflCompetitorDto,
    ): List<NflScoringQuarter> {
        val homeId = home.team?.id
        val homeAbbrev = home.team?.abbreviation
        val placed =
            plays.mapNotNull { play ->
                val period = play.period?.number?.takeIf { it > 0 } ?: return@mapNotNull null
                period to play.toPlay(homeId, homeAbbrev)
            }

        return placed
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (number, quarterPlays) -> NflScoringQuarter(number = number, plays = quarterPlays) }
    }

    private fun NflScoringPlayDto.toPlay(
        homeId: String?,
        homeAbbrev: String?,
    ): NflScoringPlay {
        val teamAbbrev = team?.abbreviation?.trim().orEmpty()
        val isHome =
            when {
                team?.id != null && homeId != null -> team.id == homeId
                else -> teamAbbrev.isNotEmpty() && teamAbbrev.equals(homeAbbrev, ignoreCase = true)
            }
        return NflScoringPlay(
            clock = clock?.displayValue?.trim().orEmpty(),
            teamAbbrev = teamAbbrev,
            isHome = isHome,
            text = text?.trim().orEmpty(),
            awayScore = awayScore,
            homeScore = homeScore,
        )
    }

    /**
     * The fixed comparison rows. A side the feed omitted stays null so the screen prints a blank, not a zero; a row
     * omitted by both clubs is not a comparison and is dropped.
     */
    private fun net.montycasa.roux.data.nfl.dto.NflBoxscoreDto?.comparisonRows(): List<NflTeamStatComparison> {
        val teams = this?.teams.orEmpty()
        val awayStats = teams.firstOrNull { it.homeAway == "away" }?.statisticsByName().orEmpty()
        val homeStats = teams.firstOrNull { it.homeAway == "home" }?.statisticsByName().orEmpty()

        return TEAM_STAT_NAMES.mapNotNull { name ->
            val away = awayStats[name]
            val home = homeStats[name]
            if (away == null && home == null) {
                null
            } else {
                NflTeamStatComparison(name = name, away = away, home = home)
            }
        }
    }

    private fun NflBoxscoreTeamDto.statisticsByName(): Map<String, String> =
        statistics
            .orEmpty()
            .mapNotNull { stat ->
                val name = stat.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val value = stat.displayValue() ?: return@mapNotNull null
                name to value
            }.toMap()

    /** The feed's string as sent. Whitespace-only is absence, not a value. */
    private fun NflBoxscoreStatDto.displayValue(): String? = displayValue?.takeIf { it.isNotBlank() }

    private fun NflLeadersTeamDto.toTeamLeaders(): NflTeamLeaders? {
        val abbreviation = team?.abbreviation?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val categories =
            leaders.orEmpty().mapNotNull { category -> category.toLeaderCategory() }
        return NflTeamLeaders(teamAbbrev = abbreviation, categories = categories)
    }

    private fun NflLeaderCategoryDto.toLeaderCategory(): NflLeaderCategory? {
        val categoryName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val heading = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: categoryName
        val lines =
            leaders.orEmpty().mapNotNull { leader ->
                val athlete = leader.athlete
                val name = athlete.toLeaderName() ?: return@mapNotNull null
                val value = leader.displayValue?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                NflLeader(
                    athleteName = name,
                    athleteId = athlete?.id?.trim()?.takeIf { it.isNotEmpty() },
                    displayValue = value,
                )
            }
        if (lines.isEmpty()) return null
        return NflLeaderCategory(name = categoryName, displayName = heading, leaders = lines)
    }

    private fun NflLeaderAthleteDto?.toLeaderName(): String? = this?.displayName?.trim()?.takeIf { it.isNotEmpty() }

    /** The feed writes kickoffs without seconds; only the missing `:00` is supplied. */
    private fun String?.toInstantOrNull(): Instant? {
        val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { Instant.parse(raw.replace(SECONDLESS_KICKOFF, "$1:00Z")) }.getOrNull()
    }
}
