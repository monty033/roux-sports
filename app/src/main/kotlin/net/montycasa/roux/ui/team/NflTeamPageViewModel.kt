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
import net.montycasa.roux.domain.model.FootballResult
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.resultFor
import net.montycasa.roux.domain.repo.NflClubScheduleRepository

/**
 * A football club's page.
 *
 * The page is one request: the season carries the record, the schedule and the bye, so there is nothing to combine and
 * no second load to keep consistent with the first.
 */
data class NflTeamPageUiState(
    val teamKey: TeamKey,
    val team: TeamRef? = null,
    val season: NflClubSchedule? = null,
    val loading: Boolean = true,
    val error: LoadError? = null,
    val stale: Freshness.Stale? = null,
) {
    /** `8-1-0`, the way football writes it: the tie is printed even when it is zero. Null until a game is final. */
    val record: String? by lazy {
        val games = season?.entries.orEmpty().filterIsInstance<net.montycasa.roux.domain.model.NflSeasonEntry.Game>()
        if (games.isEmpty()) return@lazy null
        val results = games.map { it.game.resultFor(teamKey) }
        if (results.all { it == FootballResult.Unplayed }) return@lazy null
        val wins = results.count { it == FootballResult.Win }
        val losses = results.count { it == FootballResult.Loss }
        val ties = results.count { it == FootballResult.Tie }
        "$wins-$losses-$ties"
    }
}

class NflTeamPageViewModel(
    private val schedule: NflClubScheduleRepository,
    private val teamKey: TeamKey,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NflTeamPageUiState(teamKey = teamKey))
    val uiState: StateFlow<NflTeamPageUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // No live-game signal reaches this page, so the loader applies its idle window, exactly as the
                // standings page does.
                val loadable = schedule.schedule(teamKey, anyGameLive = false)
                mutableState.update { state ->
                    state.copy(
                        team = loadable.data?.team ?: state.team,
                        season = loadable.data ?: state.season,
                        // A failed refresh over a season already on screen keeps the season and reports the failure.
                        loading = false,
                        error = loadable.error,
                        stale = loadable.freshness as? Freshness.Stale,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }
}
