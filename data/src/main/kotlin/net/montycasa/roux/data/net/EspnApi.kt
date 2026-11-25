package net.montycasa.roux.data.net

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.discardRemaining
import io.ktor.client.statement.readRawBytes
import okio.IOException

/**
 * Centralises ESPN URL and path knowledge: this is the only production file that may build an ESPN URL.
 *
 * Dates are ISO `YYYY-MM-DD` strings at the boundary and compact `YYYYMMDD` in the query, which is the form ESPN's
 * scoreboard takes. They are validated rather than interpolated blindly, so anything that is not a date - "now", a
 * timestamp, an already-compact date - throws having built nothing.
 *
 * Responses stay unparsed because the raw-response cache writes what the server sent; parsing belongs to the
 * adapters. The caller owns [client], which should come from [EspnHttpClientFactory]: it owns the host allowlist, so
 * a [baseUrl] naming any other host fails there rather than being contacted.
 */
class EspnApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://${EspnHttpClientFactory.DATA_HOST}",
) {
    /** The NFL scoreboard for one day, given as ISO `YYYY-MM-DD`. */
    suspend fun scoreboard(date: String): ApiResult {
        require(ISO_DATE.matches(date)) { "ESPN dates are ISO YYYY-MM-DD, got '$date'" }
        return get("/apis/site/v2/sports/football/nfl/scoreboard?dates=${date.replace("-", "")}")
    }

    /**
     * The NFL standings: two conference tables, or - when [group] names one - that conference's four divisions.
     *
     * The group ids are not the conference numbering they resemble: the AFC answers **8** and the NFC **7**, while 9
     * answers the conference level with no rows at all. `docs/nfl-sources.md` records the probe that found this.
     */
    suspend fun standings(group: Int? = null): ApiResult =
        get(
            if (group == null) {
                "/apis/v2/sports/football/nfl/standings"
            } else {
                "/apis/v2/sports/football/nfl/standings?level=3&group=$group"
            },
        )

    private suspend fun get(path: String): ApiResult =
        try {
            // prepareGet/execute rather than client.get: client.get buffers the whole body and
            // releases the connection before returning, so a large 5xx payload would be held in
            // memory twice for nothing. The streaming form lets a non-2xx body be discarded without
            // ever being materialised.
            client.prepareGet("${baseUrl.trimEnd('/')}$path").execute { response ->
                if (response.status.value in 200..299) {
                    ApiResult.Success(response.readRawBytes())
                } else {
                    // A failure while discarding must not change the outcome: the status is the
                    // answer, and letting it reach the outer handler would report a server error as
                    // a dead network.
                    try {
                        response.discardRemaining()
                    } catch (_: IOException) {
                        // The status still stands.
                    }
                    ApiResult.HttpError(response.status.value)
                }
            }
            // EspnHostNotAllowedException must propagate: a bug or attack must not become an
            // offline result hidden behind an endless network retry loop.
            // RateLimited must propagate: its retry delay is needed by the poller and
            // would be lost if collapsed into HttpError(429).
        } catch (_: IOException) {
            // Okio's JVM alias is java.io.IOException, including Ktor connect/socket failures.
            ApiResult.NetworkError
        }

    private companion object {
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
