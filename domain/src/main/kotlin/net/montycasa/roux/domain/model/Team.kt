package net.montycasa.roux.domain.model

/** A team's abbreviation as supplied by the NHL feed. */
@JvmInline
value class TeamKey(
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
