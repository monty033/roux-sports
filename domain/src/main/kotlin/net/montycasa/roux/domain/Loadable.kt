package net.montycasa.roux.domain

import kotlin.time.Duration
import kotlin.time.Instant

sealed interface Freshness {
    data object Fresh : Freshness

    data class Stale(
        val since: Instant,
    ) : Freshness

    data object Missing : Freshness
}

sealed interface LoadError {
    data object Network : LoadError

    data class Http(
        val status: Int,
        /** A usable server-requested pause. Null means the server did not say, never "retry at once". */
        val retryAfter: Duration? = null,
    ) : LoadError

    data object Parse : LoadError

    /**
     * The server answered with a slate belonging to a different day.
     *
     * Kept distinct from [Parse] because the body was well-formed and the failure was the reply's
     * content, and from [Network] because retrying the same date may well repeat it.
     */
    data class MismatchedDay(
        val requested: String,
        val returned: String,
    ) : LoadError

    /**
     * The device could not complete the load for a reason that is not the feed's: a cache it cannot write,
     * for instance.
     *
     * Distinct from [Network] because retrying immediately may well succeed and the connection was never the
     * problem, and from [Parse] because the response may have been perfectly good.
     */
    data object Local : LoadError

    /**
     * A request refused before it was sent, because its host is not on the allowlist: an unexpected URL, or a
     * redirect somewhere the app never agreed to talk to.
     *
     * Carries the host so the refusal can be traced, and is deliberately not retried on the normal cadence -
     * repeating the same request earns the same refusal, and a retry loop would present a bug or an attack as
     * an endless "offline".
     */
    data class Blocked(
        val host: String,
    ) : LoadError
}

/**
 * What the fetch that just ran actually did.
 *
 * A sealed result rather than a boolean plus a defaulted error, because the boolean let a caller
 * report a failure and then omit which kind: the omitted argument defaulted to a network error, so
 * a parse failure could be shown to the user as "no connection". Carrying the reason inside the
 * failure makes it impossible to forget.
 */
sealed interface FetchOutcome {
    data object Succeeded : FetchOutcome

    data class Failed(
        val error: LoadError,
    ) : FetchOutcome
}

data class Cached<T>(
    val data: T,
    val fetchedAt: Instant,
)

/** Keeps cached content visible when a refresh fails, including an offline cold start (A11). */
data class Loadable<T>(
    val data: T?,
    val freshness: Freshness,
    val error: LoadError?,
) {
    companion object {
        /**
         * Only a failure with no cached data should reach an error screen: there is nothing to show.
         * A populated cache must render even on a cold start without a network connection.
         */
        fun <T> from(
            cached: Cached<T>?,
            outcome: FetchOutcome,
        ): Loadable<T> {
            if (cached == null) {
                return Loadable(null, Freshness.Missing, (outcome as? FetchOutcome.Failed)?.error)
            }
            // Even a one-second-old copy is stale after a failed fetch: a failure must never
            // be presented as current data. Preserve the content and its last successful fetch time.
            return when (outcome) {
                is FetchOutcome.Failed -> {
                    Loadable(cached.data, Freshness.Stale(since = cached.fetchedAt), outcome.error)
                }

                FetchOutcome.Succeeded -> {
                    Loadable(cached.data, Freshness.Fresh, null)
                }
            }
        }
    }
}
