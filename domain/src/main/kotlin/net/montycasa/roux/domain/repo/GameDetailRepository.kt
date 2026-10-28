package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState

/**
 * One game's page: three endpoints, loaded together and reported apart.
 *
 * [state] is the state of the row that opened the page, and it is a parameter rather than something read back out of
 * the result because it decides how long each endpoint's copy is worth keeping: a live page is worth re-reading every
 * half minute, a final one never is, and a scheduled one sits between the two because the puck drops sometime after the
 * announced time.
 */
interface GameDetailRepository {
    suspend fun load(
        gameId: GameId,
        state: GameState,
    ): GameDetail

    /** Ignores a fresh cache. For a pull-to-refresh, where doing nothing reads as a gesture that failed. */
    suspend fun refresh(
        gameId: GameId,
        state: GameState,
    ): GameDetail
}
