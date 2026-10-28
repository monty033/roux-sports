package net.montycasa.roux.domain.repo

import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ScoreDay

/**
 * Reads one day's scoreboard, preferring the cache and falling back to the network.
 *
 * [loadDay] never throws for an ordinary network or parse problem: the failure is reported inside
 * the [Loadable] alongside whatever cached copy exists, so a cold start without a connection still
 * renders the last slate the app saw.
 *
 * [today] is passed in rather than read from a clock so "past", "today" and "future" are decided by
 * the caller's notion of the viewer's day, which is a display concern and not a property of the
 * data. It must be an ISO `YYYY-MM-DD` date, like [date].
 */
interface ScoresRepository {
    /**
     * The slate for [date].
     *
     * When a copy is fresh no request is made at all. When a copy exists but is not fresh, a request
     * is made and a failure returns the old copy marked stale rather than an error.
     */
    suspend fun loadDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay>

    /**
     * Warms the days either side of [date] so paging sideways is instant.
     *
     * Only days without a fresh copy are fetched, failures are ignored - this is speculative work
     * and must never surface as an error - and no exception escapes.
     */
    suspend fun prefetchAdjacent(
        date: String,
        today: String,
    )

    /**
     * The slate for [date], requested even when a fresh copy already exists.
     *
     * [loadDay] deliberately answers a fresh cache without touching the network, which is right for
     * a poll tick and wrong for a pull-to-refresh: the user asking for the current slate knows
     * something the cache does not, and an immediate no-op reads as a broken gesture. A failure
     * still returns whatever copy exists, marked stale.
     *
     * The default body keeps existing implementations and test doubles compiling; it is the
     * cache-respecting load, and an implementation that cannot force should say so here.
     */
    suspend fun refreshDay(
        date: String,
        today: String,
    ): Loadable<ScoreDay> = loadDay(date, today)
}
