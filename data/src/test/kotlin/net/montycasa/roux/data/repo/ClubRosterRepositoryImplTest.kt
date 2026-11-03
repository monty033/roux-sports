package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.domain.model.TeamKey
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ClubRosterRepositoryImplTest {
    private val payload = Fixtures.roster("roster-ana-current.json").toByteArray()

    private class Harness(
        val repository: ClubRosterRepositoryImpl,
        val requests: List<String>,
        val clock: MutableClock,
    )

    @Test
    fun theRequestUsesTheClubAndTheCurrentRosterPath() =
        runBlocking {
            val harness = harness()

            harness.repository.roster(TeamKey("ANA"))

            assertEquals(listOf("/v1/roster/ANA/current"), harness.requests)
        }

    @Test
    fun aSecondReadInsideSixHoursDoesNotCallTheApi() =
        runBlocking {
            val harness = harness()
            harness.repository.roster(TeamKey("ANA"))

            harness.clock.advance(5.hours)
            harness.repository.roster(TeamKey("ANA"))

            assertEquals(1, harness.requests.size)
        }

    @Test
    fun aReadPastSixHoursRefetches() =
        runBlocking {
            val harness = harness()
            harness.repository.roster(TeamKey("ANA"))

            harness.clock.advance(6.hours + 1.minutes)
            harness.repository.roster(TeamKey("ANA"))

            assertEquals(2, harness.requests.size)
        }

    @Test
    fun theTemporaryRedirectIsFollowedToTheRosterBody() =
        runBlocking {
            val harness =
                harness { request ->
                    if (request.url.encodedPath.endsWith("/current")) {
                        respond(
                            "",
                            HttpStatusCode.TemporaryRedirect,
                            headersOf(HttpHeaders.Location, "/v1/roster/ANA/2026-11-03"),
                        )
                    } else {
                        null
                    }
                }

            val result = harness.repository.roster(TeamKey("ANA"))

            assertEquals(
                listOf("/v1/roster/ANA/current", "/v1/roster/ANA/2026-11-03"),
                harness.requests,
            )
            assertEquals(16, result.data?.forwards?.size)
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
            ClubRosterRepositoryImpl(api, cache, clock),
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
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
