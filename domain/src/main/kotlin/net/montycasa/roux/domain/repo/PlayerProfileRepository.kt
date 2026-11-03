package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.PlayerProfile

/**
 * Reads one player's landing page, preferring the cache and falling back to the network.
 *
 * A failure is reported inside the [Loadable] alongside any cached copy, so the screen can show what it has and offer
 * retry.
 */
interface PlayerProfileRepository {
    suspend fun profile(playerId: Long): Loadable<PlayerProfile>
}
