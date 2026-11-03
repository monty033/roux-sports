package net.montycasa.roux.ui.team

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
import net.montycasa.roux.domain.repo.StandingsRepository

/**
 * One club's page, filled from the league table the rest of the app already reads.
 *
 * There is no team endpoint: the standings cache is the source, and `standings()` already goes to the network when that
 * cache is missing or stale, so a cold start that arrives here fetches the table once rather than asking twice for the
 * same answer. The page state carries the matched row rather than a flattened record, because "no row" and "a row that
 * happens to say 0-0-0" are different answers and must not be rendered as the same thing.
 */
data class TeamDetailUiState(
    val teamKey: TeamKey,
    val row: StandingRow? = null,
    val isLoading: Boolean = true,
    val error: LoadError? = null,
)

class TeamDetailViewModel(
    private val teamKey: TeamKey,
    private val standings: StandingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TeamDetailUiState(teamKey))
    val uiState: StateFlow<TeamDetailUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    /** Re-reads the table after a failure; the page has no other way back. */
    fun retry() = load()

    private fun load() {
        mutableState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { apply(readTable()) }
    }

    private fun apply(loaded: TableResult) {
        mutableState.update {
            it.copy(
                row = loaded.row,
                isLoading = false,
                error = loaded.table.error,
            )
        }
    }

    private suspend fun readTable(): TableResult =
        try {
            val table = standings.standings(anyGameLive = false)
            TableResult(table, table.data?.firstOrNull { it.team.key == teamKey })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A repository that throws is the same answer as one that failed: the header has no record to show.
            TableResult(Loadable(null, Freshness.Missing, LoadError.Local), null)
        }

    private data class TableResult(
        val table: Loadable<List<StandingRow>>,
        val row: StandingRow?,
    )
}
