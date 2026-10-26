package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
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
    fun seasonsUsesExactPath() = assertPath("/v1/standings-season") { seasons() }

    @Test
    fun gamecenterLandingUsesExactPath() = assertPath("/v1/gamecenter/2026020123/landing") { gamecenterLanding(2026020123L) }

    @Test
    fun gamecenterBoxscoreUsesExactPath() = assertPath("/v1/gamecenter/2026020123/boxscore") { gamecenterBoxscore(2026020123L) }

    @Test
    fun gamecenterRightRailUsesExactPath() = assertPath("/v1/gamecenter/2026020123/right-rail") { gamecenterRightRail(2026020123L) }

    @Test
    fun everyPublicMethodAvoidsNowPath() =
        withResponse { api, engine ->
            api.score("2026-10-25")
            api.standings("2026-10-25")
            api.seasons()
            api.gamecenterLanding(2026020123L)
            api.gamecenterBoxscore(2026020123L)
            api.gamecenterRightRail(2026020123L)

            val paths = engine.requestHistory.map { it.url.encodedPath }
            assertEquals(6, paths.size)
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
