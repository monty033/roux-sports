package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.data.nhl.NhlAdapter
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.TeamKey
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ClubScheduleRepositoryImplTest {
    private val payload = Fixtures.clubSchedule("club-schedule-ana-20262027.json").toByteArray()

    private class Harness(
        val repository: ClubScheduleRepositoryImpl,
        val requests: List<String>,
        val clock: MutableClock,
    )

    @Test
    fun theRequestUsesTheClubAndTheSeasonDerivedFromTheClock() =
        runBlocking {
            val harness = harness()
            harness.clock.set("2026-06-30T12:00:00Z")

            harness.repository.schedule(TeamKey("ANA"))

            assertEquals(listOf("/v1/club-schedule-season/ANA/20252026"), harness.requests)
        }

    @Test
    fun aSecondReadInsideSixHoursDoesNotCallTheApi() =
        runBlocking {
            val harness = harness()
            harness.repository.schedule(TeamKey("ANA"))

            harness.clock.advance(5.hours)
            harness.repository.schedule(TeamKey("ANA"))

            assertEquals(1, harness.requests.size)
        }

    @Test
    fun aReadPastSixHoursRefetches() =
        runBlocking {
            val harness = harness()
            harness.repository.schedule(TeamKey("ANA"))

            harness.clock.advance(6.hours + 1.minutes)
            harness.repository.schedule(TeamKey("ANA"))

            assertEquals(2, harness.requests.size)
        }

    @Test
    fun aCachedScheduleIsReturnedAsStaleWhenTheNetworkFails() =
        runBlocking {
            var fail = false
            val harness =
                harness {
                    if (fail) throw IOException("offline") else null
                }
            val first = harness.repository.schedule(TeamKey("ANA"))

            fail = true
            harness.clock.advance(7.hours)
            val second = harness.repository.schedule(TeamKey("ANA"))

            assertEquals(first.data, second.data)
            assertTrue(second.freshness is Freshness.Stale)
            assertEquals(LoadError.Network, second.error)
        }

    private fun harness(
        clock: MutableClock = MutableClock(),
        responder: (
            suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
                io.ktor.client.request.HttpRequestData,
            ) -> io.ktor.client.request.HttpResponseData?
        )? = null,
    ): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                responder?.invoke(this, request)
                    ?: respond(
                        payload,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        val cache = ResponseCache(FakeFileSystem(clock), DIRECTORY.toPath(), clock)
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(
            ClubScheduleRepositoryImpl(api, NhlAdapter, cache, clock, TimeZone.of("America/Toronto")),
            requests,
            clock,
        )
    }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-03T12:00:00Z")

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
