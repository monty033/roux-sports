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
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.data.nhl.NhlAdapter
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The league day is the load-bearing part: it decides which table is requested, cached and shown.
 * Everything else here is the shared cache-first ordering already proven for scores.
 */
class StandingsRepositoryImplTest {
    private val standingsBytes = Fixtures.bytes("standings.json")
    private val seasonsBytes = Fixtures.bytes("standings-season.json")

    private class Harness(
        val repository: StandingsRepositoryImpl,
        val requests: List<String>,
        val clock: MutableClock,
        val cache: ResponseCache,
    )

    private fun harness(
        files: ((FileSystem) -> FileSystem)? = null,
        responder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null,
    ): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                responder?.invoke(this, request)
                    ?: run {
                        val body = if (request.url.encodedPath.contains("season")) seasonsBytes else standingsBytes
                        respond(
                            body,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
            }
        val clock = MutableClock()
        val cache = ResponseCache(files?.invoke(FakeFileSystem(clock)) ?: FakeFileSystem(clock), DIRECTORY.toPath(), clock)
        // Through the factory, so the request boundary (429 handling, host allowlist) is part of the test.
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(StandingsRepositoryImpl(api, NhlAdapter, cache, clock), requests, clock, cache)
    }

    @Test
    fun theLeagueDayIsTheDateInNewYorkNotUtc() =
        runBlocking {
            val harness = harness()

            // 22:00 on the 25th in New York, already the 26th in UTC.
            harness.repository.standings(anyGameLive = false)

            assertEquals(listOf("/v1/standings/2026-10-25"), harness.requests)
        }

    @Test
    fun theLeagueDaySurvivesTheChangeOutOfDaylightSaving() =
        runBlocking {
            // 04:30Z on 2 November is 23:30 on 1 November in New York, after the November change.
            // A fixed -4 offset would call this the 2nd and ask for the wrong night's table.
            val afterChange = harness()
            afterChange.clock.set("2026-11-02T04:30:00Z")
            afterChange.repository.standings(anyGameLive = false)
            assertEquals(listOf("/v1/standings/2026-11-01"), afterChange.requests)

            // The summer case has to sit between 04:00Z and 05:00Z to be worth anything: at 03:30Z a
            // fixed -5 offset also yields the 14th, so the pair would pass a fixed-offset
            // implementation. At 04:30Z on 15 July New York is 00:30 on the 15th (-4) while a fixed
            // -5 would still say 23:30 on the 14th - that is the hour that discriminates.
            val summer = harness()
            summer.clock.set("2026-07-15T04:30:00Z")
            summer.repository.standings(anyGameLive = false)
            assertEquals(listOf("/v1/standings/2026-07-15"), summer.requests)
        }

    @Test
    fun nowIsNeverRequested() =
        runBlocking {
            val harness = harness()

            harness.repository.standings(anyGameLive = false)
            harness.repository.seasons()

            assertTrue("the deprecated /now route must never be used", harness.requests.none { it.endsWith("/now") })
            assertEquals(2, harness.requests.size)
        }

    @Test
    fun aRepeatWithinTheTtlMakesNoRequest() =
        runBlocking {
            val harness = harness()
            harness.repository.standings(anyGameLive = false)

            harness.clock.advance(30.minutes)
            harness.repository.standings(anyGameLive = false)

            assertEquals("an idle table is trusted for an hour", 1, harness.requests.size)
        }

    @Test
    fun aLiveTableHasAShorterLifetime() =
        runBlocking {
            val harness = harness()
            harness.repository.standings(anyGameLive = true)

            harness.clock.advance(30.minutes)
            harness.repository.standings(anyGameLive = true)

            assertEquals("standings move during play, so they are refetched", 2, harness.requests.size)
        }

    @Test
    fun aFailureReturnsTheCachedTableMarkedStale() =
        runBlocking {
            var fail = false
            val harness =
                harness {
                    if (fail) throw IOException("offline") else null
                }
            val first = harness.repository.standings(anyGameLive = false)
            val fetchedAt = harness.clock.now()

            fail = true
            // 90 minutes is past the one-hour idle lifetime but still 23:30 on the same New York
            // day; advancing two hours would cross local midnight and change the cache key instead
            // of exercising the stale path.
            harness.clock.advance(90.minutes)
            val second = harness.repository.standings(anyGameLive = false)

            assertEquals(first.data, second.data)
            assertTrue(second.freshness is Freshness.Stale)
            assertEquals(fetchedAt, (second.freshness as Freshness.Stale).since)
            assertEquals(LoadError.Network, second.error)
        }

    @Test
    fun aFailureWithNoCacheReportsMissing() =
        runBlocking {
            val harness =
                harness {
                    throw IOException("offline")
                }

            val result = harness.repository.seasons()

            assertNull(result.data)
            assertEquals(Freshness.Missing, result.freshness)
            assertEquals(LoadError.Network, result.error)
        }

    @Test
    fun aSeasonListIsTrustedForADay() =
        runBlocking {
            val harness = harness()
            harness.repository.seasons()

            harness.clock.advance(23.hours)
            harness.repository.seasons()
            assertEquals("within the day the list is trusted", 1, harness.requests.size)

            harness.clock.advance(2.hours)
            harness.repository.seasons()
            assertEquals("past the day it is checked again", 2, harness.requests.size)
        }

    @Test
    fun aFailedSeasonFetchIsRetriedOnTheNextCall() =
        runBlocking {
            // The name of the neighbouring test says "at most once per day", and this is its limit:
            // a failure caches nothing, so the next call tries again. Pinning it because a reader who
            // takes the other name literally would expect a backoff that does not exist.
            var fail = true
            val harness =
                harness {
                    if (fail) throw IOException("offline") else null
                }

            val first = harness.repository.seasons()
            assertNull(first.data)

            fail = false
            val second = harness.repository.seasons()

            assertEquals(2, harness.requests.size)
            assertNotNull(second.data)
            assertEquals(Freshness.Fresh, second.freshness)
        }

    @Test
    fun seasonsAndStandingsDoNotShareACacheEntry() =
        runBlocking {
            val harness = harness()

            harness.repository.standings(anyGameLive = false)
            harness.repository.seasons()

            // Same day, both fresh, and the second must not be served the first's bytes.
            harness.clock.advance(30.minutes)
            harness.repository.standings(anyGameLive = false)
            harness.repository.seasons()

            assertEquals(2, harness.requests.size)
        }

    @Test
    fun aCachedBodyThatNoLongerParsesIsRefetched() =
        runBlocking {
            val harness = harness()
            val day = harness.repository.leagueDay(harness.clock.now())
            harness.cache.put(
                CacheKey(CacheKind.Standings, day),
                "{\"not\":\"a table\"}".toByteArray(),
                harness.clock.now(),
            )

            val result = harness.repository.standings(anyGameLive = false)

            assertEquals("an unparseable cache must not block a fetch", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, result.freshness)
            assertNotNull(result.data)
        }

    @Test
    fun aRateLimitIsAnAnswerRatherThanACrash() =
        runBlocking {
            // 429 with Retry-After: the boundary raises RateLimited on purpose. It used to escape the repository and
            // take the standings screen down with it instead of showing an error next to a cached table.
            val harness =
                harness(responder = { _ ->
                    respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "30"))
                })

            val loadable = harness.repository.standings(anyGameLive = false)

            assertEquals(LoadError.Http(status = 429, retryAfter = 30.seconds), loadable.error)
            assertNull(loadable.data)
        }

    @Test
    fun aRedirectToAnUnlistedHostIsBlockedNotThrown() =
        runBlocking {
            val harness =
                harness(responder = { _ ->
                    respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "https://evil.example/steal"))
                })

            val loadable = harness.repository.standings(anyGameLive = false)

            assertEquals(LoadError.Blocked("evil.example"), loadable.error)
            assertEquals("the engine must never be asked for the blocked host", 1, harness.requests.size)
        }

    @Test
    fun aCacheThatCannotBeWrittenStillReturnsTheTableAsLocal() =
        runBlocking {
            val harness = harness(files = { delegate -> StandingsWriteFailingFileSystem(delegate) })

            val loadable = harness.repository.standings(anyGameLive = false)

            assertNotNull("a cache failure must not hide a good response", loadable.data)
            assertEquals(LoadError.Local, loadable.error)
            assertEquals(Freshness.Fresh, loadable.freshness)
        }

    @Test
    fun aCacheThatCannotBeReadStillReachesTheNetwork() =
        runBlocking {
            // ResponseCache.get swallows IOException and answers null, so this pins the combined behaviour rather
            // than a catch inside the repository: an unreadable entry must behave like a missing one, not like an
            // error and not like a reason to skip the network.
            val harness = harness(files = { delegate -> StandingsReadFailingFileSystem(delegate) })

            val loadable = harness.repository.standings(anyGameLive = false)

            assertEquals("an unreadable cache must not stop the fetch", 1, harness.requests.size)
            assertNotNull(loadable.data)
            assertEquals(Freshness.Fresh, loadable.freshness)
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-10-26T02:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }

        fun set(iso: String) {
            instant = Instant.parse(iso)
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}

/** Delegates everything, but fails the atomic move a cache write ends with. */
private class StandingsWriteFailingFileSystem(
    delegate: FileSystem,
) : ForwardingFileSystem(delegate) {
    override fun atomicMove(
        source: Path,
        target: Path,
    ): Unit = throw IOException("disk full")
}

/** Delegates everything, but cannot hand back a previously written cache entry. */
private class StandingsReadFailingFileSystem(
    delegate: FileSystem,
) : ForwardingFileSystem(delegate) {
    override fun openReadOnly(file: Path): FileHandle = throw IOException("permission denied")

    override fun metadataOrNull(path: Path): okio.FileMetadata? = throw IOException("permission denied")
}
