package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey

/** State groups always win; favourites lead within each group, then earliest start and game id. */
object ScoresOrdering {
    fun order(
        rows: List<Game>,
        favorites: Set<TeamKey> = emptySet(),
    ): List<Game> =
        rows.sortedWith(
            compareBy<Game> { rank(it.state) }
                .thenBy { it.home.key !in favorites && it.away.key !in favorites }
                .thenBy { it.startTimeUtc == null }
                // The domain stores parsed Instants, so this compares chronological time directly.
                .thenBy { it.startTimeUtc }
                .thenBy { it.id.value },
        )

    private fun rank(state: GameState): Int =
        when (state) {
            is GameState.Live -> 0
            GameState.Scheduled, GameState.Pre -> 1
            GameState.Final -> 2
            is GameState.Unknown -> 3
        }
}
