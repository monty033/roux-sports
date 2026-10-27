package net.montycasa.roux.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.StandingsRepository

data class TeamsUiState(
    val rows: List<TeamRef> = emptyList(),
    val favourites: Set<TeamKey> = emptySet(),
    val isRefreshing: Boolean = true,
    val freshness: Freshness = Freshness.Missing,
    val error: LoadError? = null,
    /** False when the store answers reads but refuses writes, which is what a newer version's file does. */
    val canEdit: Boolean = true,
    /**
     * False when the saved selection could not be read authoritatively - the read threw, or the store was
     * written by a newer version and answers reads with an empty set. The distinction matters: "no favourites"
     * and "we do not know your favourites" look identical if both are rendered as empty checkboxes.
     */
    val selectionKnown: Boolean = true,
    /** True after a write failed, so the screen can say so instead of making the tap look ignored. */
    val saveFailed: Boolean = false,
) {
    val canRetry: Boolean get() = !isRefreshing && (error != null || freshness is Freshness.Stale)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsViewModel(
    private val standings: StandingsRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val favourites = MutableStateFlow<Set<TeamKey>>(emptySet())
    private val editable = MutableStateFlow(true)
    private val saveFailed = MutableStateFlow(false)
    private val known = MutableStateFlow(true)
    private val preferenceWrites = Mutex()
    private val refreshTrigger = MutableStateFlow(0L)
    private var retained = Loadable<List<StandingRow>>(null, Freshness.Missing, null)

    // Preferences has no Flow API. Read once on init; after each successful write the local copy
    // remains authoritative, including when the screen stops collecting and later returns.
    //
    // Neither call may throw into this scope. A failed read used to kill the shared flow, which left the
    // picker on its initial "loading" state with no way back, and a throw inside `toggle`'s launch would
    // have reached the default handler and crashed the app. Favourites are convenience data: losing them
    // is worth an empty selection, never a dead screen.
    private val favouritesReady =
        viewModelScope.async {
            val read = runCatching { preferences.getFavourites() }
            // Asking whether the store accepts writes up front turns an exception on the first tap into a
            // control the screen can disable with an explanation. A check that throws answers "not writable":
            // assuming the opposite would let an edit overwrite a selection that was never read.
            val writable = runCatching { preferences.isWritable() }.getOrDefault(false)
            favourites.value = read.getOrDefault(emptySet())
            // A forward-version store answers reads with an empty set and refuses writes, so an empty set from
            // it does not mean "nothing is favourited" - it means the answer is unknown. Editing from that base
            // would overwrite choices this process never saw, so an unknown selection is not editable.
            known.value = read.isSuccess && writable
            editable.value = known.value
        }

    val uiState: StateFlow<TeamsUiState> =
        combine(
            refreshTrigger.flatMapLatest {
                flow {
                    emit(TeamsLoad(retained, isRefreshing = true))
                    // The picker needs identities, not live rankings; it does not own a game poller.
                    retained = standings.standings(anyGameLive = false)
                    emit(TeamsLoad(retained, isRefreshing = false))
                }
            },
            flow {
                favouritesReady.await()
                emitAll(favourites)
            },
            editable,
            saveFailed,
            known,
        ) { load, selected, canEdit, failedSave, selectionKnown ->
            TeamsUiState(
                rows =
                    load.value.data
                        .orEmpty()
                        .map { it.team }
                        .sortedWith(compareBy<TeamRef> { it.key !in selected }.thenBy { it.name ?: it.abbrev }),
                favourites = selected,
                isRefreshing = load.isRefreshing,
                freshness = load.value.freshness,
                error = load.value.error,
                canEdit = canEdit,
                selectionKnown = selectionKnown,
                saveFailed = failedSave,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamsUiState())

    fun toggle(team: TeamKey) {
        // Cheap rejection only: the binding check is the one after the await, which is what stops a write
        // when the store turns out to be read-only while this tap was in flight.
        if (!editable.value) return
        viewModelScope.launch {
            favouritesReady.await()
            if (!editable.value) return@launch
            preferenceWrites.withLock {
                // Read inside the lock: a suspended write must not let a later toggle lose a team.
                val current = favourites.value
                val next = if (team in current) current - team else current + team
                // Publish only after the write lands. A failed write keeps the old selection and is
                // reported, rather than throwing out of the scope or showing a change that was not saved.
                if (runCatching { preferences.setFavourites(next) }.isSuccess) {
                    favourites.value = next
                    saveFailed.value = false
                } else {
                    saveFailed.value = true
                }
            }
        }
    }

    fun retry() {
        refreshTrigger.update { it + 1 }
    }

    private data class TeamsLoad(
        val value: Loadable<List<StandingRow>>,
        val isRefreshing: Boolean,
    )
}
