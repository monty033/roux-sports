package net.montycasa.roux.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

/** Identifies a blocked ESPN destination so support can trace an unexpected URL or redirect. */
class EspnHostNotAllowedException(
    val host: String,
) : IllegalArgumentException("Blocked ESPN HTTP request to host '$host'; only ${EspnHttpClientFactory.LOGO_HOST} is allowed")

/**
 * The ESPN network boundary: NFL team logos and headshots, from ESPN's CDN host only. It is a separate client, not a
 * wider NHL one, so an NFL logo request cannot reach an NHL host and an NHL request cannot reach ESPN.
 */
object EspnHttpClientFactory {
    /** The one ESPN host the app is authorized to fetch from. Adding another requires a fresh authorization. */
    const val LOGO_HOST = "a.espncdn.com"

    private val allowedHosts = setOf(LOGO_HOST)

    private val espnBoundary =
        createClientPlugin("EspnBoundary") {
            // SendingRequest runs before the engine on every send, including redirects, so a blocked host is never
            // contacted rather than contacted and then ignored.
            on(SendingRequest) { request, _ ->
                val host = request.url.host
                if (host !in allowedHosts) throw EspnHostNotAllowedException(host)
            }
        }

    /** Inject the engine so boundary behaviour can be verified without contacting ESPN. */
    fun create(
        engine: HttpClientEngine,
        version: String,
    ): HttpClient =
        HttpClient(engine) {
            followRedirects = true
            defaultRequest {
                headers.append(HttpHeaders.UserAgent, "RouxSports/$version (+https://git.montycasa.net/patrick/roux-sports)")
            }
            install(espnBoundary)
        }
}
