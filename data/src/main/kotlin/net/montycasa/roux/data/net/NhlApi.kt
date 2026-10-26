package net.montycasa.roux.data.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import okio.IOException

sealed interface ApiResult {
    data class Success(
        val body: String,
    ) : ApiResult

    data class HttpError(
        val status: Int,
    ) : ApiResult

    data object NetworkError : ApiResult
}

/**
 * Centralises NHL URL and path knowledge: no other production file should build NHL URLs.
 * Dates are already ISO YYYY-MM-DD strings supplied by callers; this module needs no date library.
 * Responses stay unparsed for the raw-response cache in S2.5; parsing belongs to the adapters.
 * The caller owns [client], which should come from [NhlHttpClientFactory].
 */
class NhlApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api-web.nhle.com",
) {
    suspend fun score(date: String): ApiResult = get("/v1/score/$date")

    /** Always uses the dated standings form, as required by the caching and freshness policy. */
    suspend fun standings(date: String): ApiResult = get("/v1/standings/$date")

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
                ApiResult.Success(response.bodyAsText())
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
}
