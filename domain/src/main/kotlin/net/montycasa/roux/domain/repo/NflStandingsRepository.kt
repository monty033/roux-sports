package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflStandingTable

/**
 * Reads the football table, preferring the cache and falling back to the network.
 *
 * Neither method throws for an ordinary network or parse problem: the failure is reported inside the [Loadable]
 * beside whatever cached copy exists, so a cold start without a connection still draws the last table the app saw.
 *
 * Two shapes are asked for rather than one. The feed publishes the conference level as a single payload of two
 * tables, and the division level only per conference, so the division call takes the conference it is asking about.
 *
 * @see net.montycasa.roux.domain.repo.StandingsRepository the hockey equivalent, whose row shape is different enough
 *   that the two cannot share a return type: football states ties and points, hockey states overtime losses and points
 *   earned.
 */
interface NflStandingsRepository {
    /**
     * The two conference tables, sixteen clubs each.
     *
     * [anyGameLive] is a freshness input, never a request input: a table moves while games are being played and not at
     * all otherwise.
     */
    suspend fun conferences(anyGameLive: Boolean): Loadable<List<NflStandingTable>>

    /** One conference's four division tables. */
    suspend fun divisions(
        conference: NflConference,
        anyGameLive: Boolean,
    ): Loadable<List<NflStandingTable>>
}
