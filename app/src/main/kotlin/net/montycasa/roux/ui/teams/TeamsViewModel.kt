package net.montycasa.roux.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
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
import net.montycasa.roux.domain.repo.TeamAlertPreferences

data class TeamsUiState(
    val rows: List<TeamRef> = emptyList(),
    val favourites: Set<TeamKey> = emptySet(),
    /** Teams whose saved alert controls have at least one switch on. Drives the row's bell and its spoken state. */
    val alerting: Set<TeamKey> = emptySet(),
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
    /** The team whose alert sheet is open, and what its switches currently say. Null when no sheet is open. */
    val alerts: TeamAlerts? = null,
    /** True while the sheet's switches are being read or written, so a double tap cannot race itself. */
    val alertsBusy: Boolean = false,
) {
    val canRetry: Boolean get() = !isRefreshing && (error != null || freshness is Freshness.Stale)
}

/** The five alerts a team can be followed for, in the order the sheet groups them. */
enum class AlertKind {
    Upcoming,
    Start,
    Goals,
    Intermission,
    Final,
}

/**
 * One team's alert settings, as the sheet shows them.
 *
 * Every switch is read from the store rather than assumed false: a sheet that opened with all of them off would
 * invite the user to "turn on" something that is already on, and writing that value would be a no-op they would
 * read as a broken switch.
 */
data class TeamAlerts(
    val team: TeamRef,
    val start: Boolean = false,
    val goals: Boolean = false,
    val intermission: Boolean = false,
    val final: Boolean = false,
    val upcoming: Boolean = false,
    /** True when a toggle could not be saved, so the switch can snap back with an explanation. */
    val saveFailed: Boolean = false,
) {
    /** Whether the row behind the sheet should show a ringing bell. */
    val anyOn: Boolean get() = start || goals || intermission || final || upcoming
}

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsViewModel(
    private val standings: StandingsRepository,
    private val preferences: PreferencesRepository,
    /**
     * Called after an alert setting is saved, so the watches are brought in line with it.
     *
     * Passed in rather than reached for, because the view model has no business knowing about WorkManager: what
     * it knows is that the answer to "what should be watched" has changed.
     */
    private val alertsChanged: suspend () -> Unit = {},
) : ViewModel() {
    private val favourites = MutableStateFlow<Set<TeamKey>>(emptySet())
    private val alerting = MutableStateFlow<Set<TeamKey>>(emptySet())
    private val editable = MutableStateFlow(true)
    private val saveFailed = MutableStateFlow(false)
    private val known = MutableStateFlow(true)
    private val preferenceWrites = Mutex()
    private val alertWrites = Mutex()
    private val refreshTrigger = MutableStateFlow(0L)
    private var retained = Loadable<List<StandingRow>>(null, Freshness.Missing, null)

    /**
     * The row order as last decided by a load, which is *not* recomputed when the selection changes.
     *
     * Favourites sort first, so deriving the order on every emission meant a tap moved the row under the
     * user's finger - the one being tapped jumped to the top of the list, taking its switch with it, and on a
     * scrolling screen the row that had been next landed where the tap was already heading. The order is
     * therefore a decision this view model makes when it has a reason to: a load, or an explicit retry. A
     * checkbox can change without rearranging the list around the finger that changed it.
     */
    private val order = MutableStateFlow<List<TeamRef>>(emptyList())

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

    /**
     * The four small answers the screen needs, grouped because `combine` only has typed overloads up to five
     * flows and the load and favourites are the two that carry the state worth reading separately.
     */
    private data class AlertSheetState(
        val team: TeamAlerts,
        val busy: Boolean = false,
    )

    private val alertSheet = MutableStateFlow<AlertSheetState?>(null)

    private data class Extras(
        val canEdit: Boolean,
        val saveFailed: Boolean,
        val selectionKnown: Boolean,
        val sheet: AlertSheetState?,
    )

    private val extras: Flow<Extras> =
        combine(editable, saveFailed, known, alertSheet) { canEdit, failedSave, selectionKnown, sheet ->
            Extras(canEdit, failedSave, selectionKnown, sheet)
        }

    val uiState: StateFlow<TeamsUiState> =
        combine(
            refreshTrigger.flatMapLatest {
                flow {
                    emit(TeamsLoad(retained, isRefreshing = true))
                    // The picker needs identities, not live rankings; it does not own a game poller.
                    retained = standings.standings(anyGameLive = false)
                    retained.data?.let { rows ->
                        // Decided here, once per load, for the reason in [order]'s comment.
                        favouritesReady.await()
                        val teams = rows.map { it.team }
                        order.value = ordered(teams, favourites.value)
                        alerting.value = readAlerting(teams)
                    }
                    emit(TeamsLoad(retained, isRefreshing = false))
                }
            },
            flow {
                favouritesReady.await()
                emitAll(favourites)
            },
            order,
            alerting,
            extras,
        ) { load, selected, rowOrder, alertingTeams, extra ->
            TeamsUiState(
                rows = rowOrder,
                favourites = selected,
                alerting = alertingTeams,
                isRefreshing = load.isRefreshing,
                freshness = load.value.freshness,
                error = load.value.error,
                canEdit = extra.canEdit,
                selectionKnown = extra.selectionKnown,
                saveFailed = extra.saveFailed,
                alerts = extra.sheet?.team,
                alertsBusy = extra.sheet?.busy ?: false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamsUiState())

    /** Opens the alert sheet for [team], reading what is already saved before showing a switch as off. */
    fun openAlerts(team: TeamRef) {
        alertSheet.value = AlertSheetState(TeamAlerts(team), busy = true)
        viewModelScope.launch {
            val saved = runCatching { preferences.getTeamAlertPreferences(team.key) }.getOrDefault(TeamAlertPreferences())
            // Only fill in the sheet that is still open for this team: a sheet opened and closed while the read
            // was in flight must not reappear with the settings of the team the user opened before it.
            alertSheet.update { current ->
                if (current?.team?.team?.key == team.key) {
                    AlertSheetState(saved.asAlerts(team), busy = false)
                } else {
                    current
                }
            }
        }
    }

    fun closeAlerts() {
        val team = alertSheet.value?.team?.team
        alertSheet.value = null
        if (team == null) return
        // Re-read after closing rather than trusting the sheet's last shown value. A write may have just landed, and
        // the row behind the sheet must show the stored answer as soon as the sheet is out of the way. The mutex lets
        // this wait for an in-flight write instead of racing it and reading the old value.
        viewModelScope.launch {
            alertWrites.withLock {
                val saved =
                    runCatching { preferences.getTeamAlertPreferences(team.key) }
                        .getOrDefault(TeamAlertPreferences())
                alerting.update { current ->
                    if (saved.anyOn) current + team.key else current - team.key
                }
            }
        }
    }

    /**
     * Turns one alert on or off. Every switch comes through here, so the mutex, the write-then-show order and the
     * failed-write rollback exist once rather than five times.
     *
     * The stored value is read again inside the lock, and only [kind] is changed. Writing the sheet's copy of all
     * five would overwrite a switch that changed while the sheet was open with the value the sheet first saw.
     */
    fun setAlert(
        kind: AlertKind,
        on: Boolean,
    ) {
        val current = alertSheet.value ?: return
        if (current.busy) return
        val shown = current.team.withAlert(kind, on).copy(saveFailed = false)
        alertSheet.value = current.copy(team = shown, busy = true)
        viewModelScope.launch {
            alertWrites.withLock {
                val key = current.team.team.key
                // A failed read leaves the sheet on its old values: writing from a guess would claim a setting the
                // store never agreed to. The write itself is still not attempted.
                val stored = runCatching { preferences.getTeamAlertPreferences(key) }.getOrNull()
                if (stored == null) {
                    alertSheet.update { it?.copy(team = current.team.copy(saveFailed = true), busy = false) }
                    return@withLock
                }
                val next = stored.withAlert(kind, on)
                val saved = runCatching { preferences.setTeamAlertPreferences(key, next) }.isSuccess
                if (saved) {
                    alertSheet.update { it?.copy(team = next.asAlerts(current.team.team), busy = false) }
                    // The watches are only as good as the setting they were built from.
                    runCatching { alertsChanged() }
                } else {
                    // Put the switch back rather than leaving it showing a setting that was never stored.
                    alertSheet.update { it?.copy(team = current.team.copy(saveFailed = true), busy = false) }
                }
            }
        }
    }

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

    /** Which of [teams] have at least one alert switch on. Read once per load, never per row in composition. */
    private suspend fun readAlerting(teams: List<TeamRef>): Set<TeamKey> =
        teams
            .filter { team ->
                val saved = runCatching { preferences.getTeamAlertPreferences(team.key) }.getOrNull()
                saved != null && saved.anyOn
            }.map { it.key }
            .toSet()

    /** Favourites first, then by club name: the order the list uses whenever it is allowed to re-sort. */
    private fun ordered(
        teams: List<TeamRef>,
        favourites: Set<TeamKey>,
    ): List<TeamRef> = teams.sortedWith(compareBy<TeamRef> { it.key !in favourites }.thenBy { it.name ?: it.abbrev })

    private data class TeamsLoad(
        val value: Loadable<List<StandingRow>>,
        val isRefreshing: Boolean,
    )
}

private fun TeamAlertPreferences.asAlerts(team: TeamRef): TeamAlerts =
    TeamAlerts(
        team = team,
        start = start,
        goals = goals,
        intermission = intermission,
        final = final,
        upcoming = upcoming,
    )

private fun TeamAlerts.withAlert(
    kind: AlertKind,
    on: Boolean,
): TeamAlerts =
    when (kind) {
        AlertKind.Upcoming -> copy(upcoming = on)
        AlertKind.Start -> copy(start = on)
        AlertKind.Goals -> copy(goals = on)
        AlertKind.Intermission -> copy(intermission = on)
        AlertKind.Final -> copy(final = on)
    }

private fun TeamAlertPreferences.withAlert(
    kind: AlertKind,
    on: Boolean,
): TeamAlertPreferences =
    when (kind) {
        AlertKind.Upcoming -> copy(upcoming = on)
        AlertKind.Start -> copy(start = on)
        AlertKind.Goals -> copy(goals = on)
        AlertKind.Intermission -> copy(intermission = on)
        AlertKind.Final -> copy(final = on)
    }
