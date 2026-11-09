package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubSchedule
import net.montycasa.roux.domain.model.TeamKey

/**
 * Reads a club's whole-season schedule, preferring the cache and falling back to the network.
 *
 * The season is not a caller input: it follows the app's own league day, so the page cannot ask for
 * a club-season that disagrees with the calendar the rest of the app is showing.
 */
interface ClubScheduleRepository {
    /**
     * The club's games for the season containing the current league day, or for the season before it
     * when the current season has no games for the club yet. The returned [ClubSchedule.season] names
     * whichever season the games came from.
     *
     * A failure is reported inside the [Loadable] alongside any cached copy, so a club page can keep
     * its header and say that the schedule could not be read.
     */
    suspend fun schedule(teamKey: TeamKey): Loadable<ClubSchedule>
}

/** A shorter name for the same contract, for callers that already have "club" in scope. */
typealias ScheduleRepository = ClubScheduleRepository
