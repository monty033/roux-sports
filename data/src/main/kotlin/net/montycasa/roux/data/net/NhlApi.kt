package net.montycasa.roux.data.net

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.discardRemaining
import io.ktor.client.statement.readRawBytes
import net.montycasa.roux.domain.model.GameId
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

    /**
     * A club's whole-season schedule.
     *
     * Both path segments are validated before a request is built: a club is a three-letter
     * abbreviation and a season is the feed's eight-digit `20262027` form. Nothing else should be
     * able to turn a malformed club or season into a URL.
     */
    suspend fun clubSchedule(
        abbrev: String,
        season: String,
    ): ApiResult {
        require(CLUB_ABBREV.matches(abbrev)) { "Club abbreviation must be three uppercase letters, was '$abbrev'" }
        require(SEASON_ID.matches(season)) { "Season must be eight digits, was '$season'" }
        return get("/v1/club-schedule-season/$abbrev/$season")
    }

    /** The endpoint's own name, for callers that prefer to say exactly which feed is read. */
    suspend fun clubScheduleSeason(
        abbrev: String,
        season: String,
    ): ApiResult = clubSchedule(abbrev, season)

    /**
     * Path verified against the live API by hand. A captured corpus for these payloads is G1 work and is not in
     * this tree yet, so nothing here claims a fixture exists.
     */
    suspend fun gamecenterLanding(gameId: GameId): ApiResult = get(gamecenterPath(gameId, "landing"))

    /**
     * Path verified against the live API by hand. A captured corpus for these payloads is G1 work and is not in
     * this tree yet, so nothing here claims a fixture exists.
     */
    suspend fun gamecenterBoxscore(gameId: GameId): ApiResult = get(gamecenterPath(gameId, "boxscore"))

    /**
     * Path verified against the live API by hand. A captured corpus for these payloads is G1 work and is not in
     * this tree yet, so nothing here claims a fixture exists.
     */
    suspend fun gamecenterRightRail(gameId: GameId): ApiResult = get(gamecenterPath(gameId, "right-rail"))

    /**
     * Builds a gamecenter path, rejecting anything that is not a bare ten-digit id.
     *
     * [GameId] itself stays opaque on purpose: an adapter must be able to represent an id it read from a
     * messy feed, so the value type cannot be the place that refuses one. The URL is the boundary, and it
     * is also the last point before the id becomes part of a request, so a traversal attempt such as
     * `"../x"` throws here having built nothing.
     */
    private fun gamecenterPath(
        gameId: GameId,
        section: String,
    ): String {
        require(gameId.value.matches(GAME_ID)) {
            "Game id must be ten digits, was '${gameId.value}'"
        }
        return "/v1/gamecenter/${gameId.value}/$section"
    }

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
        val GAME_ID = Regex("""[0-9]{10}""")
        val CLUB_ABBREV = Regex("""[A-Z]{3}""")
        val SEASON_ID = Regex("""[0-9]{8}""")
    }
}
