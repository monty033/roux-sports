package net.montycasa.roux.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.findReachViolations
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.scores.waitForFixtureScores
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S3.4: the shell has three destinations, it starts on Scores, and the bar survives the reach audit at
 * the sizes and type scales that break layouts rather than the one size that happens to be on the desk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellTest {
    @get:Rule
    val rule = createComposeRule()

    // Bar assertions match the selectable item rather than the text alone: a destination's own screen
    // may legitimately render the same word, and matching the item keeps the assertion unambiguous.
    @Test
    fun theDrawerOpensOnStandingsAndReordersThere() {
        launchShell()
        barItem("Standings").performClick()
        rule.waitForIdle()

        // Standings has no header button: the edge swipe is the only way in, so this is what "live on every tab" means.
        rule.onRoot().performTouchInput { swipeRight() }
        rule.onNodeWithTag("leagues.drawer").assertIsDisplayed()

        rule.onNodeWithTag("leagues.edit").performClick()
        // The handle only drags after a long press, and only crosses into the next slot past half a row, so the
        // move is measured from the row rather than guessed in dp.
        val rowHeight =
            rule
                .onNodeWithTag("leagues.nfl")
                .fetchSemanticsNode()
                .size.height
                .toFloat()
        val handle = rule.onNodeWithTag("leagues.nfl.handle")
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -(rowHeight * 0.6f)))
        }
        handle.performTouchInput { moveBy(Offset(0f, -4f)) }
        handle.performTouchInput { up() }
        rule.waitForIdle()

        val nflTop =
            rule
                .onNodeWithTag("leagues.nfl")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val nhlTop =
            rule
                .onNodeWithTag("leagues.nhl")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("NFL should now sit above NHL", nflTop < nhlTop)
    }

    @Test
    fun myTeamsIsTheSelectedRowWhenTheDrawerReopens() {
        launchShell()
        rule.onNodeWithContentDescription("Open leagues").performClick()
        rule.onNodeWithText("My Teams").performClick()
        rule.waitUntil(10_000) {
            rule.onNodeWithContentDescription("Open leagues").fetchSemanticsNode()
            true
        }

        rule.onNodeWithContentDescription("Open leagues").performClick()
        rule.onNodeWithTag("leagues.myTeams").assertIsSelected()
        rule.onNodeWithTag("leagues.nhl").assertIsNotSelected()
    }

    private fun barItem(label: String) = rule.onNode(hasText(label) and isSelectable())

    private val declaredPrimaries = listOf("nav.scores", "nav.standings", "nav.teams")

    private fun launchShell(
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            RouxTheme(dark = dark) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    RouxNavHost()
                }
            }
        }
        rule.waitForFixtureScores()
    }

    private fun assertBarReach() {
        // The shell owns navigation; ScoresScreenTest audits the destination's own controls.
        // Keep window coordinates so the bar still has to satisfy the real thumb-zone limits.
        val targets = rule.reachTargets().filter { it.tag.startsWith("nav.") }
        assertEquals(declaredPrimaries.sorted(), targets.filter { it.primary }.map { it.tag }.sorted())
        val window = rule.onRoot().fetchSemanticsNode().size
        val violations =
            findReachViolations(
                controls = targets,
                windowWidthDp = window.width / rule.density.density,
                windowHeightDp = window.height / rule.density.density,
            )
        assertTrue(violations.joinToString("\n") { it.describe() }, violations.isEmpty())
    }

    @Test
    fun theThreeDestinationsAreOnTheBarAndScoresStartsSelected() {
        launchShell()

        barItem("Scores").assertExists()
        barItem("Standings").assertExists()
        barItem("Teams").assertExists()
        barItem("Scores").assertIsSelected()
    }

    @Test
    fun tappingStandingsShowsTheTable() {
        launchShell()

        // The column header exists only when the standings destination is the one on screen.
        rule.onNodeWithTag("standings.header").assertDoesNotExist()
        barItem("Standings").performClick()
        rule.onNodeWithTag("standings.header").assertExists()
        barItem("Standings").assertIsSelected()
        // And the table itself arrives from the feed rather than staying a skeleton.
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText("NHL").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theBarPassesTheReachAuditAtTheReferenceWidth() {
        launchShell()
        assertBarReach()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-420dpi")
    fun theBarPassesTheReachAuditOnASmallPhone() {
        launchShell()
        assertBarReach()
    }

    @Test
    fun theBarPassesTheReachAuditAtDoubleType() {
        launchShell(fontScale = 2f)
        assertBarReach()
    }

    @Test
    fun everyDestinationIsDeclaredFrequentSoR2Applies() {
        // Navigation is the most-used control on every screen. If the flag were dropped, R1 would still
        // pass while the tighter R2 ceiling silently stopped being checked - a test that cannot see the
        // difference is worse than no test.
        launchShell()

        val placements = rule.reachTargets().filter { it.tag.startsWith("nav.") }
        assertEquals(declaredPrimaries.sorted(), placements.map { it.tag }.sorted())
        placements.forEach { placement ->
            assertTrue("${placement.tag} must be frequent so R2 governs it", placement.frequent)
        }
    }

    @Test
    fun everyDestinationIsAtLeastFortyEightDpInBothDirections() {
        launchShell()

        val placements = rule.reachTargets().filter { it.tag.startsWith("nav.") }
        assertEquals(declaredPrimaries.sorted(), placements.map { it.tag }.sorted())
        placements.forEach { placement ->
            assertTrue(
                "${placement.tag} is ${placement.width} x ${placement.height} dp",
                placement.width >= Reach.MIN_TARGET_DP && placement.height >= Reach.MIN_TARGET_DP,
            )
        }
    }

    @Test
    fun drawerMyTeamsEnablesThePersistedFavouritesFilterAndNhlDisablesIt() {
        val app = RuntimeEnvironment.getApplication() as ScoresIntegrationApp
        runBlocking { app.container.preferences.setFavouritesOnly(false) }
        launchShell()
        rule.onNodeWithContentDescription("Open leagues").performClick()
        rule.onNodeWithTag("leagues.drawer").assertWidthIsEqualTo(240.dp)
        rule.onNodeWithText("My Teams").performClick()
        rule.waitUntil(10_000) {
            rule.onNodeWithTag("scores.header").fetchSemanticsNode()
            runBlocking { app.container.preferences.getFavouritesOnly() }
        }
        rule.onNodeWithContentDescription("Favourites only").assertDoesNotExist()
        rule.onNodeWithContentDescription("Open leagues").performClick()
        rule.onNodeWithTag("leagues.nhl").performClick()
        rule.waitUntil(10_000) {
            rule.onNodeWithTag("scores.header").fetchSemanticsNode()
            !runBlocking { app.container.preferences.getFavouritesOnly() }
        }
        rule.onNodeWithContentDescription("Favourites only").assertDoesNotExist()
    }

    @Test
    fun calendarCoversTheShellAndHidesTabsUntilDone() {
        launchShell()
        val window = rule.onRoot().getUnclippedBoundsInRoot()
        rule.onNodeWithContentDescription("Open calendar").performClick()
        assertEquals(window, rule.onNodeWithTag("calendar.overlay").getUnclippedBoundsInRoot())
        barItem("Teams").assertDoesNotExist()
        barItem("Standings").assertDoesNotExist()
        rule.onNodeWithText("DONE").performClick()
        rule.onNodeWithTag("calendar.overlay").assertDoesNotExist()
        barItem("Scores").assertIsSelected()
        barItem("Teams").assertExists()
    }

    @Test
    fun shellDrawerLight() {
        launchShell(dark = false)
        rule.onNodeWithContentDescription("Open leagues").performClick()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/shell-drawer-light.png")
    }

    @Test
    fun shellDrawerDark() {
        launchShell(dark = true)
        rule.onNodeWithContentDescription("Open leagues").performClick()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/shell-drawer-dark.png")
    }

    @Test
    fun shellLight() {
        launchShell(dark = false)
        rule.onRoot().captureRoboImage("src/test/screenshots/shell-light.png")
    }

    @Test
    fun shellDark() {
        launchShell(dark = true)
        rule.onRoot().captureRoboImage("src/test/screenshots/shell-dark.png")
    }
}
