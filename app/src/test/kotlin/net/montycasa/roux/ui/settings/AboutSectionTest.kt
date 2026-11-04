package net.montycasa.roux.ui.settings

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import net.montycasa.roux.BuildConfig
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutSectionTest {
    @get:Rule val rule = createComposeRule()

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private fun showSection() {
        rule.setContent { RouxTheme { AboutSection() } }
    }

    @Test fun theSectionNamesTheBuildVersionAndTheLicence() {
        showSection()

        rule.onNodeWithText("Roux Sports ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        rule.onNodeWithText("GPL-3.0-or-later", substring = true).assertIsDisplayed()
    }

    @Test fun theDisclaimerIsTheExactRequiredSentences() {
        showSection()

        rule
            .onNodeWithText(
                "Not affiliated with or endorsed by the NHL. Team names and logos are trademarks of the NHL " +
                    "and its teams.",
            ).assertIsDisplayed()
    }

    @Test fun theSourceControlIsAtLeastTheMinimumTargetHeight() {
        showSection()

        rule.onNodeWithText("Source code").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test fun theSourceControlHandsTheProjectUrlToABrowser() {
        showSection()

        rule.onNodeWithText("Source code").performClick()

        rule.runOnIdle {
            val started = shadowOf(application).nextStartedActivity
            assertNotNull("the source control must start an activity", started)
            // An implicit ACTION_VIEW for an https URL is the browser's request to make, not this app's:
            // no HTTP client is involved here and the test never touches the network.
            assertEquals(Intent.ACTION_VIEW, started.action)
            assertEquals("https://github.com/monty033/roux-sports".toUri(), started.data)
            assertNull("the intent must be left for the browser to resolve", started.component)
        }
    }
}
