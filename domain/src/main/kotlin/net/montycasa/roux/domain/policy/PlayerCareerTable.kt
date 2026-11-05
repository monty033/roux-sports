package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.NHL_LEAGUE
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.model.REGULAR_SEASON

/**
 * The Career tab's rules: which rows show, and how the total row is built. Pure Kotlin so the rules are tested without
 * Android.
 */
object PlayerCareerTable {
    private const val SECONDS_PER_HOUR = 3600.0

    /**
     * NHL regular-season rows, oldest season first. A traded season keeps one row per club, in the feed's club order,
     * and each row keeps its own club name. Other leagues and playoff rows are dropped here, not hidden in the UI.
     */
    fun rows(seasons: List<PlayerSeasonLine>): List<PlayerSeasonLine> =
        seasons
            .filter { it.season != null && it.leagueAbbrev == NHL_LEAGUE && it.gameTypeId == REGULAR_SEASON }
            .sortedWith(compareBy<PlayerSeasonLine> { it.season }.thenBy { it.sequence ?: Int.MIN_VALUE })

    /**
     * The total row: the sum of [rows], each counted once. Club rows are disjoint, so a traded season's two clubs add
     * their own games and nothing is added twice. A null in any contributing row makes that count null, so a missing
     * number prints as a dash instead of a short total.
     *
     * Rates are worked out from the summed counts. Faceoff percentage and average ice time cannot be summed across
     * clubs, so they come from the feed's career regular-season totals, which are already NHL-only.
     */
    fun total(
        rows: List<PlayerSeasonLine>,
        career: PlayerCareer,
    ): PlayerTotals? {
        if (rows.isEmpty()) return null
        val totals = rows.map { it.totals }
        return when {
            totals.all { it is PlayerTotals.Skater } -> {
                skaterTotal(totals.filterIsInstance<PlayerTotals.Skater>(), career)
            }

            totals.all { it is PlayerTotals.Goalie } -> {
                goalieTotal(totals.filterIsInstance<PlayerTotals.Goalie>())
            }

            else -> {
                null
            }
        }
    }

    private fun skaterTotal(
        rows: List<PlayerTotals.Skater>,
        career: PlayerCareer,
    ): PlayerTotals.Skater {
        val goals = rows.totalOf { it.goals }
        val shots = rows.totalOf { it.shots }
        val regular = career.regularSeason as? PlayerTotals.Skater
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
            faceoffWinningPctg = regular?.faceoffWinningPctg,
            avgTimeOnIceSeconds = regular?.avgTimeOnIceSeconds,
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
