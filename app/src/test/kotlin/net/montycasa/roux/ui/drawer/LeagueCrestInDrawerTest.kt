package net.montycasa.roux.ui.drawer

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import net.montycasa.roux.ui.common.LEAGUE_CREST_TAG
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The drawer names its leagues with their crests.
 *
 * Only a league the app actually carries gets artwork. A league listed but not yet supported must not
 * wear the NHL's crest, or the menu advertises sports it cannot show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeagueCrestInDrawerTest {
    @get:Rule val compose = createComposeRule()

    private fun panel(selected: String) {
        compose.setContent {
            RouxTheme {
                LeaguesPanel(
                    drawer = DrawerUi(selectedLeagueId = selected, favouritesOnly = false),
                    editing = false,
                    onEditing = {},
                    onLeagueOrder = {},
                    onLeagueSelected = {},
                    onFavouritesOnly = {},
                )
            }
        }
    }

    private fun crestCount(): Int = compose.onAllNodesWithTag(LEAGUE_CREST_TAG, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun theSupportedLeagueWearsItsCrest() {
        panel("nhl")

        // The drawer lists every league, so exactly one row may carry artwork: the supported one.
        compose
            .onNodeWithTag("leagues.nhl", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(LEAGUE_CREST_TAG))) { "the NHL row carried no crest" }
        val crests = crestCount()
        assert(crests == 1) { "the drawer drew $crests crests, expected exactly one" }
    }

    @Test
    fun aLeagueWithoutACrestDoesNotBorrowOne() {
        panel("nfl")

        compose.onNodeWithTag("leagues.nfl", useUnmergedTree = true).assertExists()
        compose
            .onNodeWithTag("leagues.nfl", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(LEAGUE_CREST_TAG)).not()) { "the NFL row wore a crest" }
    }
}
