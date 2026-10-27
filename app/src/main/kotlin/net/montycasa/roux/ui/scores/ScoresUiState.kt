package net.montycasa.roux.ui.scores

import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.policy.FavoritesHint

data class ScoresUiState(
    val selectedDate: String,
    /** Filtered rows, with favourites pinned when showing all games. */
    val rows: List<Game> = emptyList(),
    val favoritesHint: FavoritesHint? = null,
    val favouritesOnly: Boolean = true,
    /** Includes the initial load; existing rows remain visible during subsequent loads. */
    val isRefreshing: Boolean = false,
    /** Can accompany rows: a failed refresh does not replace cached content with an error screen. */
    val error: LoadError? = null,
    val freshness: Freshness = Freshness.Missing,
    /** Unfiltered navigation metadata from the last load of the selected date. */
    val dateIndex: List<DateIndexEntry> = emptyList(),
)
