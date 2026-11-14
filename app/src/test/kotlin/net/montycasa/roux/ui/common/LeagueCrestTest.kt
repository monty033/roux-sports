package net.montycasa.roux.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import net.montycasa.roux.ui.drawer.leagueCrestId
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.scores.ScoresHeader
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

const val CHIP_STAR_TAG = "scores.leagueChip.star"

/**
 * The crest is the league's, and it follows the theme the user is actually looking at.
 *
 * The two published variants are not a tint of one another - the light crest is a dark disc - so the
 * wrong one on the wrong surface is a black square on a black background, not a slightly off colour.
 */
class LeagueCrestUrlTest {
    @Test
    fun theLightCrestIsDrawnOnALightSurface() {
        val url = leagueCrestUrl("nhl", dark = false)

        assert(url != null) { "the NHL had no light crest" }
        assert(url!!.endsWith("NHL_light.svg")) { "a light surface drew $url" }
    }

    @Test
    fun theDarkCrestIsDrawnOnADarkSurface() {
        val url = leagueCrestUrl("nhl", dark = true)

        assert(url != null) { "the NHL had no dark crest" }
        assert(url!!.endsWith("NHL_dark.svg")) { "a dark surface drew $url" }
    }

    @Test
    fun aLeagueTheAppDoesNotCarryHasNoCrest() {
        listOf("nfl", "mlb", "nba", "myTeams", "").forEach { id ->
            val url = leagueCrestUrl(id, dark = false)
            assert(url == null) { "$id was given a crest: $url" }
        }
    }

    /**
     * The favourites filter is not a league, so it borrows no league's crest: the chip marks it with a star.
     */
    @Test
    fun theFavouritesFilterIsNotALeagueWithACrest() {
        val filtered = DrawerUi(selectedLeagueId = "myTeams", favouritesOnly = true)

        assert(leagueCrestId(filtered) == "myTeams") { "the filter was given ${leagueCrestId(filtered)}" }
        assert(leagueCrestUrl(leagueCrestId(filtered), dark = false) == null) { "the filter drew a crest" }
    }

    @Test
    fun theCrestComesFromTheAuthorisedHostAndNothingElse() {
        listOf(false, true).forEach { dark ->
            val url = leagueCrestUrl("nhl", dark)!!
            assert(url.startsWith("https://assets.nhle.com/logos/nhl/svg/")) { "unexpected host in $url" }
        }
    }
}

/** The chip carries a league's crest, and a star - not a crest - while the favourites filter is on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeagueCrestInHeaderTest {
    @get:Rule val compose = createComposeRule()

    private fun header(
        leagueLabel: String?,
        leagueId: String?,
        favouritesOnly: Boolean = false,
    ) {
        compose.setContent {
            RouxTheme {
                ScoresHeader(
                    liveCount = 0,
                    modifier = Modifier.fillMaxWidth(),
                    leagueLabel = leagueLabel,
                    leagueId = leagueId,
                    favouritesOnly = favouritesOnly,
                    statusInset = WindowInsets(0, 0, 0, 0),
                )
            }
        }
    }

    private fun crestCount(): Int = compose.onAllNodesWithTag(LEAGUE_CREST_TAG, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun starCount(): Int = compose.onAllNodesWithTag(CHIP_STAR_TAG, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun theChipCarriesTheCrest() {
        header(leagueLabel = "NHL", leagueId = "nhl")

        compose.onNodeWithTag("scores.leagueChip").assertExists()
        val crests = crestCount()
        assert(crests == 1) { "the chip drew $crests crests, expected exactly one" }
        assert(starCount() == 0) { "the league chip drew a star" }
    }

    @Test
    fun aLeagueWithoutACrestKeepsThePlainPill() {
        header(leagueLabel = "NFL", leagueId = "nfl")

        compose.onNodeWithTag("scores.leagueChip").assertExists()
        val crests = crestCount()
        assert(crests == 0) { "a league with no crest drew $crests crest(s)" }
        assert(starCount() == 0) { "a league with no crest drew a star" }
    }

    @Test
    fun theFavouritesChipCarriesAStarAndNoCrest() {
        // What the nav host passes while the filter is on: label My Teams, the filter's own id, favourites true.
        // A league badge beside "My Teams" would say something untrue - the filter is not a league.
        header(leagueLabel = "My Teams", leagueId = "myTeams", favouritesOnly = true)

        compose.onNodeWithText("My Teams").assertExists()
        val crests = crestCount()
        val stars = starCount()
        assert(crests == 0) { "the My Teams chip drew $crests crest(s)" }
        assert(stars == 1) { "the My Teams chip drew $stars stars, expected exactly one" }
    }

    @Test
    fun aChipWithNoLeagueAtAllDrawsNoCrest() {
        header(leagueLabel = "NHL", leagueId = null)

        val crests = crestCount()
        assert(crests == 0) { "a chip with no league drew $crests crest(s)" }
        assert(starCount() == 0) { "a chip with no league drew a star" }
    }
}
