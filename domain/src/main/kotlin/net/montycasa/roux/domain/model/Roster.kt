package net.montycasa.roux.domain.model

/** One player on a club's current roster, limited to what the Roster tab shows. */
data class RosterPlayer(
    /** The feed's player id, when present; rows can still render if an old payload omits it. */
    val id: Long? = null,
    val name: String,
    /** Null when the payload omits the number; the UI must not invent one. */
    val sweaterNumber: Int? = null,
    /** The feed's position code (C, L, R, D, G), not a group label. */
    val positionCode: String? = null,
    val headshotUrl: String? = null,
)

/**
 * A club's roster, in the feed's own three groups.
 *
 * `defensemen` in the wire payload is surfaced here as [defense], matching the rest of the app.
 * Each list is ordered by sweater number ascending by the adapter: the feed's order is not
 * documented as meaningful, and a roster is read by number.
 */
data class ClubRoster(
    val forwards: List<RosterPlayer> = emptyList(),
    val defense: List<RosterPlayer> = emptyList(),
    val goalies: List<RosterPlayer> = emptyList(),
)
