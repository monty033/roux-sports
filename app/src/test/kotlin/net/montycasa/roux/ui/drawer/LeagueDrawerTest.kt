package net.montycasa.roux.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.ui.reach.findReachViolations
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.scores.ScoresUiState
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LeagueDrawerTest {
    @get:Rule val rule = createComposeRule()
    private val state =
        mutableStateOf(
            ScoresUiState("2026-10-27", today = "2026-10-27", favouritesOnly = false, favouriteCount = 6, loadedDate = "2026-10-27"),
        )

    private fun launch(
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            RouxTheme(dark) {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    LeagueNavigationDrawer(
                        rememberDrawerState(DrawerValue.Open),
                        state.value,
                        { only -> state.value = state.value.copy(favouritesOnly = only) },
                    ) {
                        Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF08090B) else Color(0xFFF2F2EF)))
                    }
                }
            }
        }
    }

    @Test
    fun lightThemeKeepsTheDarkBrandPanelAndMeasuredTargets() {
        launch()
        rule.onNodeWithTag("leagues.drawer").assertWidthIsEqualTo(240.dp)
        val pixels = rule.onNodeWithTag("leagues.drawer").captureToImage().toPixelMap()
        assertEquals(Color(0xFF08090B), pixels[2, 2])
        rule.onNodeWithTag("leagues.myTeams").assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("leagues.nhl").assertHeightIsEqualTo(56.dp).assertIsSelected()
        rule.onNodeWithTag("leagues.wordmark").assertWidthIsEqualTo(108.dp)
        rule.onNodeWithText("HOCKEY").assertExists()
        rule.onNodeWithText("2026–27").assertExists()
        assertPanelReach()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun drawerTargetsSurviveTheSmallPhoneAtDoubleType() {
        launch(fontScale = 2f)
        rule.onNodeWithTag("leagues.drawer").assertWidthIsEqualTo(240.dp)
        rule.onNodeWithTag("leagues.myTeams").assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("leagues.nhl").assertHeightIsEqualTo(56.dp)
        assertPanelReach()
    }

    private fun assertPanelReach() {
        // Audit the panel's targets. M3's full-window dismiss scrim extends behind the sheet;
        // its semantics bounds overlap the panel even though the sheet owns those hit tests.
        val targets = rule.reachTargets().filter { it.tag.startsWith("leagues.") }
        assertEquals(listOf("leagues.myTeams", "leagues.nhl"), targets.map { it.tag })
        assertTrue(targets.none { it.primary })
        val root = rule.onRoot().fetchSemanticsNode().size
        val violations =
            findReachViolations(
                targets,
                windowWidthDp = root.width / rule.density.density,
                windowHeightDp =
                    root.height / rule.density.density,
            )
        assertTrue(violations.joinToString { it.describe() }, violations.isEmpty())
    }

    @Test
    fun myTeamsCarriesTheCurrentCountSelectsFavouritesAndCloses() {
        launch()
        rule.onNodeWithText("6", useUnmergedTree = true).assertExists()
        rule.runOnIdle { state.value = state.value.copy(favouriteCount = 7) }
        rule.onNodeWithText("7", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("leagues.myTeams").performClick()
        rule.waitForIdle()
        assertEquals(true, state.value.favouritesOnly)
        rule.onNodeWithText("My Teams").assertIsNotDisplayed()
    }

    @Test
    fun nhlSelectsAllGamesAndCloses() {
        state.value = state.value.copy(favouritesOnly = true)
        launch()
        rule.onNodeWithTag("leagues.nhl").performClick()
        rule.waitForIdle()
        assertEquals(false, state.value.favouritesOnly)
        rule.onNodeWithText("NHL").assertIsNotDisplayed()
    }

    @Test
    fun settingsIsDeliberatelyAbsent() {
        launch()
        rule.onNodeWithText("Settings", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun editingAndItsHintAreDeliberatelyAbsentForOneLeague() {
        launch()
        rule.onNodeWithContentDescription("Edit leagues").assertDoesNotExist()
        rule.onNodeWithText("Edit", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithText("Tap Edit to hide or reorder leagues").assertDoesNotExist()
    }

    @Test
    fun unsupportedLeaguesAreDeliberatelyAbsent() {
        launch()
        // Drawn but not offered: the file lists these leagues, the app has no data for them yet, so they
        // are greyed (.ditem.hid) and disabled rather than hidden or, worse, tappable into an empty day.
        listOf("NFL" to "FOOTBALL", "MLB" to "BASEBALL").forEach { (league, sport) ->
            rule.onNodeWithText(league).assertExists()
            rule.onNodeWithText(sport).assertExists()
            rule.onNodeWithTag("leagues.${league.lowercase()}").assertIsNotEnabled()
        }
        // Leagues the app has never mentioned stay absent.
        listOf("NBA", "WNBA", "MLS").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun seasonIsOmittedUntilADayIsLoaded() {
        state.value = state.value.copy(loadedDate = null)
        launch()
        rule.onNodeWithTag("leagues.season").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(loadedDate = "bad-date") }
        rule.onNodeWithTag("leagues.season").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(loadedDate = "2027-01-04") }
        rule.onNodeWithText("2026–27").assertExists()
    }

    @Test fun drawerLight() {
        launch()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/drawer-light.png")
    }

    @Test fun drawerDark() {
        launch(dark = true)
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/drawer-dark.png")
    }
}
