package net.montycasa.roux.domain.model

/**
 * One regular-season game from a player's game log. A skater's row and a goalie's row carry different fields, so each
 * shape is its own variant and a goalie never shows goals or assists it does not have.
 */
sealed interface PlayerGame {
    val gameId: Long

    /** `YYYY-MM-DD` as the feed sends it. */
    val gameDate: String?
    val opponentAbbrev: String?

    /** True for a home game, false for a road game, null when the feed does not say. */
    val isHome: Boolean?

    /** The club the player played for in this game, as its abbreviation. Null when the feed does not say. */
    val teamAbbrev: String?

    data class Skater(
        override val gameId: Long,
        override val gameDate: String?,
        override val opponentAbbrev: String?,
        override val isHome: Boolean?,
        override val teamAbbrev: String? = null,
        val goals: Int? = null,
        val assists: Int? = null,
        val points: Int? = null,
        val plusMinus: Int? = null,
        val shots: Int? = null,
        val timeOnIceSeconds: Long? = null,
    ) : PlayerGame

    data class Goalie(
        override val gameId: Long,
        override val gameDate: String?,
        override val opponentAbbrev: String?,
        override val isHome: Boolean?,
        override val teamAbbrev: String? = null,
        val decision: GoalieDecision? = null,
        val shotsAgainst: Int? = null,
        val goalsAgainst: Int? = null,
        /** A fraction: 0.91 is .910. */
        val savePercentage: Double? = null,
        val timeOnIceSeconds: Long? = null,
    ) : PlayerGame
}

/** A goalie's result for one game. A code the feed sends that is not one of these is absent, not guessed. */
enum class GoalieDecision { Win, Loss, OvertimeLoss }

/**
 * Newest game first. The feed's order is not relied on: rows sort by date descending, undated rows last, and two games
 * on one date by the later game id.
 */
fun List<PlayerGame>.newestFirst(): List<PlayerGame> =
    sortedWith(
        compareBy<PlayerGame> { it.gameDate == null }
            .thenByDescending { it.gameDate }
            .thenByDescending { it.gameId },
    )
