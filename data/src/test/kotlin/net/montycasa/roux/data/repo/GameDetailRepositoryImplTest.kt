package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What the page does about requests: which of the three endpoints it asks, what it keeps, and what each endpoint's
 * failure turns into when the other two are fine.
 *
 * The cache decision is the behaviour, so these tests count requests rather than assert on timing.
 */
class GameDetailRepositoryImplTest {
    private val gameId = GameId("2026020188")
    private val directories = mutableListOf<java.nio.file.Path>()

    @org.junit.After
    fun cleanUp() {
        directories.forEach { runCatching { it.toFile().deleteRecursively() } }
    }

    private val endpointBodies =
        mapOf(
            "landing" to Fixtures.bytes("gc-final-landing.json"),
            "boxscore" to Fixtures.bytes("gc-final-boxscore.json"),
            "right-rail" to Fixtures.bytes("gc-final-right-rail.json"),
        )

    private class Harness(
        val repository: GameDetailRepositoryImpl,
        /** Three endpoints are requested in parallel, so the collector has to tolerate concurrent appends. */
        val requests: CopyOnWriteArrayList<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
    ) {
        fun requestsFor(endpoint: String): Int = requests.count { it.endsWith("/$endpoint") }
    }

    private fun harness(respond: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null): Harness {
        val requests = CopyOnWriteArrayList<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                respond?.invoke(this, request)
                    ?: run {
                        val endpoint = request.url.encodedPath.substringAfterLast('/')
                        respond(
                            endpointBodies.getValue(endpoint),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
            }
        val clock = MutableClock()
        // A real directory rather than okio's fake filesystem: the three requests run in parallel, and FakeFileSystem
        // keeps its entries in plain collections it does not guard. It corrupted itself under concurrent cache writes,
        // which is a property of the double and not of the code under test.
        val directory =
            java.nio.file.Files
                .createTempDirectory("hermes-g8-")
        directories.add(directory)
        val cache = ResponseCache(FileSystem.SYSTEM, directory.toOkioPath(), clock)
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(GameDetailRepositoryImpl(api, cache, clock), requests, cache, clock)
    }

    /**
     * A final game does not change, so the second visit makes no request at all - not even one to prove the copy is
     * current. This is the whole reason the cache is keyed per endpoint.
     */
    @Test
    fun aFinalGameIsNeverRefetched() =
        runBlocking {
            val harness = harness()
            val first = harness.repository.load(gameId, GameState.Final)
            assertEquals(3, harness.requests.size)
            assertEquals(Freshness.Fresh, first.landing.freshness)

            harness.clock.advance(720.hours)
            val second = harness.repository.load(gameId, GameState.Final)

            assertEquals("a final page must not be requested twice", 3, harness.requests.size)
            assertEquals(Freshness.Fresh, second.landing.freshness)
            assertNotNull(second.boxscore.data)
        }

    @Test
    fun aLiveGameIsReusedInsideTheHalfMinuteAndRefetchedAfterIt() =
        runBlocking {
            val harness = harness()
            harness.repository.load(gameId, GameState.Live())
            assertEquals(3, harness.requests.size)

            harness.clock.advance(20.seconds)
            harness.repository.load(gameId, GameState.Live())
            assertEquals("inside the lifetime the copy is still good", 3, harness.requests.size)

            harness.clock.advance(11.seconds)
            harness.repository.load(gameId, GameState.Live())
            assertEquals("past 30 s the page must re-read", 6, harness.requests.size)
        }

    /** A scheduled game is neither: it has not started, so it cannot be final, but it changes at puck drop. */
    @Test
    fun aScheduledGameExpiresTooButMoreSlowly() =
        runBlocking {
            val harness = harness()
            harness.repository.load(gameId, GameState.Scheduled)

            harness.clock.advance(4.minutes)
            harness.repository.load(gameId, GameState.Scheduled)
            assertEquals(3, harness.requests.size)

            harness.clock.advance(2.minutes)
            harness.repository.load(gameId, GameState.Scheduled)
            assertEquals(6, harness.requests.size)
        }

    @Test
    fun refreshIgnoresAFreshCopy() =
        runBlocking {
            val harness = harness()
            harness.repository.load(gameId, GameState.Final)

            harness.repository.refresh(gameId, GameState.Final)

            assertEquals("a pull-to-refresh must reach the network", 6, harness.requests.size)
        }

    /**
     * The failure this exists for: one endpoint timing out must not blank the page. The goals are still readable, so the
     * banner belongs on the section that failed and nowhere else.
     */
    @Test
    fun aFailingBoxscoreLeavesTheOtherTwoFresh() =
        runBlocking {
            val harness =
                harness { request ->
                    if (request.url.encodedPath.endsWith("/boxscore")) {
                        respond("", HttpStatusCode.InternalServerError)
                    } else {
                        val endpoint = request.url.encodedPath.substringAfterLast('/')
                        respond(endpointBodies.getValue(endpoint), HttpStatusCode.OK)
                    }
                }

            val detail = harness.repository.load(gameId, GameState.Final)

            assertEquals(Freshness.Fresh, detail.landing.freshness)
            assertEquals(LoadError.Http(500), detail.boxscore.error)
            assertEquals(Freshness.Missing, detail.boxscore.freshness)
            assertNull(detail.boxscore.data)
            assertEquals(Freshness.Fresh, detail.rightRail.freshness)
            assertTrue(
                "the goals should still be there",
                detail.landing.data
                    ?.periodScoring
                    ?.isNotEmpty() == true,
            )
        }

    /** A rate limit is an answer with a pause attached, not a crash. */
    @Test
    fun aRateLimitIsReportedWithItsPauseAndDoesNotThrow() =
        runBlocking {
            val harness =
                harness {
                    respond(
                        "",
                        HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.RetryAfter, "120"),
                    )
                }

            val detail = harness.repository.load(gameId, GameState.Live())

            val error = detail.landing.error
            assertTrue("expected Http(429), got $error", error is LoadError.Http && error.status == 429)
            assertEquals(120.seconds, (error as LoadError.Http).retryAfter)
            assertEquals(Freshness.Missing, detail.landing.freshness)
        }

    /** A failed refresh keeps the copy on screen and marks it old - never fresh. */
    @Test
    fun aFailedRefreshKeepsTheCachedCopyAndCallsItStale() =
        runBlocking {
            var fail = false
            val harness =
                harness { request ->
                    if (fail) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        val endpoint = request.url.encodedPath.substringAfterLast('/')
                        respond(endpointBodies.getValue(endpoint), HttpStatusCode.OK)
                    }
                }
            harness.repository.load(gameId, GameState.Final)
            fail = true
            harness.clock.advance(1.seconds)

            val refreshed = harness.repository.refresh(gameId, GameState.Final)

            assertEquals(LoadError.Http(503), refreshed.landing.error)
            assertTrue("stale, not fresh", refreshed.landing.freshness is Freshness.Stale)
            assertNotNull(refreshed.landing.data)
        }

    /**
     * A cached copy plus a body that no longer parses: the copy stays, marked stale. The alternative - reporting a
     * parse failure as if nothing had been stored - would blank a page that was readable a moment ago.
     */
    @Test
    fun aCachedCopySurvivesAnUnparseableResponse() =
        runBlocking {
            var garbage = false
            val harness =
                harness { request ->
                    if (garbage) {
                        respond("<<<not json>>>", HttpStatusCode.OK)
                    } else {
                        val endpoint = request.url.encodedPath.substringAfterLast('/')
                        respond(endpointBodies.getValue(endpoint), HttpStatusCode.OK)
                    }
                }
            harness.repository.load(gameId, GameState.Final)
            garbage = true

            val refreshed = harness.repository.refresh(gameId, GameState.Final)

            assertEquals(LoadError.Parse, refreshed.landing.error)
            assertTrue("stale, not fresh", refreshed.landing.freshness is Freshness.Stale)
            assertNotNull(refreshed.landing.data)
        }

    /**
     * The three endpoints are requested together, not one after another.
     *
     * Proven by rendezvous rather than by timing: each response is held until its request has been joined by the other
     * two. Sequential code would never reach three in flight and the barrier would time out.
     */
    @Test
    fun theThreeRequestsAreInFlightTogether() =
        runBlocking {
            val inFlight = AtomicInteger()
            val peak = AtomicInteger()
            val harness =
                harness {
                    val now = inFlight.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    delay(200)
                    inFlight.decrementAndGet()
                    val endpoint = it.url.encodedPath.substringAfterLast('/')
                    respond(endpointBodies.getValue(endpoint), HttpStatusCode.OK)
                }

            harness.repository.load(gameId, GameState.Final)

            assertEquals("all three endpoints must be in flight at once", 3, peak.get())
        }

    /** Two endpoints failing differently must not be reported as one page-level failure. */
    @Test
    fun eachEndpointCarriesItsOwnVerdict() =
        runBlocking {
            val harness =
                harness { request ->
                    when {
                        request.url.encodedPath.endsWith("/landing") -> {
                            respond(endpointBodies.getValue("landing"), HttpStatusCode.OK)
                        }

                        request.url.encodedPath.endsWith("/boxscore") -> {
                            respond("not json at all", HttpStatusCode.OK)
                        }

                        else -> {
                            respond("", HttpStatusCode.BadGateway)
                        }
                    }
                }

            val detail = harness.repository.load(gameId, GameState.Live())

            assertEquals(Freshness.Fresh, detail.landing.freshness)
            assertEquals(LoadError.Parse, detail.boxscore.error)
            assertEquals(LoadError.Http(502), detail.rightRail.error)
        }

    /**
     * Bytes the server sent are what get cached, so an adapter change never invalidates the store. Asserted by pulling
     * the entry back and comparing it with the fixture.
     */
    @Test
    fun theCachedBytesAreTheServersBytes() =
        runBlocking {
            val harness = harness()
            harness.repository.load(gameId, GameState.Final)

            val entry = harness.cache.get(CacheKey(CacheKind.DetailLanding, gameId.value))

            assertNotNull(entry)
            assertEquals(
                Fixtures.bytes("gc-final-landing.json").size,
                entry!!.bytes.size,
            )
            assertTrue(entry.bytes.contentEquals(Fixtures.bytes("gc-final-landing.json")))
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-10-28T23:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private val Int.minutes: Duration get() = (this * 60).seconds
}
