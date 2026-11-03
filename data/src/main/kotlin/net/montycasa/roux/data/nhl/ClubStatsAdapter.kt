package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nhl.dto.ClubStatsDto
import net.montycasa.roux.data.nhl.dto.ClubStatsGoalieDto
import net.montycasa.roux.data.nhl.dto.ClubStatsSkaterDto
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.GoalieStat
import net.montycasa.roux.domain.model.SkaterStat

/**
 * Turns the club-stats payload into the two tables the Stats tab draws.
 *
 * The rows are sorted here, not in the UI: this is a leaderboard, and the feed's order is not
 * documented as meaningful. Skaters read by points, then goals, then games played; goalies by games
 * played, then goals against average. Names break the remaining ties alphabetically.
 */
internal object ClubStatsAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<ClubStats> {
        val stats =
            try {
                feedJson.decodeFromString<ClubStatsDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL club stats decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL club stats decoding failed: ${exception.message}")
            }

        val skatersWire = stats.skaters ?: return ParseResult.Failure("NHL club stats mapping failed: missing skaters")
        val goaliesWire = stats.goalies ?: return ParseResult.Failure("NHL club stats mapping failed: missing goalies")

        val skaters = skatersWire.mapNotNull { it?.toStat() }.skaterLeaderboard()
        if (skaters.isEmpty()) {
            return ParseResult.Failure("NHL club stats mapping failed: skaters has no usable players")
        }
        val goalies = goaliesWire.mapNotNull { it?.toStat() }.goalieLeaderboard()
        if (goalies.isEmpty()) {
            return ParseResult.Failure("NHL club stats mapping failed: goalies has no usable players")
        }
        return ParseResult.Success(ClubStats(skaters = skaters, goalies = goalies))
    }

    /** The endpoint's own name for the same parse, for callers that name the feed. */
    fun parseClubStats(json: String): ParseResult<ClubStats> = parse(json)

    /**
     * A missing name is the only reason to drop a row: every other number has a defensible zero,
     * but a row with no name cannot be labelled.
     */
    private fun ClubStatsSkaterDto.toStat(): SkaterStat? {
        val name = fullName() ?: return null
        return SkaterStat(
            id = playerId,
            name = name,
            positionCode = positionCode?.trim()?.takeIf { it.isNotEmpty() },
            headshotUrl = headshot?.trim()?.takeIf { it.isNotEmpty() },
            gamesPlayed = gamesPlayed ?: 0,
            goals = goals ?: 0,
            assists = assists ?: 0,
            points = points ?: 0,
            plusMinus = plusMinus ?: 0,
            penaltyMinutes = penaltyMinutes ?: 0,
            shots = shots ?: 0,
            shootingPctg = shootingPctg ?: 0.0,
            powerPlayGoals = powerPlayGoals ?: 0,
            shorthandedGoals = shorthandedGoals ?: 0,
            gameWinningGoals = gameWinningGoals ?: 0,
            overtimeGoals = overtimeGoals ?: 0,
            faceoffWinPctg = faceoffWinPctg ?: 0.0,
            avgTimeOnIcePerGame = avgTimeOnIcePerGame ?: 0.0,
            avgShiftsPerGame = avgShiftsPerGame ?: 0.0,
        )
    }

    private fun ClubStatsGoalieDto.toStat(): GoalieStat? {
        val name = fullName() ?: return null
        return GoalieStat(
            id = playerId,
            name = name,
            headshotUrl = headshot?.trim()?.takeIf { it.isNotEmpty() },
            gamesPlayed = gamesPlayed ?: 0,
            gamesStarted = gamesStarted ?: 0,
            wins = wins ?: 0,
            losses = losses ?: 0,
            overtimeLosses = overtimeLosses ?: 0,
            goalsAgainst = goalsAgainst ?: 0,
            goalsAgainstAverage = goalsAgainstAverage ?: 0.0,
            shotsAgainst = shotsAgainst ?: 0,
            saves = saves ?: 0,
            savePercentage = savePercentage ?: 0.0,
            shutouts = shutouts ?: 0,
            timeOnIce = timeOnIce ?: 0,
            assists = assists ?: 0,
            goals = goals ?: 0,
            points = points ?: 0,
            penaltyMinutes = penaltyMinutes ?: 0,
        )
    }

    private fun ClubStatsSkaterDto.fullName(): String? {
        val first = firstName?.default?.trim()?.takeIf { it.isNotEmpty() }
        val last = lastName?.default?.trim()?.takeIf { it.isNotEmpty() }
        return listOfNotNull(first, last).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun ClubStatsGoalieDto.fullName(): String? {
        val first = firstName?.default?.trim()?.takeIf { it.isNotEmpty() }
        val last = lastName?.default?.trim()?.takeIf { it.isNotEmpty() }
        return listOfNotNull(first, last).joinToString(" ").takeIf { it.isNotBlank() }
    }

    /** The leaderboard order, not the feed's. */
    private fun List<SkaterStat>.skaterLeaderboard(): List<SkaterStat> =
        sortedWith(
            compareByDescending<SkaterStat> { it.points }
                .thenByDescending { it.goals }
                .thenByDescending { it.gamesPlayed }
                .thenBy { it.name },
        )

    /** The goalie leaderboard: most games first, then the better average, then the name. */
    private fun List<GoalieStat>.goalieLeaderboard(): List<GoalieStat> =
        sortedWith(
            compareByDescending<GoalieStat> { it.gamesPlayed }
                .thenBy { it.goalsAgainstAverage }
                .thenBy { it.name },
        )
}

/** The feed's own name for the same adapter, for callers that prefer it. */
internal object StatsAdapter {
    fun parse(json: String): ParseResult<ClubStats> = ClubStatsAdapter.parse(json)

    fun parseClubStats(json: String): ParseResult<ClubStats> = ClubStatsAdapter.parse(json)
}
