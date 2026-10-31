package net.montycasa.roux.ui.scores

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.enterLeagueMode
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.hours

/** Exercises the shipped host, ViewModel factory, repositories and singleton logo loader together. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScoresIntegrationTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: ScoresIntegrationApp get() = RuntimeEnvironment.getApplication() as ScoresIntegrationApp

    @After
    @OptIn(DelicateCoilApi::class)
    fun tearDown() {
        SingletonImageLoader.get(app).shutdown()
        SingletonImageLoader.reset()
        // The container owns the preference store's scope now, so closing the client alone would leave it running.
        runBlocking { app.container.close() }
    }

    @Test
    fun fixtureRowsShowTeamAbbreviationsAndGameClicksOpenTheGamePage() {
        launchScores()

        // Visit every row: LazyColumn intentionally composes only the visible part of the corpus.
        ScoreFixture.games.forEach { game ->
            val id = game.getValue("id").jsonPrimitive.content
            rule.onNodeWithTag("scores.list").performScrollToNode(
                hasTestTag("scores.game.$id"),
            )
            rule.onNodeWithTag("scores.game.$id").assertIsDisplayed()
            listOf("awayTeam", "homeTeam").forEach { side ->
                rule
                    .onNodeWithText(
                        game
                            .getValue(side)
                            .jsonObject
                            .getValue("abbrev")
                            .jsonPrimitive.content,
                    ).assertIsDisplayed()
            }
        }
        val lastId =
            ScoreFixture.games
                .last()
                .getValue("id")
                .jsonPrimitive.content
        // A row now opens its game page; the click is safe in the sense that it cannot throw or blank the app.
        rule.onNodeWithTag("scores.game.$lastId").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("detail.back").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("detail.back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("scores.game.$lastId").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("scores.error").assertDoesNotExist()
        assertTrue(app.requests.any { it.host == "api-web.nhle.com" && it.encodedPath == "/v1/score/${ScoreFixture.date}" })

        rule.onNode(hasText("Standings") and isSelectable()).performClick()
        // Standings is a real destination now; wait for the table it renders rather than a placeholder sentence.
        // It opens on the Eastern conference, whose first block is the Atlantic.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Atlantic").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("standings.header").assertIsDisplayed()
        // Teams is a real destination now, so this asserts the picker rather than a placeholder sentence.
        rule.onNode(hasText("Teams") and isSelectable()).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.row.TOR").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Choose your favorite teams. Favorites appear first.").assertIsDisplayed()
    }

    @Test
    fun offlineColdStartShowsPreseededRowsAndStaleBannerWithoutAnErrorScreen() {
        app.offline = true
        val savedAt = app.container.clock.now() - 1.hours
        runBlocking {
            app.container.cache.put(CacheKey(CacheKind.Score, ScoreFixture.date), ScoreFixture.bytes, savedAt)
        }

        // No ViewModel has run before this launch: only the raw disk cache can supply these rows.
        launchScores()

        val first = ScoreFixture.games.first()
        listOf("awayTeam", "homeTeam").forEach { side ->
            rule
                .onNodeWithText(
                    first
                        .getValue(side)
                        .jsonObject
                        .getValue("abbrev")
                        .jsonPrimitive.content,
                ).assertIsDisplayed()
        }
        rule.onNodeWithText("Updated", substring = true).assertIsDisplayed()
        rule.onNodeWithText("3:00 PM", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t refresh. Showing saved scores.").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed()
        rule.onNodeWithTag("scores.error").assertDoesNotExist()
        assertTrue("the stale cache must attempt a refresh", app.requests.any { it.host == "api-web.nhle.com" })
    }

    @Test
    fun singletonLogoLoaderUsesTheSameMockEngineAsScores() {
        app.serveLogos = true
        launchScores()

        val first = ScoreFixture.games.first()
        val logos =
            listOf("awayTeam", "homeTeam").map {
                first
                    .getValue(it)
                    .jsonObject
                    .getValue("logo")
                    .jsonPrimitive.content
            }
        rule.waitUntil(timeoutMillis = 10_000) { logos.all { logo -> app.requests.any { it.toString() == logo } } }

        val logoRequests = app.requests.filter { it.host == "assets.nhle.com" }
        assertTrue(logoRequests.isNotEmpty())
        assertTrue(logoRequests.all { it.protocol.name == "https" })
        assertEquals(setOf("api-web.nhle.com", "assets.nhle.com"), app.requests.map { it.host }.toSet())
        // A successful SVG removes the abbreviation fallback. This also exercises the SVG decoder.
        logos.indices.forEach { index ->
            val side = if (index == 0) "awayTeam" else "homeTeam"
            val abbrev =
                first
                    .getValue(side)
                    .jsonObject
                    .getValue("abbrev")
                    .jsonPrimitive.content
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodes(hasText(abbrev), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun launchScores() {
        rule.setContent { RouxTheme { RouxNavHost() } }
        rule.enterLeagueMode()
        rule.waitForFixtureScores()
    }
}

/** Waits for repository and preference reads so host tests and snapshots see the same loaded slate. */
fun ComposeTestRule.waitForFixtureScores() {
    val id =
        ScoreFixture.games
            .first()
            .getValue("id")
            .jsonPrimitive.content
    waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithTag("scores.game.$id").fetchSemanticsNodes().isNotEmpty()
    }
}
