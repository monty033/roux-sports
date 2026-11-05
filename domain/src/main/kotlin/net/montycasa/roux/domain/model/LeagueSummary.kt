package net.montycasa.roux.domain.model

/**
 * Every club's season totals for one season, from one request for the whole league.
 *
 * A club's rank is only meaningful against the other clubs, so the league is kept whole rather than
 * fetched club by club. The list order is the feed's and carries no meaning.
 */
data class LeagueSummary(
    val clubs: List<LeagueClub>,
)

/**
 * One club's line in the league table.
 *
 * The club is identified by its full name, which is the same string the standings table carries for
 * it. The summary row has no abbreviation to join on, so [teamId] is the key a second feed is joined by;
 * the name is the fallback only when one side has no id.
 *
 * Every number is nullable because the feed can omit any of them. A missing number stays null all the
 * way to the screen, where it prints as an em dash and earns no rank. [ties] is kept so a null tie can
 * be asserted as null, but no screen reads it: the record is wins, losses and overtime losses.
 *
 * Percentages are fractions, not percentages: 0.46979 is 46.98 %. The screen multiplies by 100 only
 * when it prints one.
 *
 * [penaltyMinutes] is the season total, not a per-game rate. It comes from the league penalties table,
 * not from the summary, so it is null when that table did not answer for this club.
 */
data class LeagueClub(
    val teamName: String,
    val teamId: Int? = null,
    val gamesPlayed: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val otLosses: Int? = null,
    val ties: Int? = null,
    val points: Int? = null,
    val pointPct: Double? = null,
    val goalsForPerGame: Double? = null,
    val goalsAgainstPerGame: Double? = null,
    val shotsForPerGame: Double? = null,
    val shotsAgainstPerGame: Double? = null,
    val faceoffWinPct: Double? = null,
    val powerPlayPct: Double? = null,
    val penaltyKillPct: Double? = null,
    val teamShutouts: Int? = null,
    val penaltyMinutes: Int? = null,
)
