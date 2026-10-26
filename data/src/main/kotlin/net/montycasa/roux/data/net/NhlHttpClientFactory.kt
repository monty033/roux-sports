package net.montycasa.roux.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Identifies a blocked destination so support can trace an unexpected URL or redirect. */
class HostNotAllowedException(
    val host: String,
) : IllegalArgumentException("Blocked NHL HTTP request to host '$host'; only api-web.nhle.com and assets.nhle.com are allowed")

/** Keeps a server-requested pause distinct from other HTTP failures for the polling caller. */
class RateLimited(
    val retryAfter: Duration?,
) : Exception("NHL HTTP 429 rate limit; retry delay: ${retryAfter ?: "unspecified (missing or invalid Retry-After)"}")

/** Centralises the NHL network boundary while leaving engine ownership and version selection to callers. */
object NhlHttpClientFactory {
    private val allowedHosts = setOf("api-web.nhle.com", "assets.nhle.com")

    private val nhlBoundary =
        createClientPlugin("NhlBoundary") {
            // The allowlist stops this app talking to anything the NHL did not publish:
            // blocked hosts must never be contacted, not merely ignored after a response.
            // SendingRequest runs before the engine on every send, including redirects;
            // onRequest alone only covers the initial request pipeline in Ktor 3.
            on(SendingRequest) { request, _ ->
                val host = request.url.host
                if (host !in allowedHosts) throw HostNotAllowedException(host)
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

    /** Inject the engine so boundary behaviour can be verified without contacting NHL servers. */
    fun create(
        engine: HttpClientEngine,
        version: String,
    ): HttpClient =
        HttpClient(engine) {
            followRedirects = true
            defaultRequest {
                headers.append(HttpHeaders.UserAgent, "RouxSports/$version (+https://git.montycasa.net/patrick/roux-sports)")
            }
            install(nhlBoundary)
        }
}
