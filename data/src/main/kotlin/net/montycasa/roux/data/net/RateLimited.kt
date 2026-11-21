package net.montycasa.roux.data.net

import kotlin.time.Duration

/** Keeps a server-requested pause distinct from other HTTP failures for the polling caller. Shared by every league client. */
class RateLimited(
    val retryAfter: Duration?,
) : Exception("HTTP 429 rate limit; retry delay: ${retryAfter ?: "unspecified (missing or invalid Retry-After)"}")
