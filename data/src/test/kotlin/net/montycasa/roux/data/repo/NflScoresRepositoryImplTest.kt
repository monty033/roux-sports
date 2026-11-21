package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.EspnApi
import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.data.nfl.NflScoreboardAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The NFL cache decision is the whole behaviour under test: what is requested, what is written under which kind, and
 * what the caller is told when a request fails. Fixtures are the real captured ESPN responses.
 */
class NflScoresRepositoryImplTest {
    private val fixtureBytes = nflFixture("nfl-scoreboard-week-1.json")

    private class Harness(
        val repository: NflScoresRepositoryImpl,
        /** The `dates` query of every request the engine received, in order. */
        val requests: List<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
    )

    private fun nflFixture(name: String): ByteArray {
        val path = "/fx-nfl/$CAPTURE/$name"
        return NflScoresRepositoryImplTest::class.java
            .getResourceAsStream(path)
            ?.use { it.readBytes() }
            ?: error("Fixture resource not found: $path")
    }

    private fun harness(
        baseUrl: String = "https://${EspnHttpClientFactory.DATA_HOST}",
        files: (FileSystem) -> FileSystem = { delegate -> delegate },
        responder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null,
    ): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.parameters["dates"].orEmpty()
                responder?.invoke(this, request)
                    ?: respond(
                        fixtureBytes,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        val clock = MutableClock()
        val cache = ResponseCache(files(FakeFileSystem(clock)), DIRECTORY.toPath(), clock)
        // Through the factory, so the request boundary (429 handling, host allowlist) is part of the test.
        val api = EspnApi(client = EspnHttpClientFactory.create(engine, "test-version"), baseUrl = baseUrl)
        return Harness(
            NflScoresRepositoryImpl(api, cache, clock, Dispatchers.IO),
            requests,
            cache,
            clock,
        )
    }

    @Test
    fun aFreshCopyAnswersWithoutARequest() =
        runBlocking {
            val harness = harness()
            // A finished past slate: its copy never expires, so the answer must come from disk alone.
            harness.cache.put(CacheKey(CacheKind.NflScore, DAY), fixtureBytes, harness.clock.now())

            val loadable = harness.repository.loadDay(DAY, LATER)

            assertEquals("a fresh copy must not reach the network", emptyList<String>(), harness.requests)
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertNull(loadable.error)
            assertEquals(DAY, loadable.data?.date)
        }

    /**
     * A scheduled copy expires before its kickoff, not five minutes after it.
     *
     * The policy shortens the lifetime to the time left before the next start, minus its lead. A copy stamped ninety
     * seconds before kickoff therefore lives thirty seconds; a minute later it is stale and must be refetched. With no
     * next start it would take the five-minute idle lifetime and keep showing a scheduled row into the game.
     */
    @Test
    fun aScheduledCopyExpiresBeforeItsKickoffRatherThanAfterIt() =
        runBlocking {
            val scheduled = nflFixture("nfl-scoreboard-week-12.json")
            val parsed =
                NflScoreboardAdapter.parseScoreboard(scheduled.decodeToString(), SUNDAY) as ParseResult.Success
            val kickoff =
                parsed.value.games
                    .mapNotNull { it.startTimeUtc }
                    .filter { it > CLOCK_START }
                    .min()
            assertNotNull("the week-12 slate must carry a kickoff ahead of the clock", kickoff)
            val harness =
                harness { _ ->
                    respond(
                        scheduled,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            harness.clock.advance(kickoff!! - 90.seconds - harness.clock.now())
            harness.cache.put(CacheKey(CacheKind.NflScore, SUNDAY), scheduled, harness.clock.now())
            // Now fifty-nine seconds before kickoff: past the shortened lifetime, well inside the idle one.
            harness.clock.advance(31.seconds)

            harness.repository.loadDay(SUNDAY, SUNDAY)

            assertEquals(
                "a copy whose kickoff bound has passed must be refetched, not served as fresh",
                1,
                harness.requests.size,
            )
        }

    @Test
    fun aSuccessfulLoadWritesTheRawBytesUnderTheNflScoreKind() =
        runBlocking {
            val harness = harness()

            harness.repository.loadDay(DAY, LATER)

            val stored = harness.cache.get(CacheKey(CacheKind.NflScore, DAY))
            assertNotNull(stored)
            // The raw body the engine answered, not a re-encoding of the parsed model.
            assertArrayEquals(fixtureBytes, stored!!.bytes)
            assertNull(
                "an NFL day must never be filed under the NHL score kind",
                harness.cache.get(CacheKey(CacheKind.Score, DAY)),
            )
        }

    @Test
    fun aStaleCopyWithAServerErrorComesBackStaleAndDoesNotThrow() =
        runBlocking {
            var failing = false
            val harness =
                harness { _ ->
                    if (failing) respond("down", HttpStatusCode.InternalServerError) else null
                }
            // Today is the day itself, so the copy has the idle lifetime and goes stale after an hour.
            val first = harness.repository.loadDay(DAY, DAY)
            val fetchedAt = harness.clock.now()

            failing = true
            harness.clock.advance(1.hours)
            val second = harness.repository.loadDay(DAY, DAY)

            assertEquals(first.data, second.data)
            assertEquals(Freshness.Stale(since = fetchedAt), second.freshness)
            assertEquals(LoadError.Http(status = 500), second.error)
        }

    @Test
    fun aBlockedHostIsReportedAsBlocked() =
        runBlocking {
            val harness = harness(baseUrl = "https://api-web.nhle.com")

            val loadable = harness.repository.loadDay(DAY, LATER)

            assertEquals(LoadError.Blocked("api-web.nhle.com"), loadable.error)
            assertNull(loadable.data)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertTrue("the engine must never be asked for the blocked host", harness.requests.isEmpty())
        }

    @Test
    fun aRateLimitCarriesItsPause() =
        runBlocking {
            val harness =
                harness { _ ->
                    respond("slow down", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "30"))
                }

            val loadable = harness.repository.loadDay(DAY, LATER)

            assertEquals(LoadError.Http(status = 429, retryAfter = 30.seconds), loadable.error)
            assertNull(loadable.data)
        }

    @Test
    fun aDeadNetworkFallsBackToTheCachedDay() =
        runBlocking {
            var down = false
            val harness =
                harness { _ ->
                    if (down) throw IOException("offline") else null
                }
            val first = harness.repository.loadDay(DAY, DAY)
            val fetchedAt = harness.clock.now()

            down = true
            harness.clock.advance(1.hours)
            val second = harness.repository.loadDay(DAY, DAY)

            assertEquals("the cached slate must still be shown", first.data, second.data)
            assertEquals(Freshness.Stale(since = fetchedAt), second.freshness)
            assertEquals(LoadError.Network, second.error)
        }

    @Test
    fun aDeadNetworkWithNothingCachedReportsTheNetworkError() =
        runBlocking {
            val harness =
                harness { _ ->
                    throw IOException("offline")
                }

            val loadable = harness.repository.loadDay(DAY, LATER)

            assertNull(loadable.data)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertEquals(LoadError.Network, loadable.error)
        }

    @Test
    fun anUnparseableBodyIsReportedAsAParseError() =
        runBlocking {
            val harness =
                harness { _ ->
                    respond(
                        "{\"not\":\"a scoreboard\"}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val loadable = harness.repository.loadDay(DAY, LATER)

            assertEquals(LoadError.Parse, loadable.error)
            assertNull(loadable.data)
        }

    @Test
    fun refreshDayRequestsEvenWhenTheCopyIsFresh() =
        runBlocking {
            val harness = harness()
            // The first load fetches and writes the copy; with the day as today it is fresh for the idle lifetime.
            val loaded = harness.repository.loadDay(DAY, DAY)
            assertEquals(listOf(query(DAY)), harness.requests)
            assertEquals(Freshness.Fresh, loaded.freshness)

            val refreshed = harness.repository.refreshDay(DAY, DAY)

            assertEquals("a refresh must reach the network despite a fresh copy", 2, harness.requests.size)
            assertEquals(Freshness.Fresh, refreshed.freshness)
            assertNull(refreshed.error)
            assertEquals(DAY, refreshed.data?.date)
        }

    @Test
    fun aCacheThatCannotBeWrittenStillReturnsTheSlateAsLocal() =
        runBlocking {
            val harness = harness(files = { delegate -> NflWriteFailingFileSystem(delegate) })

            val loadable = harness.repository.loadDay(DAY, DAY)

            assertNotNull("a cache failure must not hide a good response", loadable.data)
            assertEquals(LoadError.Local, loadable.error)
            assertEquals(Freshness.Fresh, loadable.freshness)
        }

    /**
     * Speculative work promises it will not throw, and a cache write is one of the ways it could: a full disk reaches
     * `cache.put` on this path. The catch that keeps that promise is only load-bearing if a test can fail without it.
     */
    @Test
    fun prefetchAdjacentSwallowsACacheWriteFailure() =
        runBlocking {
            val harness = harness(files = { delegate -> NflWriteFailingFileSystem(delegate) })

            harness.repository.prefetchAdjacent(DAY, DAY)

            assertEquals("both neighbours are still attempted", 2, harness.requests.size)
        }

    @Test
    fun aCachedBodyThatNoLongerParsesIsTreatedAsAbsent() =
        runBlocking {
            val harness = harness()
            harness.cache.put(
                CacheKey(CacheKind.NflScore, DAY),
                "{\"stale\":\"schema\"}".toByteArray(),
                harness.clock.now(),
            )

            val result = harness.repository.loadDay(DAY, DAY)

            assertEquals("an unparseable cache must not block a fetch", 1, harness.requests.size)
            assertEquals(Freshness.Fresh, result.freshness)
            assertNotNull(result.data)
        }

    @Test
    fun cachedDayNeverRequests() =
        runBlocking {
            val harness = harness()

            assertNull("no copy means no day", harness.repository.cachedDay(DAY, LATER))
            assertTrue(harness.requests.isEmpty())

            harness.cache.put(CacheKey(CacheKind.NflScore, DAY), fixtureBytes, harness.clock.now())

            assertEquals(DAY, harness.repository.cachedDay(DAY, LATER)?.date)
            assertTrue("reading a copy must not request", harness.requests.isEmpty())
        }

    @Test
    fun prefetchAdjacentFetchesOnlyTheDaysWithoutAFreshCopyAndSwallowsFailures() =
        runBlocking {
            val harness = harness { _ -> throw IOException("offline") }
            val now = harness.clock.now()
            // The day before is finished and past, so its copy never expires: fresh.
            harness.cache.put(CacheKey(CacheKind.NflScore, PREVIOUS_DAY), fixtureBytes, now)
            // The day after is a future day with a six-hour lifetime, and this copy is seven hours old: stale.
            harness.cache.put(CacheKey(CacheKind.NflScore, NEXT_DAY), fixtureBytes, now - 7.hours)

            // Completing without throwing is the assertion: a failed neighbour must not escape.
            harness.repository.prefetchAdjacent(DAY, DAY)

            assertEquals(listOf(query(NEXT_DAY)), harness.requests)
        }

    @Test
    fun anNflLoadLeavesTheNhlEntryForTheSameDateByteIdentical() =
        runBlocking {
            val harness = harness()
            val nhlBytes = "{\"nhl\":\"entry\"}".toByteArray()
            harness.cache.put(CacheKey(CacheKind.Score, DAY), nhlBytes, harness.clock.now() - 2.hours)
            val nhlBefore = requireNotNull(harness.cache.get(CacheKey(CacheKind.Score, DAY)))

            harness.repository.loadDay(DAY, LATER)

            assertEquals(listOf(query(DAY)), harness.requests)
            val nhlAfter = requireNotNull(harness.cache.get(CacheKey(CacheKind.Score, DAY)))
            assertArrayEquals(nhlBytes, nhlAfter.bytes)
            assertEquals(nhlBefore.fetchedAt, nhlAfter.fetchedAt)
            assertArrayEquals(fixtureBytes, requireNotNull(harness.cache.get(CacheKey(CacheKind.NflScore, DAY))).bytes)
        }

    @Test
    fun anNhlWriteLeavesTheNflEntryForTheSameDateByteIdentical() =
        runBlocking {
            val harness = harness()
            harness.repository.loadDay(DAY, LATER)
            val nflBefore = requireNotNull(harness.cache.get(CacheKey(CacheKind.NflScore, DAY)))

            harness.cache.put(CacheKey(CacheKind.Score, DAY), "{\"nhl\":\"later\"}".toByteArray(), harness.clock.now())

            val nflAfter = requireNotNull(harness.cache.get(CacheKey(CacheKind.NflScore, DAY)))
            assertArrayEquals(nflBefore.bytes, nflAfter.bytes)
            assertEquals(nflBefore.fetchedAt, nflAfter.fetchedAt)
            assertEquals(DAY, harness.repository.cachedDay(DAY, LATER)?.date)
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-21T12:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val CAPTURE = "2026-11-18"
        const val DIRECTORY = "/cache"

        /** The week-1 slate: sixteen games, every one final, so on a past day its copy never expires. */
        const val DAY = "2026-09-10"
        const val PREVIOUS_DAY = "2026-09-09"
        const val NEXT_DAY = "2026-09-11"

        /** A date after [DAY], so [DAY] is a finished past slate. */
        const val LATER = "2026-11-21"

        /** A Sunday in week 12, whose captured slate is entirely scheduled. */
        const val SUNDAY = "2026-11-22"

        /** What [MutableClock] starts at, so a kickoff ahead of it can be chosen. */
        val CLOCK_START: Instant = Instant.parse("2026-11-21T12:00:00Z")

        /** The compact `dates` query ESPN takes for an ISO date. */
        fun query(date: String): String = date.replace("-", "")
    }
}

/** Delegates everything to a real file system but refuses to replace a file, which is what a full disk looks like here. */
private class NflWriteFailingFileSystem(
    delegate: FileSystem,
) : ForwardingFileSystem(delegate) {
    override fun atomicMove(
        source: Path,
        target: Path,
    ): Unit = throw IOException("disk full")
}
