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
     * The days either side of [selectedDate], already filtered and ordered like [rows].
     *
     * Read from the cache, never fetched for their own sake: this is what the screen shows for a day the viewer is
     * moving towards, and what the selected day falls back to while its own copy is still loading.
     */
    val neighboursByDate: Map<String, List<Game>> = emptyMap(),
)
