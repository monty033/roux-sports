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
import kotlin.time.Instant

/**
 * What the repository decides: which requests are made, what a day-old cache is worth, and how the clubs land under
 * the feed's division names. Every payload is a real capture.
 */
class NflTeamsRepositoryImplTest {
    private val teamsBytes = fixture("2026-11-18", "nfl-teams.json")
    private val afcBytes = fixture("2026-11-24", "nfl-standings-divisions-afc.json")
    private val nfcBytes = fixture("2026-11-24", "nfl-standings-divisions-nfc.json")

    private class Harness(
        val repository: NflTeamsRepositoryImpl,
        /** Every request the engine received, as path plus group where there was one, in order. */
        val requests: List<String>,
        val cache: ResponseCache,
        val clock: MutableClock,
    )

    private fun fixture(
        capture: String,
        name: String,
    ): ByteArray {
        val path = "/fx-nfl/$capture/$name"
        return NflTeamsRepositoryImplTest::class.java
            .getResourceAsStream(path)
            ?.use { it.readBytes() }
            ?: error("Fixture resource not found: $path")
    }

    /** [responder] sees only the request it is asked about, and answers null to take the captured payload. */
    private fun harness(responder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null): Harness {
        // The two conferences are asked for together, so the engine is entered from two threads at once.
        val requests = CopyOnWriteArrayList<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath.substringAfterLast("/") + (request.url.parameters["group"]?.let { "?$it" } ?: "")
                responder?.invoke(this, request)
                    ?: respond(
                        when {
                            request.url.encodedPath.endsWith("/teams") -> teamsBytes
                            request.url.parameters["group"] == "8" -> afcBytes
                            request.url.parameters["group"] == "7" -> nfcBytes
                            else -> error("unexpected request ${request.url}")
                        },
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        val clock = MutableClock()
        val cache = ResponseCache(FakeFileSystem(clock), DIRECTORY.toPath(), clock)
        val api = EspnApi(client = EspnHttpClientFactory.create(engine, "test-version"))
        val standings = NflStandingsRepositoryImpl(api, cache, clock, Dispatchers.IO)
        return Harness(NflTeamsRepositoryImpl(api, cache, standings, clock, Dispatchers.IO), requests, cache, clock)
    }

    @Test
    fun theClubsAreTheThirtyTwoOfThePayloadInItsOrder() =
        runBlocking {
            val loadable = harness().repository.teams()

            assertEquals(Freshness.Fresh, loadable.freshness)
            assertNull(loadable.error)
            assertEquals(32, loadable.data?.size)
            assertEquals(
                "ARI",
                loadable.data
                    ?.first()
                    ?.team
                    ?.abbrev,
            )
        }

    @Test
    fun aClubListFromTheLastDayAnswersWithoutARequest() =
        runBlocking {
            val harness = harness()
            harness.cache.put(CacheKey(CacheKind.NflTeams, "teams"), teamsBytes, harness.clock.now())
            harness.clock.advance(23.hours)

            val loadable = harness.repository.teams()

            assertEquals("a day is the window the clubs are trusted for", emptyList<String>(), harness.requests)
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertEquals(32, loadable.data?.size)
        }

    @Test
    fun aClubListOlderThanADayIsRefetched() =
        runBlocking {
            val harness = harness()
            harness.cache.put(CacheKey(CacheKind.NflTeams, "teams"), teamsBytes, harness.clock.now())
            harness.clock.advance(25.hours)

            val loadable = harness.repository.teams()

            assertEquals(listOf("teams"), harness.requests)
            assertEquals(Freshness.Fresh, loadable.freshness)
        }

    @Test
    fun theClubsAreFiledUnderTheirOwnCacheKind() =
        runBlocking {
            val harness = harness()

            harness.repository.teams()

            assertTrue(harness.cache.get(CacheKey(CacheKind.NflTeams, "teams")) != null)
            assertNull("nothing of the club list may land among the tables", harness.cache.get(CacheKey(CacheKind.NflStandings, "teams")))
        }

    @Test
    fun aFailedRequestFallsBackToTheCachedClubs() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }
            harness.cache.put(CacheKey(CacheKind.NflTeams, "teams"), teamsBytes, harness.clock.now())
            harness.clock.advance(25.hours)

            val loadable = harness.repository.teams()

            assertEquals(LoadError.Http(500), loadable.error)
            assertTrue("the cached clubs must still be served: ${loadable.freshness}", loadable.freshness is Freshness.Stale)
            assertEquals(32, loadable.data?.size)
        }

    @Test
    fun aFailureWithNothingCachedReportsTheErrorAndNoClubs() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }

            val loadable = harness.repository.teams()

            assertEquals(LoadError.Http(500), loadable.error)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertTrue(loadable.data.isNullOrEmpty())
        }

    @Test
    fun aCachedBodyThatNoLongerParsesIsTreatedAsAbsent() =
        runBlocking {
            val harness = harness()
            harness.cache.put(CacheKey(CacheKind.NflTeams, "teams"), """{"sports":"not a list"}""".toByteArray(), harness.clock.now())

            val loadable = harness.repository.teams()

            assertEquals(listOf("teams"), harness.requests)
            assertEquals(32, loadable.data?.size)
        }

    @Test
    fun groupingGivesEightDivisionsOfFourInTheFeedsOrder() =
        runBlocking {
            val loadable = harness().repository.groupedByDivision()

            val groups = loadable.data.orEmpty()
            assertEquals(
                listOf("AFC East", "AFC North", "AFC South", "AFC West", "NFC East", "NFC North", "NFC South", "NFC West"),
                groups.map { it.division },
            )
            assertTrue("every division holds four clubs: ${groups.map { it.teams.size }}", groups.all { it.teams.size == 4 })
            assertEquals(32, groups.sumOf { it.teams.size })
            assertEquals(Freshness.Fresh, loadable.freshness)
            assertNull(loadable.error)
        }

    @Test
    fun eachClubLandsUnderTheDivisionTheFeedPutsItIn() =
        runBlocking {
            val groups =
                harness()
                    .repository
                    .groupedByDivision()
                    .data
                    .orEmpty()
            val divisionOf = groups.flatMap { group -> group.teams.map { it.team.abbrev to group.division } }.toMap()

            assertEquals("AFC East", divisionOf["BUF"])
            assertEquals("AFC West", divisionOf["LV"])
            assertEquals("NFC East", divisionOf["WSH"])
            assertEquals("NFC West", divisionOf["SF"])
            assertEquals(32, divisionOf.size)
        }

    @Test
    fun insideADivisionTheClubsKeepTheTeamsFeedsOrderNotTheTablesRanking() =
        runBlocking {
            val groups =
                harness()
                    .repository
                    .groupedByDivision()
                    .data
                    .orEmpty()

            // The division tables rank their clubs (BUF, NE, NYJ, MIA; DEN, KC, LV, LAC); the teams feed lists them by
            // place name, and that is the order a list of clubs keeps.
            assertEquals(listOf("BUF", "MIA", "NE", "NYJ"), groups.first { it.division == "AFC East" }.teams.map { it.team.abbrev })
            assertEquals(listOf("DEN", "KC", "LV", "LAC"), groups.first { it.division == "AFC West" }.teams.map { it.team.abbrev })
        }

    @Test
    fun aDivisionTheFeedDidNotPublishDoesNotDropItsClubs() =
        runBlocking {
            // The NFC tables are refused: no NFC club is named by any division.
            val harness =
                harness { request ->
                    if (request.url.parameters["group"] == "7") respond("down", HttpStatusCode.ServiceUnavailable) else null
                }

            val loadable = harness.repository.groupedByDivision()

            val groups = loadable.data.orEmpty()
            assertEquals(32, groups.sumOf { it.teams.size })
            assertEquals(
                listOf("AFC East", "AFC North", "AFC South", "AFC West", null),
                groups.map { it.division },
            )
            val ungrouped = groups.last().teams.map { it.team.abbrev }
            assertEquals(16, ungrouped.size)
            assertTrue("every unplaced club is an NFC one: $ungrouped", "SF" in ungrouped && "WSH" in ungrouped && "BUF" !in ungrouped)
            assertEquals("the failure is reported so the screen can offer a retry", LoadError.Http(503), loadable.error)
            assertEquals("the clubs themselves are fresh", Freshness.Fresh, loadable.freshness)
        }

    @Test
    fun aClubNoTableNamesFollowsTheDivisionsWithoutAHeading() =
        runBlocking {
            // One extra club in the teams payload that no division table mentions.
            val extra =
                String(teamsBytes).replaceFirst(
                    """"teams":[""",
                    """"teams":[{"team":{"abbreviation":"ZZZ","displayName":"Nowhere Nobodies"}},""",
                )
            val harness =
                harness { request ->
                    if (request.url.encodedPath.endsWith("/teams")) {
                        respond(extra, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        null
                    }
                }

            val groups =
                harness.repository
                    .groupedByDivision()
                    .data
                    .orEmpty()

            assertEquals(9, groups.size)
            assertEquals(8, groups.count { it.division != null && it.teams.size == 4 })
            assertNull(groups.last().division)
            assertEquals(listOf("ZZZ"), groups.last().teams.map { it.team.abbrev })
        }

    @Test
    fun noDivisionTablesAtAllLeavesEveryClubInOneUnheadedGroup() =
        runBlocking {
            val harness =
                harness { request ->
                    if (request.url.parameters.contains("group")) respond("down", HttpStatusCode.InternalServerError) else null
                }

            val groups =
                harness.repository
                    .groupedByDivision()
                    .data
                    .orEmpty()

            assertEquals(1, groups.size)
            assertNull(groups.single().division)
            assertEquals(32, groups.single().teams.size)
        }

    @Test
    fun noClubsMeansNoGroupsAndTheErrorIsPassedOn() =
        runBlocking {
            val harness = harness { respond("nope", HttpStatusCode.InternalServerError) }

            val loadable = harness.repository.groupedByDivision()

            assertTrue(loadable.data.orEmpty().isEmpty())
            assertEquals(LoadError.Http(500), loadable.error)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertEquals("the tables are not asked for when there is nothing to put under them", listOf("teams"), harness.requests)
        }

    @Test
    fun groupingReusesTheStandingsCacheInsteadOfAskingAgain() =
        runBlocking {
            val harness = harness()

            harness.repository.groupedByDivision()
            harness.repository.groupedByDivision()

            assertEquals(
                "one request per payload, however often the tab is opened",
                listOf("teams", "standings?8", "standings?7").sorted(),
                harness.requests.sorted(),
            )
        }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-24T12:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val DIRECTORY = "/cache"
    }
}
