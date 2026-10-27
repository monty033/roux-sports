package net.montycasa.roux.ui.scores

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.AppContainer
import net.montycasa.roux.RouxApp
import net.montycasa.roux.data.cache.CacheKey
import net.montycasa.roux.data.cache.CacheKind
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.theme.RouxTheme
import okio.Path.Companion.toPath
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
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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
        app.container.httpClient.close()
    }

    @Test
    fun fixtureRowsShowTeamAbbreviationsAndGameClicksRemainSafe() {
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
        rule.onNodeWithTag("scores.game.$lastId").performClick().assertIsDisplayed()
        rule.onNodeWithTag("scores.error").assertDoesNotExist()
        assertTrue(app.requests.any { it.host == "api-web.nhle.com" && it.encodedPath == "/v1/score/${ScoreFixture.date}" })

        rule.onNode(hasText("Standings") and isSelectable()).performClick()
        rule.onNodeWithText("League standings will appear here.").assertIsDisplayed()
        rule.onNode(hasText("Teams") and isSelectable()).performClick()
        rule.onNodeWithText("Team lists and favourites will appear here.").assertIsDisplayed()
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
        rule.onNodeWithText("Updated 15:00").assertIsDisplayed()
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

/** Replaces only the container inputs; the real application's singleton factory still builds Coil. */
class ScoresIntegrationApp : RouxApp() {
    /** Each test must observe its own requests, including URLs another test answered with a 404. */
    override fun newImageLoader(context: Context): ImageLoader =
        super
            .newImageLoader(context)
            .newBuilder()
            .diskCache(null)
            .build()

    var offline = false
    var serveLogos = false
    val requests = CopyOnWriteArrayList<Url>()

    override val container: AppContainer by lazy {
        AppContainer(
            filesDir = filesDir.absolutePath.toPath(),
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("${ScoreFixture.date}T20:00:00Z")
                },
            engine =
                MockEngine { request ->
                    requests += request.url
                    if (offline) throw IOException("offline cold start")
                    when (request.url.host) {
                        "api-web.nhle.com" -> {
                            respond(
                                ScoreFixture.bytes,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "assets.nhle.com" -> {
                            respond(
                                """<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><path fill="red" d="M0 0h32v32H0z"/></svg>""",
                                if (serveLogos) HttpStatusCode.OK else HttpStatusCode.NotFound,
                                headersOf(HttpHeaders.ContentType, "image/svg+xml"),
                            )
                        }

                        else -> {
                            error("Unexpected host: ${request.url.host}")
                        }
                    }
                },
        )
    }
}

/** The existing data corpus is a test resource in :app too; no duplicate capture or rewritten date. */
private object ScoreFixture {
    val bytes =
        requireNotNull(javaClass.getResourceAsStream("/fx/2026-10-26/score-gameday.json")) {
            "Missing captured score fixture"
        }.use { it.readBytes() }
    private val body = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    val date = body.getValue("currentDate").jsonPrimitive.content
    val games = body.getValue("games").jsonArray.map { it.jsonObject }
}
