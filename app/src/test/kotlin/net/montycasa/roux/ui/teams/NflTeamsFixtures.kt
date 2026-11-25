package net.montycasa.roux.ui.teams

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.EspnApi
import net.montycasa.roux.data.repo.nflStandingsRepository
import net.montycasa.roux.data.repo.nflTeamsRepository
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflTeamGroup
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.NflTeamsRepository
import net.montycasa.roux.ui.scores.ScoreFixture
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The captured football clubs, grouped through the real repository and adapter: a screen or a collision check built on
 * hand-written clubs would show the screen, not whether what the adapter makes of the feed renders.
 */
internal object NflTeamsFixtures {
    /** The repository over the captured teams feed and the 24 November division tables. */
    val repository: NflTeamsRepository by lazy {
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.parse("2026-11-24T20:00:00Z")
            }
        val client =
            HttpClient(
                MockEngine { request ->
                    val fixture =
                        when {
                            request.url.encodedPath.endsWith("/football/nfl/teams") -> {
                                ScoreFixture.nfl("2026-11-18", "nfl-teams.json")
                            }

                            request.url.parameters["group"] == "8" -> {
                                ScoreFixture.nfl("2026-11-24", "nfl-standings-divisions-afc.json")
                            }

                            request.url.parameters["group"] == "7" -> {
                                ScoreFixture.nfl("2026-11-24", "nfl-standings-divisions-nfc.json")
                            }

                            else -> {
                                error("unexpected request ${request.url}")
                            }
                        }
                    respond(fixture, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        val api = EspnApi(client)
        val cache = ResponseCache(directory = Files.createTempDirectory("nfl-teams").toString().toPath(), clock = clock)
        nflTeamsRepository(api, cache, nflStandingsRepository(api, cache, clock), clock)
    }

    /** The eight divisions and their thirty-two clubs, as the repository states them. */
    val groups: List<NflTeamGroup> by lazy {
        runBlocking {
            val loaded = repository.groupedByDivision()
            requireNotNull(loaded.data) { "the football teams fixture did not load: ${loaded.error}" }
        }
    }

    /**
     * A source that answers at once from the already-loaded clubs. The real repository reads on `Dispatchers.IO`, which a
     * view model test's scheduler cannot wait for, so a test of the view model's own rules uses this and leaves the
     * repository to its own tests.
     */
    val source: TeamsSource =
        TeamsSource {
            Loadable(
                groups.map { group -> TeamsGroup(group.division, group.teams.map { it.team }) },
                Freshness.Fresh,
                null,
            )
        }

    /** What the football picker holds once its clubs have loaded. */
    fun state(favourites: Set<TeamKey> = emptySet()): TeamsUiState {
        val teamGroups = groups.map { group -> TeamsGroup(group.division, group.teams.map { it.team }) }
        return TeamsUiState(
            rows = teamGroups.flatMap { it.teams },
            groups = teamGroups,
            grouped = true,
            favourites = favourites,
            isRefreshing = false,
            freshness = Freshness.Fresh,
        )
    }
}
