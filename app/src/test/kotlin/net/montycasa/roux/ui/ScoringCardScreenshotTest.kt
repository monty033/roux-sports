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
import net.montycasa.roux.ui.detail.ScoringSummary
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scoring summary is taller than the detail page's viewport, so the page-level matrix captures can only ever
 * photograph part of it: Compose scrolls an anchor just into view, which puts a card of this height half under the
 * fold either way. This records the card on its own at phone width so the whole thing — the period heading that names
 * the score columns, every goal row and the divider between periods — is reviewable in one image, in both themes.
 *
 * The data is the same real UTA @ PHI fixture the matrix uses, so the two cannot drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScoringCardScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun scoringCardInBothThemes() {
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        ScoringSummary(
                            game = AuditStates.detailGame,
                            landing = GameLanding(goals = AuditStates.fullGoals),
                        )
                    }
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/scoring-card-$theme.png")
        }
    }
}
