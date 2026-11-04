package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.LeagueSummary

/**
 * Reads every club's season totals for the whole league, preferring the cache and falling back to the
 * network.
 *
 * One answer serves every club page, so the league is read once rather than once per club. The season
 * is not a caller input: it comes from the same league day the schedule and the club stats derive.
 */
interface LeagueStatsRepository {
    /**
     * The league table for the season containing the current league day.
     *
     * A failure is reported inside the [Loadable] alongside any cached copy, so the Team Stats tab can
     * keep showing the last table it had and offer retry.
     */
    suspend fun summary(): Loadable<LeagueSummary>
}
