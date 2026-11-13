package net.montycasa.roux.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import net.montycasa.roux.ui.scores.ScoresHeader
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test
    fun theCrestComesFromTheAuthorisedHostAndNothingElse() {
        listOf(false, true).forEach { dark ->
            val url = leagueCrestUrl("nhl", dark)!!
            assert(url.startsWith("https://assets.nhle.com/logos/nhl/svg/")) { "unexpected host in $url" }
        }
    }
}

/** The chip in the top bar carries the same crest, and only when there is one to carry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeagueCrestInHeaderTest {
    @get:Rule val compose = createComposeRule()

    private fun header(
        leagueLabel: String?,
        leagueId: String?,
    ) {
        compose.setContent {
            RouxTheme {
                ScoresHeader(
                    liveCount = 0,
                    modifier = Modifier.fillMaxWidth(),
                    leagueLabel = leagueLabel,
                    leagueId = leagueId,
                    statusInset = WindowInsets(0, 0, 0, 0),
                )
            }
        }
    }

    private fun crestCount(): Int = compose.onAllNodesWithTag(LEAGUE_CREST_TAG, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun theChipCarriesTheCrest() {
        header(leagueLabel = "NHL", leagueId = "nhl")

        compose.onNodeWithTag("scores.leagueChip").assertExists()
        val crests = crestCount()
        assert(crests == 1) { "the chip drew $crests crests, expected exactly one" }
    }

    @Test
    fun aLeagueWithoutACrestKeepsThePlainPill() {
        header(leagueLabel = "NFL", leagueId = "nfl")

        compose.onNodeWithTag("scores.leagueChip").assertExists()
        val crests = crestCount()
        assert(crests == 0) { "a league with no crest drew $crests crest(s)" }
    }

    @Test
    fun aChipWithNoLeagueAtAllDrawsNoCrest() {
        header(leagueLabel = "NHL", leagueId = null)

        val crests = crestCount()
        assert(crests == 0) { "a chip with no league drew $crests crest(s)" }
    }
}
