package net.montycasa.roux.domain.model

/**
 * A league the app reads. [id] is the stable string that persisted keys and [net.montycasa.roux.domain.policy.LeagueOrder]
 * use; it is the same for every reader of the value, so it must not change.
 */
enum class League(
    val id: String,
) {
    NHL("nhl"),
    NFL("nfl"),
}

/**
 * A team's abbreviation as supplied by its league's feed. The abbreviation alone is not unique: ten NFL abbreviations
 * also name an NHL club, so the league is part of the identity.
 */
data class TeamKey(
    val league: League,
    val abbrev: String,
)

/** A team's identity and optional display assets, independent of any game's score. */
data class TeamRef(
    val key: TeamKey,
    val name: String? = null,
    /**
     * The club without its place, as the feed states it ("Hurricanes", "Maple Leafs"). Tables are scanned by club,
     * so they show this; the full name stays for anywhere a place matters. Null when the feed omits it.
     */
    val shortName: String? = null,
    val logoUrl: String? = null,
    val darkLogoUrl: String? = null,
) {
    // Validation belongs to the adapter layer; unexpected abbreviations and missing
    // display fields are retained without throwing.
    val abbrev: String get() = key.abbrev
}
