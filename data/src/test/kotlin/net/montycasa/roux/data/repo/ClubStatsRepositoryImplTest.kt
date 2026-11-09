package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.TeamKey
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ClubStatsRepositoryImplTest {
    private val payload = Fixtures.clubStats("club-stats-ana-20262027.json").toByteArray()

    /** A well-formed reply with both groups present and no rows: a season that has not started for the club. */
    private val emptySeason = """{"skaters":[],"goalies":[]}""".toByteArray()

    private class Reply(
        val status: HttpStatusCode,
        val body: ByteArray,
    )

    private class Harness(
        val repository: ClubStatsRepositoryImpl,
        val requests: List<String>,
        val clock: MutableClock,
        val fileSystem: FakeFileSystem,
    )

    @Test
    fun theRequestUsesTheClubTheSeasonAndRegularSeasonGameType() =
        runBlocking {
            val harness = harness()

            harness.repository.stats(TeamKey("ANA"))

            assertEquals(listOf("/v1/club-stats/ANA/20262027/2"), harness.requests)
        }

    @Test
    fun aSecondReadInsideSixHoursDoesNotCallTheApi() =
        runBlocking {
            val harness = harness()
            harness.repository.stats(TeamKey("ANA"))

            harness.clock.advance(5.hours)
            harness.repository.stats(TeamKey("ANA"))

            assertEquals(1, harness.requests.size)
        }

    @Test
    fun aReadPastSixHoursRefetches() =
        runBlocking {
            val harness = harness()
            harness.repository.stats(TeamKey("ANA"))

            harness.clock.advance(6.hours + 1.minutes)
            harness.repository.stats(TeamKey("ANA"))

            assertEquals(2, harness.requests.size)
        }

    @Test
    fun theStatsEntryIsItsOwnCacheKind() =
        runBlocking {
            val harness = harness()
            harness.repository.stats(TeamKey("ANA"))

            assertEquals(listOf("club-stats-ANA-20262027.roux"), cacheEntries(harness))
        }

    @Test
    fun anEmptyPreSeasonRollsBackOnceToThePreviousSeason() =
        runBlocking {
            val harness =
                harness(clock = MutableClock("2026-07-15T12:00:00Z")) { path ->
                    if (path.contains("/20262027/")) Reply(HttpStatusCode.OK, emptySeason) else Reply(HttpStatusCode.OK, payload)
                }

            val read = harness.repository.stats(TeamKey("ANA"))

            assertEquals(
                listOf("/v1/club-stats/ANA/20262027/2", "/v1/club-stats/ANA/20252026/2"),
                harness.requests,
            )
            val stats = requireNotNull(read.data)
            assertEquals("the value names the season its numbers came from", "20252026", stats.season)
            assertTrue("the previous season's skaters are shown", stats.skaters.isNotEmpty())
            assertTrue("the previous season's goalies are shown", stats.goalies.isNotEmpty())
            assertNull(read.error)
        }

    @Test
    fun aMidSeasonPopulatedReadIsNotRolledBack() =
        runBlocking {
            val harness = harness(clock = MutableClock("2026-11-03T12:00:00Z"))

            val read = harness.repository.stats(TeamKey("ANA"))

            assertEquals(listOf("/v1/club-stats/ANA/20262027/2"), harness.requests)
            assertEquals("20262027", requireNotNull(read.data).season)
        }

    @Test
    fun anErrorOnTheCurrentSeasonDoesNotFallBack() =
        runBlocking {
            val harness =
                harness(clock = MutableClock("2026-07-15T12:00:00Z")) {
                    Reply(HttpStatusCode.NotFound, ByteArray(0))
                }

            val read = harness.repository.stats(TeamKey("ANA"))

            assertEquals("an HTTP error must not show last season's totals as current", 1, harness.requests.size)
            assertNull(read.data)
            assertEquals(LoadError.Http(status = 404), read.error)
        }

    @Test
    fun aReplyWithOneEmptyGroupBesideRowsDoesNotFallBack() =
        runBlocking {
            val oneGroupOnly =
                """{"skaters":[{"playerId":1,"firstName":{"default":"Ada"},"lastName":{"default":"Lovelace"}}],"goalies":[]}"""
            val harness =
                harness(clock = MutableClock("2026-07-15T12:00:00Z")) {
                    Reply(HttpStatusCode.OK, oneGroupOnly.toByteArray())
                }

            val read = harness.repository.stats(TeamKey("ANA"))

            assertEquals("a broken reply is not an empty season", 1, harness.requests.size)
            assertNull(read.data)
            assertEquals(LoadError.Parse, read.error)
        }

    @Test
    fun theFallbackIsStoredUnderItsOwnSeasonKey() =
        runBlocking {
            val harness =
                harness(clock = MutableClock("2026-07-15T12:00:00Z")) { path ->
                    if (path.contains("/20262027/")) Reply(HttpStatusCode.OK, emptySeason) else Reply(HttpStatusCode.OK, payload)
                }

            harness.repository.stats(TeamKey("ANA"))

            assertEquals(listOf("club-stats-ANA-20252026.roux"), cacheEntries(harness))
        }

    private fun cacheEntries(harness: Harness): List<String> =
        harness.fileSystem
            .list("/cache".toPath())
            .map { it.name }
            .filter { it.endsWith(".roux") }

    private fun harness(
        clock: MutableClock = MutableClock(),
        bodyFor: (String) -> Reply = { Reply(HttpStatusCode.OK, payload) },
    ): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                bodyFor(request.url.encodedPath).let { reply ->
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
            ClubStatsRepositoryImpl(api, cache, clock),
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
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
