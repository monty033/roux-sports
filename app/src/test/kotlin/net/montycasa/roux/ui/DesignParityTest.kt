package net.montycasa.roux.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.scores.waitForFixtureScores
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pictures the parity claims are measured against: the whole shell at the design's own canvas, in both weights.
 *
 * The shell rather than the scores screen alone, because `scores-screen.html` draws the bottom navigation as part of the
 * frame - a capture without it would leave the most prominent band of the comparison unaccounted for.
 *
 * Data comes from the same fixtures the integration tests use, so these frames are the app a fan sees rather than a
 * hand-built ideal. 411 x 923 is the canvas the design is drawn for; 360 x 780 is the narrow variant the rail's
 * `5.125rem` exists for, and capturing both stops a narrow-only regression from hiding behind a wide baseline.
 *
 * mdpi, so one PNG pixel is one CSS pixel: the baselines land at exactly 411 x 923 and 360 x 780 px and can be laid
 * beside the design file at 1:1. At the device's 420 dpi they came out 2.625x larger - a correct capture of the wrong
 * thing.
 *
 * Record with `recordRoborazziDebug`, then verify.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignParityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun scores411x923Light() = record("scores-411x923-light", dark = false)

    @Test
    fun scores411x923Dark() = record("scores-411x923-dark", dark = true)

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun scores360x780Light() = record("scores-360x780-light", dark = false)

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun scores360x780Dark() = record("scores-360x780-dark", dark = true)

    private fun record(
        name: String,
        dark: Boolean,
    ) {
        // No composable helper: Compose lint demands an uppercase name for one and ktlint forbids it.
        rule.setContent { RouxTheme(dark = dark) { RouxNavHost() } }
        rule.waitForFixtureScores()
        rule.onRoot().captureRoboImage("src/test/screenshots/design-parity/$name.png")
    }
}
