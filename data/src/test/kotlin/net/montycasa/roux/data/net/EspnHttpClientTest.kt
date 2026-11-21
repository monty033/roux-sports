package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two clients refuse each other's hosts, and a refused host is never handed to the engine. */
class EspnHttpClientTest {
    @Test
    fun nhlAndOtherHostsNeverReachTheEspnEngine() {
        for (host in listOf("api-web.nhle.com", "assets.nhle.com", "api.nhle.com", "example.com", "site.web.api.espn.com")) {
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
    fun theNhlClientRefusesTheEspnLogoHost() {
        MockEngine { _ -> respond("", HttpStatusCode.OK) }.use { mockEngine ->
            NhlHttpClientFactory.create(mockEngine, "test-version").use { client ->
                assertThrows(HostNotAllowedException::class.java) {
                    runBlocking { client.get("https://a.espncdn.com/i/teamlogos/nfl/500/phi.png") }
                }

                assertTrue("an NFL logo must never reach the NHL engine", mockEngine.requestHistory.isEmpty())
            }
        }
    }
}
