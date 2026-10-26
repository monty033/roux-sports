package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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

    @Test
    fun productionSourceHasNoUndatedStandingsLiteral() {
        val root = File(System.getProperty("roux.rootDir"))
        val source = File(root, "data/src/main/kotlin/net/montycasa/roux/data/net/NhlApi.kt").readText()

        assertFalse(source.contains("standings/now"))
    }

    @Test
    fun successPreservesRawBody() {
        val body = "  {\n  \"unknownField\": [1, 2], \"team\": \"Montréal\"\n}\n"
        withResponse(body = body) { api, _ ->
            assertEquals(ApiResult.Success(body), api.score("2026-10-25"))
        }
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
        assertEquals(ApiResult.Success("{}"), api.request())
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
}
