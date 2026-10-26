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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val fixtureText = Fixtures.text("score-gameday.json")
    private val fixtureBytes = Fixtures.bytes("score-gameday.json")

    /**
     * The slate's own date, read from the fixture rather than assumed from its directory name.
     *
     * The captured corpus lives under `fx/2026-10-26/` but the body says `currentDate` is
     * `2026-10-24`, so a hardcoded date here silently made every test in this class exercise a
     * mismatched response - the very bug one of the tests now pins.
     */
    private val date =
        requireNotNull(Regex("\"currentDate\":\"([0-9]{4}-[0-9]{2}-[0-9]{2})\"").find(fixtureText)) {
            "the fixture must carry currentDate"
        }.groupValues[1]

    private class Harness(
        val repository: ScoresRepositoryImpl,
        val requests: List<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
        /** Every body handed to the engine, so a test can compare one with what was cached. */
        val bodies: MutableList<ByteArray>,
    )

    /** The same real slate, but labelled with the requested date, as a matching answer would be. */
    private fun slateFor(requested: String): ByteArray =
        fixtureText.replace(Regex("\"currentDate\":\"[0-9-]+\""), "\"currentDate\":\"$requested\"").toByteArray()

    private fun harness(responder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null): Harness {
        val requests = mutableListOf<String>()
        val lastBody = mutableListOf<ByteArray>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                responder?.invoke(this, request)
                    ?: run {
                        val requested = request.url.encodedPath.substringAfterLast('/')
                        val body = slateFor(requested)
                        lastBody += body
                        respond(
                            body,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
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
            lastBody,
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
            assertArrayEquals(harness.bodies.last(), stored!!.bytes)
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

    @Test
    fun aSlateForAnotherDayIsNeitherReturnedNorCached() =
        runBlocking {
            // Answers the requested date with the fixture's own date, which is what the API did to
            // this very suite: a well-formed body for the wrong day.
            val harness =
                harness { _ ->
                    respond(
                        fixtureBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val result = harness.repository.loadDay("2026-11-01", "2026-11-01")

            assertNull("another day's scores must not be shown as this day's", result.data)
            assertEquals(LoadError.MismatchedDay("2026-11-01", date), result.error)
            assertNull(
                "another day's slate must never be filed under this key",
                harness.cache.get(CacheKey(CacheKind.Score, "2026-11-01")),
            )
        }

    @Test
    fun aQuietDateThatEchoesTodayIsAccepted() =
        runBlocking {
            // Browsing a date with no games: the endpoint echoes today beside an empty list. This
            // is the only empty answer that is trusted, and the echo is what earns the exemption.
            val harness =
                harness { _ ->
                    respond(
                        "{\"currentDate\":\"$date\",\"games\":[]}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val result = harness.repository.loadDay("2026-11-01", date)

            assertEquals(Freshness.Fresh, result.freshness)
            assertNotNull(result.data)
            assertEquals(0, result.data!!.games.size)
        }

    @Test
    fun anEmptySlateDatedSomeOtherDayIsRefusedRatherThanCached() =
        runBlocking {
            // A past empty slate never expires, so accepting this would file 2026-10-20's empty
            // answer under 2026-11-01 permanently, with no error to notice.
            val harness =
                harness { _ ->
                    respond(
                        "{\"currentDate\":\"2026-10-20\",\"games\":[]}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val result = harness.repository.loadDay("2026-11-01", date)

            assertNull(result.data)
            assertEquals(LoadError.MismatchedDay("2026-11-01", "2026-10-20"), result.error)
            assertNull(harness.cache.get(CacheKey(CacheKind.Score, "2026-11-01")))
        }

    @Test
    fun aBodyWhoseRowsAllFailedToMapIsNotMistakenForAQuietDay() =
        runBlocking {
            // One unusable row next to a date that is not the requested one: the slate is empty
            // after mapping, but it is emphatically not a quiet day.
            val harness =
                harness { _ ->
                    respond(
                        "{\"currentDate\":\"$date\",\"games\":[{\"id\":1}]}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val result = harness.repository.loadDay("2026-11-01", date)

            assertNull(result.data)
            assertTrue(result.error is LoadError.MismatchedDay)
        }

    @Test
    fun prefetchSurvivesAnImpossibleDate() =
        runBlocking {
            val harness = harness()

            // The interface promises no escaping exception; the day shift throws on this input.
            harness.repository.prefetchAdjacent("2026-02-30", date)

            assertEquals(emptyList<String>(), harness.requests)
        }

    @Test
    fun cancellingPrefetchStopsBeforeTheSecondNeighbour() =
        runBlocking {
            val harness =
                harness { request ->
                    delay(5_000)
                    respond(
                        slateFor(request.url.encodedPath.substringAfterLast('/')),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val job = launch { harness.repository.prefetchAdjacent("2026-10-15", date) }
            while (harness.requests.isEmpty()) delay(10)
            job.cancelAndJoin()

            // This pins the observable behaviour, not the mechanism: whether the guarded block
            // rethrows or swallows, a cancelled coroutine still stops at its next suspension, so a
            // mutant that replaces the rethrow with Unit passes this test. Keeping the rethrow is a
            // structured-concurrency rule, and it is deliberately not claimed as proven here.
            assertTrue("prefetch must stop when its caller cancels", job.isCancelled)
            assertEquals("the second neighbour must not be requested", 1, harness.requests.size)
        }

    @Test
    fun aSlowFetchIsStampedWhenItCompletesNotWhenItStarted() =
        runBlocking {
            var built: Harness? = null
            built =
                harness { _ ->
                    built!!.clock.advance(2.hours)
                    respond(
                        slateFor(date),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val h = requireNotNull(built)

            h.repository.loadDay(date, date)

            val stored = h.cache.get(CacheKey(CacheKind.Score, date))
            assertNotNull(stored)
            assertEquals(
                "the stamp is the moment the bytes arrived, not the moment the load began",
                h.clock.now(),
                stored!!.fetchedAt,
            )
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

    @Test
    fun refusesToShiftOutsideTheFourDigitRange() {
        // A shift off the end of the supported range must fail loudly rather than produce a
        // five-digit year the API would reject.
        assertThrowsIllegalArgument("9999-12-31", 1)
        assertThrowsIllegalArgument("0001-01-01", -1)
    }

    private fun assertThrowsIllegalArgument(
        date: String,
        days: Int = 1,
    ) {
        try {
            IsoDate.shift(date, days)
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException for $date")
    }
}
