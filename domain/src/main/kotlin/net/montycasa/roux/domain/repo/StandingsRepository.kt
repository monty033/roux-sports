package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow

/**
 * Reads the league table, preferring the cache and falling back to the network.
 *
 * Neither method throws for an ordinary network or parse problem: the failure is reported inside the
 * [Loadable] alongside whatever cached copy exists, so a cold start without a connection still
 * renders the last table the app saw.
 *
 * There is no date parameter. The league table is addressed by the league's own calendar date, and
 * the NHL league day is the date in New York - so the day boundary belongs to the repository, which
 * owns the clock, rather than to each caller. A caller in another timezone must not be able to ask
 * for a different table than the app's other screens.
 */
interface StandingsRepository {
    /**
     * The league table for the current league day.
     *
     * [anyGameLive] only affects how long a cached table is trusted: standings move while games are
     * being played, and not at all otherwise. It is a freshness input, never a request input.
     */
    suspend fun standings(anyGameLive: Boolean): Loadable<List<StandingRow>>

    /**
     * The seasons the league publishes.
     *
     * Fetched rarely and cached for a day: the list changes about once a season, and a picker built
     * on it must not spend a request every time it is opened.
     */
    suspend fun seasons(): Loadable<List<Season>>
}
