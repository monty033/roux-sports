package net.montycasa.roux.domain.model

/**
 * A player's landing page: who the player is, what the feed says they have done, and their last five games.
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
    val shootsCatches: String? = null,
    /** Null when the feed carries no draft record, which is different from a draft record with empty fields. */
    val draft: PlayerDraft? = null,
    val career: PlayerCareer = PlayerCareer(),
    /** Every season row the feed sent, across leagues. One season can hold several rows. */
    val seasons: List<PlayerSeasonLine> = emptyList(),
    val lastFive: List<PlayerRecentGame> = emptyList(),
)

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
)

/**
 * The rows of the newest season the feed carries, in reading order: the NHL regular-season row first, then the rest by
 * descending sequence. A player whose current hockey is not NHL keeps their own season here rather than a blank card.
 */
fun List<PlayerSeasonLine>.newestSeasonRows(): List<PlayerSeasonLine> {
    val newest = mapNotNull { it.season }.maxOrNull() ?: return emptyList()
    return filter { it.season == newest }
        .sortedWith(
            compareBy<PlayerSeasonLine> { if (it.leagueAbbrev == NHL_LEAGUE && it.gameTypeId == REGULAR_SEASON) 0 else 1 }
                .thenByDescending { it.sequence ?: Int.MIN_VALUE },
        )
}

private const val NHL_LEAGUE = "NHL"
private const val REGULAR_SEASON = 2

/**
 * A skater's or goalie's game from the last five. The two shapes differ, so each has its own variant.
 */
sealed interface PlayerRecentGame {
    /** `YYYY-MM-DD` as the feed sends it. */
    val gameDate: String?
    val opponentAbbrev: String?

    /** True for a home game, false for a road game, null when the feed does not say. */
    val isHome: Boolean?

    data class Skater(
        override val gameDate: String?,
        override val opponentAbbrev: String?,
        override val isHome: Boolean?,
        val goals: Int? = null,
        val assists: Int? = null,
        val points: Int? = null,
        val plusMinus: Int? = null,
        val penaltyMinutes: Int? = null,
        val shots: Int? = null,
        val powerPlayGoals: Int? = null,
        val shorthandedGoals: Int? = null,
        val timeOnIceSeconds: Long? = null,
    ) : PlayerRecentGame

    data class Goalie(
        override val gameDate: String?,
        override val opponentAbbrev: String?,
        override val isHome: Boolean?,
        /** The feed's decision code: W, L or OTL. */
        val decision: String? = null,
        val gamesStarted: Int? = null,
        val goalsAgainst: Int? = null,
        val shotsAgainst: Int? = null,
        val savePercentage: Double? = null,
        val penaltyMinutes: Int? = null,
        val timeOnIceSeconds: Long? = null,
    ) : PlayerRecentGame
}
