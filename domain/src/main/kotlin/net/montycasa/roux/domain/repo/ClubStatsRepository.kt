package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.TeamKey

/**
 * Reads a club's season skater and goalie statistics, preferring the cache and falling back to the
 * network.
 *
 * The season is not a caller input: it comes from the same league day the schedule already derives,
 * so the Stats tab cannot ask for a different season than the tab beside it.
 */
interface ClubStatsRepository {
    /**
     * The club's statistics for the season containing the current league day.
     *
     * A failure is reported inside the [Loadable] alongside any cached copy, so the club page can
     * keep its header and tab row and offer retry in the tab.
     */
    suspend fun stats(teamKey: TeamKey): Loadable<ClubStats>

    /** The endpoint's own name, for callers that prefer to say exactly which feed is read. */
    suspend fun clubStats(teamKey: TeamKey): Loadable<ClubStats> = stats(teamKey)
}

/** A shorter name for the same contract, matching the other club-page repositories. */
typealias StatsRepository = ClubStatsRepository
