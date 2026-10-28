package net.montycasa.roux.ui.common

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant

/**
 * The banner reads on the device's clock, in the device's 12/24-hour convention, with no zone label - the decision
 * recorded in the stage plan. The day it compares against is injected, so this never depends on when it runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StaleBannerTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var originalZone: TimeZone

    @Before
    fun pinTheDeviceToVancouver() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"))
    }

    @After
    fun restore() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun todayShowsTheDeviceClock() {
        // 21:00Z is 2:00 PM in Vancouver; an hour earlier is 1:00 PM there.
        banner(now = "2026-10-26T21:00:00Z", since = "2026-10-26T20:00:00Z")
        rule.onNodeWithText("Updated 1:00 PM").assertExists()
    }

    @Test
    fun yesterdaySaysYesterday() {
        banner(now = "2026-10-26T21:00:00Z", since = "2026-10-25T20:00:00Z")
        rule.onNodeWithText("yesterday", substring = true).assertExists()
    }

    @Test
    fun anythingOlderShowsItsDateRatherThanImpersonatingToday() {
        banner(now = "2026-10-28T21:00:00Z", since = "2026-10-26T20:00:00Z")
        val text =
            rule
                .onNodeWithText("Updated", substring = true)
                .fetchSemanticsNode()
                .config
                .toString()
        assertTrue("a two-day-old cache must carry its date, got: $text", text.contains("Oct 26"))
    }

    private fun banner(
        now: String,
        since: String,
    ) {
        rule.setContent {
            RouxTheme(dark = false) {
                StaleBanner(
                    since = Instant.parse(since),
                    onRetry = {},
                    now = Instant.parse(now),
                )
            }
        }
    }
}
