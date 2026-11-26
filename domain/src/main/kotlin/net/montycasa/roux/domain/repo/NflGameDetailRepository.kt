package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.NflGameDetail

/**
 * One football game's page.
 *
 * [state] is the state of the row that opened the page and decides how long a cached copy is worth keeping, through
 * [net.montycasa.roux.domain.policy.FreshnessPolicy.detailMaxAge]: a live page is re-read every half minute, a final one
 * after the league's overnight correction window, and a scheduled one before its kickoff.
 */
interface NflGameDetailRepository {
    suspend fun load(
        gameId: GameId,
        state: GameState,
    ): Loadable<NflGameDetail?>

    /** Ignores a fresh cache. For a pull-to-refresh, where doing nothing reads as a gesture that failed. */
    suspend fun refresh(
        gameId: GameId,
        state: GameState,
    ): Loadable<NflGameDetail?>
}
