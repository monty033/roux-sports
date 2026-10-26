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
    val logoUrl: String? = null,
    val darkLogoUrl: String? = null,
) {
    // Validation belongs to the adapter layer; unexpected abbreviations and missing
    // display fields are retained without throwing.
    val abbrev: String get() = key.abbrev
}
