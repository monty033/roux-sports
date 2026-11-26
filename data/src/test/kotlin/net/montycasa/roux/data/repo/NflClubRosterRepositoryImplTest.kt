package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.TeamKey
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

/** What the roster loader asks for, where it files the answer, and what the caller is told when a request fails. */
class NflClubRosterRepositoryImplTest {
    private val buffaloBytes = fixture("buf")
    private val philadelphiaBytes = fixture("phi")

    private class Harness(
        val repository: NflClubRosterRepositoryImpl,
        /** The club path and query each request named, in order. */
        val requests: List<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
    )

    private fun fixture(club: String): ByteArray =
        NflClubRosterRepositoryImplTest::class.java
            .getResourceAsStream("/fx-nfl/2026-11-25/nfl-roster-$club.json")
            ?.use { it.readBytes() }
            ?: error("Fixture resource not found: nfl-roster-$club.json")

    private fun harness(
        responder: (
            suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
                io.ktor.client.request.HttpRequestData,
            ) -> io.ktor.client.request.HttpResponseData
        )? = null,
    ): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath + (
                    request.url.encodedQuery
                        .takeIf { it.isNotEmpty() }
                        ?.let { "?$it" }
                        .orEmpty()
                )
                responder?.invoke(this, request)
                    ?: respond(
                        buffaloBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        val clock = MutableClock()
        val cache = ResponseCache(FakeFileSystem(clock), DIRECTORY.toPath(), clock)
        val api = EspnApi(client = EspnHttpClientFactory.create(engine, "test-version"))
        return Harness(NflClubRosterRepositoryImpl(api, cache, clock, Dispatchers.IO), requests, cache, clock)
    }

    private val bills = TeamKey(League.NFL, "BUF")

    @Test
    fun aRosterIsFetchedOnceAndThenServedFromTheCache() =
        runBlocking {
            val harness = harness()

            val first = harness.repository.roster(bills)
            val second = harness.repository.roster(bills)

            assertEquals("the second read must not reach the network", 1, harness.requests.size)
            assertEquals("/apis/site/v2/sports/football/nfl/teams/buf?enable=roster", harness.requests.single())
            assertEquals(72, first.data?.players?.size)
            assertEquals(Freshness.Fresh, second.freshness)
        }

    @Test
    fun aRosterOlderThanTheSixHourWindowIsRefetched() =
        runBlocking {
            val harness = harness()

            harness.repository.roster(bills)
            harness.clock.advance(7.hours)
            harness.repository.roster(bills)

            assertEquals("past the six-hour window the feed is asked again", 2, harness.requests.size)
        }

    @Test
    fun eachClubIsFiledUnderItsOwnEntry() =
        runBlocking {
            val harness =
                harness { request ->
                    respond(
                        if ("phi" in request.url.encodedPath) philadelphiaBytes else buffaloBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            harness.repository.roster(bills)
            val philadelphia = harness.repository.roster(TeamKey(League.NFL, "PHI"))

            assertEquals(
                listOf(
                    "/apis/site/v2/sports/football/nfl/teams/buf?enable=roster",
                    "/apis/site/v2/sports/football/nfl/teams/phi?enable=roster",
                ),
                harness.requests,
            )
            assertEquals(
                "PHI",
                philadelphia.data
                    ?.team
                    ?.key
                    ?.abbrev,
            )
            assertTrue(harness.cache.get(CacheKey(CacheKind.NflRoster, "BUF")) != null)
            assertTrue(harness.cache.get(CacheKey(CacheKind.NflRoster, "PHI")) != null)
        }

    @Test
    fun aFailedRequestFallsBackToTheCachedRoster() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }
            harness.cache.put(CacheKey(CacheKind.NflRoster, "BUF"), buffaloBytes, harness.clock.now())
            harness.clock.advance(7.hours)

            val loadable = harness.repository.roster(bills)

            assertEquals(LoadError.Http(500), loadable.error)
            assertTrue("the cached roster stands in: ${loadable.freshness}", loadable.freshness is Freshness.Stale)
            assertEquals(72, loadable.data?.players?.size)
        }

    @Test
    fun aFailureWithNothingCachedReportsTheErrorAndNoRoster() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }

            val loadable = harness.repository.roster(bills)

            assertEquals(LoadError.Http(500), loadable.error)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertNull(loadable.data)
        }

    @Test
    fun aCachedBodyThatNoLongerParsesIsTreatedAsAbsent() =
        runBlocking {
            val harness = harness()
            harness.cache.put(
                CacheKey(CacheKind.NflRoster, "BUF"),
                """{"team":{"athletes":"not a list"}}""".toByteArray(),
                harness.clock.now(),
            )

            val loadable = harness.repository.roster(bills)

            assertEquals("a corrupt copy must not be served", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertEquals(72, loadable.data?.players?.size)
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-25T12:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
