package net.montycasa.roux.domain.model

/**
 * A club's season statistics, split the way the Stats tab reads them: skaters first, then goalies.
 *
 * Both lists are already in leaderboard order when they arrive from the adapter. That ordering is
 * deliberately not the feed's: the wire order is not documented as meaningful, so the page sorts by
 * the numbers a reader compares first.
 *
 * [season] is the feed's eight-digit season id, such as `20252026`, and names the season these numbers
 * belong to. It is the season the page asked for unless that season had no rows for the club yet, in
 * which case the page shows the season before and this names that one, so the UI can say so.
 */
data class ClubStats(
    val season: String,
    val skaters: List<SkaterStat> = emptyList(),
    val goalies: List<GoalieStat> = emptyList(),
)

/**
 * One skater's season line.
 *
 * Every number is nullable because the feed can omit any of them. A missing number stays null all
 * the way to the table, where it prints as an em dash; it is never turned into a zero, because zero
 * is a real value a player can have.
 *
 * [avgTimeOnIcePerGame] is **seconds**, not minutes: the feed sends 958.7857 for 15:58 a game. Keeping
 * the feed's unit here means an adapter cannot quietly divide by sixty on the way through.
 *
 * [shootingPctg] is a fraction, not a percentage: 0.1 is ten percent. The UI multiplies it by 100
 * only when it prints the value as a percentage.
 */
data class SkaterStat(
    /** The feed's player id, when present. */
    val playerId: Long? = null,
    val name: String,
    /** The feed's position code (C, L, R, D), not the table group. */
    val positionCode: String? = null,
    val headshotUrl: String? = null,
    val gamesPlayed: Int? = null,
    val goals: Int? = null,
    val assists: Int? = null,
    val points: Int? = null,
    val plusMinus: Int? = null,
    val penaltyMinutes: Int? = null,
    val shots: Int? = null,
    val shootingPctg: Double? = null,
    val powerPlayGoals: Int? = null,
    val shorthandedGoals: Int? = null,
    val gameWinningGoals: Int? = null,
    val overtimeGoals: Int? = null,
    val faceoffWinPctg: Double? = null,
    /** Average ice time per game in seconds, exactly as the feed sends it. */
    val avgTimeOnIcePerGame: Double? = null,
    val avgShiftsPerGame: Double? = null,
)

/**
 * One goaltender's season line.
 *
 * [savePercentage] and [goalsAgainstAverage] arrive as fractions/totals respectively; the UI prints
 * the first with a leading dot (`.870`) and the second with two decimals (`3.00`). Both are nullable
 * because the feed can omit them, and `0.0` is a real value rather than absence.
 */
data class GoalieStat(
    val playerId: Long? = null,
    val name: String,
    /** Goalies carry no position code in the stats payload. */
    val positionCode: String? = null,
    val headshotUrl: String? = null,
    val gamesPlayed: Int? = null,
    val gamesStarted: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val overtimeLosses: Int? = null,
    val goalsAgainst: Int? = null,
    val goalsAgainstAverage: Double? = null,
    val shotsAgainst: Int? = null,
    val saves: Int? = null,
    val savePercentage: Double? = null,
    val shutouts: Int? = null,
    /** Total ice time in seconds, exactly as the feed sends it. */
    val timeOnIce: Long? = null,
    val assists: Int? = null,
    val goals: Int? = null,
    val points: Int? = null,
    val penaltyMinutes: Int? = null,
)
