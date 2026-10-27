package net.montycasa.roux.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S3.4: the shell has three destinations, it starts on Scores, and the bar survives the reach audit at
 * the sizes and type scales that break layouts rather than the one size that happens to be on the desk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellTest {
    @get:Rule
    val rule = createComposeRule()

    // A destination's name appears twice on screen - on its bar item and, once selected, as the
    // placeholder heading. Bar assertions therefore match the selectable item, not the text alone.
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
    fun tappingStandingsShowsItsPlaceholder() {
        launchShell()

        // Both the bar label and the placeholder heading read "Standings", so the assertion is on the
        // placeholder's own sentence - which exists only when the destination is actually shown.
        rule.onNodeWithText("League standings will appear here.").assertDoesNotExist()
        barItem("Standings").performClick()
        rule.onNodeWithText("League standings will appear here.").assertExists()
        barItem("Standings").assertIsSelected()
    }

    @Test
    fun theBarPassesTheReachAuditAtTheReferenceWidth() {
        launchShell()
        rule.assertPrimaryControlsReachable(*declaredPrimaries.toTypedArray())
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-420dpi")
    fun theBarPassesTheReachAuditOnASmallPhone() {
        launchShell()
        rule.assertPrimaryControlsReachable(*declaredPrimaries.toTypedArray())
    }

    @Test
    fun theBarPassesTheReachAuditAtDoubleType() {
        launchShell(fontScale = 2f)
        rule.assertPrimaryControlsReachable(*declaredPrimaries.toTypedArray())
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
