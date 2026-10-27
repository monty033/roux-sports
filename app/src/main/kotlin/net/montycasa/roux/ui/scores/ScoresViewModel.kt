package net.montycasa.roux.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.DateStrip
import net.montycasa.roux.domain.policy.FavoritesOrdering
import net.montycasa.roux.domain.policy.PollPolicy
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import kotlin.time.Clock

/** Collection owns all polling, including the five-second WhileSubscribed grace period. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoresViewModel(
    private val scores: ScoresRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    private val leagueZone = TimeZone.of("America/New_York")
    private val selectedDate = MutableStateFlow(DateStrip.defaultSelection(emptyList(), leagueDay()))
    private val refreshTrigger = MutableStateFlow(0L)
    private val favouritesOnly = MutableStateFlow<Boolean?>(null)
    private val preferenceWrites = Mutex()

    // Retain the selected day's unfiltered content across a cancelled request or upstream restart.
    // The repository remains the authority on freshness, including after time spent in the background.
    private var retained: DayLoad? = null

    val uiState: StateFlow<ScoresUiState> =
        combine(
            combine(selectedDate, leagueDays(), refreshTrigger) { date, today, trigger ->
                Request(date, today, trigger)
            }.flatMapLatest { request -> pollDay(request) },
            settings(),
        ) { load, settings ->
            val filtered =
                FavoritesOrdering.filter(
                    load.value.data
                        ?.games
                        .orEmpty(),
                    settings.favourites,
                    settings.only,
                )
            ScoresUiState(
                selectedDate = load.date,
                liveCount =
                    load.value.data
                        ?.games
                        .orEmpty()
                        .count { it.isLive },
                favouriteCount = settings.favourites.size,
                loadedDate = load.value.data?.date,
                rows = if (settings.only) filtered.rows else FavoritesOrdering.pin(filtered.rows, settings.favourites),
                favoritesHint = filtered.hint,
                favouritesOnly = settings.only,
                isRefreshing = load.refreshing,
                error = load.value.error,
                freshness = load.value.freshness,
                dateIndex =
                    load.value.data
                        ?.dateIndex
                        .orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoresUiState(selectedDate.value))

    /** Calls the cache-aware repository immediately, even when the cadence has stopped. */
    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    fun onPullToRefresh() = refresh()

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    fun setFavouritesOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                preferences.setFavouritesOnly(enabled)
                favouritesOnly.value = enabled
            }
        }
    }

    private fun settings(): Flow<Settings> =
        flow {
            val favourites = preferences.getFavourites()
            val storedOnly = preferences.getFavouritesOnly()
            emitAll(favouritesOnly.map { Settings(favourites, it ?: storedOnly) })
        }

    private fun pollDay(request: Request): Flow<DayLoad> =
        flow {
            var value: Loadable<ScoreDay> =
                retained?.takeIf { it.date == request.date }?.value
                    ?: Loadable(null, Freshness.Missing, null)
            // Only a new upstream start announces progress: a pull-to-refresh, a date change, or the
            // midnight rollover, each of which begins a fresh flow. A cadence tick loads silently,
            // because a live game polling every 25 seconds would otherwise flash a progress indicator
            // on the primary screen for the length of every request.
            var announceProgress = true
            while (currentCoroutineContext().isActive) {
                if (announceProgress) {
                    emit(DayLoad(request.date, value, refreshing = true))
                }
                // Always load first on resubscription; loadDay checks FreshnessPolicy itself.
                value = scores.loadDay(request.date, request.today)
                val loaded = DayLoad(request.date, value, refreshing = false)
                retained = loaded
                emit(loaded)
                announceProgress = false
                val now = clock.now()
                val wait =
                    PollPolicy.nextDelay(
                        now,
                        request.date,
                        now.toLocalDateTime(leagueZone).date.toString(),
                        value.data?.games.orEmpty(),
                    ) ?: break
                delay(wait)
            }
        }

    // Midnight must wake even a completed poll flow. Calendar arithmetic handles 23/25-hour DST days.
    private fun leagueDays(): Flow<String> =
        flow {
            while (currentCoroutineContext().isActive) {
                val now = clock.now()
                val date = now.toLocalDateTime(leagueZone).date
                emit(date.toString())
                delay(date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(leagueZone) - now)
            }
        }

    private fun leagueDay(): String =
        clock
            .now()
            .toLocalDateTime(leagueZone)
            .date
            .toString()

    private data class Request(
        val date: String,
        val today: String,
        val trigger: Long,
    )

    private data class Settings(
        val favourites: Set<TeamKey>,
        val only: Boolean,
    )

    private data class DayLoad(
        val date: String,
        val value: Loadable<ScoreDay>,
        val refreshing: Boolean,
    )
}
