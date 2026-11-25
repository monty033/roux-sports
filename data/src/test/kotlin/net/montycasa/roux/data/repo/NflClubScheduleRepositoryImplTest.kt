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

/** What the season loader asks for, where it files the answer, and what the caller is told when a request fails. */
class NflClubScheduleRepositoryImplTest {
    private val buffaloBytes = fixture("buf")
    private val fortyNinersBytes = fixture("sf")

    private class Harness(
        val repository: NflClubScheduleRepositoryImpl,
        /** The club path each request named, in order. */
        val requests: List<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
    )

    private fun fixture(club: String): ByteArray =
        NflClubScheduleRepositoryImplTest::class.java
            .getResourceAsStream("/fx-nfl/2026-11-25/nfl-schedule-$club.json")
            ?.use { it.readBytes() }
            ?: error("Fixture resource not found: nfl-schedule-$club.json")

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
                requests +=
                    request.url.encodedPath
                        .substringAfter("/teams/")
                        .substringBefore("/schedule")
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
        return Harness(NflClubScheduleRepositoryImpl(api, cache, clock, Dispatchers.IO), requests, cache, clock)
    }

    private val bills = TeamKey(League.NFL, "BUF")

    @Test
    fun aSeasonIsFetchedOnceAndThenServedFromTheCache() =
        runBlocking {
            val harness = harness()

            val first = harness.repository.schedule(bills, anyGameLive = false)
            val second = harness.repository.schedule(bills, anyGameLive = false)

            assertEquals("the second read must not reach the network", 1, harness.requests.size)
            assertEquals("buf", harness.requests.single())
            assertEquals(17, first.data?.entries?.count { it is net.montycasa.roux.domain.model.NflSeasonEntry.Game })
            assertEquals(Freshness.Fresh, second.freshness)
        }

    @Test
    fun aSeasonOlderThanTheScheduleWindowIsRefetched() =
        runBlocking {
            val harness = harness()

            harness.repository.schedule(bills, anyGameLive = false)
            harness.clock.advance(7.hours)
            harness.repository.schedule(bills, anyGameLive = false)

            assertEquals("past the six-hour window the feed is asked again", 2, harness.requests.size)
        }

    @Test
    fun eachClubIsFiledUnderItsOwnEntry() =
        runBlocking {
            val harness =
                harness { request ->
                    respond(
                        if ("sf" in request.url.encodedPath) fortyNinersBytes else buffaloBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            harness.repository.schedule(bills, anyGameLive = false)
            val sf = harness.repository.schedule(TeamKey(League.NFL, "SF"), anyGameLive = false)

            assertEquals(listOf("buf", "sf"), harness.requests)
            assertEquals(
                "SF",
                sf.data
                    ?.team
                    ?.key
                    ?.abbrev,
            )
            assertTrue(harness.cache.get(CacheKey(CacheKind.NflSchedule, "BUF")) != null)
            assertTrue(harness.cache.get(CacheKey(CacheKind.NflSchedule, "SF")) != null)
        }

    @Test
    fun aFailedRequestFallsBackToTheCachedSeason() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }
            harness.cache.put(CacheKey(CacheKind.NflSchedule, "BUF"), buffaloBytes, harness.clock.now())
            harness.clock.advance(7.hours)

            val loadable = harness.repository.schedule(bills, anyGameLive = false)

            assertEquals(LoadError.Http(500), loadable.error)
            assertTrue("the cached season stands in: ${loadable.freshness}", loadable.freshness is Freshness.Stale)
            assertEquals(17, loadable.data?.entries?.count { it is net.montycasa.roux.domain.model.NflSeasonEntry.Game })
        }

    @Test
    fun aFailureWithNothingCachedReportsTheErrorAndNoSeason() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }

            val loadable = harness.repository.schedule(bills, anyGameLive = false)

            assertEquals(LoadError.Http(500), loadable.error)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertNull(loadable.data)
        }

    @Test
    fun aCachedBodyThatNoLongerParsesIsTreatedAsAbsent() =
        runBlocking {
            val harness = harness()
            harness.cache.put(
                CacheKey(CacheKind.NflSchedule, "BUF"),
                """{"events":"not a list"}""".toByteArray(),
                harness.clock.now(),
            )

            val loadable = harness.repository.schedule(bills, anyGameLive = false)

            assertEquals("a corrupt copy must not be served", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertEquals(17, loadable.data?.entries?.count { it is net.montycasa.roux.domain.model.NflSeasonEntry.Game })
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
