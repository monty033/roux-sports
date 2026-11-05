package net.montycasa.roux.data.repo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LeagueStatsRepositoryImplTest {
    private val payload = Fixtures.text(Fixtures.CLUB_STATS_CAPTURE, "league-team-summary-20262027.json").toByteArray()

    private val penaltiesPayload =
        Fixtures.text(Fixtures.LEAGUE_PENALTIES_CAPTURE, "league-team-penalties-20262027.json").toByteArray()

    private class Harness(
        val repository: LeagueStatsRepositoryImpl,
        val requests: List<String>,
        /** The encoded query of each request, in the same order as [requests]. */
        val queries: List<String>,
        val clock: MutableClock,
        val fileSystem: FakeFileSystem,
        /** The status each route answers with; a refresh that fails is the case the stale fallback exists for. */
        val status: Status,
    )

    private class Status {
        var summary: HttpStatusCode = HttpStatusCode.OK
        var penalties: HttpStatusCode = HttpStatusCode.OK
    }

    @Test
    fun theRequestsGoToTheStatsHostForTheSeasonAndRegularSeason() =
        runBlocking {
            val harness = harness()

            harness.repository.summary()

            assertEquals(setOf(SUMMARY_PATH, PENALTIES_PATH), harness.requests.toSet())
            assertEquals(2, harness.requests.size)
            harness.queries.forEach { query ->
                assertTrue("query was $query", query.contains("seasonId=20262027"))
                assertTrue("query was $query", query.contains("gameTypeId=2"))
            }
        }

    @Test
    fun aSecondReadInsideSixHoursDoesNotCallTheApi() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()

            harness.clock.advance(5.hours)
            harness.repository.summary()

            assertEquals(2, harness.requests.size)
        }

    @Test
    fun aReadPastSixHoursRefetchesBothTables() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()

            harness.clock.advance(6.hours + 1.minutes)
            harness.repository.summary()

            assertEquals(4, harness.requests.size)
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
            harness.status.summary = HttpStatusCode.InternalServerError
            val second = harness.repository.summary()

            assertEquals(4, harness.requests.size)
            assertEquals(32, second.data?.clubs?.size)
            assertTrue("freshness was ${second.freshness}", second.freshness is Freshness.Stale)
            assertEquals(LoadError.Http(500), second.error)
        }

    /**
     * The penalties table has its own entry beside the league table's, so neither can evict or overwrite the other.
     * The mutation that files penalties under the league kind leaves one entry where two are expected.
     */
    @Test
    fun theLeagueTableAndItsPenaltiesAreSeparateCacheEntries() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()

            val entries =
                harness.fileSystem
                    .list("/cache".toPath())
                    .map { it.name }
                    .filter { it.endsWith(".roux") }
                    .sorted()
            assertEquals(
                listOf("league-penalties-20262027-2.roux", "league-stats-20262027-2.roux"),
                entries,
            )
        }

    /** A penalties failure with no cached copy leaves the table fresh, with every penalty minute null and never zero. */
    @Test
    fun aPenaltiesFailureLeavesTheTableFreshAndItsMinutesNull() =
        runBlocking {
            val harness = harness()
            harness.status.penalties = HttpStatusCode.InternalServerError

            val loadable = harness.repository.summary()

            assertNull(loadable.error)
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertEquals(32, loadable.data?.clubs?.size)
            assertTrue(loadable.data!!.clubs.all { it.penaltyMinutes == null })
        }

    /** With the summary cached and the penalties request failing, the summary still answers fresh. */
    @Test
    fun aPenaltiesFailureDoesNotMarkTheSummaryStale() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()

            harness.clock.advance(7.hours)
            harness.status.penalties = HttpStatusCode.InternalServerError
            val loadable = harness.repository.summary()

            assertEquals(Freshness.Fresh, loadable.freshness)
            assertNull(loadable.error)
            assertEquals(32, loadable.data?.clubs?.size)
        }

    @Test
    fun aSuccessfulReadHasNoError() =
        runBlocking {
            val loadable = harness().repository.summary()

            assertNull(loadable.error)
            assertEquals(Freshness.Fresh, loadable.freshness)
        }

    /** Anaheim's penalty minutes are the penalties table's own figure, joined by id onto the summary line. */
    @Test
    fun aClubsPenaltyMinutesComeFromThePenaltiesTable() =
        runBlocking {
            val ana =
                harness()
                    .repository
                    .summary()
                    .data!!
                    .clubs
                    .first { it.teamName == "Anaheim Ducks" }

            assertEquals(24, ana.teamId)
            assertEquals(205, ana.penaltyMinutes)
        }

    private fun harness(clock: MutableClock = MutableClock()): Harness {
        val requests = CopyOnWriteArrayList<String>()
        val queries = CopyOnWriteArrayList<String>()
        val status = Status()
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                requests += path
                queries += request.url.encodedQuery
                val penalties = path == PENALTIES_PATH
                val code = if (penalties) status.penalties else status.summary
                respond(
                    if (code == HttpStatusCode.OK) (if (penalties) penaltiesPayload else payload) else ByteArray(0),
                    code,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val fileSystem = FakeFileSystem(clock)
        val cache = ResponseCache(fileSystem, DIRECTORY.toPath(), clock)
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(
            // The two loads start together, but the in-memory filesystem is not thread-safe, so the test runs their
            // I/O one at a time. Concurrency is still exercised: both async loads are launched before either is awaited.
            LeagueStatsRepositoryImpl(api, cache, clock, ioDispatcher = Dispatchers.IO.limitedParallelism(1)),
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
        const val SUMMARY_PATH = "/stats/rest/en/team/summary"
        const val PENALTIES_PATH = "/stats/rest/en/team/penalties"
    }
}
