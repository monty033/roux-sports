package net.montycasa.roux

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * S3.4 replaced the blank title screen with the shell, so "does the activity start" is no longer a
     * question about a label. It launches the three-destination bar with Scores selected, which is what a
     * person sees when they open the app.
     */
    @Test
    fun launchesTheShellOnScores() {
        composeRule.onNode(hasText("Scores") and isSelectable()).assertIsDisplayed().assertIsSelected()
        composeRule.onNode(hasText("Standings") and isSelectable()).assertIsDisplayed()
        composeRule.onNode(hasText("Teams") and isSelectable()).assertIsDisplayed()
    }
}
