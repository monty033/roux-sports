package net.montycasa.roux.domain.model

/**
 * Every club's season penalty minutes for one season, from the league penalties table.
 *
 * It is a separate feed from the league summary, so it is kept as its own value and joined onto the
 * summary by the data layer. The list order is the feed's and carries no meaning.
 */
data class LeaguePenalties(
    val clubs: List<ClubPenalties>,
)

/**
 * One club's penalty minutes as the penalties table names it.
 *
 * [teamId] is the key the join prefers. [penaltyMinutes] is the season total and is null when the feed
 * omits it, never zero.
 */
data class ClubPenalties(
    val teamId: Int?,
    val teamName: String,
    val penaltyMinutes: Int?,
)
