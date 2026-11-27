package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflGameLog
import net.montycasa.roux.domain.model.NflPlayerId
import net.montycasa.roux.domain.model.NflPlayerProfile

/**
 * Reads one football player's page: the profile and the game log.
 *
 * They are two endpoints and two cache entries, loaded separately so a failed log never takes the profile away and a
 * log refresh never evicts the profile. The player is [NflPlayerId], not a bare string or number, so an NFL id can
 * never be read as an NHL player.
 */
interface NflPlayerRepository {
    suspend fun profile(playerId: NflPlayerId): Loadable<NflPlayerProfile?>

    suspend fun gameLog(playerId: NflPlayerId): Loadable<NflGameLog?>
}
