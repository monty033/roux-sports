package net.montycasa.roux.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P6: one golden per (screen state, theme) for every state in [AuditStates]. The state list is shared with
 * ContrastAndTypeTest, so a screen added to the audit is photographed here without editing this file.
 *
 * Goldens land in app/src/test/screenshots/matrix/<state>-<theme>.png. Changing a golden is a claim that the UI
 * changed; read the diff image before re-recording. See README "Screenshot baselines".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotMatrixTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everyAuditStateInBothThemes() {
        val screens = AuditStates.screens()
        val index = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                screens[index.intValue].content()
            }
        }
        var captured = 0
        screens.forEachIndexed { i, screen ->
            listOf(false, true).forEach { isDark ->
                val theme = if (isDark) "dark" else "light"
                rule.runOnIdle {
                    index.intValue = i
                    dark.value = isDark
                }
                rule.waitForIdle()
                val file = screen.name.replace('.', '-') + "-" + theme
                rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file.png")
                captured++
            }
        }
        assertEquals("one golden per (state, theme)", screens.size * 2, captured)
    }
}
