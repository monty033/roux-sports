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
import java.io.IOException
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

    /** The 2027-28 capture before any club has a line: a well-formed reply with no rows, used for the requested season. */
    private val noClubsYet = Fixtures.bytes(Fixtures.ROLLOVER_CAPTURE, "league-summary-20272028.json")

    /** The 2025-26 table and penalties, the season before the requested one, which has every club's line. */
    private val previousTable = Fixtures.bytes(Fixtures.ROLLOVER_CAPTURE, "league-summary-20252026.json")
    private val previousPenalties = Fixtures.bytes(Fixtures.ROLLOVER_CAPTURE, "league-penalties-20252026.json")

    private class Harness(
        val repository: LeagueStatsRepositoryImpl,
        val requests: List<String>,
        /** The encoded query of each request, in the same order as [requests]. */
        val queries: List<String>,
        val clock: MutableClock,
        val fileSystem: FakeFileSystem,
        /** The status each route answers with; a refresh that fails is the case the stale fallback exists for. */
        val status: Status,
        /** Replies for one endpoint and season, keyed by [replyKey]. They win over [status] and the default payloads. */
        val replies: MutableMap<String, Reply>,
    )

    private class Status {
        var summary: HttpStatusCode = HttpStatusCode.OK
        var penalties: HttpStatusCode = HttpStatusCode.OK
    }

    private class Reply(
        val status: HttpStatusCode,
        val body: ByteArray,
        /** Thrown by the mock engine instead of answering, to model a request that never got a reply. */
        val throws: Throwable? = null,
    )

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

            assertEquals(
                listOf("league-penalties-20262027-2.roux", "league-stats-20262027-2.roux"),
                cacheEntries(harness),
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

    @Test
    fun aCurrentSeasonWithClubsIsReturnedWithoutASecondRequest() =
        runBlocking {
            val harness = harness()

            val loadable = harness.repository.summary()

            assertEquals(listOf("20262027"), harness.fetched(SUMMARY_PATH))
            assertEquals(listOf("20262027"), harness.fetched(PENALTIES_PATH))
            assertEquals("20262027", loadable.data?.season)
        }

    /**
     * On a pre-season day the requested season has no clubs yet, so the table rolls back once to the season before.
     * The penalties must then be the previous season's too: they join onto the table club by club, so a 2025-26 table
     * with 2026-27 penalties would print the wrong minutes. Anaheim's figure is the one that tells them apart.
     */
    @Test
    fun anEmptyPreSeasonTableRollsBackOnceAndItsPenaltiesFollowTheSameSeason() =
        runBlocking {
            val harness = harness(clock = MutableClock("2026-07-15T12:00:00Z"))
            harness.replies[replyKey(SUMMARY_PATH, "20262027")] = Reply(HttpStatusCode.OK, noClubsYet)
            harness.replies[replyKey(SUMMARY_PATH, "20252026")] = Reply(HttpStatusCode.OK, previousTable)
            harness.replies[replyKey(PENALTIES_PATH, "20252026")] = Reply(HttpStatusCode.OK, previousPenalties)

            val loadable = harness.repository.summary()

            assertEquals(listOf("20262027", "20252026"), harness.fetched(SUMMARY_PATH))
            assertEquals("the penalties follow the season the table settled on", listOf("20252026"), harness.fetched(PENALTIES_PATH))
            assertEquals("20252026", loadable.data?.season)
            assertEquals(32, loadable.data?.clubs?.size)
            assertNull(loadable.error)
            assertEquals(Freshness.Fresh, loadable.freshness)
            val ana = loadable.data!!.clubs.first { it.teamName == "Anaheim Ducks" }
            assertEquals("the 2025-26 penalties, not 2026-27's 205", 814, ana.penaltyMinutes)
        }

    /** The fallback is stored under its own season key, so the next read is served from it. */
    @Test
    fun theFallbackIsCachedUnderItsOwnSeasonKeys() =
        runBlocking {
            val harness = harness(clock = MutableClock("2026-07-15T12:00:00Z"))
            harness.replies[replyKey(SUMMARY_PATH, "20262027")] = Reply(HttpStatusCode.OK, noClubsYet)
            harness.replies[replyKey(SUMMARY_PATH, "20252026")] = Reply(HttpStatusCode.OK, previousTable)
            harness.replies[replyKey(PENALTIES_PATH, "20252026")] = Reply(HttpStatusCode.OK, previousPenalties)

            harness.repository.summary()

            assertEquals(
                listOf("league-penalties-20252026-2.roux", "league-stats-20252026-2.roux"),
                cacheEntries(harness),
            )
        }

    /**
     * The fallback is cached beside the current season's entries, not over them. The current table was stored on an
     * earlier read; a later read whose current season comes back empty must leave that entry as it was.
     */
    @Test
    fun aFallbackNeverOverwritesTheCurrentSeasonsEntries() =
        runBlocking {
            val harness = harness()
            harness.repository.summary()
            val currentTable = "/cache/league-stats-20262027-2.roux".toPath()
            val currentPenalties = "/cache/league-penalties-20262027-2.roux".toPath()
            val tableBefore = harness.fileSystem.read(currentTable) { readByteString() }
            val penaltiesBefore = harness.fileSystem.read(currentPenalties) { readByteString() }

            harness.clock.advance(7.hours)
            harness.replies[replyKey(SUMMARY_PATH, "20262027")] = Reply(HttpStatusCode.OK, noClubsYet)
            harness.replies[replyKey(SUMMARY_PATH, "20252026")] = Reply(HttpStatusCode.OK, previousTable)
            harness.replies[replyKey(PENALTIES_PATH, "20252026")] = Reply(HttpStatusCode.OK, previousPenalties)
            val loadable = harness.repository.summary()

            assertEquals("the page shows the season before", "20252026", loadable.data?.season)
            assertEquals(tableBefore, harness.fileSystem.read(currentTable) { readByteString() })
            assertEquals(penaltiesBefore, harness.fileSystem.read(currentPenalties) { readByteString() })
        }

    /** A rollback is one step: when the season before has no clubs either, that empty answer is shown, not a third season. */
    @Test
    fun anEmptyPreviousSeasonIsShownAndNotRolledBackAgain() =
        runBlocking {
            val harness = harness(clock = MutableClock("2026-07-15T12:00:00Z"))
            harness.replies[replyKey(SUMMARY_PATH, "20262027")] = Reply(HttpStatusCode.OK, noClubsYet)
            harness.replies[replyKey(SUMMARY_PATH, "20252026")] = Reply(HttpStatusCode.OK, noClubsYet)

            val loadable = harness.repository.summary()

            assertEquals(listOf("20262027", "20252026"), harness.fetched(SUMMARY_PATH))
            assertNull(loadable.data)
            assertEquals(LoadError.Parse, loadable.error)
        }

    /** An HTTP error on the current season is a failure to read, not an empty season, so it never asks for last season. */
    @Test
    fun anErrorOnTheCurrentSeasonDoesNotRollBack() =
        runBlocking {
            val harness = harness()
            harness.status.summary = HttpStatusCode.InternalServerError

            val loadable = harness.repository.summary()

            assertEquals(listOf("20262027"), harness.fetched(SUMMARY_PATH))
            assertEquals(listOf("20262027"), harness.fetched(PENALTIES_PATH))
            assertNull(loadable.data)
            assertEquals(LoadError.Http(500), loadable.error)
        }

    @Test
    fun aNetworkFailureOnTheCurrentSeasonDoesNotRollBack() =
        runBlocking {
            val harness = harness()
            harness.replies[replyKey(SUMMARY_PATH, "20262027")] =
                Reply(HttpStatusCode.OK, ByteArray(0), throws = IOException("offline"))

            val loadable = harness.repository.summary()

            assertEquals(listOf("20262027"), harness.fetched(SUMMARY_PATH))
            assertEquals(LoadError.Network, loadable.error)
        }

    @Test
    fun aMalformedCurrentSeasonDoesNotRollBack() =
        runBlocking {
            val harness = harness()
            harness.replies[replyKey(SUMMARY_PATH, "20262027")] =
                Reply(HttpStatusCode.OK, "<html>maintenance</html>".toByteArray())

            val loadable = harness.repository.summary()

            assertEquals("a parse failure is not an empty season", listOf("20262027"), harness.fetched(SUMMARY_PATH))
            assertEquals(LoadError.Parse, loadable.error)
        }

    private fun cacheEntries(harness: Harness): List<String> =
        harness.fileSystem
            .list("/cache".toPath())
            .map { it.name }
            .filter { it.endsWith(".roux") }
            .sorted()

    private fun harness(clock: MutableClock = MutableClock()): Harness {
        val requests = CopyOnWriteArrayList<String>()
        val queries = CopyOnWriteArrayList<String>()
        val status = Status()
        val replies = mutableMapOf<String, Reply>()
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                val season = SEASON_IN_QUERY.find(request.url.encodedQuery)!!.groupValues[1]
                requests += path
                queries += request.url.encodedQuery
                val reply = replies[replyKey(path, season)] ?: defaultReply(path, status)
                reply.throws?.let { throw it }
                respond(
                    reply.body,
                    reply.status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val fileSystem = FakeFileSystem(clock)
        val cache = ResponseCache(fileSystem, DIRECTORY.toPath(), clock)
        val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
        return Harness(
            // The two loads run in turn, because the in-memory filesystem is not thread-safe, so the test runs their
            // I/O one at a time. The penalties are read after the table, so there is no overlap to exercise here.
            LeagueStatsRepositoryImpl(api, cache, clock, ioDispatcher = Dispatchers.IO.limitedParallelism(1)),
            requests,
            queries,
            clock,
            fileSystem,
            status,
            replies,
        )
    }

    /** The reply for an endpoint that has no override: the status the test set, and the 2026-27 capture when it is OK. */
    private fun defaultReply(
        path: String,
        status: Status,
    ): Reply {
        val penalties = path == PENALTIES_PATH
        val code = if (penalties) status.penalties else status.summary
        val body = if (code == HttpStatusCode.OK) (if (penalties) penaltiesPayload else payload) else ByteArray(0)
        return Reply(code, body)
    }

    /** The season each request for [path] asked for, in the order the requests were made. */
    private fun Harness.fetched(path: String): List<String> =
        requests.zip(queries).filter { it.first == path }.map { SEASON_IN_QUERY.find(it.second)!!.groupValues[1] }

    private fun replyKey(
        path: String,
        season: String,
    ) = "$path $season"

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
        const val SUMMARY_PATH = "/stats/rest/en/team/summary"
        const val PENALTIES_PATH = "/stats/rest/en/team/penalties"
        val SEASON_IN_QUERY = Regex("seasonId=(\\d{8})")
    }
}
