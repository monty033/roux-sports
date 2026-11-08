package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.NHL_LEAGUE
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals

/**
 * The Career tab's rules: which rows show, and how the total rows are built. Pure Kotlin so the rules are tested without
 * Android.
 */
object PlayerCareerTable {
    private const val SECONDS_PER_HOUR = 3600.0

    /**
     * NHL rows, oldest season first. Within a season the regular-season row comes before the playoff row. A traded season
     * keeps one row per club for each kind, in the feed's club order, and each row keeps its own club name. Other leagues
     * and rows with no season are dropped here, not hidden in the UI.
     */
    fun rows(seasons: List<PlayerSeasonLine>): List<PlayerSeasonLine> =
        seasons
            .filter { it.season != null && it.leagueAbbrev == NHL_LEAGUE && (it.isRegularSeason || it.isPlayoffs) }
            .sortedWith(
                compareBy<PlayerSeasonLine> { it.season }
                    .thenBy { it.gameTypeId }
                    .thenBy { it.sequence ?: Int.MIN_VALUE },
            )

    /**
     * The total of [rows], which must be of one kind (all regular season, or all playoffs), each counted once. Club rows
     * are disjoint, so a traded season's two clubs add their own games and nothing is added twice. A null in any
     * contributing row makes that count null, so a missing number prints as a dash instead of a short total.
     *
     * Rates are worked out from the summed counts. Faceoff percentage and average ice time cannot be summed across
     * clubs, so they come from the feed's career line for the same kind of game, [rates], which is already NHL-only.
     */
    fun total(
        rows: List<PlayerSeasonLine>,
        rates: PlayerTotals?,
    ): PlayerTotals? {
        if (rows.isEmpty()) return null
        val totals = rows.map { it.totals }
        return when {
            totals.all { it is PlayerTotals.Skater } -> {
                skaterTotal(totals.filterIsInstance<PlayerTotals.Skater>(), rates as? PlayerTotals.Skater)
            }

            totals.all { it is PlayerTotals.Goalie } -> {
                goalieTotal(totals.filterIsInstance<PlayerTotals.Goalie>())
            }

            else -> {
                null
            }
        }
    }

    /**
     * The playoff total, at the foot of the playoff card. It sums the playoff rows only, so with no playoff rows there is
     * no total and no card. The feed's playoff career line supplies the rates, as the regular total does.
     */
    fun playoffTotal(
        rows: List<PlayerSeasonLine>,
        playoffs: PlayerTotals?,
    ): PlayerTotals? = total(rows.filter { it.isPlayoffs }, playoffs)

    /**
     * The season the Game log tab shows when [currentSeason] has no games yet: the newest NHL regular-season season
     * before it, read from the same rows the Career tab shows, so both tabs name the same season. Other leagues and
     * playoff rows never stand in for it. Null when the player has no such row.
     */
    fun gameLogFallbackSeason(
        seasons: List<PlayerSeasonLine>,
        currentSeason: Int,
    ): Int? =
        rows(seasons)
            .filter { it.isRegularSeason }
            .mapNotNull { it.season }
            .filter { it < currentSeason }
            .maxOrNull()

    private fun skaterTotal(
        rows: List<PlayerTotals.Skater>,
        rates: PlayerTotals.Skater?,
    ): PlayerTotals.Skater {
        val goals = rows.totalOf { it.goals }
        val shots = rows.totalOf { it.shots }
        return PlayerTotals.Skater(
            gamesPlayed = rows.totalOf { it.gamesPlayed },
            goals = goals,
            assists = rows.totalOf { it.assists },
            points = rows.totalOf { it.points },
            plusMinus = rows.totalOf { it.plusMinus },
            penaltyMinutes = rows.totalOf { it.penaltyMinutes },
            powerPlayGoals = rows.totalOf { it.powerPlayGoals },
            powerPlayPoints = rows.totalOf { it.powerPlayPoints },
            shorthandedGoals = rows.totalOf { it.shorthandedGoals },
            shorthandedPoints = rows.totalOf { it.shorthandedPoints },
            gameWinningGoals = rows.totalOf { it.gameWinningGoals },
            overtimeGoals = rows.totalOf { it.overtimeGoals },
            shots = shots,
            shootingPctg = if (goals != null && shots != null && shots > 0) goals.toDouble() / shots else null,
            faceoffWinningPctg = rates?.faceoffWinningPctg,
            avgTimeOnIceSeconds = rates?.avgTimeOnIceSeconds,
        )
    }

    private fun goalieTotal(rows: List<PlayerTotals.Goalie>): PlayerTotals.Goalie {
        val goalsAgainst = rows.totalOf { it.goalsAgainst }
        val shotsAgainst = rows.totalOf { it.shotsAgainst }
        val timeOnIce = rows.totalOfLong { it.timeOnIceSeconds }
        return PlayerTotals.Goalie(
            gamesPlayed = rows.totalOf { it.gamesPlayed },
            gamesStarted = rows.totalOf { it.gamesStarted },
            wins = rows.totalOf { it.wins },
            losses = rows.totalOf { it.losses },
            overtimeLosses = rows.totalOf { it.overtimeLosses },
            goalsAgainst = goalsAgainst,
            goalsAgainstAverage =
                if (goalsAgainst != null && timeOnIce != null && timeOnIce > 0) {
                    goalsAgainst * SECONDS_PER_HOUR / timeOnIce
                } else {
                    null
                },
            shotsAgainst = shotsAgainst,
            savePercentage =
                if (goalsAgainst != null && shotsAgainst != null && shotsAgainst > 0) {
                    (shotsAgainst - goalsAgainst).toDouble() / shotsAgainst
                } else {
                    null
                },
            shutouts = rows.totalOf { it.shutouts },
            timeOnIceSeconds = timeOnIce,
            goals = rows.totalOf { it.goals },
            assists = rows.totalOf { it.assists },
            penaltyMinutes = rows.totalOf { it.penaltyMinutes },
        )
    }

    /** Adds the values, or returns null the moment one is missing: a short sum would read as a real total. */
    private fun <T> List<T>.totalOf(selector: (T) -> Int?): Int? =
        fold<T, Int?>(0) { acc, row ->
            val v = selector(row)
            if (acc == null || v == null) null else acc + v
        }

    private fun <T> List<T>.totalOfLong(selector: (T) -> Long?): Long? =
        fold<T, Long?>(0L) { acc, row ->
            val v = selector(row)
            if (acc == null || v == null) null else acc + v
        }
}
