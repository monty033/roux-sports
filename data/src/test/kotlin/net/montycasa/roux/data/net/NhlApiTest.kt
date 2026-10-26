package net.montycasa.roux.data.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class NhlApiTest {
    @Test
    fun standingsUsesExactDatedPath() = assertPath("/v1/standings/2026-10-25") { standings("2026-10-25") }

    @Test
    fun scoreUsesExactDatedPath() = assertPath("/v1/score/2026-10-25") { score("2026-10-25") }

    @Test
    fun seasonsUsesExactPath() = assertPath("/v1/standings-season") { seasons() }

    @Test
    fun gamecenterLandingUsesExactPath() = assertPath("/v1/gamecenter/2026020123/landing") { gamecenterLanding(2026020123L) }

    @Test
    fun gamecenterBoxscoreUsesExactPath() = assertPath("/v1/gamecenter/2026020123/boxscore") { gamecenterBoxscore(2026020123L) }

    @Test
    fun gamecenterRightRailUsesExactPath() = assertPath("/v1/gamecenter/2026020123/right-rail") { gamecenterRightRail(2026020123L) }

    @Test
    fun everyPublicMethodAvoidsNowPath() =
        withResponse { api, engine ->
            api.score("2026-10-25")
            api.standings("2026-10-25")
            api.seasons()
            api.gamecenterLanding(2026020123L)
            api.gamecenterBoxscore(2026020123L)
            api.gamecenterRightRail(2026020123L)

            val paths = engine.requestHistory.map { it.url.encodedPath }
            assertEquals(6, paths.size)
            assertTrue(paths.toString(), paths.none { it.contains("now") })
        }

    /**
     * The undated form is unreachable by construction: the date argument is validated, so
     * `standings("now")` never builds a URL at all. This is the guarantee a source scan cannot
     * give, because a scan cannot see a route assembled at runtime from parameters.
     */
    @Test
    fun undatedStandingsArgumentIsRejectedBeforeAnyRequest() =
        withResponse { api, engine ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.standings("now") }
            }

            assertTrue("No request may be built for a rejected date", engine.requestHistory.isEmpty())
        }

    @Test
    fun undatedScoreArgumentIsRejectedBeforeAnyRequest() =
        withResponse { api, engine ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.score("today") }
            }

            assertTrue("No request may be built for a rejected date", engine.requestHistory.isEmpty())
        }

    @Test
    fun dateNeedsFullIsoShapeNotJustDigits() =
        withResponse { api, engine ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.standings("2026-10-2") }
            }
            assertTrue(engine.requestHistory.isEmpty())
        }

    /**
     * Defence in depth on top of the validation above: catches a reintroduced literal anywhere in
     * production sources, including one assembled by concatenation, and including one in a file
     * other than this one.
     *
     * Uses [scanStringLiterals] rather than regex passes. Two naive versions of this check were
     * written and both were wrong, each in a way that made the test pass while the route was
     * reachable: stripping comments first deleted the tail of `"https://api-web.nhle.com"` because
     * the URL contains `//`, which orphaned a quote and mispaired every later literal; and plain
     * substring matching flagged the KDoc that explains why the undated form is refused.
     */
    @Test
    fun noProductionSourceConstructsUndatedStandingsPath() {
        val root = File(System.getProperty("roux.rootDir"))
        val sources =
            listOf("data/src/main", "domain/src/main", "app/src/main")
                .map { File(root, it) }
                .filter { it.exists() }
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

        assertTrue("Expected to scan production sources, found none", sources.isNotEmpty())

        val offenders = sources.filter { file -> containsUndatedStandings(file.readText()) }

        assertFalse("Undated standings literal found in $offenders", offenders.isNotEmpty())
    }

    /**
     * The detector itself is load-bearing, so it is tested rather than trusted. Every case here is
     * one that broke a previous version of the check.
     */
    @Test
    fun undatedStandingsDetectorCatchesConcatenationAndIgnoresProse() {
        assertTrue(
            "a plain literal must be caught",
            containsUndatedStandings("""private val u = "/v1/standings/now""""),
        )
        assertTrue(
            "two halves joined by + must be caught",
            containsUndatedStandings("""private val u = "/v1/standings/" + "now""""),
        )
        assertTrue(
            "a line break inside the concatenation must be caught",
            containsUndatedStandings("""val u = "/v1/standings/" +${"\n"}        "now""""),
        )
        assertFalse(
            "prose in a comment must not be caught",
            containsUndatedStandings("// never request /v1/standings/now, it is undated"),
        )
        assertFalse(
            "a KDoc explanation must not be caught",
            containsUndatedStandings("/** Refuses /v1/standings/now. */"),
        )
        assertFalse(
            "a URL literal must not confuse the scanner",
            containsUndatedStandings("""val base = "https://api-web.nhle.com"; val s = "/v1/standings-season""""),
        )
        assertTrue(
            "a URL literal followed by the real route must still be caught",
            containsUndatedStandings("""val base = "https://api-web.nhle.com"; val u = "/v1/standings/now""""),
        )
    }

    @Test
    fun successPreservesBytesExactly() {
        // Bytes that do not survive a decode/encode round trip: Latin-1 text plus a lone 0xFF,
        // which is not valid UTF-8 at all. A text-based capture would rewrite both.
        val raw =
            byteArrayOf(
                0x7B,
                0x22,
                0x6E,
                0x22,
                0x3A,
                0x22,
                0x4D,
                0xE9.toByte(),
                0x78,
                0x69,
                0x63,
                0x6F,
                0x22,
                0x7D,
                0xFF.toByte(),
            )

        withResponseBytes(raw) { api, _ ->
            val result = api.score("2026-10-25")
            assertTrue("Expected Success, got $result", result is ApiResult.Success)
            assertArrayEquals(raw, (result as ApiResult.Success).bytes)
        }
    }

    @Test
    fun successBodyDecodesToTheReceivedText() =
        withResponse(body = """{"unknownField":[1,2],"team":"Montréal"}""") { api, _ ->
            val result = api.score("2026-10-25")
            assertEquals(
                """{"unknownField":[1,2],"team":"Montréal"}""",
                (result as ApiResult.Success).bytes.decodeToString(),
            )
        }

    @Test
    fun serviceUnavailableReturnsHttpError503() =
        withResponse(status = HttpStatusCode.ServiceUnavailable) { api, _ ->
            assertEquals(ApiResult.HttpError(503), api.score("2026-10-25"))
        }

    @Test
    fun notFoundReturnsHttpError404() =
        withResponse(status = HttpStatusCode.NotFound) { api, _ ->
            assertEquals(ApiResult.HttpError(404), api.score("2026-10-25"))
        }

    @Test
    fun ioExceptionReturnsNetworkError() =
        runBlocking {
            MockEngine { throw IOException("Connection lost") }.use { engine ->
                NhlHttpClientFactory.create(engine, "test-version").use { client ->
                    assertEquals(ApiResult.NetworkError, NhlApi(client).score("2026-10-25"))
                }
            }
        }

    @Test
    fun blockedHostPropagatesHostNotAllowedException() {
        MockEngine { respond("", HttpStatusCode.OK) }.use { engine ->
            NhlHttpClientFactory.create(engine, "test-version").use { client ->
                val api = NhlApi(client, baseUrl = "https://example.com")
                val exception =
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { api.score("2026-10-25") }
                    }

                assertEquals("example.com", exception.host)
                assertTrue("A blocked host must never reach the engine", engine.requestHistory.isEmpty())
            }
        }
    }

    @Test
    fun rateLimitPropagatesRateLimitedWithDelay() =
        withResponse(
            status = HttpStatusCode.TooManyRequests,
            headers = headersOf(HttpHeaders.RetryAfter, "120"),
        ) { api, _ ->
            val exception =
                assertThrows(RateLimited::class.java) {
                    runBlocking { api.score("2026-10-25") }
                }

            assertEquals(120.seconds, exception.retryAfter)
        }

    private fun assertPath(
        expected: String,
        request: suspend NhlApi.() -> ApiResult,
    ) = withResponse { api, engine ->
        val result = api.request()
        assertTrue("Expected Success, got $result", result is ApiResult.Success)
        val url = engine.requestHistory.single().url
        assertEquals(expected, url.encodedPath)
        assertEquals("api-web.nhle.com", url.host)
    }

    private fun withResponse(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Headers = headersOf(),
        block: suspend (NhlApi, MockEngine) -> Unit,
    ) = runBlocking {
        MockEngine { respond(body, status, headers) }.use { engine ->
            NhlHttpClientFactory.create(engine, "test-version").use { client ->
                block(NhlApi(client), engine)
            }
        }
    }

    private fun withResponseBytes(
        body: ByteArray,
        block: suspend (NhlApi, MockEngine) -> Unit,
    ) = runBlocking {
        MockEngine { respond(body, HttpStatusCode.OK) }.use { engine ->
            NhlHttpClientFactory.create(engine, "test-version").use { client ->
                block(NhlApi(client), engine)
            }
        }
    }

    private companion object {
        const val UNDATED_STANDINGS = "standings/now"
        const val TRIPLE_QUOTE = "\"\"\""
    }

    /**
     * True when [source] can build an undated standings route from its string literals, including
     * one split across a `+` concatenation.
     *
     * A scanner rather than regex passes, because comments and literals interleave and each can
     * contain the other's delimiters: a URL literal contains `//`, and a comment can contain a
     * quoted route. Only a left-to-right pass that tracks which state it is in gets both right.
     */
    private fun containsUndatedStandings(source: String): Boolean {
        val literals = scanStringLiterals(source)
        var joined = StringBuilder()
        var previousEnd = -1

        for (literal in literals) {
            if (joined.isNotEmpty()) {
                val gap = source.substring(previousEnd + 1, literal.start).trim()
                if (gap != "+") {
                    if (isUndatedStandingsRoute(joined.toString())) return true
                    joined = StringBuilder()
                }
            }
            joined.append(literal.text)
            previousEnd = literal.end
        }
        return isUndatedStandingsRoute(joined.toString())
    }

    private fun isUndatedStandingsRoute(text: String): Boolean = text.filterNot(Char::isWhitespace).contains(UNDATED_STANDINGS)

    private class Literal(
        val text: String,
        val start: Int,
        val end: Int,
    )

    private fun scanStringLiterals(source: String): List<Literal> {
        val literals = mutableListOf<Literal>()
        val length = source.length
        var index = 0

        while (index < length) {
            when {
                source.startsWith("//", index) -> {
                    while (index < length && source[index] != '\n') index++
                }

                source.startsWith("/*", index) -> {
                    index += 2
                    while (index < length && !source.startsWith("*/", index)) index++
                    index = minOf(index + 2, length)
                }

                source.startsWith(TRIPLE_QUOTE, index) -> {
                    val start = index
                    index += TRIPLE_QUOTE.length
                    val close = source.indexOf(TRIPLE_QUOTE, index)
                    val contentEnd = if (close < 0) length else close
                    literals += Literal(source.substring(index, contentEnd), start, minOf(contentEnd + 2, length - 1))
                    index = minOf(contentEnd + TRIPLE_QUOTE.length, length)
                }

                source[index] == '"' -> {
                    val start = index
                    val text = StringBuilder()
                    index++
                    while (index < length && source[index] != '"') {
                        if (source[index] == '\\' && index + 1 < length) {
                            text.append(source[index + 1])
                            index += 2
                        } else {
                            text.append(source[index])
                            index++
                        }
                    }
                    val end = minOf(index, length - 1)
                    index++
                    literals += Literal(text.toString(), start, end)
                }

                else -> {
                    index++
                }
            }
        }
        return literals
    }
}
