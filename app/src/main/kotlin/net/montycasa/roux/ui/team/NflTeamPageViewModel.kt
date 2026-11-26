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
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.resultFor
import net.montycasa.roux.domain.repo.NflClubRosterRepository
import net.montycasa.roux.domain.repo.NflClubScheduleRepository

/**
 * A football club's page.
 *
 * The page has two independent requests: the season carries the record, the schedule and the bye, and the roster
 * carries the other tab. They are loaded together but kept apart here, so a roster failure cannot take the schedule
 * away and a schedule failure cannot take the roster away.
 */
data class NflTeamPageUiState(
    val teamKey: TeamKey,
    val team: TeamRef? = null,
    val season: NflClubSchedule? = null,
    val loading: Boolean = true,
    val error: LoadError? = null,
    val stale: Freshness.Stale? = null,
    val roster: NflClubRoster? = null,
    val rosterLoading: Boolean = true,
    val rosterError: LoadError? = null,
    val rosterStale: Freshness.Stale? = null,
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
    private val roster: NflClubRosterRepository,
    private val teamKey: TeamKey,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NflTeamPageUiState(teamKey = teamKey))
    val uiState: StateFlow<NflTeamPageUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    /** Reloads both tabs. The schedule tab's retry uses this; the roster tab has its own. */
    fun refresh() {
        loadSchedule()
        loadRoster()
    }

    /** Reloads the roster alone, so a failed roster does not also ask the feed for the season again. */
    fun refreshRoster() {
        loadRoster()
    }

    private fun loadSchedule() {
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

    private fun loadRoster() {
        mutableState.update { it.copy(rosterLoading = true, rosterError = null) }
        viewModelScope.launch {
            try {
                val loadable = roster.roster(teamKey)
                mutableState.update { state ->
                    state.copy(
                        // A roster answer also names the club, so a failed season still gets a header.
                        team = loadable.data?.team ?: state.team,
                        roster = loadable.data ?: state.roster,
                        rosterLoading = false,
                        rosterError = loadable.error,
                        rosterStale = loadable.freshness as? Freshness.Stale,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }
}
