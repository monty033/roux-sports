package net.montycasa.roux.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.ui.player.PlayerDetailScreen
import net.montycasa.roux.ui.team.TeamDetailScreen
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player and club pages' non-opening tabs. Their tab selection is internal view state, so the shared
 * [AuditStates] list cannot put them in the screenshot matrix; this file taps each tab into view and records the same
 * two themes the matrix does.
 *
 * The opening tabs (Season and Schedule) are photographed by [ScreenshotMatrixTest] through the shared state list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailTabsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val dark = mutableStateOf(false)

    @Test
    fun playerCareerGameLogAndInfoTabsInBothThemes() {
        rule.setContent {
            RouxTheme(dark = dark.value) {
                PlayerDetailScreen(
                    state = DetailPageFixtures.playerState,
                    currentSeason = DetailPageFixtures.CURRENT_SEASON,
                    today = DetailPageFixtures.TODAY,
                    onBack = {},
                    onRetry = {},
                    onGameClick = {},
                )
            }
        }

        captureTab("player.tab.Career", "player-career")
        captureTab("player.tab.GameLog", "player-gamelog")
        captureTab("player.tab.Info", "player-info")
    }

    @Test
    fun clubPlayersAndTeamStatsTabsInBothThemes() {
        rule.setContent {
            RouxTheme(dark = dark.value) {
                TeamDetailScreen(
                    state = DetailPageFixtures.teamState,
                    onBack = {},
                    now = DetailPageFixtures.NOW,
                )
            }
        }

        captureTab("team.tab.Players", "team-players")
        captureTab("team.tab.TeamStats", "team-stats")
    }

    @Test
    fun clubPagesNameTheSeasonWhenTheyShowLastSeasonsDataInBothThemes() {
        rule.setContent {
            RouxTheme(dark = dark.value) {
                TeamDetailScreen(
                    state = DetailPageFixtures.teamStateLastSeason,
                    onBack = {},
                    now = DetailPageFixtures.NOW,
                )
            }
        }

        captureBothThemes("team-schedule-last-season")
        captureTab("team.tab.Players", "team-players-last-season")
        captureTab("team.tab.TeamStats", "team-stats-last-season")
    }

    private fun captureTab(
        tag: String,
        file: String,
    ) {
        // Invoke the tab's own action rather than a touch: a tap starts a ripple, and the two theme captures then
        // disagree about how far through it they were.
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        captureBothThemes(file)
    }

    private fun captureBothThemes(file: String) {
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-$theme.png")
        }
    }
}
