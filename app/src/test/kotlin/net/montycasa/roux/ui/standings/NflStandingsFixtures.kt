@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.standings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflStandingRow
import net.montycasa.roux.domain.model.NflStandingTable
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.scores.ScoreFixture
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The captured football tables, read through the real repository and adapter: a golden or an assertion built on
 * hand-written rows would show the screen, not whether what the adapter makes of the feed renders.
 *
 * The conference capture is the 18 November table and the division captures are the 24 November ones; the two are a
 * week apart, which is why the screen tests assert on the figures of one level at a time.
 */
internal object NflStandingsFixtures {
    /** A fixed instant for screenshot renders: the stale banner's "today" must not follow the suite's run date. */
    val SCREENSHOT_NOW: Instant = Instant.parse("2026-11-25T20:00:00Z")

    val conferences: List<NflStandingTable> by lazy { load(group = null) }

    val divisions: Map<NflConference, List<NflStandingTable>> by lazy {
        mapOf(NflConference.AFC to load(group = "8"), NflConference.NFC to load(group = "7"))
    }

    /** The feed's own NFC table: third and fourth seeds sit below the eighth and ninth, and one club has a tie. */
    val nfc: NflStandingTable get() = conferences.single { "national" in it.name.lowercase() }

    val afc: NflStandingTable get() = conferences.single { "american" in it.name.lowercase() }

    private fun load(group: String?): List<NflStandingTable> {
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.parse("2026-11-24T20:00:00Z")
            }
        val client =
            HttpClient(
                MockEngine { request ->
                    val fixture =
                        when (request.url.parameters["group"]) {
                            null -> ScoreFixture.nfl("2026-11-18", "nfl-standings.json")
                            "8" -> ScoreFixture.nfl("2026-11-24", "nfl-standings-divisions-afc.json")
                            "7" -> ScoreFixture.nfl("2026-11-24", "nfl-standings-divisions-nfc.json")
                            else -> error("unexpected group in ${request.url}")
                        }
                    respond(fixture, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        val cache = ResponseCache(directory = Files.createTempDirectory("nfl-standings").toString().toPath(), clock = clock)
        val repository = nflStandingsRepository(EspnApi(client), cache, clock)
        return runBlocking {
            val loaded =
                when (group) {
                    null -> repository.conferences(anyGameLive = false)
                    "8" -> repository.divisions(NflConference.AFC, anyGameLive = false)
                    else -> repository.divisions(NflConference.NFC, anyGameLive = false)
                }
            requireNotNull(loaded.data) { "the football fixture for group $group did not load: ${loaded.error}" }
        }
    }

    /** A club with nothing but its identity: every figure the feed states is null. */
    fun bareRow(abbrev: String) = NflStandingRow(team = TeamRef(TeamKey(League.NFL, abbrev), name = abbrev))

    /** The screen as the shell draws it: above a three-destination bar, so the table gets the height it really gets. */
    @Composable
    fun Page(
        state: NflStandingsUiState,
        onSelectTab: (NflStandingsTab) -> Unit = {},
        onSelectConference: (NflConference) -> Unit = {},
        onTeamClick: (TeamKey) -> Unit = {},
        now: Instant = SCREENSHOT_NOW,
    ) {
        Scaffold(bottomBar = {
            NavigationBar {
                Spacer(Modifier.width(16.dp))
                listOf("Scores", "Standings", "Teams").forEach { label ->
                    NavigationBarItem(
                        selected = label == "Standings",
                        onClick = {},
                        icon = { Text(label.take(1)) },
                        label = { Text(label) },
                        modifier = Modifier.primaryControl("test.nav.$label", frequent = true),
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
        }) { insets ->
            NflStandingsScreen(
                state,
                onRefresh = {},
                onSelectTab = onSelectTab,
                onSelectConference = onSelectConference,
                modifier = Modifier.padding(insets),
                onTeamClick = onTeamClick,
                now = now,
            )
        }
    }

    /** What the view model would hold once [tab] had been read, for the fixtures' own conference tables. */
    fun state(
        tab: NflStandingsTab,
        conference: NflConference = NflConference.AFC,
    ): NflStandingsUiState {
        val conferenceLoad = NflStandingsLoad(conferences)
        val divisionLoad = NflStandingsLoad(divisions.getValue(conference))
        val tables =
            when (tab) {
                NflStandingsTab.Division -> divisionLoad.tables
                NflStandingsTab.Conference -> listOf(conferenceTable(conferences, conference)!!.let { it.copy(rows = seedOrder(it.rows)) })
                NflStandingsTab.League -> listOf(NflStandingTable("", leagueOrder(conferences.flatMap { it.rows })))
            }
        return NflStandingsUiState(
            tables = tables,
            tab = tab,
            conference = conference,
            loading = false,
            conferenceLoad = conferenceLoad,
            divisionLoads = mapOf(conference to divisionLoad),
        )
    }
}
