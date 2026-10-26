package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Engine history proves that rejected destinations are never contacted, including through redirects. */
class NhlHttpClientTest {
    @Test
    fun blockedHostNeverReachesEngine() {
        MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
            NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                val exception =
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { client.get("https://example.com/v1/score/2026-10-26") }
                    }

                assertEquals("example.com", exception.host)
                assertTrue("A blocked host must never reach the engine", mockEngine.requestHistory.isEmpty())
            }
        }
    }

    @Test
    fun apiHostSucceeds() =
        runBlocking {
            MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
                NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    assertEquals(HttpStatusCode.OK, client.get("https://api-web.nhle.com/v1/score/2026-10-26").status)
                    assertEquals(listOf("api-web.nhle.com"), mockEngine.requestHistory.map { it.url.host })
                }
            }
        }

    @Test
    fun assetsHostSucceeds() =
        runBlocking {
            MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
                NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    assertEquals(HttpStatusCode.OK, client.get("https://assets.nhle.com/logos/nhl/svg/TOR_light.svg").status)
                    assertEquals(listOf("assets.nhle.com"), mockEngine.requestHistory.map { it.url.host })
                }
            }
        }

    @Test
    fun temporaryRedirectFollowsRelativeLocationAndPreservesMethod() =
        runBlocking {
            MockEngine { request ->
                if (request.url.encodedPath == "/v1/standings/2026-10-26") {
                    respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/v1/standings/2026-10-25"))
                } else {
                    respond("", HttpStatusCode.OK)
                }
            }.use { mockEngine ->
                NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    val response = client.head("https://api-web.nhle.com/v1/standings/2026-10-26")

                    assertEquals(2, mockEngine.requestHistory.size)
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("https://api-web.nhle.com/v1/standings/2026-10-25", mockEngine.requestHistory[1].url.toString())
                    assertEquals(listOf(HttpMethod.Head, HttpMethod.Head), mockEngine.requestHistory.map { it.method })
                }
            }
        }

    @Test
    fun redirectedLookalikeSubdomainNeverReachesEngine() {
        // evil.example proves nothing about suffix matching: a regression to `host.endsWith("nhle.com")`
        // would still block it. A lookalike under the real domain is the case that separates an exact
        // host set from a suffix check, and it is the one an attacker would actually reach for.
        MockEngine { request ->
            if (request.url.host == "api-web.nhle.com") {
                respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://evil.nhle.com/x"))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }.use { mockEngine ->
            NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                val exception =
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { client.get("https://api-web.nhle.com/v1/score/2026-10-26") }
                    }

                assertEquals("evil.nhle.com", exception.host)
                assertEquals(1, mockEngine.requestHistory.size)
                assertEquals(listOf("api-web.nhle.com"), mockEngine.requestHistory.map { it.url.host })
            }
        }
    }

    @Test
    fun lookalikeSubdomainIsBlockedDirectlyToo() =
        runBlocking {
            MockEngine { respond("", HttpStatusCode.OK) }.use { mockEngine ->
                NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { client.get("https://evil.nhle.com/v1/score/2026-10-26") }
                    }

                    assertTrue("A lookalike host must never reach the engine", mockEngine.requestHistory.isEmpty())
                }
            }
        }

    @Test
    fun redirectedBlockedHostNeverReachesEngine() {
        MockEngine { request ->
            if (request.url.host == "api-web.nhle.com") {
                respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://evil.example/x"))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }.use { mockEngine ->
            NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                val exception =
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { client.get("https://api-web.nhle.com/v1/score/2026-10-26") }
                    }

                assertEquals("evil.example", exception.host)
                assertEquals(1, mockEngine.requestHistory.size)
                assertEquals(listOf("api-web.nhle.com"), mockEngine.requestHistory.map { it.url.host })
            }
        }
    }

    @Test
    fun userAgentIncludesSuppliedVersionExactly() =
        runBlocking {
            MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
                NhlHttpClientFactory.create(mockEngine, "2.3-test").use { client ->
                    client.get("https://api-web.nhle.com/v1/score/2026-10-26")

                    assertEquals(
                        "RouxSports/2.3-test (+https://git.montycasa.net/patrick/roux-sports)",
                        mockEngine.requestHistory.single().headers[HttpHeaders.UserAgent],
                    )
                }
            }
        }

    @Test
    fun rateLimitParsesDeltaSeconds() {
        val exception = rateLimitFailure("120")

        assertEquals(120.seconds, exception.retryAfter)
    }

    @Test
    fun rateLimitWithoutRetryAfterHasNoDelay() {
        assertNull(rateLimitFailure(null).retryAfter)
    }

    @Test
    fun rateLimitWithHttpDateHasNoDelay() {
        assertNull(rateLimitFailure("Wed, 28 Oct 2026 12:00:00 GMT").retryAfter)
    }

    private fun rateLimitFailure(retryAfter: String?): RateLimited {
        MockEngine { _ ->
            respond(
                "",
                HttpStatusCode.TooManyRequests,
                if (retryAfter == null) headersOf() else headersOf(HttpHeaders.RetryAfter, retryAfter),
            )
        }.use { mockEngine ->
            NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                return assertThrows(RateLimited::class.java) {
                    runBlocking { client.get("https://api-web.nhle.com/v1/score/2026-10-26") }
                }
            }
        }
    }
}
