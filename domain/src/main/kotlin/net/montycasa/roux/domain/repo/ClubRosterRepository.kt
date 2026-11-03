package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.TeamKey

/**
 * Reads a club's current roster, preferring the cache and falling back to the network.
 *
 * Failures are reported inside the [Loadable] so the club page can keep its header and tab row and
 * offer retry in the content area.
 */
interface ClubRosterRepository {
    suspend fun roster(teamKey: TeamKey): Loadable<ClubRoster>
}

/** A shorter name for the same contract, matching the other club-page repositories. */
typealias RosterRepository = ClubRosterRepository
