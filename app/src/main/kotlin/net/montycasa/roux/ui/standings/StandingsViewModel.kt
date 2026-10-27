package net.montycasa.roux.ui.standings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.StandingsGrouping
import net.montycasa.roux.domain.policy.StandingsGroups
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.StandingsRepository

data class StandingsUiState(
    val groups: StandingsGroups = StandingsGroups(emptyList()),
    val view: StandingsView = StandingsView.League,
    val isRefreshing: Boolean = false,
    val error: LoadError? = null,
    val stale: Freshness.Stale? = null,
    val loading: Boolean = true,
    val favouritesKnown: Boolean = false,
    val favouritesOnly: Boolean = true,
    val pinnedShown: Boolean = false,
)

class StandingsViewModel(
    private val standings: StandingsRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StandingsUiState())
    val uiState: StateFlow<StandingsUiState> = mutableState.asStateFlow()
    private var rows = emptyList<StandingRow>()
    private var favourites = emptySet<TeamKey>()

    init {
        refresh()
    }

    fun setView(view: StandingsView) {
        mutableState.update { grouped(it.copy(view = view)) }
    }

    /** Also rereads the shared preferences, whose repository currently has no observable API. */
    fun refresh() {
        if (mutableState.value.isRefreshing) return
        mutableState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val selected = readPreference { preferences.getFavourites() }
            val supported = readPreference { preferences.isWritable() } == true
            val only = readPreference { preferences.getFavouritesOnly() }
            favourites = selected.orEmpty()
            mutableState.update {
                grouped(it.copy(favouritesKnown = selected != null && supported, favouritesOnly = only ?: it.favouritesOnly))
            }
            val loaded =
                try {
                    standings.standings(anyGameLive = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A cache that cannot be written is not a network failure. Surfacing it here keeps the
                    // exception from escaping the scope (which would take the app down) and stops the spinner.
                    // Keep whatever staleness and rows were already on screen: the table did not stop being
                    // from that moment because this refresh failed.
                    mutableState.update {
                        it.copy(loading = false, isRefreshing = false, error = LoadError.Local)
                    }
                    return@launch
                }
            rows = loaded.data.orEmpty()
            mutableState.update {
                grouped(
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        error = loaded.error,
                        stale = stale(loaded),
                    ),
                )
            }
        }
    }

    private fun grouped(state: StandingsUiState): StandingsUiState {
        val pinned = if (state.favouritesKnown && state.favouritesOnly) rows.filter { it.team.key in favourites } else emptyList()
        return state.copy(groups = StandingsGrouping.group(rows, state.view, pinned), pinnedShown = pinned.isNotEmpty())
    }

    /**
     * Only the repository knows when the table on screen was fetched. Inventing a timestamp from the clock
     * would label a failed refresh as "updated just now", so a missing stale marker stays missing and the
     * screen offers its error state instead.
     */
    private fun stale(loaded: Loadable<List<StandingRow>>): Freshness.Stale? = loaded.freshness as? Freshness.Stale

    private suspend fun <T> readPreference(read: suspend () -> T): T? =
        try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}
