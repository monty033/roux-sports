package net.montycasa.roux.domain.model

/**
 * A club's season schedule and the season those games belong to.
 *
 * [season] is the feed's eight-digit season id, such as `20252026`. It is the season the page asked
 * for unless that season had no games for the club yet, in which case it is the season before, so the
 * UI can say which season it is showing.
 */
data class ClubSchedule(
    val season: String,
    val games: List<ClubGame>,
)
