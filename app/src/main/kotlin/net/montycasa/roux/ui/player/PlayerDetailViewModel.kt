package net.montycasa.roux.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.repo.PlayerProfileRepository
import kotlin.time.Clock

data class PlayerDetailUiState(
    val isLoading: Boolean = true,
    val profile: Loadable<PlayerProfile>? = null,
    /** Null while the game log is still being read. An empty list is a log with no games, not a failure. */
    val gameLog: Loadable<List<PlayerGame>>? = null,
)

/**
 * One player's page. The profile comes from [repository], which owns the cache and the freshness rule; this class only
 * asks for it and keeps the answer on screen.
 */
class PlayerDetailViewModel(
    private val playerId: Long,
    private val repository: PlayerProfileRepository,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    /** The season being played now, as the feed's eight-digit id. It decides whether a season card says "This season". */
    val currentSeason: Int = LeagueCalendar.seasonId(clock.now(), zone).toInt()

    /** Today's civil date. The Info card counts a player's age to it, so a birthday turns over on the day itself. */
    val today: LocalDate = clock.now().toLocalDateTime(zone).date

    private val mutableState = MutableStateFlow(PlayerDetailUiState())
    val uiState: StateFlow<PlayerDetailUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /**
     * The profile first, then the game log for the season being played now. The log is never the profile's newest season,
     * which can be last season's. A failed log leaves the profile on screen.
     */
    private fun load() {
        mutableState.update { it.copy(isLoading = true, gameLog = null) }
        viewModelScope.launch {
            val read = readProfile()
            mutableState.update { it.copy(profile = read, isLoading = false) }
            val profile = read.data ?: return@launch
            val log = readGameLog(profile)
            mutableState.update { it.copy(gameLog = log) }
        }
    }

    private suspend fun readProfile(): Loadable<PlayerProfile> =
        try {
            repository.profile(playerId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A repository that throws is the same answer as one that failed: there is nothing to show yet.
            Loadable(null, Freshness.Missing, LoadError.Local)
        }

    private suspend fun readGameLog(profile: PlayerProfile): Loadable<List<PlayerGame>> =
        try {
            repository.gameLog(profile.playerId, currentSeason, profile.isGoalie)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Loadable(null, Freshness.Missing, LoadError.Local)
        }
}
