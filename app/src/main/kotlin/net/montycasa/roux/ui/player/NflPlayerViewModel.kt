package net.montycasa.roux.ui.player

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
import net.montycasa.roux.domain.model.NflGameLog
import net.montycasa.roux.domain.model.NflPlayerId
import net.montycasa.roux.domain.model.NflPlayerProfile
import net.montycasa.roux.domain.repo.NflPlayerRepository

/**
 * One football player's page.
 *
 * The profile and the log are two endpoints, so they are two [Loadable]s here: a failed log leaves the profile on
 * screen and a failed profile leaves whatever log arrived. Null means "not asked yet"; a [Loadable] with no data is an
 * answer, including an empty one.
 */
data class NflPlayerUiState(
    val playerId: NflPlayerId,
    val profile: Loadable<NflPlayerProfile?>? = null,
    val gameLog: Loadable<NflGameLog?>? = null,
    val loading: Boolean = true,
)

class NflPlayerViewModel(
    private val playerId: NflPlayerId,
    private val repository: NflPlayerRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NflPlayerUiState(playerId = playerId))
    val uiState: StateFlow<NflPlayerUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    /** Reloads both endpoints. Each keeps its own answer, so one failure never clears the other. */
    fun refresh() {
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val profile = readProfile()
            mutableState.update { it.copy(profile = profile) }
            val log = readGameLog()
            mutableState.update { it.copy(gameLog = log, loading = false) }
        }
    }

    private suspend fun readProfile(): Loadable<NflPlayerProfile?> =
        try {
            repository.profile(playerId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A repository that throws is the same answer as one that failed: there is nothing to show yet.
            Loadable(null, Freshness.Missing, LoadError.Local)
        }

    private suspend fun readGameLog(): Loadable<NflGameLog?> =
        try {
            repository.gameLog(playerId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Loadable(null, Freshness.Missing, LoadError.Local)
        }
}
