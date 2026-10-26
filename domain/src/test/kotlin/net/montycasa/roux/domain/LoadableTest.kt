package net.montycasa.roux.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.time.Instant

class LoadableTest {
    private val fetchedAt = Instant.parse("2026-10-26T16:00:00Z")
    private val cached = Cached(listOf("PHI", "ANA"), fetchedAt)

    @Test
    fun failedFetchWithoutCacheIsMissingWithTheFailureReason() {
        val result = Loadable.from<List<String>>(null, FetchOutcome.Failed(LoadError.Network))
        assertNull(result.data)
        assertEquals(Freshness.Missing, result.freshness)
        assertEquals(LoadError.Network, result.error)
    }

    @Test
    fun successfulFetchWithoutCacheIsMissingWithoutError() {
        val result = Loadable.from<List<String>>(null, FetchOutcome.Succeeded)
        assertNull(result.data)
        assertEquals(Freshness.Missing, result.freshness)
        assertNull(result.error)
    }

    @Test
    fun successfulFetchWithCacheIsFreshAndPreservesData() {
        val result = Loadable.from(cached, FetchOutcome.Succeeded)
        assertSame(cached.data, result.data)
        assertEquals(Freshness.Fresh, result.freshness)
        assertNull(result.error)
    }

    @Test
    fun failedFetchWithCacheIsStaleSinceFetchedAtAndPreservesData() {
        val result = Loadable.from(cached, FetchOutcome.Failed(LoadError.Network))
        assertSame(cached.data, result.data)
        assertEquals(Freshness.Stale(since = fetchedAt), result.freshness)
        assertEquals(LoadError.Network, result.error)
    }

    @Test
    fun aParseFailureIsNeverReportedAsANetworkFailure() {
        // The boolean form defaulted the error to Network, so a caller with a parse failure who
        // omitted the argument told the user their connection was down. The reason now travels
        // inside the outcome and cannot be omitted.
        val result = Loadable.from(cached, FetchOutcome.Failed(LoadError.Parse))
        assertEquals(LoadError.Parse, result.error)
        assertEquals(Freshness.Stale(since = fetchedAt), result.freshness)
    }

    @Test
    fun customErrorIsPreservedWithoutCache() {
        val error = LoadError.Http(503)
        val result = Loadable.from<List<String>>(null, FetchOutcome.Failed(error))
        assertEquals(Freshness.Missing, result.freshness)
        assertEquals(error, result.error)
    }

    @Test
    fun customErrorIsPreservedWithCache() {
        val error = LoadError.Http(503)
        val result = Loadable.from(cached, FetchOutcome.Failed(error))
        assertEquals(Freshness.Stale(since = fetchedAt), result.freshness)
        assertSame(cached.data, result.data)
        assertEquals(error, result.error)
    }
}
