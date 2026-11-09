package net.montycasa.roux.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pre-game page at the narrowest phone width and font scale 2, in both themes: the sub-navigation pills and the
 * two-club cards at their largest. Each theme gets the top of the page and the goaltenders card at the bottom.
 *
 * Goldens land in app/src/test/screenshots/matrix/. Changing one is a claim that the UI changed; read the diff first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreGameLargeTextScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun preGameAt360dpWithFontScale2() {
        val screen = AuditStates.screens().single { it.name == "detail.pregame" }
        val dark = mutableStateOf(false)
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                RouxTheme(dark = dark.value) {
                    screen.content()
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            runCatching {
                rule.onNodeWithTag("detail.hero").performScrollTo()
                rule.waitForIdle()
            }
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/detail-pregame-w360-font2-$theme-top.png")
            rule.onNodeWithTag("detail.section.goaltenders").performScrollTo()
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/detail-pregame-w360-font2-$theme-goaltenders.png")
        }
    }
}
