package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.TeamKey

/**
 * Guidance distinguishes missing team selection from a day without favourite games, since only the
 * former needs configuration. No success case is needed: a null hint means no guidance is required.
 */
enum class FavoritesHint {
    PickTeams,
    NoFavouriteGames,
}

/** Rows in feed order with optional guidance; a hint can accompany rows when no teams are selected. */
data class FavoritesResult(
    val rows: List<Game>,
    val hint: FavoritesHint?,
)

/** Pure filtering for one day's games, with team identity supplied by the caller. */
object FavoritesOrdering {
    /**
     * Keep games involving either favourite team when [favoritesOnly] is on, preserving feed order.
     * With no favourites, return all games and [FavoritesHint.PickTeams] regardless of the toggle:
     * an unconfigured filter should invite team selection rather than hide the entire scoreboard.
     * With favourites selected, an empty filtered result yields [FavoritesHint.NoFavouriteGames],
     * including an empty day, because team selection cannot fix the day's lack of favourite games.
     * With the toggle off, selected favourites never hide rows or produce an absence hint.
     * An empty day with no favourites still yields PickTeams; missing selection takes precedence.
     * Filtering does not order rows; callers apply [ScoresOrdering] in either toggle state.
     */
    fun filter(
        games: List<Game>,
        favorites: Set<TeamKey>,
        favoritesOnly: Boolean,
    ): FavoritesResult {
        if (favorites.isEmpty()) return FavoritesResult(games, FavoritesHint.PickTeams)
        if (!favoritesOnly) return FavoritesResult(games, null)
        val rows = games.filter { it.involves(favorites) }
        return FavoritesResult(rows, if (rows.isEmpty()) FavoritesHint.NoFavouriteGames else null)
    }

    private fun Game.involves(favorites: Set<TeamKey>): Boolean = home.key in favorites || away.key in favorites
}
