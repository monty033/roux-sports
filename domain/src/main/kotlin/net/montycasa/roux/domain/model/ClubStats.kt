package net.montycasa.roux.domain.model

/**
 * A club's season statistics, split the way the supply tab reads them: skaters first, then goalies.
 *
 * Both lists are already in leaderboard order when they arrive from the adapter. That ordering is
 * deliberately not the feed's: the wire order is not documented as meaningful, so the page sorts by
 * the numbers a reader compares first.
 */
data class ClubStats(
    val skaters: List<SkaterStat> = emptyList(),
    val goalies: List<GoalieStat> = emptyList(),
)

/**
 * One skater's season line.
 *
 * [avgTimeOnIcePerGame] is **seconds**, not minutes: the feed sends 958.7857 for 15:58 a game. Keeping
 * the feed's unit here means an adapter cannot quietly divide by sixty on the way through.
 *
 * [shootingPctg] is a fraction, not a percentage: 0.1 is ten percent. The UI multiplies it by 100
 * only when it prints the value as a percentage.
 */
data class SkaterStat(
    /** The feed's player id, when present. */
    val id: Long? = null,
    val name: String,
    /** The feed's position code (C, L, R, D), not the table group. */
    val positionCode: String? = null,
    val headshotUrl: String? = null,
    val gamesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val plusMinus: Int = 0,
    val penaltyMinutes: Int = 0,
    val shots: Int = 0,
    val shootingPctg: Double = 0.0,
    val powerPlayGoals: Int = 0,
    val shorthandedGoals: Int = 0,
    val gameWinningGoals: Int = 0,
    val overtimeGoals: Int = 0,
    val faceoffWinPctg: Double = 0.0,
    /** Average ice time per game in seconds, exactly as the feed sends it. */
    val avgTimeOnIcePerGame: Double = 0.0,
    val avgShiftsPerGame: Double = 0.0,
    /** The feed's own spelling for the same id. */
    val playerId: Long? = id,
    /** An alias that makes the row's identity column read the same as the roster's. */
    val position: String? = positionCode,
    /** An alias for callers that follow the feed's field name. */
    val headshot: String? = headshotUrl,
    /** The average ice time in seconds, named for what it is rather than what the feed calls it. */
    val toiSeconds: Double = avgTimeOnIcePerGame,
    /** The same value under a name a formatting caller is likely to reach for. */
    val averageTimeOnIce: Double = avgTimeOnIcePerGame,
) {
    /** The shooting fraction as a percentage (0..100), without any rounding. */
    val shootingPercentage: Double get() = shootingPctg * 100.0
}

/**
 * One goaltender's season line.
 *
 * [savePercentage] and [goalsAgainstAverage] arrive as fractions/totals respectively; the UI prints
 * the first with a leading dot (`.870`) and the second with two decimals (`3.00`).
 */
data class GoalieStat(
    val id: Long? = null,
    val name: String,
    /** Goalies are the goalie group even though the stats payload omits a position code. */
    val positionCode: String? = "G",
    val headshotUrl: String? = null,
    val gamesPlayed: Int = 0,
    val gamesStarted: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val overtimeLosses: Int = 0,
    val goalsAgainst: Int = 0,
    val goalsAgainstAverage: Double = 0.0,
    val shotsAgainst: Int = 0,
    val saves: Int = 0,
    val savePercentage: Double = 0.0,
    val shutouts: Int = 0,
    /** Total ice time in seconds, exactly as the feed sends it. */
    val timeOnIce: Long = 0,
    val assists: Int = 0,
    val goals: Int = 0,
    val points: Int = 0,
    val penaltyMinutes: Int = 0,
    /** The feed's own spelling for the same id. */
    val playerId: Long? = id,
    /** An alias for callers that follow the roster model's spelling. */
    val position: String? = positionCode,
    /** An alias for callers that follow the feed's field name. */
    val headshot: String? = headshotUrl,
    /** An alias for the column label. */
    val gaa: Double = goalsAgainstAverage,
    /** An alias using the feed's shorter spelling. */
    val savePctg: Double = savePercentage,
)

/** Alternate names for the two row types, so callers can use whichever vocabulary they arrived with. */
typealias SkaterStats = SkaterStat

typealias GoalieStats = GoalieStat

typealias ClubSkater = SkaterStat

typealias ClubGoalie = GoalieStat

typealias ClubStatsSkater = SkaterStat

typealias ClubStatsGoalie = GoalieStat

typealias ClubSkaterStats = SkaterStat

typealias ClubGoalieStats = GoalieStat

typealias SkaterSeasonStats = SkaterStat

typealias GoalieSeasonStats = GoalieStat

typealias TeamStats = ClubStats

typealias ClubSeasonStats = ClubStats
