package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflTeam
import net.montycasa.roux.domain.model.NflTeamGroup

/**
 * Reads the football clubs, preferring the cache and falling back to the network.
 *
 * Neither method throws for an ordinary network or parse problem: the failure is reported inside the [Loadable]
 * beside whatever cached copy exists.
 *
 * @see NflStandingsRepository where the division tables [groupedByDivision] is composed from come from.
 */
interface NflTeamsRepository {
    /** The clubs in the order the teams feed lists them, with no grouping. */
    suspend fun teams(): Loadable<List<NflTeam>>

    /**
     * The same clubs under the feed's division names.
     *
     * The teams feed states no division, so this composes it with the division tables: each club lands under the
     * division whose table names it, divisions come in the order the tables state them (AFC, then NFC), and clubs
     * keep the teams feed's order inside a division. A club no division table names is returned in a trailing group
     * with a null division. It is never dropped: the list is the clubs, and the grouping is only how they are shown.
     */
    suspend fun groupedByDivision(): Loadable<List<NflTeamGroup>>
}
