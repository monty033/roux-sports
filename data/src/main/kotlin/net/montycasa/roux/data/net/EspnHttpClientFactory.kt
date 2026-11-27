package net.montycasa.roux.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds

/** Identifies a blocked ESPN destination so support can trace an unexpected URL or redirect. */
class EspnHostNotAllowedException(
    val host: String,
) : IllegalArgumentException(
        "Blocked ESPN HTTP request to host '$host'; only ${EspnHttpClientFactory.DATA_HOST} and ${EspnHttpClientFactory.LOGO_HOST} are allowed",
    )

/**
 * The ESPN network boundary: NFL data from ESPN's site API host, and NFL team logos and headshots from ESPN's CDN
 * host. It is a separate client, not a wider NHL one, so an NFL request cannot reach an NHL host and an NHL request
 * cannot reach ESPN.
 */
object EspnHttpClientFactory {
    /** The ESPN data host the app is authorized to fetch from. Adding another requires a fresh authorization. */
    const val DATA_HOST = "site.api.espn.com"

    /** The ESPN image host the app is authorized to fetch from. Adding another requires a fresh authorization. */
    const val LOGO_HOST = "a.espncdn.com"

    private val allowedHosts = setOf(DATA_HOST, LOGO_HOST)

    private val espnBoundary =
        createClientPlugin("EspnBoundary") {
            // SendingRequest runs before the engine on every send, including redirects, so a blocked host is never
            // contacted rather than contacted and then ignored.
            on(SendingRequest) { request, _ ->
                val host = request.url.host
                if (host !in allowedHosts) throw EspnHostNotAllowedException(host)
            }
            onResponse { response ->
                if (response.status == HttpStatusCode.TooManyRequests) {
                    val value = response.headers[HttpHeaders.RetryAfter]
                    // Only delta-seconds are supported. A wrong guess is worse than saying nothing.
                    val retryAfter =
                        value
                            ?.takeIf { it.isNotEmpty() && it.all { character -> character in '0'..'9' } }
                            ?.toLongOrNull()
                            ?.seconds
                    throw RateLimited(retryAfter)
                }
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
                headers.append(HttpHeaders.UserAgent, UserAgent.of(version))
            }
            install(espnBoundary)
        }
}
