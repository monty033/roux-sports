package net.montycasa.roux.ui.scores

import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.policy.FavoritesHint
import net.montycasa.roux.domain.policy.LeagueOrder

data class ScoresUiState(
    val selectedDate: String,
    /** Current day from the polling clock in the existing league zone. */
    val today: String,
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
    /** Counted before the favourites filter, from the loaded day without another request. */
    val liveCount: Int = 0,
    val favouriteCount: Int = 0,
    val leagueOrder: List<String> = LeagueOrder.canonical,
    val selectedLeagueId: String = "nhl",
    /** Feed day in the league zone; null until a day has actually loaded. */
    val loadedDate: String? = null,
    /**
     * Rows for every day the screen knows, already filtered and ordered like [rows].
     *
     * Read from the cache, never fetched for their own sake: this is what a page shows for a day the viewer is moving
     * towards, and what the selected day falls back to while its own copy is still loading. It accumulates rather than
     * describing only the neighbours, because a day stops being a neighbour the instant it becomes the selection and a
     * page must not lose its rows at that moment.
     */
    val rowsByDate: Map<String, List<Game>> = emptyMap(),
)
