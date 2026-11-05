package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeagueSummary

/**
 * The eleven league-table metrics the Team Stats tab ranks, in the order the tab shows them.
 *
 * [higherIsBetter] is the direction a rank counts in: goals against, shots against and penalty minutes are
 * better when they are lower, so a rank of 1 means the fewest, not the most.
 */
enum class LeagueMetric(
    val higherIsBetter: Boolean,
    val valueFor: (LeagueClub) -> Double?,
) {
    GoalsForPerGame(higherIsBetter = true, valueFor = { it.goalsForPerGame }),
    GoalsAgainstPerGame(higherIsBetter = false, valueFor = { it.goalsAgainstPerGame }),
    ShotsForPerGame(higherIsBetter = true, valueFor = { it.shotsForPerGame }),
    ShotsAgainstPerGame(higherIsBetter = false, valueFor = { it.shotsAgainstPerGame }),
    FaceoffWinPct(higherIsBetter = true, valueFor = { it.faceoffWinPct }),
    PowerPlayPct(higherIsBetter = true, valueFor = { it.powerPlayPct }),
    PenaltyKillPct(higherIsBetter = true, valueFor = { it.penaltyKillPct }),
    PenaltyMinutes(higherIsBetter = false, valueFor = { it.penaltyMinutes?.toDouble() }),
    Points(higherIsBetter = true, valueFor = { it.points?.toDouble() }),
    PointPct(higherIsBetter = true, valueFor = { it.pointPct }),
    TeamShutouts(higherIsBetter = true, valueFor = { it.teamShutouts?.toDouble() }),
}

/**
 * One club's place among every club in a league, computed from the whole league rather than from any
 * fixed list of clubs.
 *
 * Ranks are competition ranks: a club's rank is one plus the number of clubs strictly better than it,
 * so equal values share the better rank and the next rank is skipped (`1, 2, 2, 4`). Only a strictly
 * better value counts against a club; an equal one does not.
 *
 * A club with no value for a metric has no rank for it, and a missing value elsewhere in the league
 * never counts against anyone. [clubCount] is the number of clubs ranked, which is the "of 32" shown.
 */
class LeagueRanking private constructor(
    private val league: LeagueSummary,
    /** The club being ranked, so a screen can print its own record beside its ranks. */
    val club: LeagueClub,
) {
    /** The number of clubs the league ranks; a rank is always between 1 and this. */
    val clubCount: Int get() = league.clubs.size

    /** The club's rank for [metric], or null when the club has no value for it. */
    fun rankOf(metric: LeagueMetric): Int? {
        val mine = metric.valueFor(club) ?: return null
        val better =
            league.clubs.count { other ->
                val theirs = metric.valueFor(other) ?: return@count false
                if (metric.higherIsBetter) theirs > mine else theirs < mine
            }
        return better + 1
    }

    /**
     * The length of the rank bar for [metric] as a fraction of its track: `(clubCount - rank + 1) / clubCount`,
     * so rank 1 fills the track and the last place is `1 / clubCount`. Tied clubs share a rank and so a length.
     * Null when the club has no rank for [metric], and such a row draws no bar.
     */
    fun barFractionOf(metric: LeagueMetric): Float? {
        val rank = rankOf(metric) ?: return null
        return (clubCount - rank + 1).toFloat() / clubCount
    }

    companion object {
        /**
         * The ranking for the club called [teamName], or null when the league has no club by that name.
         *
         * A null here is a real answer - a club with no games yet is absent from the feed - and must not
         * be read as a club with zeros. If the feed repeats a name, the first club with it is used.
         */
        fun forClub(
            league: LeagueSummary,
            teamName: String,
        ): LeagueRanking? = league.clubs.firstOrNull { it.teamName == teamName }?.let { LeagueRanking(league, it) }
    }
}
