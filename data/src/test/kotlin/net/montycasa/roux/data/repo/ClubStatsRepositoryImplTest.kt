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
import net.montycasa.roux.domain.model.TeamKey
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ClubStatsRepositoryImplTest {
    private val payload = Fixtures.clubStats("club-stats-ana-20262027.json").toByteArray()

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

            val entries =
                harness.fileSystem
                    .list("/cache".toPath())
                    .map { it.name }
                    .filter { it.endsWith(".roux") }
            assertEquals(listOf("club-stats-ANA-20262027.roux"), entries)
        }

    private fun harness(clock: MutableClock = MutableClock()): Harness {
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                respond(
                    payload,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
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
