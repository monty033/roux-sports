@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ShotTotals
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The stats card's numbers belong to a club, so each 46 dp column is named over its own values. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class GameStatsHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each club labels its own column above the numbers`() {
        compose.setContent {
            RouxTheme(dark = true) {
                GameStatsRows(game = game, rail = rail)
            }
        }

        // The away club is CBJ and the home club is PHI: swapping them would still draw two labels, so assert both.
        compose.onNodeWithTag("detail.stats.away").assertTextEquals("CBJ")
        compose.onNodeWithTag("detail.stats.home").assertTextEquals("PHI")

        val awayLabel = compose.onNodeWithTag("detail.stats.away").getUnclippedBoundsInRoot()
        val homeLabel = compose.onNodeWithTag("detail.stats.home").getUnclippedBoundsInRoot()
        val awayNumber = compose.onNodeWithText("21").getUnclippedBoundsInRoot()
        val homeNumber = compose.onNodeWithText("28").getUnclippedBoundsInRoot()

        // A column heading sits above its numbers, sharing the column's own edge rather than the row's centre.
        assertTrue(awayLabel.bottom.value <= awayNumber.top.value)
        assertTrue(homeLabel.bottom.value <= homeNumber.top.value)
        assertEquals(awayLabel.left.value, awayNumber.left.value, 1f)
        assertEquals(homeLabel.right.value, homeNumber.right.value, 1f)
    }

    private val game =
        Game(
            id = GameId("2026020188"),
            date = "2026-10-26",
            state = GameState.Final,
            home = TeamRef(TeamKey("PHI"), name = "Flyers"),
            away = TeamRef(TeamKey("CBJ"), name = "Blue Jackets"),
            homeScore = 4,
            awayScore = 3,
        )

    private val rail = GameRightRail(shots = ShotTotals(awayShots = 21, homeShots = 28))
}
