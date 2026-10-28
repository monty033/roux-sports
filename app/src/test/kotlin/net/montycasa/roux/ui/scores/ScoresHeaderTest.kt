package net.montycasa.roux.ui.scores

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScoresHeaderTest {
    @get:Rule val rule = createComposeRule()
    private val liveCount = mutableStateOf(2)

    private fun launch(
        dark: Boolean = false,
        onOpen: () -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme(dark) {
                ScoresHeader(liveCount.value, onOpen, Modifier.fillMaxWidth())
            }
        }
    }

    @Test
    fun geometryAndAccessibleNavigationMatchTheReference() {
        var opens = 0
        launch(onOpen = { opens++ })
        // The file says 64 dp; a floor would pass an accidental 72.
        rule.onNodeWithTag("scores.header").assertHeightIsEqualTo(64.dp)
        rule
            .onNodeWithContentDescription("Open leagues")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, opens)
        // .brandmark is 1.25rem in the authority, overriding the brief's 18 dp Image box.
        rule.onNodeWithContentDescription("Roux Sports").assertHeightIsEqualTo(20.dp)
        val wordmark = rule.onNodeWithTag("scores.wordmark").getUnclippedBoundsInRoot()
        assertEquals(57f, (wordmark.right - wordmark.left).value, 1f)
        // Transparent margins explain the CSS box: 20 dp contains 18 dp of visible orange ink.
        val pixels = rule.onNodeWithTag("scores.wordmark").captureToImage().toPixelMap()
        val inkRows =
            (0 until pixels.height).filter { y ->
                (0 until pixels.width).any { x ->
                    val pixel = pixels[x, y]
                    pixel.red > 0.8f && pixel.green < 0.6f && pixel.blue < 0.3f
                }
            }
        assertEquals(18f, (inkRows.last() - inkRows.first() + 1) / rule.density.density, 0.1f)
        val menu = rule.reachTargets().single { it.tag == "scores.openLeagues" }
        assertFalse(menu.primary)
        assertEquals(48f, menu.width, 0.01f)
        assertEquals(48f, menu.height, 0.01f)
    }

    @Test
    fun pillCountsLiveGamesAndDisappearsEntirelyAtZero() {
        launch()
        rule.onNodeWithText("2 LIVE").assertExists()
        rule.onNodeWithTag("scores.livePill").assertHeightIsEqualTo(28.dp).assertWidthIsEqualTo(72.dp)
        rule.runOnIdle { liveCount.value = 1 }
        rule.onNodeWithText("1 LIVE").assertExists()
        rule.runOnIdle { liveCount.value = 0 }
        rule.onNodeWithTag("scores.livePill").assertDoesNotExist()
        rule.onNodeWithText("LIVE", substring = true).assertDoesNotExist()
    }

    @Test
    fun headerStaysAboveTheScrollingList() {
        val games =
            (0..20).map {
                Game(GameId("game-$it"), "2026-10-27", GameState.Scheduled, TeamRef(TeamKey("BOS")), TeamRef(TeamKey("TOR")))
            }
        rule.setContent {
            RouxTheme {
                ScoresScreen(
                    ScoresUiState("2026-10-27", today = "2026-10-27", rows = games, freshness = net.montycasa.roux.domain.Freshness.Fresh),
                    {},
                    {},
                    {},
                )
            }
        }
        val header = rule.onNodeWithTag("scores.header").getUnclippedBoundsInRoot()
        val list = rule.onNodeWithTag("scores.list").getUnclippedBoundsInRoot()
        assertEquals(header.bottom.value, list.top.value, 0.01f)
        rule.onNodeWithTag("scores.list").performScrollToNode(hasTestTag("scores.game.game-20"))
        assertEquals(header, rule.onNodeWithTag("scores.header").getUnclippedBoundsInRoot())
    }

    @Test fun headerLight() {
        launch()
        rule.onNodeWithTag("scores.header").captureRoboImage("src/test/screenshots/header/header-light.png")
    }

    @Test fun headerDark() {
        launch(dark = true)
        rule.onNodeWithTag("scores.header").captureRoboImage("src/test/screenshots/header/header-dark.png")
    }
}
