package net.montycasa.roux.data.nhl

/**
 * Deliberate point-in-time contract for the league table's expected size.
 * The feed cannot be trusted to declare its own completeness. Expansion or contraction
 * requires a deliberate one-line update to this constant. Until then, a changed-size table
 * fails loudly so the repository can serve cached data instead of rendering a table that
 * may be short a team. Only size is pinned so renames and relocations can still render.
 */
internal object NhlLeagueShape {
    const val EXPECTED_TEAM_COUNT = 32
}
