package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.data.nhl.NhlAdapter
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.TeamKey
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ClubScheduleRepositoryImplTest {
    private val payload = Fixtures.clubSchedule("club-schedule-ana-20262027.json").toByteArray()

    /** Toronto's season that has games: the previous season on a pre-season day, and a populated current season mid-season. */
    private val seasonWithGames = Fixtures.rollover("club-schedule-TOR-20252026.json").toByteArray()

    /** Toronto's upcoming season before any game is scheduled: a well-formed reply with no games. */
    private val upcomingSeason = Fixtures.rollover("club-schedule-TOR-20272028-empty.json").toByteArray()

    private class Reply(
        val status: HttpStatusCode,
        val body: ByteArray,
    )

    private class Harness(
        val repository: ClubScheduleRepositoryImpl,
        val requests: List<String>,
        val clock: MutableClock,
        val fileSystem: FakeFileSystem,
    )

    @Test
    fun theRequestUsesTheClubAndTheSeasonDerivedFromTheClock() =
        runBlocking {
            val harness = harness()
            harness.clock.set("2026-06-30T12:00:00Z")

            harness.repository.schedule(TeamKey("ANA"))

            assertEquals(listOf("/v1/club-schedule-season/ANA/20252026"), harness.requests)
        }

    @Test
    fun aSecondReadInsideSixHoursDoesNotCallTheApi() =
        runBlocking {
            val harness = harness()
            harness.repository.schedule(TeamKey("ANA"))

            harness.clock.advance(5.hours)
            harness.repository.schedule(TeamKey("ANA"))

            assertEquals(1, harness.requests.size)
        }

    @Test
    fun aReadPastSixHoursRefetches() =
        runBlocking {
            val harness = harness()
            harness.repository.schedule(TeamKey("ANA"))

            harness.clock.advance(6.hours + 1.minutes)
            harness.repository.schedule(TeamKey("ANA"))

            assertEquals(2, harness.requests.size)
        }

    @Test
    fun aCachedScheduleIsReturnedAsStaleWhenTheNetworkFails() =
        runBlocking {
            var fail = false
            val harness =
                harness {
                    if (fail) throw IOException("offline") else null
                }
            val first = harness.repository.schedule(TeamKey("ANA"))

            fail = true
            harness.clock.advance(7.hours)
            val second = harness.repository.schedule(TeamKey("ANA"))

            assertEquals(first.data, second.data)
            assertTrue(second.freshness is Freshness.Stale)
            assertEquals(LoadError.Network, second.error)
        }

    @Test
    fun anEmptyPreSeasonRollsBackOnceToThePreviousSeason() =
        runBlocking {
            val harness = harness(clock = MutableClock("2026-07-15T12:00:00Z"), bodyFor = ::seasonWithoutGamesIsEmpty)

            val read = harness.repository.schedule(TeamKey("TOR"))

            assertEquals(
                listOf("/v1/club-schedule-season/TOR/20262027", "/v1/club-schedule-season/TOR/20252026"),
                harness.requests,
            )
            val schedule = requireNotNull(read.data)
            assertEquals("the value names the season its games came from", "20252026", schedule.season)
            assertTrue("the previous season's games are shown", schedule.games.isNotEmpty())
            assertNull(read.error)
        }

    @Test
    fun aCurrentSeasonWithGamesIsReturnedWithoutASecondRequest() =
        runBlocking {
            val harness = harness(clock = MutableClock("2026-11-03T12:00:00Z"), bodyFor = { Reply(HttpStatusCode.OK, seasonWithGames) })

            val read = harness.repository.schedule(TeamKey("TOR"))

            assertEquals(listOf("/v1/club-schedule-season/TOR/20262027"), harness.requests)
            assertEquals("20262027", requireNotNull(read.data).season)
        }

    @Test
    fun anErrorOnTheCurrentSeasonDoesNotFallBack() =
        runBlocking {
            val harness =
                harness(
                    clock = MutableClock("2026-07-15T12:00:00Z"),
                    bodyFor = { Reply(HttpStatusCode.NotFound, ByteArray(0)) },
                )

            val read = harness.repository.schedule(TeamKey("TOR"))

            assertEquals("an HTTP error must not ask for last season", 1, harness.requests.size)
            assertNull(read.data)
            assertEquals(LoadError.Http(status = 404), read.error)
        }

    @Test
    fun aMalformedCurrentSeasonDoesNotFallBack() =
        runBlocking {
            val harness =
                harness(
                    clock = MutableClock("2026-07-15T12:00:00Z"),
                    bodyFor = { Reply(HttpStatusCode.OK, "not json".toByteArray()) },
                )

            val read = harness.repository.schedule(TeamKey("TOR"))

            assertEquals("a parse failure must not be treated as an empty season", 1, harness.requests.size)
            assertNull(read.data)
            assertEquals(LoadError.Parse, read.error)
        }

    @Test
    fun aNetworkFailureOnTheCurrentSeasonDoesNotFallBack() =
        runBlocking {
            val harness =
                harness(clock = MutableClock("2026-07-15T12:00:00Z")) {
                    throw IOException("offline")
                }

            val read = harness.repository.schedule(TeamKey("TOR"))

            assertEquals(1, harness.requests.size)
            assertEquals(LoadError.Network, read.error)
        }

    @Test
    fun theFallbackIsCachedUnderItsOwnSeasonKeyAndServedFromThere() =
        runBlocking {
            val harness = harness(clock = MutableClock("2026-07-15T12:00:00Z"), bodyFor = ::seasonWithoutGamesIsEmpty)
            harness.repository.schedule(TeamKey("TOR"))

            val entries = cacheEntries(harness)
            assertEquals(listOf("club-schedule-TOR-20252026.roux"), entries)

            harness.clock.advance(1.hours)
            val again = harness.repository.schedule(TeamKey("TOR"))

            // The empty current season is never stored, so it is asked again; the fallback is answered from its own entry.
            assertEquals(
                listOf(
                    "/v1/club-schedule-season/TOR/20262027",
                    "/v1/club-schedule-season/TOR/20252026",
                    "/v1/club-schedule-season/TOR/20262027",
                ),
                harness.requests,
            )
            assertEquals("20252026", requireNotNull(again.data).season)
        }

    @Test
    fun aFallbackNeverOverwritesTheCurrentSeasonsEntry() =
        runBlocking {
            var currentIsEmpty = false
            val harness =
                harness(
                    clock = MutableClock("2026-11-03T12:00:00Z"),
                    bodyFor = { path ->
                        when {
                            !path.endsWith("20262027") -> Reply(HttpStatusCode.OK, seasonWithGames)
                            currentIsEmpty -> Reply(HttpStatusCode.OK, upcomingSeason)
                            else -> Reply(HttpStatusCode.OK, seasonWithGames)
                        }
                    },
                )
            // First read: the current season has games and is stored under its own key.
            harness.repository.schedule(TeamKey("TOR"))
            val currentFile = "/cache/club-schedule-TOR-20262027.roux".toPath()
            val before = harness.fileSystem.read(currentFile) { readByteString() }

            currentIsEmpty = true
            harness.clock.advance(7.hours)
            val read = harness.repository.schedule(TeamKey("TOR"))

            assertEquals("the page shows the season before", "20252026", requireNotNull(read.data).season)
            val after = harness.fileSystem.read(currentFile) { readByteString() }
            assertEquals("the current season's entry is the one it was, not the fallback", before, after)
        }

    private fun seasonWithoutGamesIsEmpty(path: String): Reply =
        if (path.endsWith("20262027")) Reply(HttpStatusCode.OK, upcomingSeason) else Reply(HttpStatusCode.OK, seasonWithGames)

    private fun cacheEntries(harness: Harness): List<String> =
        harness.fileSystem
            .list("/cache".toPath())
            .map { it.name }
            .filter { it.endsWith(".roux") }

    private fun harness(
        clock: MutableClock = MutableClock(),
        bodyFor: (String) -> Reply = { Reply(HttpStatusCode.OK, payload) },
        responder: (
            suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
                io.ktor.client.request.HttpRequestData,
            ) -> io.ktor.client.request.HttpResponseData?
        )? = null,
    ): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                responder?.invoke(this, request)
                    ?: bodyFor(request.url.encodedPath).let { reply ->
                        respond(
                            reply.body,
                            reply.status,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
            }
        val fileSystem = FakeFileSystem(clock)
        val cache = ResponseCache(fileSystem, DIRECTORY.toPath(), clock)
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(
            ClubScheduleRepositoryImpl(api, NhlAdapter, cache, clock, TimeZone.of("America/Toronto")),
            requests,
            clock,
            fileSystem,
        )
    }

    private class MutableClock(
        iso: String = "2026-11-03T12:00:00Z",
    ) : Clock {
        private var instant: Instant = Instant.parse(iso)

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }

        fun set(iso: String) {
            instant = Instant.parse(iso)
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
