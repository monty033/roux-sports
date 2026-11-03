package net.montycasa.roux.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.policy.LeagueCalendar
import net.montycasa.roux.domain.repo.PlayerProfileRepository
import kotlin.time.Clock

data class PlayerDetailUiState(
    val isLoading: Boolean = true,
    val profile: Loadable<PlayerProfile>? = null,
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

    private val mutableState = MutableStateFlow(PlayerDetailUiState())
    val uiState: StateFlow<PlayerDetailUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val read = readProfile()
            mutableState.update { it.copy(profile = read, isLoading = false) }
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
}
