package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.TeamKey

/**
 * Reads one football club's current roster.
 *
 * The roster is one payload, so there is one call and one cache entry per club. It is a separate entry from the
 * club's season: a roster refresh must never evict or overwrite the schedule the other tab is reading.
 */
interface NflClubRosterRepository {
    suspend fun roster(club: TeamKey): Loadable<NflClubRoster?>
}
