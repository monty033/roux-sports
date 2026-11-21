package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** The two clients refuse each other's hosts, and a refused host is never handed to the engine. */
class EspnHttpClientTest {
    @Test
    fun nhlAndOtherHostsNeverReachTheEspnEngine() {
        for (
        host in
        listOf(
            "api-web.nhle.com",
            "assets.nhle.com",
            "api.nhle.com",
            "example.com",
            "site.web.api.espn.com",
            "site.api.espn.com.evil.test",
        )
        ) {
            MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
                EspnHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    val exception =
                        assertThrows(EspnHostNotAllowedException::class.java) {
                            runBlocking { client.get("https://$host/x") }
                        }

                    assertEquals(host, exception.host)
                    assertTrue("$host must never reach the engine", mockEngine.requestHistory.isEmpty())
                }
            }
        }
    }

    @Test
    fun theEspnLogoHostSucceeds() =
        runBlocking {
            MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
                EspnHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    val url = "https://a.espncdn.com/i/teamlogos/nfl/500/phi.png"
                    assertEquals(HttpStatusCode.OK, client.get(url).status)
                    assertEquals(listOf("a.espncdn.com"), mockEngine.requestHistory.map { it.url.host })
                }
            }
        }

    @Test
    fun theEspnDataHostReachesTheEngine() =
        runBlocking {
            MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
                EspnHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    val url = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20261122"
                    assertEquals(HttpStatusCode.OK, client.get(url).status)
                    assertEquals(listOf("site.api.espn.com"), mockEngine.requestHistory.map { it.url.host })
                }
            }
        }

    @Test
    fun aRateLimitCarriesTheServerPause() {
        assertEquals(30.seconds, rateLimitFailure("30").retryAfter)
    }

    @Test
    fun aRateLimitWithoutRetryAfterHasNoDelay() {
        assertNull(rateLimitFailure(null).retryAfter)
    }

    @Test
    fun aRateLimitWithAnHttpDateHasNoDelay() {
        // The date form is deliberately unsupported: a wrong guess is worse than saying nothing.
        assertNull(rateLimitFailure("Wed, 21 Oct 2026 07:28:00 GMT").retryAfter)
    }

    @Test
    fun aRateLimitWithANegativeDelayHasNoDelay() {
        assertNull(rateLimitFailure("-5").retryAfter)
    }

    @Test
    fun aRedirectToAnNhlHostIsBlockedBeforeTheEngineSeesIt() {
        MockEngine { _ ->
            respond(
                "",
                HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://api-web.nhle.com/v1/score/2026-11-22"),
            )
        }.use { mockEngine ->
            EspnHttpClientFactory.create(mockEngine, "test-version").use { client ->
                val exception =
                    assertThrows(EspnHostNotAllowedException::class.java) {
                        runBlocking { client.get("https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard") }
                    }

                assertEquals("api-web.nhle.com", exception.host)
                assertEquals(
                    "only the first, allowed request may reach the engine",
                    listOf("site.api.espn.com"),
                    mockEngine.requestHistory.map { it.url.host },
                )
            }
        }
    }

    @Test
    fun theNhlClientRefusesTheEspnLogoHost() {
        for (
        url in
        listOf(
            "https://a.espncdn.com/i/teamlogos/nfl/500/phi.png",
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20261122",
        )
        ) {
            MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
                NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { client.get(url) }
                    }

                    assertTrue("$url must never reach the NHL engine", mockEngine.requestHistory.isEmpty())
                }
            }
        }
    }

    private fun rateLimitFailure(retryAfter: String?): RateLimited {
        MockEngine { _ ->
            respond(
                "",
                HttpStatusCode.TooManyRequests,
                if (retryAfter == null) headersOf() else headersOf(HttpHeaders.RetryAfter, retryAfter),
            )
        }.use { mockEngine ->
            EspnHttpClientFactory.create(mockEngine, "test-version").use { client ->
                return assertThrows(RateLimited::class.java) {
                    runBlocking { client.get("https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard") }
                }
            }
        }
    }
}
