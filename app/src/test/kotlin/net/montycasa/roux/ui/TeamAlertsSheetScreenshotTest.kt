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
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.ui.settings.SettingsScreen
import net.montycasa.roux.ui.teams.TeamAlerts
import net.montycasa.roux.ui.teams.TeamsScreen
import net.montycasa.roux.ui.teams.TeamsUiState
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The five-switch alert sheet, at the size of the phone it is used on.
 *
 * The matrix renders 320x470, where five switches with three headings cannot fit and the capture shows the sheet
 * mid-scroll - which looks like clipping whether or not anything is clipped. This records the whole sheet at 393x881
 * in both themes, so the thing Patrick judges is the thing his phone draws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h881dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TeamAlertsSheetScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theFiveSwitchSheetInBothThemes() {
        val row = TeamRef(TeamKey("ANA"), name = "Anaheim Ducks")
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        TeamsScreen(
                            state =
                                TeamsUiState(
                                    rows = listOf(row),
                                    favourites = setOf(row.key),
                                    isRefreshing = false,
                                    alerts =
                                        TeamAlerts(
                                            team = row,
                                            start = true,
                                            goals = true,
                                            intermission = true,
                                        ),
                                ),
                            onToggle = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/team-alerts-sheet-$theme.png")
        }
    }

    @Test
    fun theLeadTimeSettingInBothThemes() {
        // The matrix cannot show this either: at 320x470 only the first option is above the fold, so the choice the
        // screen exists for looks like a single row. Recorded whole, at phone size, in both themes.
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(mode = ThemeMode.System, onMode = {}, onBack = {}, leadMinutes = 30)
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/settings-lead-time-$theme.png")
        }
    }
}
