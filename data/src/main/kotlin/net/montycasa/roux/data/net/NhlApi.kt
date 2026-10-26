package net.montycasa.roux.data.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import okio.IOException

sealed interface ApiResult {
    /**
     * The response exactly as received, undecoded.
     *
     * Deliberately bytes rather than text: the S2.5 cache must write what the server sent so an
     * adapter change never invalidates it. Decoding here would re-encode on the way back to disk,
     * and any response that does not round-trip through UTF-8 - a mis-declared charset, a stray
     * invalid byte - would be silently rewritten. Decode at the parsing layer instead.
     *
     * Not a data class: [ByteArray] compares by identity, so a generated `equals` would be a lie.
     */
    class Success(
        val bytes: ByteArray,
    ) : ApiResult

    data class HttpError(
        val status: Int,
    ) : ApiResult

    data object NetworkError : ApiResult
}

/**
 * Centralises NHL URL and path knowledge: no other production file should build NHL URLs.
 *
 * Dates are ISO `YYYY-MM-DD` strings supplied by callers; this module needs no date library. They
 * are validated rather than interpolated blindly, which is what makes the undated standings form
 * impossible to request: `/v1/standings/now` cannot be expressed through [standings] because "now"
 * is not a date.
 *
 * Responses stay unparsed for the raw-response cache in S2.5; parsing belongs to the adapters.
 * The caller owns [client], which should come from [NhlHttpClientFactory].
 */
class NhlApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api-web.nhle.com",
) {
    suspend fun score(date: String): ApiResult = get(datedPath("/v1/score", date))

    /** Always uses the dated standings form, as required by the caching and freshness policy. */
    suspend fun standings(date: String): ApiResult = get(datedPath("/v1/standings", date))

    suspend fun seasons(): ApiResult = get("/v1/standings-season")

    /** PROVISIONAL: this path and response shape are expected but unverified until S7.1. */
    suspend fun gamecenterLanding(gameId: Long): ApiResult = get("/v1/gamecenter/$gameId/landing")

    /** PROVISIONAL: this path and response shape are expected but unverified until S7.1. */
    suspend fun gamecenterBoxscore(gameId: Long): ApiResult = get("/v1/gamecenter/$gameId/boxscore")

    /** PROVISIONAL: this path and response shape are expected but unverified until S7.1. */
    suspend fun gamecenterRightRail(gameId: Long): ApiResult = get("/v1/gamecenter/$gameId/right-rail")

    private suspend fun get(path: String): ApiResult =
        try {
            val response = client.get("${baseUrl.trimEnd('/')}$path")
            if (response.status.value in 200..299) {
                ApiResult.Success(response.readRawBytes())
            } else {
                ApiResult.HttpError(response.status.value)
            }
            // HostNotAllowedException must propagate: a bug or attack must not become an
            // offline result hidden behind an endless network retry loop.
            // RateLimited must propagate: its retry delay is needed by the poller and
            // would be lost if collapsed into HttpError(429).
        } catch (_: IOException) {
            // Okio's JVM alias is java.io.IOException, including Ktor connect/socket failures.
            ApiResult.NetworkError
        }

    /**
     * Rejects anything that is not an ISO date before a request is built.
     *
     * This is a structural guard, not a formatting nicety. It is what stops the undated
     * `/v1/standings/now` form from being reachable at all, including by way of a caller that
     * passes "now", today's name, or any other stand-in for a date.
     */
    private fun datedPath(
        prefix: String,
        date: String,
    ): String {
        require(ISO_DATE.matches(date)) { "NHL dates are ISO YYYY-MM-DD, got '$date'" }
        return "$prefix/$date"
    }

    private companion object {
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
