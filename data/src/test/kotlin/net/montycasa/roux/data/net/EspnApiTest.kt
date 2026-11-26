package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class EspnApiTest {
    @Test
    fun aDayRequestHitsTheDatedScoreboard() =
        runBlocking {
            MockEngine { respond("{}", HttpStatusCode.OK) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    val result = EspnApi(client).scoreboard("2026-11-22")

                    assertTrue("Expected Success, got $result", result is ApiResult.Success)
                    assertEquals(
                        "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20261122",
                        engine.requestHistory
                            .single()
                            .url
                            .toString(),
                    )
                }
            }
        }

    @Test
    fun aGameSummaryRequestNamesTheEventInTheQuery() =
        runBlocking {
            MockEngine { respond("{}", HttpStatusCode.OK) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    val result = EspnApi(client).gameSummary("401872923")

                    assertTrue("Expected Success, got $result", result is ApiResult.Success)
                    assertEquals(
                        "https://site.api.espn.com/apis/site/v2/sports/football/nfl/summary?event=401872923",
                        engine.requestHistory
                            .single()
                            .url
                            .toString(),
                    )
                }
            }
        }

    @Test
    fun theClubsRequestIsTheBareTeamsPathOnTheDataHost() =
        runBlocking {
            MockEngine { respond("{}", HttpStatusCode.OK) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    val result = EspnApi(client).teams()

                    assertTrue("Expected Success, got $result", result is ApiResult.Success)
                    // No `limit`: the feed answers all thirty-two either way, and a parameter that does nothing is noise.
                    assertEquals(
                        "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams",
                        engine.requestHistory
                            .single()
                            .url
                            .toString(),
                    )
                }
            }
        }

    /**
     * Without `enable=roster` the feed answers 200 with an empty athlete list, which would read as a club with no
     * players. The parameter is part of the request rather than something a future caller may forget.
     */
    @Test
    fun theRosterRequestAlwaysAsksForTheRosterPayload() =
        runBlocking {
            MockEngine { respond("{}", HttpStatusCode.OK) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    val result = EspnApi(client).teamRoster("BUF")

                    assertTrue("Expected Success, got $result", result is ApiResult.Success)
                    // The club path, not `/roster`: that endpoint answers 200 with a different, much shorter payload.
                    assertEquals(
                        "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/buf?enable=roster",
                        engine.requestHistory
                            .single()
                            .url
                            .toString(),
                    )
                }
            }
        }

    @Test
    fun aNonIsoDateBuildsNoRequest() =
        runBlocking {
            MockEngine { respond("{}", HttpStatusCode.OK) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    val api = EspnApi(client)

                    for (bad in listOf("now", "2026-1-2", "20261122", "2026-11-22T00:00:00Z")) {
                        assertThrows("'$bad' must be rejected", IllegalArgumentException::class.java) {
                            runBlocking { api.scoreboard(bad) }
                        }
                        assertTrue("'$bad' must build no request", engine.requestHistory.isEmpty())
                    }
                }
            }
        }

    /** Not valid UTF-8 (0x80 and 0xFF), so any decode and re-encode on the way through would change it. */
    @Test
    fun aSuccessfulBodyComesBackAsRawBytes() =
        runBlocking {
            val raw = byteArrayOf(0x7B, 0x80.toByte(), 0xFF.toByte(), 0x7D)

            MockEngine { respond(raw, HttpStatusCode.OK) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    val result = EspnApi(client).scoreboard("2026-11-22")

                    assertTrue("Expected Success, got $result", result is ApiResult.Success)
                    assertArrayEquals(raw, (result as ApiResult.Success).bytes)
                }
            }
        }

    @Test
    fun aServerErrorComesBackAsItsStatus() =
        runBlocking {
            MockEngine { respond("down", HttpStatusCode.InternalServerError) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    assertEquals(ApiResult.HttpError(status = 500), EspnApi(client).scoreboard("2026-11-22"))
                }
            }
        }

    @Test
    fun aDeadNetworkComesBackAsNetworkError() =
        runBlocking {
            MockEngine { throw IOException("Connection lost") }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    assertEquals(ApiResult.NetworkError, EspnApi(client).scoreboard("2026-11-22"))
                }
            }
        }

    @Test
    fun aRateLimitPropagatesThePause() =
        runBlocking {
            MockEngine { respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "75")) }.use { engine ->
                EspnHttpClientFactory.create(engine, "test-version").use { client ->
                    val exception =
                        assertThrows(RateLimited::class.java) {
                            runBlocking { EspnApi(client).scoreboard("2026-11-22") }
                        }

                    assertEquals(75.seconds, exception.retryAfter)
                }
            }
        }

    /** The base URL is a constructor argument, so the client's allowlist is what stops it naming a second host. */
    @Test
    fun theApiCannotBePointedAtAnNhlHost() =
        runBlocking {
            MockEngine { respond("{}", HttpStatusCode.OK) }.use { engine ->
                val api =
                    EspnApi(
                        client = EspnHttpClientFactory.create(engine, "v"),
                        baseUrl = "https://api-web.nhle.com",
                    )

                val exception =
                    assertThrows(EspnHostNotAllowedException::class.java) {
                        runBlocking { api.scoreboard("2026-11-22") }
                    }

                assertEquals("api-web.nhle.com", exception.host)
                assertTrue("an NHL host must never reach the ESPN engine", engine.requestHistory.isEmpty())
            }
        }
}
