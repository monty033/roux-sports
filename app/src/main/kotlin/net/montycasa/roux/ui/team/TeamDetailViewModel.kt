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
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.ClubRosterRepository
import net.montycasa.roux.domain.repo.ClubScheduleRepository
import net.montycasa.roux.domain.repo.ClubStatsRepository
import net.montycasa.roux.domain.repo.StandingsRepository
import kotlin.time.Clock

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
    /**
     * The club's schedule, or null before the schedule has answered.
     *
     * Kept separate from [row] on purpose: the header reads the standings row and the section reads
     * this, so one request failing cannot take the other's content away.
     */
    val schedule: Loadable<List<ClubGame>>? = null,
    /** The current roster, with its own cache entry and failure. */
    val roster: Loadable<ClubRoster>? = null,
    /**
     * True while a roster request is in flight. Deliberately not the header's [isLoading]: a header refresh must
     * not put the roster's retry into a spinner, and a failed roster is not "still loading" by default.
     */
    val isRosterLoading: Boolean = false,
    /** The club's season statistics, with the same independent cache and failure rules as the roster. */
    val stats: Loadable<ClubStats>? = null,
    /** True while a stats request is in flight; the header's flag must not drive this tab's retry. */
    val isStatsLoading: Boolean = false,
)

class TeamDetailViewModel(
    private val teamKey: TeamKey,
    private val standings: StandingsRepository,
    private val schedule: ClubScheduleRepository? = null,
    private val roster: ClubRosterRepository? = null,
    private val stats: ClubStatsRepository? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TeamDetailUiState(teamKey))
    val uiState: StateFlow<TeamDetailUiState> = mutableState.asStateFlow()

    init {
        loadTableAndSchedule()
        loadRoster()
        loadStats()
    }

    /** Re-reads the header and schedule after a failure; roster retry is [retryRoster]. */
    fun retry() = loadTableAndSchedule()

    /** Re-reads only the roster, leaving the schedule and header untouched. */
    fun retryRoster() = loadRoster()

    /** Re-reads only the statistics, leaving the schedule, header and roster untouched. */
    fun retryStats() = loadStats()

    /** Re-reads both sources behind the Players tab, leaving the header and schedule untouched. */
    fun retryPlayers() {
        loadRoster()
        loadStats()
    }

    private fun loadTableAndSchedule() {
        mutableState.update { it.copy(isLoading = true, error = null, schedule = null) }
        viewModelScope.launch { apply(readTable()) }
        // Independent requests: a failed schedule must not blank the header, and a failed header
        // must not hide a schedule the feed did return.
        if (schedule != null) {
            viewModelScope.launch { mutableState.update { it.copy(schedule = readSchedule()) } }
        }
    }

    private fun loadRoster() {
        if (roster == null) return
        mutableState.update { it.copy(roster = null, isRosterLoading = true) }
        viewModelScope.launch {
            val read = readRoster()
            mutableState.update { it.copy(roster = read, isRosterLoading = false) }
        }
    }

    private fun loadStats() {
        if (stats == null) return
        mutableState.update { it.copy(stats = null, isStatsLoading = true) }
        viewModelScope.launch {
            val read = readStats()
            mutableState.update { it.copy(stats = read, isStatsLoading = false) }
        }
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

    private suspend fun readSchedule(): Loadable<List<ClubGame>> =
        try {
            schedule?.schedule(teamKey) ?: Loadable(null, Freshness.Missing, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The header is already on screen; the section reports the failure and offers retry.
            Loadable(null, Freshness.Missing, LoadError.Local)
        }

    private suspend fun readRoster(): Loadable<ClubRoster> =
        try {
            roster?.roster(teamKey) ?: Loadable(null, Freshness.Missing, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Loadable(null, Freshness.Missing, LoadError.Local)
        }

    private suspend fun readStats(): Loadable<ClubStats> =
        try {
            stats?.stats(teamKey) ?: Loadable(null, Freshness.Missing, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Loadable(null, Freshness.Missing, LoadError.Local)
        }

    private data class TableResult(
        val table: Loadable<List<StandingRow>>,
        val row: StandingRow?,
    )
}
