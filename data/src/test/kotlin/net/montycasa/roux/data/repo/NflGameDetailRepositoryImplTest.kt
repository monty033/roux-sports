package net.montycasa.roux.data.repo

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
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.EspnApi
import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflGameStatus
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The football game page's cache contract: one event, one summary, one entry.
 *
 * Requests are counted rather than timed, because whether the cache answered is the behaviour under test.
 */
class NflGameDetailRepositoryImplTest {
    private val gameId = GameId(League.NFL, "401872923")
    private val directories = mutableListOf<java.nio.file.Path>()

    @org.junit.After
    fun cleanUp() {
        directories.forEach { runCatching { it.toFile().deleteRecursively() } }
    }

    private val fixture = Fixtures.nflGame("nfl-game-401872923.json").encodeToByteArray()

    private class Harness(
        val repository: NflGameDetailRepositoryImpl,
        val requests: CopyOnWriteArrayList<String>,
        val clock: MutableClock,
    )

    private fun harness(respond: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null): Harness {
        val requests = CopyOnWriteArrayList<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.parameters["event"].orEmpty()
                respond?.invoke(this, request)
                    ?: respond(fixture, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val clock = MutableClock()
        // A real directory: the cache writes on a dispatcher, and the fake filesystem is not concurrency-safe.
        val directory =
            java.nio.file.Files
                .createTempDirectory("hermes-nfl-game-")
        directories.add(directory)
        val cache = ResponseCache(FileSystem.SYSTEM, directory.toOkioPath(), clock)
        val api = EspnApi(EspnHttpClientFactory.create(engine, "test-version"))
        return Harness(NflGameDetailRepositoryImpl(api, cache, clock), requests, clock)
    }

    @Test
    fun aFreshCopyAnswersWithoutARequest() =
        runBlocking {
            val harness = harness()
            val first = harness.repository.load(gameId, GameState.Final)
            assertEquals(1, harness.requests.size)
            assertTrue(first.data != null)

            val second = harness.repository.load(gameId, GameState.Final)

            assertEquals("a fresh final copy must not ask the feed again", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, second.freshness)
            assertEquals(NflGameStatus.Final("Final/OT"), second.data?.status)
        }

    @Test
    fun aStaleCopyIsTheFallbackWhenTheRefreshFails() =
        runBlocking {
            var fail = false
            val harness =
                harness {
                    if (fail) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(fixture, HttpStatusCode.OK)
                    }
                }
            harness.repository.load(gameId, GameState.Final)
            harness.clock.advance(25.hours)
            fail = true

            val refreshed = harness.repository.load(gameId, GameState.Final)

            assertEquals(2, harness.requests.size)
            assertEquals(LoadError.Http(503), refreshed.error)
            assertTrue("the copy is stale, never current", refreshed.freshness is Freshness.Stale)
            assertNotNull("the last readable game stays on screen", refreshed.data)
            assertEquals(31, refreshed.data?.home?.score)
        }

    @Test
    fun aNetworkFailureWithNothingCachedIsMissing() =
        runBlocking {
            val harness = harness { throw IOException("offline") }

            val result = harness.repository.load(gameId, GameState.Final)

            assertNull(result.data)
            assertEquals(Freshness.Missing, result.freshness)
            assertEquals(LoadError.Network, result.error)
        }

    @Test
    fun eachEventGetsItsOwnCacheEntry() =
        runBlocking {
            val otherId = GameId(League.NFL, "401873066")
            val bodies =
                mapOf(
                    "401872923" to fixture,
                    "401873066" to Fixtures.nflGame("nfl-game-401873066.json").encodeToByteArray(),
                )
            val harness =
                harness { request ->
                    val event = request.url.parameters["event"].orEmpty()
                    respond(bodies.getValue(event), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }

            harness.repository.load(gameId, GameState.Final)
            harness.repository.load(otherId, GameState.Final)
            harness.repository.load(gameId, GameState.Final)

            assertEquals("two events were requested and the third read was cached", 2, harness.requests.size)
            assertEquals(listOf("401872923", "401873066"), harness.requests.toList())
        }

    @Test
    fun refreshIgnoresAFreshCopy() =
        runBlocking {
            val harness = harness()
            harness.repository.load(gameId, GameState.Final)

            harness.repository.refresh(gameId, GameState.Final)

            assertEquals("pull-to-refresh must reach the feed", 2, harness.requests.size)
        }

    @Test
    fun aScheduledBodyIsNotServedOnceTheGameIsFinal() =
        runBlocking {
            val harness = harness()
            harness.repository.load(gameId, GameState.Scheduled)
            harness.repository.load(gameId, GameState.Final)

            assertEquals("the state boundary wins over a fresh clock", 2, harness.requests.size)
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-25T20:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }
}
