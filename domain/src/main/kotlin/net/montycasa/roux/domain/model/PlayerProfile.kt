package net.montycasa.roux.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.until

/**
 * A player's landing page: who the player is and what the feed says they have done, season by season and in career
 * totals. The game log is a separate request and lives in [PlayerGame].
 *
 * Every stat the feed can omit is nullable. Absence means the feed did not send the number, and it prints as an em
 * dash. A real zero is data and prints as zero, so no adapter may turn a missing number into `0`.
 */
data class PlayerProfile(
    val playerId: Long,
    val firstName: String,
    val lastName: String,
    /** The feed's position code: C, L, R, D or G. A goalie is the only position with its own stat set. */
    val position: String,
    val sweaterNumber: Int? = null,
    val headshotUrl: String? = null,
    val heightInches: Int? = null,
    val weightPounds: Int? = null,
    /** `YYYY-MM-DD` as the feed sends it. The UI formats it for display. */
    val birthDate: String? = null,
    val birthCity: String? = null,
    val birthCountry: String? = null,
    /** The hand the player shoots with, as the feed words it. Every position reads "Shoots", goalies included. */
    val shoots: String? = null,
    /** Null when the feed carries no draft record, which is different from a draft record with empty fields. */
    val draft: PlayerDraft? = null,
    val career: PlayerCareer = PlayerCareer(),
    /** Every season row the feed sent, across leagues. One season can hold several rows. */
    val seasons: List<PlayerSeasonLine> = emptyList(),
) {
    /** True for a goalie, whose stat set and game log differ from a skater's. */
    val isGoalie: Boolean get() = position == GOALIE_POSITION

    private companion object {
        const val GOALIE_POSITION = "G"
    }
}

data class PlayerDraft(
    val year: Int? = null,
    val teamAbbrev: String? = null,
    val round: Int? = null,
    val pickInRound: Int? = null,
    val overallPick: Int? = null,
)

/**
 * A player's career totals. [playoffs] is absent unless the feed sent a playoff line; it is never synthesised as zero.
 */
data class PlayerCareer(
    val regularSeason: PlayerTotals? = null,
    val playoffs: PlayerTotals? = null,
)

/**
 * One set of totals, in the shape of the player's position. The skater and goalie objects carry different keys, so one
 * union with fake zeros would invent numbers the feed never sent.
 */
sealed interface PlayerTotals {
    data class Skater(
        val gamesPlayed: Int? = null,
        val goals: Int? = null,
        val assists: Int? = null,
        val points: Int? = null,
        val plusMinus: Int? = null,
        val penaltyMinutes: Int? = null,
        val powerPlayGoals: Int? = null,
        val powerPlayPoints: Int? = null,
        val shorthandedGoals: Int? = null,
        val shorthandedPoints: Int? = null,
        val gameWinningGoals: Int? = null,
        val overtimeGoals: Int? = null,
        val shots: Int? = null,
        /** A fraction: 0.0714 is 7.1 percent. */
        val shootingPctg: Double? = null,
        /** A fraction: 0.5 is 50 percent. */
        val faceoffWinningPctg: Double? = null,
        /** Average ice time per game, in whole seconds. */
        val avgTimeOnIceSeconds: Long? = null,
    ) : PlayerTotals

    data class Goalie(
        val gamesPlayed: Int? = null,
        val gamesStarted: Int? = null,
        val wins: Int? = null,
        val losses: Int? = null,
        val overtimeLosses: Int? = null,
        val goalsAgainst: Int? = null,
        val goalsAgainstAverage: Double? = null,
        val shotsAgainst: Int? = null,
        /** A fraction: 0.90981 is .910. */
        val savePercentage: Double? = null,
        val shutouts: Int? = null,
        /** Total time on ice, in whole seconds. */
        val timeOnIceSeconds: Long? = null,
        val goals: Int? = null,
        val assists: Int? = null,
        val penaltyMinutes: Int? = null,
    ) : PlayerTotals
}

/**
 * One season row. A player's season can hold several, one per league or team, so a row keeps its own league and team.
 */
data class PlayerSeasonLine(
    /** The feed's eight-digit season, such as `20262027`. Null when the row does not say, and such rows never win. */
    val season: Int?,
    val gameTypeId: Int?,
    val leagueAbbrev: String?,
    val teamName: String?,
    /** Orders rows inside one season. */
    val sequence: Int?,
    val totals: PlayerTotals,
    /** The club's abbreviation (`CAR`), when the club is one the adapter knows. Null otherwise, and the UI prints the name. */
    val teamAbbrev: String? = null,
) {
    /** True for a regular-season row: the feed's game type 2. */
    val isRegularSeason: Boolean get() = gameTypeId == REGULAR_SEASON

    /** True for a playoff row: the feed's game type 3. */
    val isPlayoffs: Boolean get() = gameTypeId == PLAYOFF_GAME_TYPE
}

/**
 * The position as it is printed: the feed's `C`, `L`, `R`, `D` and `G`, with the wings written `LW` and `RW`. A code that
 * is none of those is returned exactly as the feed sent it, rather than guessed into one of the five.
 */
fun playerPositionLabel(code: String): String =
    when (code) {
        "L" -> "LW"
        "R" -> "RW"
        else -> code
    }

/**
 * The player's age in whole years on [today]: the years between the birth date and today, so a player is one year old on
 * the first birthday, not the day before it. Null when the birth date is absent or does not parse, and never negative.
 */
fun PlayerProfile.ageOn(today: LocalDate): Int? {
    val birth = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    // `until` truncates toward zero, so a birth date after today would read as 0 years; it has no age at all.
    if (birth > today) return null
    return birth.until(today, DateTimeUnit.YEAR).toInt()
}

/** The newest season any row carries, or null when no row says which season it is. */
fun List<PlayerSeasonLine>.newestSeason(): Int? = mapNotNull { it.season }.maxOrNull()

/**
 * The rows of the newest season the feed carries, in reading order: the NHL regular-season row first, then the rest by
 * descending sequence. A player whose current hockey is not NHL keeps their own season here rather than a blank card.
 */
fun List<PlayerSeasonLine>.newestSeasonRows(): List<PlayerSeasonLine> {
    val newest = newestSeason() ?: return emptyList()
    return filter { it.season == newest }
        .sortedWith(
            compareBy<PlayerSeasonLine> { if (it.leagueAbbrev == NHL_LEAGUE && it.gameTypeId == REGULAR_SEASON) 0 else 1 }
                .thenByDescending { it.sequence ?: Int.MIN_VALUE },
        )
}

/**
 * The Season tab's rows: the NHL regular-season rows of the newest season that has one, in club order. A traded season
 * gives one row per club. Other leagues and playoff rows are left out, since they are not this tab's to show.
 */
fun List<PlayerSeasonLine>.newestNhlRegularSeasonRows(): List<PlayerSeasonLine> {
    val nhl = filter { it.season != null && it.leagueAbbrev == NHL_LEAGUE && it.isRegularSeason }
    val newest = nhl.mapNotNull { it.season }.maxOrNull() ?: return emptyList()
    return nhl.filter { it.season == newest }.sortedBy { it.sequence ?: Int.MIN_VALUE }
}

/** The feed's league code for the NHL. */
internal const val NHL_LEAGUE = "NHL"

/** The feed's game-type id for the regular season. */
internal const val REGULAR_SEASON = 2

/** The feed's game-type id for the playoffs. */
internal const val PLAYOFF_GAME_TYPE = 3
