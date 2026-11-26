package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.TeamKey

/**
 * Reads one football club's season.
 *
 * The season is a single payload, so there is one call and one cache entry per club. [anyGameLive] is a freshness
 * input only: a schedule changes when a game finishes, not while it is being played.
 */
interface NflClubScheduleRepository {
    suspend fun schedule(
        club: TeamKey,
        anyGameLive: Boolean,
    ): Loadable<NflClubSchedule?>
}
