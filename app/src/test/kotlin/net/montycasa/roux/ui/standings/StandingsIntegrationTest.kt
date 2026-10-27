package net.montycasa.roux.ui.standings

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.reach.PrimaryControlTag
import net.montycasa.roux.ui.scores.ScoreFixture
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses ScoreFixtures' MockEngine and the captured dated standings table through the shipped shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandingsIntegrationTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: ScoresIntegrationApp get() = RuntimeEnvironment.getApplication() as ScoresIntegrationApp
    private val fixtureTeams =
        Json
            .parseToJsonElement(ScoreFixture.bytes("standings.json").decodeToString())
            .jsonObject
            .getValue("standings")
            .jsonArray
            .map { it.jsonObject }
            .sortedBy { it.getValue("leagueSequence").jsonPrimitive.int }
            .map {
                it
                    .getValue("teamAbbrev")
                    .jsonObject
                    .getValue("default")
                    .jsonPrimitive.content
            }

    @After
    @OptIn(DelicateCoilApi::class)
    fun tearDown() {
        SingletonImageLoader.get(app).shutdown()
        SingletonImageLoader.reset()
        runBlocking { app.container.close() }
    }

    @Test
    fun coldStartStandingsTabShowsEveryCapturedTeamThroughTheLastRow() {
        openStandingsFromColdStart()

        // Verify the table by scrolling through every captured row, including the last team, rather
        // than counting composed nodes. The expected count and last team come from the real fixture.
        val visited = mutableSetOf<String>()
        fixtureTeams.forEach { abbrev ->
            val tag = "standings.row.0.$abbrev"
            rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            rule.onNode(hasText(abbrev) and hasAnyAncestor(hasTestTag(tag))).assertIsDisplayed()
            visited += abbrev
        }
        assertEquals(fixtureTeams.size, visited.size)
        println("Standings in RouxNavHost: ${visited.size} fixture rows reached; last team=${visited.last()}")
    }

    @Test
    fun standingsRequestsUseTheContainersLeagueDayAndNeverNow() {
        openStandingsFromColdStart()

        val leagueDay =
            app.container.clock
                .now()
                .toLocalDateTime(TimeZone.of("America/New_York"))
                .date
        val expectedUrl = "https://api-web.nhle.com/v1/standings/$leagueDay"
        val standingsUrls = app.requests.filter { it.encodedPath.startsWith("/v1/standings/") }.map { it.toString() }
        assertEquals(listOf(expectedUrl), standingsUrls)
        assertFalse("No recorded request may use /now: ${app.requests}", app.requests.any { it.toString().endsWith("/now") })
        println("Standings integration recorded request URLs: ${app.requests.map { it.toString() }}")
    }

    @Test
    fun conferenceSwitcherChangesTheTableInTheRealShell() {
        openStandingsFromColdStart()

        rule.onNodeWithTag("standings.view.Conference").performClick().assertIsSelected()
        rule.onNodeWithText("Eastern").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Western").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("NHL").assertDoesNotExist()
        rule.onNodeWithTag("standings.view.League").performClick().assertIsSelected()
        rule.onNodeWithText("NHL").performScrollTo().assertIsDisplayed()
    }

    private fun openStandingsFromColdStart() {
        assertTrue("No destination has run before mounting the shell", app.requests.isEmpty())
        rule.setContent { RouxTheme { RouxNavHost() } }
        // Scores is the start destination. Go directly to Standings without interacting with Scores
        // or Teams, so neither can initialise the standings repository on this destination's behalf.
        rule.onNode(hasText("Standings") and isSelectable()).performClick().assertIsSelected()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("standings.row.0.${fixtureTeams.first()}").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("NHL").assertIsDisplayed()
        rule
            .onAllNodes(
                SemanticsMatcher("bottom navigation destination") {
                    it.config.getOrNull(PrimaryControlTag)?.startsWith("nav.") == true
                },
            ).assertCountEquals(3)
        rule.onNode(hasText("Teams") and isSelectable()).assertIsNotSelected()
    }
}
