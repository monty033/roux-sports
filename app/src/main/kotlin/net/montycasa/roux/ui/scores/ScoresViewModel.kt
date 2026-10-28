package net.montycasa.roux.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.DateStrip
import net.montycasa.roux.domain.policy.FavoritesOrdering
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.policy.LeagueOrder
import net.montycasa.roux.domain.policy.PollPolicy
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import kotlin.random.Random
import kotlin.time.Clock

/** Collection owns all polling, including the five-second WhileSubscribed grace period. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoresViewModel(
    private val scores: ScoresRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    /** The device's zone: the league day is this phone's civil date, so it is injected rather than assumed. */
    private val deviceZone: TimeZone = TimeZone.currentSystemDefault(),
    /** Jitter source for the failure backoff, injected so a test can pin the multiplier. */
    private val jitter: () -> Double = { Random.Default.nextDouble() },
) : ViewModel() {
    private val selectedDate = MutableStateFlow(DateStrip.defaultSelection(emptyList(), leagueDay()))

    // The trigger carries whether this upstream start was the user asking. A pull must reach the network even when
    // the cache is fresh, which is exactly what loadDay refuses to do; everything else keeps the cache-aware load.
    private val refreshTrigger = MutableStateFlow(Trigger())
    private val favouritesOnly = MutableStateFlow<Boolean?>(null)
    private val preferenceWrites = Mutex()
    private val leagueOrder = MutableStateFlow<List<String>?>(null)

    // Which league row the drawer highlights when the filter is off. Only the user's choice: the persisted order is
    // an ordering, not a selection.
    private val chosenLeague = MutableStateFlow<String?>(null)

    /**
     * What the drawer draws, independent of the feed.
     *
     * Nothing here reaches [scores], so the drawer can be collected from a tab that never shows games without
     * starting a poll. That is the whole reason it is not part of the scores state.
     */
    val drawerState: StateFlow<DrawerUi> =
        combine(settings(), chosenLeague) { settings, chosen ->
            DrawerUi(
                favouriteCount = settings.favourites.size,
                favouritesOnly = settings.only,
                leagueOrder = settings.order,
                selectedLeagueId = if (settings.only) MY_TEAMS_LEAGUE else chosen ?: DEFAULT_LEAGUE,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(GRACE_MILLIS), DrawerUi())

    /** A drawer row was chosen: My Teams means the favourites filter, a league means show that league. */
    fun selectLeague(leagueId: String) {
        if (leagueId == MY_TEAMS_LEAGUE) {
            setFavouritesOnly(true)
        } else {
            chosenLeague.value = leagueId
            setFavouritesOnly(false)
        }
    }

    // Retain the selected day's unfiltered content across a cancelled request or upstream restart.
    // The repository remains the authority on freshness, including after time spent in the background.
    private var retained: DayLoad? = null

    // The pending neighbour warm-up. Kept so a new selection can cancel the previous one: a user flicking through
    // dates should cost one prefetch for the day they stopped on, not one per day they passed through.
    private var prefetchJob: Job? = null

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
                today = load.today,
                liveCount =
                    load.value.data
                        ?.games
                        .orEmpty()
                        .count { it.isLive },
                favouriteCount = settings.favourites.size,
                leagueOrder = settings.order,
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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoresUiState(selectedDate.value, leagueDay()))

    /** Loads immediately, even when the cadence has stopped. A pull also reaches past a fresh cache. */
    fun refresh() {
        refreshTrigger.update { Trigger(count = it.count + 1, force = true) }
    }

    fun onPullToRefresh() = refresh()

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    fun setFavouritesOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                // The filter applies now even if it cannot be stored: a disk that refuses the write must not take the
                // process down, and a tap that appears to do nothing is worse than one that forgets after a restart.
                favouritesOnly.value = enabled
                try {
                    preferences.setFavouritesOnly(enabled)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Not persisted; the session still honours the choice.
                }
            }
        }
    }

    fun setLeagueOrder(order: List<String>) {
        val reconciled = LeagueOrder.reconcile(order)
        // Publish immediately so a held handle follows the finger while durable writes serialize.
        leagueOrder.value = reconciled
        viewModelScope.launch {
            preferenceWrites.withLock {
                try {
                    preferences.setLeagueOrder(reconciled)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // The order is already applied in this session; losing it on restart beats losing the app.
                }
            }
        }
    }

    /**
     * Stored preferences, with this session's unconfirmed choices taking precedence.
     *
     * The stored half observes: a favourite added on the team picker, or a write that lands after this screen opened,
     * reaches the list without anyone re-reading the file.
     */
    private fun settings(): Flow<Settings> =
        combine(
            combine(
                preferences.observeFavourites(),
                preferences.observeFavouritesOnly(),
                preferences.observeLeagueOrder(),
            ) { favourites, only, order -> Settings(favourites, only, order) },
            combine(favouritesOnly, leagueOrder) { only, order -> only to order },
        ) { stored, chosen ->
            Settings(stored.favourites, chosen.first ?: stored.only, chosen.second ?: stored.order)
        }

    /**
     * Warms the days either side of [date] once the selection has settled.
     *
     * The delay is the whole point: without it every day the user flicks past would start two speculative fetches.
     * A load that produced no slate schedules nothing - its neighbours would fail the same way, and a request whose
     * only outcome is an error is noise on a battery.
     */
    private fun schedulePrefetch(
        date: String,
        today: String,
        hasSlate: Boolean,
    ) {
        prefetchJob?.cancel()
        if (!hasSlate) return
        prefetchJob =
            viewModelScope.launch {
                delay(PREFETCH_SETTLE_MS)
                scores.prefetchAdjacent(date, today)
            }
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
            // Consecutive failures, reset by any answer. The cadence cannot be derived from a slate we do not have:
            // an offline cold start has no games, so the normal policy would return null and stop the loop for good.
            var failedAttempts = 0
            // Only the load that starts this flow can be forced. The cadence below it is polling, and polling must
            // keep honouring a fresh cache instead of hammering the feed every 25 seconds.
            var forceNext = request.trigger.force
            while (currentCoroutineContext().isActive) {
                if (announceProgress) {
                    emit(DayLoad(request.date, request.today, value, refreshing = true))
                }
                // Always load first on resubscription; loadDay checks FreshnessPolicy itself.
                value =
                    if (forceNext) {
                        forceNext = false
                        scores.refreshDay(request.date, request.today)
                    } else {
                        scores.loadDay(request.date, request.today)
                    }
                val loaded = DayLoad(request.date, request.today, value, refreshing = false)
                retained = loaded
                schedulePrefetch(request.date, request.today, hasSlate = value.data != null)
                emit(loaded)
                announceProgress = false
                // A blocked host is a refusal, not a transport failure: asking again earns the same refusal. Stop
                // the loop rather than re-asking on the normal cadence; a new upstream start - a refresh, a date
                // change, the midnight rollover - tries once more. This is what makes LoadError.Blocked's promise
                // ("not retried on the normal cadence") true instead of merely written down.
                if (value.error is LoadError.Blocked) break
                val now = clock.now()
                val error = value.error
                val wait =
                    if (error != null) {
                        failedAttempts += 1
                        val backoff = PollPolicy.backoff(failedAttempts, jitter)
                        // A rate limit is an instruction, not a suggestion: wait at least as long as it asked, and
                        // keep the larger of the two so a small Retry-After cannot short-circuit the backoff.
                        val retryAfter = (error as? LoadError.Http)?.retryAfter
                        if (retryAfter != null && retryAfter > backoff) retryAfter else backoff
                    } else {
                        failedAttempts = 0
                        PollPolicy.nextDelay(
                            now,
                            request.date,
                            LeagueCalendar.leagueDay(now, deviceZone),
                            value.data?.games.orEmpty(),
                        ) ?: break
                    }
                delay(wait)
            }
        }

    // Midnight must wake even a completed poll flow. Calendar arithmetic handles 23/25-hour DST days.
    private fun leagueDays(): Flow<String> =
        flow {
            while (currentCoroutineContext().isActive) {
                val now = clock.now()
                val date = LeagueCalendar.leagueDate(now, deviceZone)
                emit(date.toString())
                delay(date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(deviceZone) - now)
            }
        }

    /** The day the app is browsing. One definition, shared with Standings, so the two tabs never disagree. */
    private fun leagueDay(): String = LeagueCalendar.leagueDay(clock.now(), deviceZone)

    private data class Request(
        val date: String,
        val today: String,
        val trigger: Trigger,
    )

    /**
     * [count] exists so a refresh is a distinct event even when nothing else changed; [force] says the load that
     * starts this flow came from the user, and only that first load may bypass a fresh cache.
     */
    private data class Trigger(
        val count: Long = 0,
        val force: Boolean = false,
    )

    private data class Settings(
        val favourites: Set<TeamKey>,
        val only: Boolean,
        val order: List<String>,
    )

    private companion object {
        /** Long enough that flicking through dates does not prefetch every pass, short enough to feel instant. */
        const val PREFETCH_SETTLE_MS = 500L

        /** Matches the list's grace period: a rotation must not restart the drawer's collection. */
        const val GRACE_MILLIS = 5_000L

        /** The drawer's My Teams row, which is the favourites filter rather than a league. */
        const val MY_TEAMS_LEAGUE = "myTeams"
        const val DEFAULT_LEAGUE = "nhl"
    }

    private data class DayLoad(
        val date: String,
        val today: String,
        val value: Loadable<ScoreDay>,
        val refreshing: Boolean,
    )
}

/**
 * The drawer's own state, derived from preferences alone.
 *
 * Deliberately not a slice of [ScoresUiState]: the drawer is reachable from every tab, including ones that show no
 * games, and opening it must never be the reason a feed request happens.
 */
data class DrawerUi(
    val favouriteCount: Int = 0,
    val favouritesOnly: Boolean = true,
    val leagueOrder: List<String> = LeagueOrder.canonical,
    val selectedLeagueId: String = "myTeams",
)
