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
import net.montycasa.roux.domain.model.NflConference
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The cache decision is the behaviour under test: what is requested, which key it lands under, and what the caller is
 * told when a request fails. Fixtures are the real captured ESPN payloads.
 */
class NflStandingsRepositoryImplTest {
    private val conferenceBytes = fixture("2026-11-18", "nfl-standings.json")
    private val afcDivisionBytes = fixture("2026-11-24", "nfl-standings-divisions-afc.json")

    private class Harness(
        val repository: NflStandingsRepositoryImpl,
        /** The `group` query of every request the engine received, in order; null for the conference level. */
        val requests: List<String?>,
        val cache: ResponseCache,
        val clock: MutableClock,
    )

    private fun fixture(
        capture: String,
        name: String,
    ): ByteArray {
        val path = "/fx-nfl/$capture/$name"
        return NflStandingsRepositoryImplTest::class.java
            .getResourceAsStream(path)
            ?.use { it.readBytes() }
            ?: error("Fixture resource not found: $path")
    }

    private fun harness(
        responder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null,
    ): Harness {
        val requests = mutableListOf<String?>()
        val engine =
            MockEngine { request ->
                requests += request.url.parameters["group"]
                responder?.invoke(this, request)
                    ?: respond(
                        conferenceBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        val clock = MutableClock()
        val cache = ResponseCache(FakeFileSystem(clock), DIRECTORY.toPath(), clock)
        val api = EspnApi(client = EspnHttpClientFactory.create(engine, "test-version"))
        return Harness(
            NflStandingsRepositoryImpl(api, cache, clock, Dispatchers.IO),
            requests,
            cache,
            clock,
        )
    }

    @Test
    fun aFreshTableAnswersWithoutARequest() =
        runBlocking {
            val harness = harness()
            harness.cache.put(CacheKey(CacheKind.NflStandings, "conferences"), conferenceBytes, harness.clock.now())

            val loadable = harness.repository.conferences(anyGameLive = false)

            assertEquals("a fresh table must not reach the network", emptyList<String?>(), harness.requests)
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertNull(loadable.error)
            assertEquals(listOf("American Football Conference", "National Football Conference"), loadable.data?.map { it.name })
        }

    @Test
    fun aTableOlderThanTheIdleLifetimeIsRefetched() =
        runBlocking {
            val harness = harness()
            harness.cache.put(CacheKey(CacheKind.NflStandings, "conferences"), conferenceBytes, harness.clock.now())
            harness.clock.advance(61.minutes)

            val loadable = harness.repository.conferences(anyGameLive = false)

            assertEquals("an hour-old table must be refetched", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, loadable.freshness)
        }

    @Test
    fun aLiveDayShortensHowLongATableIsTrusted() =
        runBlocking {
            // Twenty minutes old: inside the hour an idle table is trusted for, past the ten minutes a live one is.
            val harness = harness()
            harness.cache.put(CacheKey(CacheKind.NflStandings, "conferences"), conferenceBytes, harness.clock.now())
            harness.clock.advance(20.minutes)

            harness.repository.conferences(anyGameLive = false)
            assertEquals("an idle table is trusted for an hour", 0, harness.requests.size)

            harness.repository.conferences(anyGameLive = true)
            assertEquals("a table during live play is not trusted for twenty minutes", 1, harness.requests.size)
        }

    @Test
    fun aConferencesTablesAndADivisionsTablesNeverShareAnEntry() =
        runBlocking {
            val harness =
                harness { request ->
                    respond(
                        if (request.url.parameters["group"] == null) conferenceBytes else afcDivisionBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val divisions = harness.repository.divisions(NflConference.AFC, anyGameLive = false)
            val conferences = harness.repository.conferences(anyGameLive = false)

            assertEquals(listOf("8", null), harness.requests)
            assertEquals(listOf("AFC East", "AFC North", "AFC South", "AFC West"), divisions.data?.map { it.name })
            assertEquals(
                "the conference call must not be answered from the division entry",
                listOf("American Football Conference", "National Football Conference"),
                conferences.data?.map { it.name },
            )
        }

    @Test
    fun eachConferenceAsksForItsOwnGroup() =
        runBlocking {
            val harness = harness()

            harness.repository.divisions(NflConference.AFC, anyGameLive = false)
            harness.repository.divisions(NflConference.NFC, anyGameLive = false)

            // The ids are the feed's, and they are not the conference numbering they resemble.
            assertEquals(listOf("8", "7"), harness.requests)
        }

    @Test
    fun aFailedRequestFallsBackToTheCachedTable() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }
            harness.cache.put(CacheKey(CacheKind.NflStandings, "conferences"), conferenceBytes, harness.clock.now())
            harness.clock.advance(61.minutes)

            val loadable = harness.repository.conferences(anyGameLive = false)

            assertEquals(LoadError.Http(500), loadable.error)
            assertTrue("the cached table must still be served: ${loadable.freshness}", loadable.freshness is Freshness.Stale)
            assertEquals(2, loadable.data?.size)
        }

    @Test
    fun aFailureWithNothingCachedReportsTheErrorAndNoTables() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }

            val loadable = harness.repository.conferences(anyGameLive = false)

            assertEquals(LoadError.Http(500), loadable.error)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertTrue("no cached table means no rows to draw: ${loadable.data}", loadable.data.isNullOrEmpty())
        }

    @Test
    fun aCachedBodyThatNoLongerParsesIsTreatedAsAbsent() =
        runBlocking {
            val harness = harness()
            harness.cache.put(
                CacheKey(CacheKind.NflStandings, "conferences"),
                """{"children":"not a list"}""".toByteArray(),
                harness.clock.now(),
            )

            val loadable = harness.repository.conferences(anyGameLive = false)

            assertEquals("a corrupt copy must not be served as fresh", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertEquals(2, loadable.data?.size)
        }

    @Test
    fun aTableIsFiledUnderTheNflStandingsKind() =
        runBlocking {
            val harness = harness()

            harness.repository.conferences(anyGameLive = false)

            assertTrue(
                "the table must land in its own cache kind",
                harness.cache.get(CacheKey(CacheKind.NflStandings, "conferences")) != null,
            )
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-24T12:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
