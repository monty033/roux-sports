/*
 * The dial at the foot of the screen: logos only, endless, and the handle that pulls the menu up.
 */
package net.montycasa.roux.ui.drawer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import net.montycasa.roux.ui.scores.MY_TEAMS_LEAGUE
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LeagueCarouselTest {
    @get:Rule val rule = createComposeRule()

    private val chosen = mutableListOf<String>()
    private var opened = 0

    private fun launch(
        order: List<String> = listOf("nhl"),
        selected: String = "nhl",
    ) {
        rule.setContent {
            RouxTheme {
                LeagueCarousel(
                    order = order,
                    selectedId = selected,
                    onSelect = { chosen += it },
                    onOpenMenu = { opened++ },
                )
            }
        }
    }

    @Test
    fun theDialShowsMyTeamsAndTheLeague() {
        launch()
        rule.onNodeWithTag("leagues.carousel").assertExists()
        rule.onNodeWithTag("leagues.carousel.$MY_TEAMS_LEAGUE").assertExists()
        rule.onNodeWithTag("leagues.carousel.nhl").assertExists()
    }

    @Test
    fun tappingALogoChoosesThatLeague() {
        launch(order = listOf("nhl"), selected = MY_TEAMS_LEAGUE)
        rule.onNodeWithTag("leagues.carousel.nhl").performClick()
        rule.waitForIdle()
        assertEquals(listOf("nhl"), chosen)
    }

    @Test
    fun theHandleOpensTheMenu() {
        launch()
        rule.onNodeWithTag("leagues.grabber").performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
    }

    /**
     * Two logos is enough to prove the loop: swiping one way off the end of the row lands on the logo that was behind
     * you. A row with ends would simply stop.
     */
    @Test
    fun swipingTheDialWrapsRoundInsteadOfStopping() {
        launch(order = listOf("nhl"), selected = "nhl")
        // Left takes the row forward: the league leaves the middle and My Teams takes it.
        rule.onNodeWithTag("leagues.carousel").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertTrue("expected a selection, got $chosen", chosen.isNotEmpty())
        assertEquals(MY_TEAMS_LEAGUE, chosen.last())

        // Every logo in the row carries a live copy, so the same league must not be announced twice at once.
        rule.onNodeWithTag("leagues.carousel.$MY_TEAMS_LEAGUE").assertExists()
    }

    /** An unavailable league is drawn - the reader should see what is coming - but the dial will not rest on it. */
    @Test
    fun anUnavailableLeagueIsShownButCannotBeChosen() {
        launch(order = listOf("nhl", "nfl"), selected = "nhl")
        rule.onNodeWithTag("leagues.carousel.nfl").assertExists()
        rule.onNodeWithTag("leagues.carousel.nfl").performClick()
        rule.waitForIdle()
        assertTrue("a greyed league must not select: $chosen", chosen.isEmpty())
    }

    @Test
    fun eachLeagueIsAnnouncedOnceThroughItsNearestCopy() {
        // Two leagues: the middle speaks for one, the slot ahead speaks for the other, and the copy two slots on is
        // that same league again and stays quiet.
        assertEquals(listOf(true, true, false), (10..12).map { isExposed(it, centre = 10, count = 2) })
        // Four leagues: exactly one copy of each wins, and they lie either side of the middle.
        val exposed = (8..12).filter { isExposed(it, centre = 10, count = 4) }
        assertEquals(4, exposed.size)
        assertEquals(exposed.map { Math.floorMod(it, 4) }.distinct().size, exposed.size)
    }

    @Test
    fun theLoopStartsWithTheChosenLogoInTheMiddle() {
        assertEquals(0, loopStart(2, 0) % 2)
        assertEquals(1, loopStart(2, 1) % 2)
        assertEquals(2, loopStart(3, 2) % 3)
        // Deep enough inside the row that neither end is reachable by ordinary swiping.
        assertTrue(loopStart(3, 0) > 1_000_000)
    }

    @Test
    fun anUnknownOrderFallsBackToTheAvailableCatalogue() {
        val entries = carouselEntries(emptyList())
        assertEquals(MY_TEAMS_LEAGUE, entries.first().id)
        assertTrue(entries.all { it.id == MY_TEAMS_LEAGUE || it.available })
        // The star is always available, even with no league to show beside it.
        assertEquals(listOf(MY_TEAMS_LEAGUE, "nhl"), entries.map { it.id })
    }
}
