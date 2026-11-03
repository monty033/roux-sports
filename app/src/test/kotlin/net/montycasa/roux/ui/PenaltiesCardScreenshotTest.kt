package net.montycasa.roux.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.ui.detail.PenaltiesSummary
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The penalties summary is taller than the detail page's viewport, so a page-level screenshot can only photograph
 * part of it. This records the whole card on its own at phone width so every block, the fighting majors that share a
 * clock and the one served line are reviewable in a single image, in both themes.
 *
 * The data is the same real UTA @ PHI fixture the matrix uses, so the two cannot drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PenaltiesCardScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun penaltiesCardInBothThemes() {
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        PenaltiesSummary(
                            game = AuditStates.detailGame,
                            landing = GameLanding(penalties = AuditStates.fullPenalties),
                        )
                    }
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/penalties-card-$theme.png")
        }
    }
}
