package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.ClubSeasonStats
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.RankedStat
import net.montycasa.roux.domain.model.SeasonRecord
import kotlin.math.roundToLong

/** One season statistic as the pre-game card prints it: each club's value with its rank beside it. */
data class SeasonStatRow(
    val label: String,
    val awayValue: String,
    val awayRank: String,
    val homeValue: String,
    val homeRank: String,
)

/**
 * The wording and number formats of a game that has not started yet: records, ranks, percentages, goaltenders.
 *
 * Numbers are formatted with integer arithmetic on purpose. Rounding through `String.format` would pull a JVM type into
 * `:domain`, and this module has to stay common Kotlin.
 */
object PreGameFormat {
    /** A club shows at most this many goaltenders. The feed lists them all; the card does not. */
    const val GOALIES_PER_CLUB = 3

    private const val EN_DASH = "–"

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** `10–6–2`, with en dashes as the app prints score dashes. */
    fun recordText(record: SeasonRecord): String = "${record.wins}$EN_DASH${record.losses}$EN_DASH${record.overtimeLosses}"

    /** A club's abbreviation with its season record, as the hero prints it: `COL 10–6–2`. */
    fun clubRecordText(
        abbrev: String,
        record: SeasonRecord,
    ): String = "$abbrev ${recordText(record)}"

    /**
     * An English ordinal: `1st`, `2nd`, `3rd`, `4th`, `11th`, `21st`.
     *
     * A rank of zero or less means the feed did not place the club, so the card says nothing rather than `0th`. That is
     * the same rule the club page uses.
     */
    fun ordinal(rank: Int): String {
        if (rank <= 0) return EN_DASH
        val suffix =
            when {
                rank % 100 in 11..13 -> "th"
                rank % 10 == 1 -> "st"
                rank % 10 == 2 -> "nd"
                rank % 10 == 3 -> "rd"
                else -> "th"
            }
        return "$rank$suffix"
    }

    /** A fraction printed as a percentage with one decimal: `0.233333` is `23.3%`. */
    fun percent(fraction: Double): String {
        val tenths = (fraction * 1000).roundToLong()
        return "${tenths / 10}.${tenths % 10}%"
    }

    /** A per-game value with two decimals: `4.0` is `4.00`, `2.59842` is `2.60`. */
    fun perGame(value: Double): String {
        val hundredths = (value * 100).roundToLong()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }

    /** The streak as the feed's type letter and count: `W1`, `L3`. Null when either half is missing. */
    fun streakText(form: ClubForm): String? {
        val type = form.streakType ?: return null
        val count = form.streak ?: return null
        return "$type$count"
    }

    /** An ISO `YYYY-MM-DD` date as the card prints it: `Oct 10`. Null when the date does not parse. */
    fun shortDate(isoDate: String): String? {
        val parts = isoDate.split('-')
        if (parts.size != 3) return null
        val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        return "${MONTHS[month - 1]} $day"
    }

    /**
     * The season-stat rows, in page order. A row is kept only when both clubs have its value and its rank, because a row
     * with one side missing would print half a comparison.
     */
    fun seasonStatRows(
        away: ClubSeasonStats?,
        home: ClubSeasonStats?,
    ): List<SeasonStatRow> {
        if (away == null || home == null) return emptyList()
        return listOfNotNull(
            statRow("Power play", away.powerPlay, home.powerPlay) { percent(it) },
            statRow("Penalty kill", away.penaltyKill, home.penaltyKill) { percent(it) },
            statRow("Face-off", away.faceoff, home.faceoff) { percent(it) },
            statRow("Goals for / game", away.goalsFor, home.goalsFor) { perGame(it) },
            statRow("Goals against / game", away.goalsAgainst, home.goalsAgainst) { perGame(it) },
        )
    }

    private fun statRow(
        label: String,
        awayStat: RankedStat?,
        homeStat: RankedStat?,
        format: (Double) -> String,
    ): SeasonStatRow? {
        if (awayStat == null || homeStat == null) return null
        return SeasonStatRow(
            label = label,
            awayValue = format(awayStat.value),
            awayRank = ordinal(awayStat.rank),
            homeValue = format(homeStat.value),
            homeRank = ordinal(homeStat.rank),
        )
    }

    /**
     * One club's goaltenders for the card: the ones whose [GoalieSeason.teamId] is [teamId], most games played first, at
     * most [GOALIES_PER_CLUB].
     *
     * A goaltender with no games played sorts last rather than first, and nobody is marked as the starter. A null
     * [teamId] matches nobody, because a goaltender cannot be attributed to a club without its id.
     */
    fun goaliesFor(
        teamId: Long?,
        goalies: List<GoalieSeason>,
    ): List<GoalieSeason> {
        if (teamId == null) return emptyList()
        return goalies
            .filter { it.teamId == teamId }
            .sortedByDescending { it.gamesPlayed ?: -1 }
            .take(GOALIES_PER_CLUB)
    }

    /** The goaltender's name and sweater number: `A. Akhtyamov #70`. The number is left out when the feed omits it. */
    fun goalieHeading(goalie: GoalieSeason): String = listOfNotNull(goalie.name, goalie.sweaterNumber?.let { "#$it" }).joinToString(" ")

    /**
     * The goaltender's season line: `14 GP · 9–3–1 · 2.60 GAA · 90.1%`. Null when the feed sent no games played, which is
     * how a goaltender with no season is told apart from one with a bad one.
     */
    fun goalieStats(goalie: GoalieSeason): String? {
        val played = goalie.gamesPlayed ?: return null
        return listOfNotNull(
            "$played GP",
            goalie.record?.let { recordText(it) },
            goalie.goalsAgainstAverage?.let { "${perGame(it)} GAA" },
            goalie.savePercentage?.let { percent(it) },
        ).joinToString(" · ")
    }
}
