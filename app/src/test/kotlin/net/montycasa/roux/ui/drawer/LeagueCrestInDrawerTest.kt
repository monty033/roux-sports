package net.montycasa.roux.ui.drawer

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import net.montycasa.roux.R
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
    fun everyOpenableLeagueWearsItsCrest() {
        panel("nhl")

        // The drawer lists every league, and exactly the openable ones may carry artwork: hockey and football.
        listOf("nhl", "nfl").forEach { id ->
            compose
                .onNodeWithTag("leagues.$id", useUnmergedTree = true)
                .assert(hasAnyDescendant(hasTestTag(LEAGUE_CREST_TAG))) { "the ${id.uppercase()} row carried no crest" }
        }
        val crests = crestCount()
        assert(crests == 2) { "the drawer drew $crests crests, expected exactly two" }
    }

    @Test
    fun anUnopenableLeagueNeverWearsACrestEvenWhenTheAppCanDrawOne() {
        // Football has crest artwork, so its crest alone cannot keep the row honest. What may not borrow a crest is
        // any league the app cannot open: the drawer must not advertise a sport it has no scores for.
        compose.setContent {
            RouxTheme {
                LeaguesPanel(
                    drawer = DrawerUi(selectedLeagueId = "nhl", favouritesOnly = false),
                    editing = false,
                    onEditing = {},
                    onLeagueOrder = {},
                    onLeagueSelected = {},
                    onFavouritesOnly = {},
                    catalog =
                        listOf(
                            DrawerLeague("nhl", "NHL", R.string.drawer_hockey, true),
                            DrawerLeague("nfl", "NFL", R.string.drawer_football, false),
                        ),
                )
            }
        }

        compose
            .onNodeWithTag("leagues.nfl", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(LEAGUE_CREST_TAG)).not()) { "an unopenable league wore a crest" }
    }

    @Test
    fun aLeagueWithoutACrestDoesNotBorrowOne() {
        panel("mlb")

        compose.onNodeWithTag("leagues.mlb", useUnmergedTree = true).assertExists()
        compose
            .onNodeWithTag("leagues.mlb", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(LEAGUE_CREST_TAG)).not()) { "the MLB row wore a crest" }
    }
}
