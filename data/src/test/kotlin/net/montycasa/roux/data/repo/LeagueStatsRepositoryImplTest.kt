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
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
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

class LeagueStatsRepositoryImplTest {
    private val payload = Fixtures.text(Fixtures.CLUB_STATS_CAPTURE, "league-team-summary-20262027.json").toByteArray()

    private class Harness(
        val repository: LeagueStatsRepositoryImpl,
        val requests: List<String>,
        /** The encoded query of each request, in the same order as [requests]. */
        val queries: List<String>,
        val clock: MutableClock,
        val fileSystem: FakeFileSystem,
        /** The status the next answer carries; a refresh that fails is the case the stale fallback exists for. */
        val status: Status,
    )

    private class Status {
        var code: HttpStatusCode = HttpStatusCode.OK
    }

    @Test
    fun theRequestGoesToTheStatsHostForTheSeasonAndRegularSeason() =
        runBlocking {
            val harness = harness()

            harness.repository.summary()

            assertEquals(listOf("/stats/rest/en/team/summary"), harness.requests)
            val query = harness.queries.single()
            assertTrue("query was $query", query.contains("seasonId=20262027"))
            assertTrue("query was $query", query.contains("gameTypeId=2"))
        }

    @Test
    fun aSecondReadInsideSixHoursDoesNotCallTheApi() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()

            harness.clock.advance(5.hours)
            harness.repository.summary()

            assertEquals(1, harness.requests.size)
        }

    @Test
    fun aReadPastSixHoursRefetches() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()

            harness.clock.advance(6.hours + 1.minutes)
            harness.repository.summary()

            assertEquals(2, harness.requests.size)
        }

    /**
     * A refresh that fails returns the table already on disk, and says it is stale rather than
     * presenting a day-old answer as current.
     */
    @Test
    fun aFailedRefreshReturnsTheCachedTableMarkedStale() =
        runBlocking {
            val harness = harness()
            val first = harness.repository.summary()
            assertEquals(Freshness.Fresh, first.freshness)

            harness.clock.advance(7.hours)
            harness.status.code = HttpStatusCode.InternalServerError
            val second = harness.repository.summary()

            assertEquals(2, harness.requests.size)
            assertEquals(32, second.data?.clubs?.size)
            assertTrue("freshness was ${second.freshness}", second.freshness is Freshness.Stale)
            assertEquals(LoadError.Http(500), second.error)
        }

    @Test
    fun theLeagueTableIsItsOwnCacheKind() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()

            val entries =
                harness.fileSystem
                    .list("/cache".toPath())
                    .map { it.name }
                    .filter { it.endsWith(".roux") }
            assertEquals(listOf("league-stats-20262027-2.roux"), entries)
        }

    @Test
    fun aSuccessfulReadHasNoError() =
        runBlocking {
            val loadable = harness().repository.summary()

            assertNull(loadable.error)
            assertEquals(Freshness.Fresh, loadable.freshness)
        }

    private fun harness(clock: MutableClock = MutableClock()): Harness {
        val requests = mutableListOf<String>()
        val queries = mutableListOf<String>()
        val status = Status()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                queries += request.url.encodedQuery
                respond(
                    if (status.code == HttpStatusCode.OK) payload else ByteArray(0),
                    status.code,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val fileSystem = FakeFileSystem(clock)
        val cache = ResponseCache(fileSystem, DIRECTORY.toPath(), clock)
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(
            LeagueStatsRepositoryImpl(api, cache, clock),
            requests,
            queries,
            clock,
            fileSystem,
            status,
        )
    }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-03T12:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
