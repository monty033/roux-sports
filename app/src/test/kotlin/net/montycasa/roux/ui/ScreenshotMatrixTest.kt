package net.montycasa.roux.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.EspnApi
import net.montycasa.roux.data.repo.nflScoresRepository
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.ScoresOrdering
import net.montycasa.roux.ui.common.LocalLeagueCrestArtwork
import net.montycasa.roux.ui.scores.LocalTeamLogoArtwork
import net.montycasa.roux.ui.scores.ScoresScreen
import net.montycasa.roux.ui.scores.ScoresUiState
import net.montycasa.roux.ui.standings.NflStandingsFixtures
import net.montycasa.roux.ui.standings.NflStandingsTab
import net.montycasa.roux.ui.teams.NflTeamsFixtures
import net.montycasa.roux.ui.teams.TeamsScreen
import net.montycasa.roux.ui.theme.RouxTheme
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * P6: one golden per (screen state, theme) for every state in [AuditStates]. The state list is shared with
 * ContrastAndTypeTest, so a screen added to the audit is photographed here without editing this file.
 *
 * Goldens land in app/src/test/screenshots/matrix/<state>-<theme>.png. Changing a golden is a claim that the UI
 * changed; read the diff image before re-recording. See README "Screenshot baselines".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotMatrixTest {
    @get:Rule
    val rule = createComposeRule()

    /** Long enough for the tab selection animation to reach rest before a state is photographed. */
    private val animationSettleMillis = 1_000L

    /** States whose page is longer than the viewport, so the last card needs its own image. */
    private val scrolledStates = listOf("detail.full", "detail.live", "detail.scheduled", "detail.pregame")

    /**
     * States with pre-game cards between the hero and Game info. Season stats and Season series sit between the top and
     * the info image, so neither shows them: each gets its own capture.
     */
    private val preGameStates = listOf("detail.pregame")

    /** States with a shots card: it sits between two cards, so neither the top nor the info image can show it. */
    private val shotsStates = listOf("detail.full", "detail.live")

    /** States with a scoring summary: it too sits between two cards, so it needs its own capture. */
    private val scoringStates = listOf("detail.full", "detail.live")

    /** States with a penalties summary: the same between-cards problem, directly below the scoring summary. */
    private val penaltyStates = listOf("detail.full", "detail.live")

    /**
     * The states the matrix is contracted to photograph. A state list that silently shrinks is the failure mode this
     * audit exists to prevent, so the set and its size are pinned rather than derived from the list itself.
     */
    private val expectedStates =
        setOf(
            "settings",
            "scores.data",
            "scores.empty",
            "scores.stale",
            "scores.offseason",
            "scores.offseason.opener",
            "standings.eastPlayoffs",
            "standings.eastDivision",
            "standings.westPlayoffs",
            "standings.league",
            "teams.list",
            "teams.favourites",
            "teams.alerts",
            "teams.alerts.off",
            "detail.full",
            "detail.live",
            "detail.scheduled",
            "detail.pregame",
            "detail.partial",
            "detail.playoff",
            "player.season",
            "player.gamelog.fallback",
            "player.preseason.season",
            "player.preseason.gamelog",
            "player.preseason.career",
            "team.schedule",
            "team.preseason",
            "drawer.leagues",
        )

    @Test
    fun everyAuditStateInBothThemes() {
        val screens = AuditStates.screens()
        assertEquals("audited state count", expectedStates.size, screens.size)
        assertEquals("audited states", expectedStates, screens.map { it.name }.toSet())
        val index = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                // Crests are network artwork; these captures are recorded without it.
                CompositionLocalProvider(LocalLeagueCrestArtwork provides false) {
                    screens[index.intValue].content()
                }
            }
        }
        var captured = 0
        val failures = mutableListOf<AssertionError>()
        screens.forEachIndexed { i, screen ->
            listOf(false, true).forEach { isDark ->
                val theme = if (isDark) "dark" else "light"
                rule.runOnIdle {
                    index.intValue = i
                    dark.value = isDark
                }
                rule.waitForIdle()
                // A state whose content sits behind a tab is photographed with that tab open, as the audit states say.
                screen.tab?.let { tab ->
                    // The click action, not a touch: a touch starts a ripple on the tab, and its frames run on the wall
                    // clock, so the photograph would differ between runs. The page's own scroll still needs its time.
                    rule.onNodeWithTag(tab).performSemanticsAction(SemanticsActions.OnClick)
                    rule.mainClock.advanceTimeBy(animationSettleMillis)
                    rule.waitForIdle()
                }
                val file = screen.name.replace('.', '-') + "-" + theme
                // Always start from the top. The scrolled captures below leave the list where they finished, so without
                // this the dark pass photographed the light pass's scroll position and the "unscrolled" image was not.
                // Anchored on the hero rather than "the first scrollable": on the scores and standings pages that is the
                // date shelf, and scrolling it would silently change those goldens too.
                if (screen.name.startsWith("detail.")) {
                    runCatching {
                        rule.onNodeWithTag("detail.hero").performScrollTo()
                        rule.waitForIdle()
                    }
                }
                // Settings and the alert sheet both scroll inside a fixed surface. Start them from the top so
                // the next state's "unscrolled" image is not the previous state's scroll position.
                if (screen.name.startsWith("settings")) {
                    runCatching {
                        rule.onNodeWithText("Settings").performScrollTo()
                        rule.waitForIdle()
                    }
                }
                if (screen.name.startsWith("teams.alerts")) {
                    runCatching {
                        rule.onNodeWithTag("teams.alerts.upcoming").performScrollTo()
                        rule.waitForIdle()
                    }
                }
                // The card at the top of the page is the one place a state is distinguishable - the hero carries the
                // live capsule, the clock and the score - so it is always captured unscrolled. Scrolling before the
                // capture made detail.full and detail.live produce the same picture, which audited nothing.
                try {
                    rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file.png")
                } catch (failure: AssertionError) {
                    failures += failure
                }
                captured++
                // The sheet scrolls too: the bottom image is where the last two switches and the pinned Done
                // are compared with the top image's first three.
                if (screen.name.startsWith("teams.alerts")) {
                    rule.onNodeWithTag("teams.alerts.final").performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-bottom.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                // A card between two others is in neither image: scrolling to it is the only way the matrix sees it.
                if (screen.name in shotsStates) {
                    rule.onNodeWithTag("detail.section.shots").performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-shots.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                if (screen.name in preGameStates) {
                    for ((suffix, anchor) in listOf("stats" to "detail.section.season-stats", "series" to "detail.section.season-series")) {
                        rule.onNodeWithTag(anchor).performScrollTo()
                        rule.waitForIdle()
                        try {
                            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-$suffix.png")
                        } catch (failure: AssertionError) {
                            failures += failure
                        }
                    }
                }
                if (screen.name in scoringStates) {
                    // The page-level scoring image cannot show the whole card — it is taller than the viewport and
                    // Compose only scrolls an anchor just into view — so it anchors on the card and shows rows. The
                    // complete card, heading included, is recorded by ScoringCardScreenshotTest.
                    val anchor = "detail.section.scoring-summary"
                    rule.onNodeWithTag(anchor).performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-scoring.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                if (screen.name in penaltyStates) {
                    // Like the scoring card, the penalties card is taller than the viewport; the page-level capture
                    // anchors on it and shows rows, while PenaltiesCardScreenshotTest records the whole card.
                    val anchor = "detail.section.penalties-summary"
                    rule.onNodeWithTag(anchor).performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-penalties.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                // The bottom of a long page needs its own image, or the last card ships unseen.
                if (screen.name in scrolledStates) {
                    rule.onNodeWithTag("detail.section.info").performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-info.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
            }
        }
        assertEquals("one golden per (state, theme)", screens.size * 2, captured)
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} screenshot mismatches", failures.first())
    }

    /**
     * The three football states in both themes, each drawn from the real NFL adapter: a golden of a hand-built day would
     * show the screen, not whether what the adapter makes of the feed renders.
     */
    @Test
    fun footballStatesInBothThemes() {
        val states = footballStates()
        assertEquals("football states", listOf("nfl-final", "nfl-scheduled", "nfl-empty"), states.map { it.first })
        assertTrue("every football day loaded", states.all { it.second.error == null && it.second.loadedDate != null })
        assertTrue("the final day has games", states[0].second.rows.isNotEmpty())
        assertTrue("the scheduled day has games", states[1].second.rows.isNotEmpty())
        assertTrue("the empty day has none", states[2].second.rows.isEmpty())
        // The feed numbers the football weeks, and a day with no games states none, so the shelf has nothing to label
        // there rather than guessing which week a gap falls in.
        assertEquals("a week only where the feed states one", listOf(10, 12, null), states.map { it.second.week })

        val index = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                CompositionLocalProvider(LocalLeagueCrestArtwork provides false) {
                    // Keyed on the state so each day starts from its own pager, as a fresh selection does in the app.
                    key(index.intValue) { ScoresScreen(states[index.intValue].second, {}, {}, {}) }
                }
            }
        }
        var captured = 0
        states.forEachIndexed { i, (name, _) ->
            listOf(false, true).forEach { isDark ->
                val theme = if (isDark) "dark" else "light"
                rule.runOnIdle {
                    index.intValue = i
                    dark.value = isDark
                }
                rule.waitForIdle()
                rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$name-$theme.png")
                captured++
            }
        }
        assertEquals("one golden per football state and theme", states.size * 2, captured)
    }

    /**
     * The football table's states in both themes at 360 dp, the narrowest phone the layout promises: the tables are read
     * through the real repository and adapter, so what is drawn is what the captured feed makes of it. The conference
     * page is the NFC one, whose feed order puts the third and fourth seeds under the eighth and ninth.
     */
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h780dp-mdpi")
    fun footballStandingsStatesInBothThemes() {
        val stale = Freshness.Stale(Instant.parse("2026-11-24T14:00:00Z"))
        val states =
            listOf(
                "nfl-standings-division" to NflStandingsFixtures.state(NflStandingsTab.Division),
                "nfl-standings-conference" to NflStandingsFixtures.state(NflStandingsTab.Conference, NflConference.NFC),
                "nfl-standings-league" to NflStandingsFixtures.state(NflStandingsTab.League),
                "nfl-standings-stale" to
                    NflStandingsFixtures
                        .state(NflStandingsTab.Conference, NflConference.AFC)
                        .copy(stale = stale, error = LoadError.Http(503)),
            )
        assertEquals(
            "football standings states",
            listOf("nfl-standings-division", "nfl-standings-conference", "nfl-standings-league", "nfl-standings-stale"),
            states.map { it.first },
        )
        assertTrue("every football table has rows", states.all { (_, state) -> state.tables.any { it.rows.isNotEmpty() } })

        val index = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                CompositionLocalProvider(LocalLeagueCrestArtwork provides false) {
                    key(index.intValue) { NflStandingsFixtures.Page(states[index.intValue].second) }
                }
            }
        }
        var captured = 0
        states.forEachIndexed { i, (name, _) ->
            listOf(false, true).forEach { isDark ->
                val theme = if (isDark) "dark" else "light"
                rule.runOnIdle {
                    index.intValue = i
                    dark.value = isDark
                }
                rule.waitForIdle()
                rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$name-$theme.png")
                captured++
            }
        }
        assertEquals("one golden per football standings state and theme", states.size * 2, captured)
    }

    /**
     * The football Teams list in both themes at 360 dp, with artwork off so the goldens show the abbreviation fallback
     * rather than a network image. The clubs and their divisions are read through the real repository and adapter, so
     * what is drawn is what the captured feed makes of them; one club is starred to show the star on a grouped row.
     */
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h780dp-mdpi")
    fun footballTeamsInBothThemes() {
        val state = NflTeamsFixtures.state(favourites = setOf(TeamKey(League.NFL, "BUF")))
        assertEquals("every football club is in the state", 32, state.rows.size)
        assertEquals("eight divisions", 8, state.groups.size)

        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                CompositionLocalProvider(LocalLeagueCrestArtwork provides false, LocalTeamLogoArtwork provides false) {
                    TeamsScreen(state = state, onToggle = {}, onRetry = {})
                }
            }
        }
        var captured = 0
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/nfl-teams-$theme.png")
            captured++
        }
        assertEquals("one golden per theme", 2, captured)
    }

    /** The NFL scoreboards the football goldens load, keyed by the `dates` parameter ESPN is asked for. */
    private val nflScoreboards =
        mapOf(
            "20261115" to "nfl-scoreboard-sunday-a.json",
            "20261126" to "nfl-scoreboard-week-12.json",
            "20261117" to "nfl-scoreboard-emptyday.json",
        )

    /**
     * Loads each football day through the public NFL repository, served from the captured scoreboards by a mock engine. The
     * cache is a fresh directory, so every day is a network answer rather than a file left by another run.
     */
    private fun footballStates(): List<Pair<String, ScoresUiState>> {
        val today = "2026-11-18"
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.parse("${today}T20:00:00Z")
            }
        val client =
            HttpClient(
                MockEngine { request ->
                    val fixture = nflScoreboards[request.url.parameters["dates"]]
                    if (fixture == null) {
                        respond(ByteArray(0), HttpStatusCode.NotFound)
                    } else {
                        respond(nflFixture(fixture), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                },
            )
        val cache = ResponseCache(directory = Files.createTempDirectory("nfl-matrix").toString().toPath(), clock = clock)
        val repository = nflScoresRepository(EspnApi(client), cache, clock)
        return runBlocking {
            listOf("nfl-final" to "2026-11-15", "nfl-scheduled" to "2026-11-26", "nfl-empty" to "2026-11-17").map { (name, date) ->
                val loaded = repository.loadDay(date, today)
                val day = loaded.data
                name to
                    ScoresUiState(
                        selectedDate = date,
                        today = today,
                        rows = day?.games?.let { ScoresOrdering.order(it) }.orEmpty(),
                        freshness = loaded.freshness,
                        error = loaded.error,
                        loadedDate = day?.date,
                        dateIndex = day?.dateIndex.orEmpty(),
                        week = day?.week,
                        favouritesOnly = false,
                    )
            }
        }
    }

    private fun nflFixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/fx-nfl/2026-11-18/$name")) {
            "missing NFL fixture $name"
        }.use { it.readBytes() }
}
