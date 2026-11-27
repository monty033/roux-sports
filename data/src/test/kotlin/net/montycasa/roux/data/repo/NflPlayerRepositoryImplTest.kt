package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.EspnApi
import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.NflPlayerId
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What the football player loader asks for, where it files the answers, and how a blocked host is reported. */
class NflPlayerRepositoryImplTest {
    private val playerId = NflPlayerId("3046779")
    private val profileBytes = fixture("nfl-player-qb-goff.json")
    private val logBytes = fixture("nfl-gamelog-qb-goff.json")

    private class Harness(
        val repository: NflPlayerRepositoryImpl,
        /** Each request's path, in order. */
        val requests: List<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
    )

    private fun fixture(file: String): ByteArray =
        NflPlayerRepositoryImplTest::class.java
            .getResourceAsStream("/fx-nfl/$CAPTURE/$file")
            ?.use { it.readBytes() }
            ?: error("Fixture resource not found: $file")

    private fun harness(responder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                responder?.invoke(this, request)
                    ?: respond(
                        if (request.url.encodedPath.endsWith("/gamelog")) logBytes else profileBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        val clock = MutableClock()
        val cache = ResponseCache(FakeFileSystem(clock), DIRECTORY.toPath(), clock)
        val api = EspnApi(client = EspnHttpClientFactory.create(engine, "test-version"))
        return Harness(NflPlayerRepositoryImpl(api, cache, clock, Dispatchers.IO), requests, cache, clock)
    }

    /** Both endpoints are fetched once, then each is answered from its own entry on the second visit. */
    @Test
    fun theProfileAndLogAreEachFetchedOnceAndThenServedFromTheCache() =
        runBlocking {
            val harness = harness()

            val firstProfile = harness.repository.profile(playerId)
            val secondProfile = harness.repository.profile(playerId)
            val firstLog = harness.repository.gameLog(playerId)
            val secondLog = harness.repository.gameLog(playerId)

            assertEquals(
                listOf(
                    "/apis/common/v3/sports/football/nfl/athletes/3046779",
                    "/apis/common/v3/sports/football/nfl/athletes/3046779/gamelog",
                ),
                harness.requests,
            )
            assertEquals("Jared Goff", firstProfile.data?.name)
            assertEquals(Freshness.Fresh, secondProfile.freshness)
            assertEquals(16, firstLog.data?.columns?.size)
            assertEquals(Freshness.Fresh, secondLog.freshness)
            // The two answers are separate entries, not one entry rewritten by the other.
            assertTrue(harness.cache.get(CacheKey(CacheKind.NflPlayer, playerId.value)) != null)
            assertTrue(harness.cache.get(CacheKey(CacheKind.NflPlayerGameLog, playerId.value)) != null)
        }

    @Test
    fun aProfileOlderThanItsDayIsRefetched() =
        runBlocking {
            val harness = harness()

            harness.repository.profile(playerId)
            harness.clock.advance(25.hours)
            harness.repository.profile(playerId)

            assertEquals("past the day the feed is asked again", 2, harness.requests.size)
        }

    /**
     * A host outside the ESPN allowlist is refused before any request exists. That is a [LoadError.Blocked], not a
     * network failure: reporting it as offline would invite a retry loop that earns the same refusal every time.
     */
    @Test
    fun aBlockedHostIsReportedAsBlockedRatherThanOffline() =
        runBlocking {
            val requests = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    requests += request.url.encodedPath
                    respond(profileBytes, HttpStatusCode.OK)
                }
            val clock = MutableClock()
            val cache = ResponseCache(FakeFileSystem(clock), DIRECTORY.toPath(), clock)
            val api =
                EspnApi(
                    client = EspnHttpClientFactory.create(engine, "test-version"),
                    baseUrl = "https://site.web.api.espn.com",
                )
            val repository = NflPlayerRepositoryImpl(api, cache, clock, Dispatchers.IO)

            val loadable = repository.profile(playerId)

            assertEquals(LoadError.Blocked("site.web.api.espn.com"), loadable.error)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertNull(loadable.data)
            assertTrue("a refused host must build no request", requests.isEmpty())
        }

    @Test
    fun aFailedRequestFallsBackToTheCachedProfile() =
        runBlocking {
            var fail = false
            val harness =
                harness {
                    if (fail) {
                        respond("nope", HttpStatusCode.InternalServerError)
                    } else {
                        respond(profileBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            harness.repository.profile(playerId)
            harness.clock.advance(25.hours)
            fail = true

            val loadable = harness.repository.profile(playerId)

            assertEquals(LoadError.Http(500), loadable.error)
            assertTrue("the cached profile stands in: ${loadable.freshness}", loadable.freshness is Freshness.Stale)
            assertEquals("Jared Goff", loadable.data?.name)
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-26T12:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val CAPTURE = "2026-11-26"
        const val DIRECTORY = "/cache"
    }
}
