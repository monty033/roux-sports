package net.montycasa.roux.data.repo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.nhl.NhlAdapter
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ScoreDay
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The cache decision is the whole behaviour under test: what is requested, what is written, and what
 * the caller is told when a request fails. Fixtures are the real captured NHL responses.
 */
class ScoresRepositoryImplTest {
    private val date = "2026-10-26"
    private val fixture = Fixtures.bytes("score-gameday.json")

    private class Harness(
        val repository: ScoresRepositoryImpl,
        val requests: List<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
    )

    private fun harness(responder: ((MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)?) = null): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                responder?.invoke(this, request)
                    ?: respond(
                        fixture,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        val clock = MutableClock()
        val fileSystem = FakeFileSystem(clock)
        val cache = ResponseCache(fileSystem, DIRECTORY.toPath(), clock)
        val api = NhlApi(HttpClient(engine))
        return Harness(
            ScoresRepositoryImpl(api, NhlAdapter, cache, clock),
            requests,
            cache,
            clock,
        )
    }

    @Test
    fun aFreshCacheMakesNoRequest() =
        runBlocking {
            val harness = harness()
            harness.repository.loadDay(date, date)
            val afterFirst = harness.requests.size

            val second = harness.repository.loadDay(date, date)

            assertEquals(1, afterFirst)
            assertEquals("a fresh copy must not be refetched", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, second.freshness)
            assertNotNull(second.data)
        }

    @Test
    fun aFailedFetchReturnsTheCachedDayMarkedStale() =
        runBlocking {
            var fail = false
            val harness =
                harness { request ->
                    if (fail) throw IOException("offline") else null
                }
            val first = harness.repository.loadDay(date, date)
            val fetchedAt = harness.clock.now()

            fail = true
            harness.clock.advance(1.hours)
            // The fixture's own date, not the directory it lives in: a finished PAST slate never
            // expires, so a hardcoded "today" here would silently skip the fetch this test needs.
            val second = harness.repository.loadDay(date, requireNotNull(first.data).date)

            assertEquals(Freshness.Fresh, first.freshness)
            assertEquals("the cached slate must still be shown", first.data, second.data)
            assertTrue("a failure is never presented as current", second.freshness is Freshness.Stale)
            assertEquals(fetchedAt, (second.freshness as Freshness.Stale).since)
            assertEquals(LoadError.Network, second.error)
        }

    @Test
    fun aFailureWithNoCacheReportsMissing() =
        runBlocking {
            val harness =
                harness { _ ->
                    throw IOException("offline")
                }

            val result = harness.repository.loadDay(date, date)

            assertNull(result.data)
            assertEquals(Freshness.Missing, result.freshness)
            assertEquals(LoadError.Network, result.error)
        }

    @Test
    fun aParseFailureIsTreatedLikeAFailedFetchAndKeepsTheCache() =
        runBlocking {
            var broken = false
            val harness =
                harness { _ ->
                    if (!broken) return@harness null
                    respond(
                        "{\"not\":\"a scoreboard\"}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val first = harness.repository.loadDay(date, date)

            broken = true
            harness.clock.advance(1.hours)
            val second = harness.repository.loadDay(date, requireNotNull(first.data).date)

            assertEquals("a parse failure is not a network error", LoadError.Parse, second.error)
            assertEquals(first.data, second.data)
            assertTrue(second.freshness is Freshness.Stale)
        }

    @Test
    fun aSuccessWritesTheExactBytesToTheCache() =
        runBlocking {
            val harness = harness()

            harness.repository.loadDay(date, date)

            val stored = harness.cache.get(CacheKey(CacheKind.Score, date))
            assertNotNull(stored)
            // The raw body, not a re-encoding of the parsed model: this is what survives an
            // adapter change without invalidating every cached day.
            assertArrayEquals(fixture, stored!!.bytes)
        }

    @Test
    fun aCachedBodyThatNoLongerParsesIsTreatedAsAbsent() =
        runBlocking {
            val harness = harness()
            harness.cache.put(
                CacheKey(CacheKind.Score, date),
                "{\"stale\":\"schema\"}".toByteArray(),
                harness.clock.now(),
            )

            val result = harness.repository.loadDay(date, date)

            assertEquals("an unparseable cache must not block a fetch", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, result.freshness)
            assertNotNull(result.data)
        }

    @Test
    fun prefetchWarmsOnlyTheNeighboursAndOnlyOnce() =
        runBlocking {
            val harness = harness()

            harness.repository.prefetchAdjacent("2026-10-15", "2026-10-26")
            val firstPass = harness.requests.toList()

            harness.repository.prefetchAdjacent("2026-10-15", "2026-10-26")

            assertEquals(
                listOf("/v1/score/2026-10-14", "/v1/score/2026-10-16"),
                firstPass,
            )
            assertEquals("the second pass has fresh copies", 2, harness.requests.size)
        }

    @Test
    fun prefetchDoesNotRefetchANeighbourThatIsAlreadyFresh() =
        runBlocking {
            val harness = harness()
            harness.repository.loadDay("2026-10-16", "2026-10-26")

            harness.repository.prefetchAdjacent("2026-10-15", "2026-10-26")

            assertEquals(listOf("/v1/score/2026-10-16", "/v1/score/2026-10-14"), harness.requests)
        }

    @Test
    fun prefetchNeverPropagatesAFailure() =
        runBlocking {
            val harness =
                harness { _ ->
                    throw IOException("offline")
                }

            harness.repository.prefetchAdjacent("2026-10-15", "2026-10-26")
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-10-26T20:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: kotlin.time.Duration) {
            instant += by
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}

/** Paging by one day crosses month and year boundaries; string arithmetic would be silently wrong. */
class IsoDateTest {
    @Test
    fun shiftsWithinAMonth() {
        assertEquals("2026-10-15", IsoDate.shift("2026-10-14", 1))
        assertEquals("2026-10-13", IsoDate.shift("2026-10-14", -1))
    }

    @Test
    fun crossesAMonthBoundary() {
        assertEquals("2026-11-01", IsoDate.shift("2026-10-31", 1))
        assertEquals("2026-09-30", IsoDate.shift("2026-10-01", -1))
    }

    @Test
    fun crossesAYearBoundary() {
        assertEquals("2027-01-01", IsoDate.shift("2026-12-31", 1))
        assertEquals("2026-12-31", IsoDate.shift("2027-01-01", -1))
    }

    @Test
    fun handlesLeapYears() {
        // 2028 is divisible by 4; 2000 by 400; 2100 is not a leap year.
        assertEquals("2028-02-29", IsoDate.shift("2028-02-28", 1))
        assertEquals("2028-03-01", IsoDate.shift("2028-02-29", 1))
        assertEquals("2000-02-29", IsoDate.shift("2000-02-28", 1))
        assertEquals("2100-03-01", IsoDate.shift("2100-02-28", 1))
    }

    @Test
    fun rejectsDatesThatAreNotReal() {
        assertThrowsIllegalArgument("2026-02-30")
        assertThrowsIllegalArgument("2026-13-01")
        assertThrowsIllegalArgument("26-10-01")
        assertThrowsIllegalArgument("not-a-date")
    }

    private fun assertThrowsIllegalArgument(date: String) {
        try {
            IsoDate.shift(date, 1)
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException for $date")
    }
}
