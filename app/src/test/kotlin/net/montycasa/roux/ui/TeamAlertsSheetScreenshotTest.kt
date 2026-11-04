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
 * The alert sheet at the size of the phone it is used on, in the two states that have to be told apart.
 *
 * The matrix renders 320x470, where the sheet's rows cannot all fit and the capture shows it mid-scroll - which looks
 * like clipping whether or not anything is clipped. This records the whole sheet at 393x881, for every switch off
 * (where the reminder's chosen answer is Off and must not wear the accent that means "on") and for a reminder set to
 * 30 minutes with the other alerts on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h881dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TeamAlertsSheetScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theSheetOffAndPartlyOnInBothThemes() {
        val row = TeamRef(TeamKey("ANA"), name = "Anaheim Ducks")
        val dark = mutableStateOf(false)
        val alerts = mutableStateOf(TeamAlerts(team = row))
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
                                    alerts = alerts.value,
                                ),
                            onToggle = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }

        // Default: nothing is on, so "Off" is the chosen answer.
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/team-alerts-sheet-off-light.png")

        rule.runOnIdle {
            alerts.value =
                TeamAlerts(
                    team = row,
                    start = true,
                    goals = true,
                    intermission = true,
                    upcoming = true,
                    leadMinutes = 30,
                )
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/team-alerts-sheet-light.png")

        rule.runOnIdle { dark.value = true }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/team-alerts-sheet-dark.png")
    }
}
