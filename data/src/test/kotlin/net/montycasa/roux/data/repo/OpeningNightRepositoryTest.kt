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
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.data.nhl.NhlAdapter
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The opening-night walk is deliberately quiet: every failure is "not known yet", not an error the
 * Scores screen can show. The fixtures are the real three blocks captured on 6 November 2026.
 */
class OpeningNightRepositoryTest {
    private val capture = "2026-11-06"

    @Test
    fun theWalkStopsAtTheTwoHopRegularSeasonDay() =
        runBlocking {
            val harness = fixtureHarness()

            assertEquals("2026-09-29", harness.repository.openingNight("2026-09-19"))
            assertEquals(
                listOf("/v1/schedule/2026-09-19", "/v1/schedule/2026-09-26"),
                harness.requests,
            )
        }

    @Test
    fun aFreshAnswerIsServedFromTheSeasonCacheWithoutAnotherWalk() =
        runBlocking {
            val harness = fixtureHarness()
            harness.repository.openingNight("2026-09-19")
            val firstWalk = harness.requests.size

            // A different start date in the same season still uses the same season entry.
            harness.clock.advance(6.days)
            assertEquals("2026-09-29", harness.repository.openingNight("2026-09-20"))
            assertEquals(firstWalk, harness.requests.size)
        }

    @Test
    fun theAnswerExpiresAfterSevenDaysAndIsWalkedAgain() =
        runBlocking {
            val harness = fixtureHarness()
            harness.repository.openingNight("2026-09-19")
            val firstWalk = harness.requests.size

            harness.clock.advance(7.days + 1.minutes)
            assertEquals("2026-09-29", harness.repository.openingNight("2026-09-19"))
            assertEquals(firstWalk * 2, harness.requests.size)
        }

    @Test
    fun sixPreseasonBlocksExhaustTheCapAndReturnNull() =
        runBlocking {
            val dates =
                listOf(
                    "2026-08-01",
                    "2026-08-08",
                    "2026-08-15",
                    "2026-08-22",
                    "2026-08-29",
                    "2026-09-05",
                    "2026-09-12",
                )
            var index = 0
            val harness =
                harness { _ ->
                    val day = dates[index]
                    val next = dates[index + 1]
                    index += 1
                    respondJson(
                        """{"nextStartDate":"$next","gameWeek":[{"date":"$day","games":[{"gameType":1}]}]}""",
                    )
                }

            assertNull(harness.repository.openingNight("2026-08-01"))
            assertEquals(6, harness.requests.size)
        }

    @Test
    fun notFoundIsNullRatherThanAnException() =
        runBlocking {
            val harness = harness { respond("", HttpStatusCode.NotFound) }

            assertNull(harness.repository.openingNight("2026-09-19"))
        }

    @Test
    fun aNetworkFailureIsNullRatherThanAnException() =
        runBlocking {
            val harness = harness { throw IOException("offline") }

            assertNull(harness.repository.openingNight("2026-09-19"))
        }

    @Test
    fun anUnreadableBodyIsNullRatherThanAnException() =
        runBlocking {
            val harness = harness { respondJson("{not json") }

            assertNull(harness.repository.openingNight("2026-09-19"))
        }

    @Test
    fun aFailedWalkIsNotCachedAndALaterAttemptCanSucceed() =
        runBlocking {
            var fail = true
            val harness =
                harness { request ->
                    if (fail) {
                        respond("", HttpStatusCode.NotFound)
                    } else {
                        fixtureResponder(request)
                    }
                }

            assertNull(harness.repository.openingNight("2026-09-19"))
            fail = false
            assertEquals("2026-09-29", harness.repository.openingNight("2026-09-19"))
        }

    private fun fixtureHarness(): Harness = harness { request -> fixtureResponder(request) }

    private suspend fun MockRequestHandleScope.fixtureResponder(request: HttpRequestData): HttpResponseData {
        val name =
            when (request.url.encodedPath) {
                "/v1/schedule/2026-09-19" -> "schedule-2026-09-19.json"
                "/v1/schedule/2026-09-26" -> "schedule-2026-09-26.json"
                "/v1/schedule/2026-09-29" -> "schedule-2026-09-29.json"
                else -> error("unexpected schedule path: ${request.url.encodedPath}")
            }
        return respondJson(Fixtures.text(capture, name))
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData =
        respond(
            body,
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private class Harness(
        val repository: ScoresRepositoryImpl,
        val requests: MutableList<String>,
        val clock: MutableClock,
    )

    private fun harness(responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                responder(this, request) ?: error("no response for ${request.url.encodedPath}")
            }
        val clock = MutableClock()
        val cache = ResponseCache(FakeFileSystem(clock), DIRECTORY.toPath(), clock)
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(
            ScoresRepositoryImpl(api, NhlAdapter, cache, clock, Dispatchers.IO),
            requests,
            clock,
        )
    }

    private class MutableClock(
        private var instant: Instant = Instant.parse("2026-11-06T12:00:00Z"),
    ) : Clock {
        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
