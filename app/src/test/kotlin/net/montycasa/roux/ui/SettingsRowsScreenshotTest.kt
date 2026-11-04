package net.montycasa.roux.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.ui.settings.SettingsScreen
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settings rows at the size of the phone they are used on.
 *
 * The matrix renders 320x470, where this page is mostly About, so it cannot show the two rows that changed. Recorded
 * at 393x881 in both themes: name on the left, options in the same line, the chosen one filled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h881dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theTwoSettingsRowsInBothThemes() {
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        SettingsScreen(mode = ThemeMode.System, onMode = {}, onBack = {})
                    }
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/settings-rows-$theme.png")
        }
    }
}
