package net.montycasa.roux.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.ui.league.LeagueNotReady
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The placeholder is the only thing standing between an NFL key and an NHL endpoint, so both halves are pinned. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeagueNotReadyTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun onlyNhlHasPages() {
        assertTrue(leagueHasPages(League.NHL))
        assertFalse(leagueHasPages(League.NFL))
    }

    @Test
    fun thePlaceholderSaysItsNotReadyAndBackCallsBack() {
        var backs = 0
        rule.setContent { LeagueNotReady(onBack = { backs++ }) }

        val copy =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.league_page_not_ready)
        rule.onNodeWithText(copy).assertIsDisplayed()
        rule.onNodeWithTag("league.notReady.back").assertIsDisplayed().performClick()
        assertEquals(1, backs)
    }

    /** Back is a reach target on the app's convention, not a primary control, so the screen declares none. */
    @Test
    fun thePlaceholderPassesTheReachAudit() {
        rule.setContent { LeagueNotReady(onBack = {}) }
        rule.assertPrimaryControlsReachable()
    }
}
