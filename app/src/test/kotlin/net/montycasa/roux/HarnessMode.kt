package net.montycasa.roux

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput

/**
 * My Teams is the stored default and hides Standings, because a cross-league slate has no table to show.
 * A test that wants the full bar chooses a league first, through the control a reader uses: the menu is pulled up
 * from the bar, and NHL is one row inside it.
 */
internal fun ComposeTestRule.enterLeagueMode() {
    waitForIdle()
    onNodeWithTag("nav.bar").performTouchInput {
        down(center)
        moveBy(Offset(0f, -2000f))
        up()
    }
    waitForIdle()
    onNodeWithTag("leagues.nhl").performClick()
    waitForIdle()
}
