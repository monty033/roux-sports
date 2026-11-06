package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.domain.model.GameId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class NhlApiTest {
    @Test
    fun standingsUsesExactDatedPath() = assertPath("/v1/standings/2026-10-25") { standings("2026-10-25") }

    @Test
    fun scoreUsesExactDatedPath() = assertPath("/v1/score/2026-10-25") { score("2026-10-25") }

    @Test
    fun scheduleUsesExactDatedPath() = assertPath("/v1/schedule/2026-09-19") { schedule("2026-09-19") }

    @Test
    fun seasonsUsesExactPath() = assertPath("/v1/standings-season") { seasons() }

    @Test
    fun rosterUsesExactCurrentPath() = assertPath("/v1/roster/ANA/current") { roster("ANA") }

    @Test
    fun clubStatsUsesTheSeasonAndRegularSeasonGameType() = assertPath("/v1/club-stats/ANA/20262027/2") { clubStats("ANA", "20262027") }

    @Test
    fun gamecenterLandingUsesExactPath() = assertPath("/v1/gamecenter/2026020123/landing") { gamecenterLanding(GameId("2026020123")) }

    @Test
    fun gamecenterBoxscoreUsesExactPath() = assertPath("/v1/gamecenter/2026020123/boxscore") { gamecenterBoxscore(GameId("2026020123")) }

    @Test
    fun gamecenterRightRailUsesExactPath() =
        assertPath("/v1/gamecenter/2026020123/right-rail") { gamecenterRightRail(GameId("2026020123")) }

    /**
     * A traversal attempt must die at the URL builder, having built nothing. Checking the exception
     * alone would pass even if a request had already gone out and been discarded.
     */
    @Test
    fun traversalGameIdIsRejectedBeforeAnyRequest() =
        withResponse { api, engine ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.gamecenterLanding(GameId("../x")) }
            }
            assertTrue("No request may be built for a rejected id", engine.requestHistory.isEmpty())
        }

    @Test
    fun everyGamecenterSectionRejectsTheSameBadIds() =
        withResponse { api, engine ->
            for (
            bad in
            listOf(
                "../x",
                "",
                "202602012",
                "20260201234",
                "20260201a3",
                " 2026020123",
                "2026020123 ",
                "2026020123\n",
                "+2026020123",
                "-2026020123",
                "2026-020123",
                // Fullwidth digits: rejected today. This case is what would notice the pattern being swapped for
                // a unicode-aware class, which is exactly how "ten digits" stops meaning ten digits.
                "\uFF12\uFF10\uFF12\uFF16\uFF10\uFF12\uFF10\uFF11\uFF12\uFF13",
            )
            ) {
                for (call in listOf<suspend NhlApi.() -> Unit>(
                    { gamecenterLanding(GameId(bad)) },
                    { gamecenterBoxscore(GameId(bad)) },
                    { gamecenterRightRail(GameId(bad)) },
                )) {
                    assertThrows("'$bad' must be rejected", IllegalArgumentException::class.java) {
                        runBlocking { api.call() }
                    }
                }
            }
            assertTrue("A rejected id must not reach the network", engine.requestHistory.isEmpty())
        }

    /**
     * The value type stays opaque so an adapter can represent a garbage id from the feed and let the
     * caller decide; the refusal belongs to the URL, which this pins.
     */
    @Test
    fun gameIdItselfDoesNotValidate() {
        GameId("../x")
        GameId("")
    }

    @Test
    fun everyPublicMethodAvoidsNowPath() =
        withResponse { api, engine ->
            api.score("2026-10-25")
            api.schedule("2026-09-19")
            api.standings("2026-10-25")
            api.seasons()
            api.roster("ANA")
            api.gamecenterLanding(GameId("2026020123"))
            api.gamecenterBoxscore(GameId("2026020123"))
            api.gamecenterRightRail(GameId("2026020123"))

            val paths = engine.requestHistory.map { it.url.encodedPath }
            assertEquals(8, paths.size)
            assertTrue(paths.toString(), paths.none { it.contains("now") })
        }

    /**
     * The undated form is unreachable by construction: the date argument is validated, so
     * `standings("now")` never builds a URL at all. This is the guarantee a source scan cannot
     * give, because a scan cannot see a route assembled at runtime from parameters.
     */
    @Test
    fun undatedStandingsArgumentIsRejectedBeforeAnyRequest() =
        withResponse { api, engine ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.standings("now") }
            }

            assertTrue("No request may be built for a rejected date", engine.requestHistory.isEmpty())
        }

    @Test
    fun undatedScoreArgumentIsRejectedBeforeAnyRequest() =
        withResponse { api, engine ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.score("today") }
            }

            assertTrue("No request may be built for a rejected date", engine.requestHistory.isEmpty())
        }

    @Test
    fun dateNeedsFullIsoShapeNotJustDigits() =
        withResponse { api, engine ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.standings("2026-10-2") }
            }
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun successPreservesBytesExactly() {
        // Bytes that do not survive a decode/encode round trip: Latin-1 text plus a lone 0xFF,
        // which is not valid UTF-8 at all. A text-based capture would rewrite both.
        val raw =
            byteArrayOf(
                0x7B,
                0x22,
                0x6E,
                0x22,
                0x3A,
                0x22,
                0x4D,
                0xE9.toByte(),
                0x78,
                0x69,
                0x63,
                0x6F,
                0x22,
                0x7D,
                0xFF.toByte(),
            )

        withResponseBytes(raw) { api, _ ->
            val result = api.score("2026-10-25")
            assertTrue("Expected Success, got $result", result is ApiResult.Success)
            assertArrayEquals(raw, (result as ApiResult.Success).bytes)
        }
    }

    @Test
    fun successBodyDecodesToTheReceivedText() =
        withResponse(body = """{"unknownField":[1,2],"team":"Montréal"}""") { api, _ ->
            val result = api.score("2026-10-25")
            assertEquals(
                """{"unknownField":[1,2],"team":"Montréal"}""",
                (result as ApiResult.Success).bytes.decodeToString(),
            )
        }

    @Test
    fun serviceUnavailableReturnsHttpError503() =
        withResponse(status = HttpStatusCode.ServiceUnavailable) { api, _ ->
            assertEquals(ApiResult.HttpError(503), api.score("2026-10-25"))
        }

    @Test
    fun notFoundReturnsHttpError404() =
        withResponse(status = HttpStatusCode.NotFound) { api, _ ->
            assertEquals(ApiResult.HttpError(404), api.score("2026-10-25"))
        }

    @Test
    fun ioExceptionReturnsNetworkError() =
        runBlocking {
            MockEngine { throw IOException("Connection lost") }.use { engine ->
                NhlHttpClientFactory.create(engine, "test-version").use { client ->
                    assertEquals(ApiResult.NetworkError, NhlApi(client).score("2026-10-25"))
                }
            }
        }

    @Test
    fun blockedHostPropagatesHostNotAllowedException() {
        MockEngine { respond("", HttpStatusCode.OK) }.use { engine ->
            NhlHttpClientFactory.create(engine, "test-version").use { client ->
                val api = NhlApi(client, baseUrl = "https://example.com")
                val exception =
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { api.score("2026-10-25") }
                    }

                assertEquals("example.com", exception.host)
                assertTrue("A blocked host must never reach the engine", engine.requestHistory.isEmpty())
            }
        }
    }

    @Test
    fun rateLimitPropagatesRateLimitedWithDelay() =
        withResponse(
            status = HttpStatusCode.TooManyRequests,
            headers = headersOf(HttpHeaders.RetryAfter, "120"),
        ) { api, _ ->
            val exception =
                assertThrows(RateLimited::class.java) {
                    runBlocking { api.score("2026-10-25") }
                }

            assertEquals(120.seconds, exception.retryAfter)
        }

    private fun assertPath(
        expected: String,
        request: suspend NhlApi.() -> ApiResult,
    ) = withResponse { api, engine ->
        val result = api.request()
        assertTrue("Expected Success, got $result", result is ApiResult.Success)
        val url = engine.requestHistory.single().url
        assertEquals(expected, url.encodedPath)
        assertEquals("api-web.nhle.com", url.host)
    }

    private fun withResponse(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Headers = headersOf(),
        block: suspend (NhlApi, MockEngine) -> Unit,
    ) = runBlocking {
        MockEngine { respond(body, status, headers) }.use { engine ->
            NhlHttpClientFactory.create(engine, "test-version").use { client ->
                block(NhlApi(client), engine)
            }
        }
    }

    private fun withResponseBytes(
        body: ByteArray,
        block: suspend (NhlApi, MockEngine) -> Unit,
    ) = runBlocking {
        MockEngine { respond(body, HttpStatusCode.OK) }.use { engine ->
            NhlHttpClientFactory.create(engine, "test-version").use { client ->
                block(NhlApi(client), engine)
            }
        }
    }
}
