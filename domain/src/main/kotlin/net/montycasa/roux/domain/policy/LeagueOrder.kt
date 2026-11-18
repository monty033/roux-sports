package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.League

/** Stable ids, independent of display names or current data availability. */
object LeagueOrder {
    val canonical: List<String> = listOf("nhl", "nfl", "mlb")

    /**
     * The [League] a stored id names, or null when no league has that id. "mlb" is canonical but has no [League] yet,
     * and an unknown id must not be read as NHL, so the caller decides what an unmapped id means.
     */
    fun leagueOf(id: String): League? = League.entries.firstOrNull { it.id == id }

    /** Keep saved relative order while accommodating removed, added and repeated catalog ids. */
    fun reconcile(
        saved: List<String>,
        catalog: List<String> = canonical,
    ): List<String> = (saved.filter { it in catalog } + catalog).distinct()
}
