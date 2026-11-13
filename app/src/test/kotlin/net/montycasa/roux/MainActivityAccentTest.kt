package net.montycasa.roux

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.domain.model.AccentColor
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app's own theme applies the stored accent when it starts.
 *
 * AccentRenderingTest hands Blue straight to RouxTheme, so it cannot see whether MainActivity passes the observed
 * value through. This stores Blue in the real preference store, launches MainActivity the way the app starts, and
 * reads the pixels of one accent-coloured element. The wordmark is an orange PNG by design, so nothing here asserts
 * over the whole frame: the element is the nav menu glyph, which is tinted with the scheme's primary.
 *
 * Light is stored explicitly so the glyph's colour is the light primary, `lightSignature`, and not something that
 * depends on the host's system theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityAccentTest {
    private val app = RuntimeEnvironment.getApplication() as ScoresIntegrationApp

    /** Runs before the activity rule, so MainActivity's first read of the store already sees the stored choice. */
    private val seedStore =
        TestRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    runBlocking {
                        app.container.preferences.setThemeMode(ThemeMode.Light)
                        app.container.preferences.setAccentColor(AccentColor.Blue)
                    }
                    base.evaluate()
                }
            }
        }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(seedStore).around(composeRule)

    private val blueSignature = Color(0xFF0F5FFF)
    private val shippedOrangeSignature = Color(0xFFC93A06)

    private fun count(
        pixels: PixelMap,
        colour: Color,
    ): Int {
        var n = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y] == colour) n++
            }
        }
        return n
    }

    @Test
    fun navMenuGlyphIsPaintedWithTheStoredAccent() {
        composeRule.waitForIdle()
        val pixels = composeRule.onNodeWithTag("nav.menu.icon", useUnmergedTree = true).captureToImage().toPixelMap()
        val blue = count(pixels, blueSignature)
        val orange = count(pixels, shippedOrangeSignature)
        assertTrue("the stored Blue accent was not painted on nav.menu.icon (Blue pixels: $blue)", blue > 0)
        assertTrue("$orange pixel(s) of the shipped orange were painted on nav.menu.icon under Blue", orange == 0)
    }
}
