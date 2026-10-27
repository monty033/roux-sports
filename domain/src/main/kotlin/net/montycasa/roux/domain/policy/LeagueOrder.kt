package net.montycasa.roux.domain.policy

/** Stable ids, independent of display names or current data availability. */
object LeagueOrder {
    val canonical: List<String> = listOf("nhl", "nfl", "mlb")

    /** Keep saved relative order while accommodating removed, added and repeated catalog ids. */
    fun reconcile(
        saved: List<String>,
        catalog: List<String> = canonical,
    ): List<String> = (saved.filter { it in catalog } + catalog).distinct()
}
