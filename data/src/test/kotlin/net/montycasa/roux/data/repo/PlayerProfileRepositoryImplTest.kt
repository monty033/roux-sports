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
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PlayerProfileRepositoryImplTest {
    private val payload = Fixtures.text("2026-11-03", "player-8476316-landing.json").toByteArray()
    private val gameLogPayload = Fixtures.text("2026-11-04", "player-8476316-game-log-20262027.json").toByteArray()

    @Test
    fun theRequestAsksForThatPlayersLanding() =
        runBlocking {
            val harness = Harness()

            harness.repository.profile(PLAYER_ID)

            assertEquals(listOf("/v1/player/8476316/landing"), harness.requests)
        }

    @Test
    fun theProfileIsItsOwnCacheEntryKeyedByThePlayer() =
        runBlocking {
            val harness = Harness()

            harness.repository.profile(PLAYER_ID)

            val entries =
                harness.fileSystem
                    .list("/cache".toPath())
                    .map { it.name }
                    .filter { it.endsWith(".roux") }
            assertEquals(listOf("player-profile-8476316.roux"), entries)
        }

    @Test
    fun aSecondReadInsideSixHoursIsServedFromTheCache() =
        runBlocking {
            val harness = Harness()
            val first = harness.repository.profile(PLAYER_ID)

            harness.clock.advance(5.hours)
            val second = harness.repository.profile(PLAYER_ID)

            assertEquals(1, harness.requests.size)
            assertEquals(Freshness.Fresh, second.freshness)
            assertEquals(first.data, second.data)
        }

    @Test
    fun aStaleEntryRefetchesPastSixHours() =
        runBlocking {
            val harness = Harness()
            harness.repository.profile(PLAYER_ID)

            harness.clock.advance(6.hours + 1.minutes)
            val refetched = harness.repository.profile(PLAYER_ID)

            assertEquals(2, harness.requests.size)
            assertEquals(Freshness.Fresh, refetched.freshness)
            assertNull(refetched.error)
        }

    @Test
    fun aFailedRefreshReturnsTheCachedProfileMarkedStale() =
        runBlocking {
            val harness = Harness()
            val first = harness.repository.profile(PLAYER_ID)
            val fetchedAt = harness.clock.now()

            harness.clock.advance(7.hours)
            harness.failing = true
            val second = harness.repository.profile(PLAYER_ID)

            assertEquals(first.data, second.data)
            assertEquals(Freshness.Stale(since = fetchedAt), second.freshness)
            assertEquals(LoadError.Http(status = 500), second.error)
        }

    @Test
    fun aFailureWithNothingCachedReportsTheErrorAndNoProfile() =
        runBlocking {
            val harness = Harness()
            harness.failing = true

            val loadable = harness.repository.profile(PLAYER_ID)

            assertEquals(1, harness.requests.size)
            assertNull(loadable.data)
            assertEquals(Freshness.Missing, loadable.freshness)
            assertEquals(LoadError.Http(status = 500), loadable.error)
        }

    @Test
    fun theGameLogAsksForThatSeasonsRegularSeasonGameType() =
        runBlocking {
            val harness = Harness()

            harness.repository.gameLog(PLAYER_ID, season = 20262027, isGoalie = true)

            assertEquals(listOf("/v1/player/8476316/game-log/20262027/2"), harness.requests)
        }

    @Test
    fun theGameLogIsItsOwnCacheEntryKeyedByPlayerAndSeason() =
        runBlocking {
            val harness = Harness()

            harness.repository.gameLog(PLAYER_ID, season = 20262027, isGoalie = true)

            val entries =
                harness.fileSystem
                    .list("/cache".toPath())
                    .map { it.name }
                    .filter { it.endsWith(".roux") }
            assertEquals(listOf("player-game-log-8476316-20262027.roux"), entries)
        }

    @Test
    fun theGameLogReturnsItsRowsInTheFeedsOrder() =
        runBlocking {
            val harness = Harness()

            val loadable = harness.repository.gameLog(PLAYER_ID, season = 20262027, isGoalie = true)

            assertEquals(listOf(2026020246L, 2026020231L), loadable.data?.map { it.gameId })
            assertNull(loadable.error)
        }

    @Test
    fun aFailedGameLogLeavesTheProfileReadable() =
        runBlocking {
            val harness = Harness()
            harness.gameLogFailing = true

            val log = harness.repository.gameLog(PLAYER_ID, season = 20262027, isGoalie = true)
            val profile = harness.repository.profile(PLAYER_ID)

            assertNull(log.data)
            assertEquals(LoadError.Http(status = 500), log.error)
            assertEquals(8476316L, profile.data?.playerId)
            assertNull(profile.error)
        }

    private inner class Harness {
        val clock = MutableClock()
        val requests = mutableListOf<String>()
        var failing = false
        var gameLogFailing = false
        val fileSystem = FakeFileSystem(clock)
        val repository: PlayerProfileRepositoryImpl

        init {
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    requests += path
                    val isGameLog = path.contains("/game-log/")
                    when {
                        isGameLog && gameLogFailing -> respond("upstream error", HttpStatusCode.InternalServerError)
                        isGameLog -> respond(gameLogPayload, HttpStatusCode.OK, jsonHeaders())
                        failing -> respond("upstream error", HttpStatusCode.InternalServerError)
                        else -> respond(payload, HttpStatusCode.OK, jsonHeaders())
                    }
                }
            val cache = ResponseCache(fileSystem, CACHE_DIRECTORY.toPath(), clock)
            val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
            repository = PlayerProfileRepositoryImpl(api, cache, clock)
        }

        private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
    }

    private class MutableClock : Clock {
        private var instant: Instant = Instant.parse("2026-11-03T12:00:00Z")

        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "/cache"
        const val PLAYER_ID = 8476316L
    }
}
