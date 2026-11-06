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
    /** The stats host serves league-wide tables; it is separate from the web host the other endpoints use. */
    private val statsBaseUrl: String = "https://api.nhle.com",
) {
    suspend fun score(date: String): ApiResult = get(datedPath("/v1/score", date))

    /** A week's schedule, walked by the opening-night resolver. Dated like the score and standings forms. */
    suspend fun schedule(date: String): ApiResult = get(datedPath("/v1/schedule", date))

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
     * A club's current roster. The live endpoint answers with a 307 to the dated roster; the shared
     * client sets `followRedirects = true`, so this path relies on that and must not opt out.
     */
    suspend fun roster(abbrev: String): ApiResult {
        require(CLUB_ABBREV.matches(abbrev)) { "Club abbreviation must be three uppercase letters, was '$abbrev'" }
        return get("/v1/roster/$abbrev/current")
    }

    /**
     * A club's skater and goalie statistics for one season and game type.
     *
     * Both path segments are validated before a request is built, so a malformed club or season
     * cannot turn into a URL. The Stats tab always asks for game type 2 (the regular season).
     */
    suspend fun clubStats(
        abbrev: String,
        season: String,
        gameType: Int = 2,
    ): ApiResult {
        require(CLUB_ABBREV.matches(abbrev)) { "Club abbreviation must be three uppercase letters, was '$abbrev'" }
        require(SEASON_ID.matches(season)) { "Season must be eight digits, was '$season'" }
        require(gameType in 1..3) { "Game type must be 1, 2 or 3, was '$gameType'" }
        return get("/v1/club-stats/$abbrev/$season/$gameType")
    }

    /** The endpoint's own name, for callers that prefer to say exactly which feed is read. */
    suspend fun clubStatsSeason(
        abbrev: String,
        season: String,
        gameType: Int = 2,
    ): ApiResult = clubStats(abbrev, season, gameType)

    /**
     * Every club's season totals for one season and game type, in a single response.
     *
     * This is the stats host's team summary, not the web host's: `api.nhle.com` answers with one row per
     * club, so one request covers the whole league. The season and game type are validated like
     * [clubStats] before they reach the query string, and the `%20` spaces are the feed's own spelling of
     * the expression, not an encoding choice.
     */
    suspend fun leagueSummary(
        season: String,
        gameType: Int = 2,
    ): ApiResult {
        require(SEASON_ID.matches(season)) { "Season must be eight digits, was '$season'" }
        require(gameType in 1..3) { "Game type must be 1, 2 or 3, was '$gameType'" }
        return get("/stats/rest/en/team/summary?cayenneExp=seasonId=$season%20and%20gameTypeId=$gameType", statsBaseUrl)
    }

    /**
     * Every club's season penalty totals for one season and game type, in a single response.
     *
     * The same stats-host table shape as [leagueSummary], with the same validation, because the two are joined
     * on the club. It is a separate request so a failure here cannot take the summary with it.
     */
    suspend fun leaguePenalties(
        season: String,
        gameType: Int = 2,
    ): ApiResult {
        require(SEASON_ID.matches(season)) { "Season must be eight digits, was '$season'" }
        require(gameType in 1..3) { "Game type must be 1, 2 or 3, was '$gameType'" }
        return get("/stats/rest/en/team/penalties?cayenneExp=seasonId=$season%20and%20gameTypeId=$gameType", statsBaseUrl)
    }

    /**
     * A player's landing page: identity, career and season totals.
     *
     * The id is checked before a request is built, so only a positive whole number can become part of the path.
     */
    suspend fun playerLanding(playerId: Long): ApiResult {
        require(playerId > 0) { "Player id must be positive, was '$playerId'" }
        return get("/v1/player/$playerId/landing")
    }

    /**
     * One player's game log for one season and game type: a row per game the player appeared in.
     *
     * The id and season are checked before a request is built, the same as the other season-scoped calls. The feed
     * does not sort the rows, so callers must not rely on their order.
     */
    suspend fun playerGameLog(
        playerId: Long,
        season: String,
        gameType: Int = 2,
    ): ApiResult {
        require(playerId > 0) { "Player id must be positive, was '$playerId'" }
        require(SEASON_ID.matches(season)) { "Season must be eight digits, was '$season'" }
        require(gameType in 1..3) { "Game type must be 1, 2 or 3, was '$gameType'" }
        return get("/v1/player/$playerId/game-log/$season/$gameType")
    }

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

    private suspend fun get(
        path: String,
        base: String = baseUrl,
    ): ApiResult =
        try {
            // prepareGet/execute rather than client.get: client.get buffers the whole body and
            // releases the connection before returning, so a large 5xx payload would be held in
            // memory twice for nothing. The streaming form lets a non-2xx body be discarded without
            // ever being materialised.
            client.prepareGet("${base.trimEnd('/')}$path").execute { response ->
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
