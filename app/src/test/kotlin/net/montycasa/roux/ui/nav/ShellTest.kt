package net.montycasa.roux.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.findReachViolations
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.scores.ScoreFixture
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.scores.waitForFixtureScores
import net.montycasa.roux.ui.teams.waitForTeamsRows
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S3.4: the shell has three destinations, it starts on Scores, and the bar survives the reach audit at
 * the sizes and type scales that break layouts rather than the one size that happens to be on the desk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellTest {
    @get:Rule
    val rule = createComposeRule()

    // Bar assertions match the selectable item rather than the text alone: a destination's own screen
    // may legitimately render the same word, and matching the item keeps the assertion unambiguous.
    @Test
    fun drawerSettingsNavigatesAndBackReturnsHome() {
        launchShell()
        pullSheetOpen()
        rule.onNodeWithTag("leagues.settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Theme").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.waitForIdle()
        barItem("Scores").assertIsSelected()
        rule.onNodeWithText("Theme").assertDoesNotExist()
    }

    @Test
    fun aTabPressStillLandsOnItsPage() {
        launchShell()

        barItem("Teams").performClick()
        rule.waitForIdle()
        barItem("Teams").assertIsSelected()
    }

    @Test
    fun theDrawerOpensOnStandingsAndReordersThere() {
        launchShell()

        // The shell's pager owns horizontal drags, so the drawer no longer opens on a swipe anywhere: it is opened
        // by the header button on Scores. Standings and Teams have no such button yet, which is a known gap.
        rule.onNodeWithTag("nav.menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("leagues.sheet").assertIsDisplayed()

        rule.onNodeWithTag("leagues.edit").performClick()
        // The handle only drags after a long press, and only crosses into the next slot past half a row, so the
        // move is measured from the row rather than guessed in dp.
        val rowHeight =
            rule
                .onNodeWithTag("leagues.nfl")
                .fetchSemanticsNode()
                .size.height
                .toFloat()
        val handle = rule.onNodeWithTag("leagues.nfl.handle")
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -(rowHeight * 0.6f)))
        }
        handle.performTouchInput { moveBy(Offset(0f, -4f)) }
        handle.performTouchInput { up() }
        rule.waitForIdle()

        val nflTop =
            rule
                .onNodeWithTag("leagues.nfl")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val nhlTop =
            rule
                .onNodeWithTag("leagues.nhl")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("NFL should now sit above NHL", nflTop < nhlTop)
    }

    @Test
    fun myTeamsIsTheSelectedRowWhenTheDrawerReopens() {
        launchShell()
        pullSheetOpen()
        rule.onNodeWithText("My Teams").performClick()
        rule.waitUntil(10_000) {
            rule.onNodeWithTag("nav.menu", useUnmergedTree = true).fetchSemanticsNode()
            true
        }

        pullSheetOpen()
        rule.onNodeWithTag("leagues.myTeams").assertIsSelected()
        rule.onNodeWithTag("leagues.nhl").assertIsNotSelected()
    }

    private fun barItem(label: String) = rule.onNode(hasText(label) and isSelectable())

    // The bar depends on the mode: My Teams spans leagues, so Standings has no table to show there.
    private val leagueModeBar = listOf("nav.menu", "nav.scores", "nav.standings", "nav.teams")
    private val myTeamsBar = listOf("nav.menu", "nav.scores", "nav.teams")

    private fun launchShell(
        dark: Boolean = false,
        fontScale: Float = 1f,
        leagueMode: Boolean = false,
    ) {
        rule.setContent {
            RouxTheme(dark = dark) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    RouxNavHost()
                }
            }
        }
        rule.waitForFixtureScores()
        // A fresh install stores favourites-only, which opens in My Teams. Tests about the three-tab bar
        // choose a league first, through the control a reader would use rather than by seeding a file.
        if (leagueMode) {
            chooseLeague("nhl")
        }
    }

    /**
     * Pulls the menu up from the bar. The bar is the sheet's handle, so the pull starts on the seats the thumb already
     * knows rather than on a strip that only exists for this purpose.
     */
    private fun pullSheetOpen(by: Float = 2000f) {
        rule.onNodeWithTag("nav.bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -by))
            up()
        }
        rule.waitForIdle()
    }

    /** Opens the menu the way a reader would and takes a league. */
    private fun chooseLeague(id: String) {
        pullSheetOpen()
        rule.onNodeWithTag("leagues.$id").performClick()
        rule.waitForIdle()
    }

    private fun sheetBounds() = rule.onNodeWithTag("leagues.sheet").fetchSemanticsNode().boundsInRoot

    private fun barBounds() = rule.onNodeWithTag("nav.bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertBarReach(expected: List<String> = leagueModeBar) {
        // The shell owns navigation; ScoresScreenTest audits the destination's own controls.
        // Keep window coordinates so the bar still has to satisfy the real thumb-zone limits.
        val targets = rule.reachTargets().filter { it.tag.startsWith("nav.") }
        assertEquals(expected.sorted(), targets.filter { it.primary }.map { it.tag }.sorted())
        val window = rule.onRoot().fetchSemanticsNode().size
        val violations =
            findReachViolations(
                controls = targets,
                windowWidthDp = window.width / rule.density.density,
                windowHeightDp = window.height / rule.density.density,
            )
        assertTrue(violations.joinToString("\n") { it.describe() }, violations.isEmpty())
    }

    @Test
    fun myTeamsOpensWithoutStandingsAndChoosingALeagueBringsItBack() {
        launchShell()

        // My Teams is the stored default, so a fresh shell has no standings table to offer.
        barItem("Scores").assertExists()
        barItem("Teams").assertExists()
        barItem("Standings").assertDoesNotExist()
        barItem("Scores").assertIsSelected()

        // Choosing a league is what makes a table meaningful again.
        chooseLeague("nhl")
        barItem("Standings").assertExists()
    }

    @Test
    fun aLeagueOpensWithAllThreeDestinations() {
        launchShell(leagueMode = true)

        barItem("Scores").assertExists()
        barItem("Standings").assertExists()
        barItem("Teams").assertExists()
        barItem("Scores").assertIsSelected()
    }

    @Test
    fun theStarReturnsToMyTeamsAndDropsStandings() {
        launchShell(leagueMode = true)
        barItem("Standings").assertExists()

        pullSheetOpen()
        rule.onNodeWithTag("leagues.myTeams").performClick()
        // The star writes the preference, and the bar follows the stored value a frame or two later; asserting
        // on the next frame asserted before the write landed.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Standings").fetchSemanticsNodes().isEmpty()
        }

        barItem("Standings").assertDoesNotExist()
        barItem("Scores").assertIsSelected()
    }

    @Test
    fun theGrabberIsOnEveryMainPage() {
        // The affordance is the only thing on a parked screen that says the menu is there, so it has to survive
        // every destination, not just the one the app happens to open on.
        launchShell(leagueMode = true)
        rule.onNodeWithTag("nav.menu").assertExists()

        barItem("Standings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav.menu").assertExists()
    }

    @Test
    fun pullingTheBarOpensTheLeaguesMenu() {
        launchShell(leagueMode = true)
        rule.onNodeWithTag("leagues.myTeams").assertDoesNotExist()

        pullSheetOpen()

        rule.onNodeWithTag("leagues.myTeams").assertExists()
    }

    @Test
    fun tappingTheGrabberOpensTheSheetFully() {
        launchShell(leagueMode = true)
        rule.onNodeWithTag("nav.menu").performClick()
        rule.waitForIdle()

        val sheet = sheetBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        // A tap and a pull land in the same place: open is open, and it covers the bar rather than resting on it.
        assertTrue("sheet should be open", sheet.height > 0f)
        assertEquals(window.bottom, sheet.bottom, 1f)
        assertTrue("a tap should open the whole menu", sheet.height > window.height * 0.4f)
        assertTrue("but never the whole screen", sheet.height < window.height * 0.7f)
    }

    @Test
    fun aQuickFlickDownClosesTheSheetEvenThoughItBarelyTravels() {
        launchShell(leagueMode = true)
        pullSheetOpen()
        val before = rule.onNodeWithTag("leagues.sheet").getUnclippedBoundsInRoot()
        assertTrue("the menu should be open first", (before.bottom - before.top).value > 100f)

        // A short, fast push downward: it travels well under half the sheet's height, so only the release speed can
        // carry it closed. Without that the menu snapped back and read as a gesture that does not work.
        rule.onNodeWithTag("leagues.sheet").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 120f, durationMillis = 60)
        }
        rule.waitForIdle()

        rule.onNodeWithTag("leagues.sheet").assertDoesNotExist()
    }

    @Test
    fun swipingDownOnTheSheetClosesIt() {
        launchShell(leagueMode = true)
        pullSheetOpen()
        rule.onNodeWithTag("leagues.sheet").assertExists()

        // From the body, not the strip at its top edge: a downward drag takes the menu away.
        rule.onNodeWithTag("leagues.sheet").performTouchInput { swipeDown() }
        rule.waitForIdle()

        rule.onNodeWithTag("leagues.sheet").assertDoesNotExist()
    }

    @Test
    fun aShortPullFallsBackToParkedRatherThanRestingHalfway() {
        launchShell(leagueMode = true)
        rule.onNodeWithTag("nav.bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -160f))
            up()
        }
        rule.waitForIdle()

        rule.onNodeWithTag("leagues.sheet").assertDoesNotExist()
    }

    @Test
    fun theBarKeepsItsPlaceWhileTheSheetIsOpen() {
        launchShell(leagueMode = true)
        val before = barBounds()
        pullSheetOpen()
        val after = barBounds()
        // The tabs are the one thing that must not move: they are where the thumb already is.
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
    }

    @Test
    fun tappingStandingsShowsTheTable() {
        launchShell(leagueMode = true)

        // The column header exists only when the standings destination is the one on screen.
        rule.onNodeWithTag("standings.header").assertDoesNotExist()
        barItem("Standings").performClick()
        rule.onNodeWithTag("standings.header").assertExists()
        barItem("Standings").assertIsSelected()
        // And the table itself arrives from the feed rather than staying a skeleton. The screen opens on the
        // Eastern conference, so the first heading that proves data is the Atlantic.
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText("Atlantic").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theBarPassesTheReachAuditWithTwoTabs() {
        // My Teams hides Standings, so the audit has to hold for the shorter bar too.
        launchShell()
        assertBarReach(expected = myTeamsBar)
    }

    @Test
    fun theBarPassesTheReachAuditAtTheReferenceWidth() {
        launchShell(leagueMode = true)
        assertBarReach()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-420dpi")
    fun theBarPassesTheReachAuditOnASmallPhone() {
        launchShell(leagueMode = true)
        assertBarReach()
    }

    @Test
    fun theBarPassesTheReachAuditAtDoubleType() {
        launchShell(fontScale = 2f, leagueMode = true)
        assertBarReach()
    }

    @Test
    fun everyDestinationIsDeclaredFrequentSoR2Applies() {
        // Navigation is the most-used control on every screen. If the flag were dropped, R1 would still
        // pass while the tighter R2 ceiling silently stopped being checked - a test that cannot see the
        // difference is worse than no test.
        launchShell(leagueMode = true)

        val placements = rule.reachTargets().filter { it.tag.startsWith("nav.") }
        assertEquals(leagueModeBar.sorted(), placements.map { it.tag }.sorted())
        placements.forEach { placement ->
            assertTrue("${placement.tag} must be frequent so R2 governs it", placement.frequent)
        }
    }

    @Test
    fun everyDestinationIsAtLeastFortyEightDpInBothDirections() {
        launchShell(leagueMode = true)

        val placements = rule.reachTargets().filter { it.tag.startsWith("nav.") }
        assertEquals(leagueModeBar.sorted(), placements.map { it.tag }.sorted())
        placements.forEach { placement ->
            assertTrue(
                "${placement.tag} is ${placement.width} x ${placement.height} dp",
                placement.width >= Reach.MIN_TARGET_DP && placement.height >= Reach.MIN_TARGET_DP,
            )
        }
    }

    @Test
    fun drawerMyTeamsEnablesThePersistedFavouritesFilterAndNhlDisablesIt() {
        val app = RuntimeEnvironment.getApplication() as ScoresIntegrationApp
        runBlocking { app.container.preferences.setFavouritesOnly(false) }
        launchShell()
        rule.onNodeWithTag("nav.menu").performClick()
        rule
            .onNodeWithTag(
                "leagues.sheet",
            ).assertWidthIsEqualTo(
                with(rule.density) {
                    rule
                        .onRoot()
                        .fetchSemanticsNode()
                        .size.width
                        .toDp()
                },
            )
        rule.onNodeWithText("My Teams").performClick()
        rule.waitUntil(10_000) {
            rule.onNodeWithTag("scores.header").fetchSemanticsNode()
            runBlocking { app.container.preferences.getFavouritesOnly() }
        }
        rule.onNodeWithContentDescription("Favourites only").assertDoesNotExist()
        rule.onNodeWithTag("nav.menu").performClick()
        rule.onNodeWithTag("leagues.nhl").performClick()
        rule.waitUntil(10_000) {
            rule.onNodeWithTag("scores.header").fetchSemanticsNode()
            !runBlocking { app.container.preferences.getFavouritesOnly() }
        }
        rule.onNodeWithContentDescription("Favourites only").assertDoesNotExist()
    }

    @Test
    fun calendarCoversTheShellAndHidesTabsUntilSelection() {
        launchShell()
        val window = rule.onRoot().getUnclippedBoundsInRoot()
        rule.onNodeWithTag("scores.dateLabel").performClick()
        assertEquals(window, rule.onNodeWithTag("calendar.overlay").getUnclippedBoundsInRoot())
        barItem("Teams").assertDoesNotExist()
        barItem("Standings").assertDoesNotExist()
        rule.onNodeWithText("TODAY").performClick()
        rule.onNodeWithTag("calendar.overlay").assertDoesNotExist()
        barItem("Scores").assertIsSelected()
        barItem("Teams").assertExists()
    }

    @Test
    fun shellSheetLight() {
        launchShell(dark = false)
        rule.onNodeWithTag("nav.menu").performClick()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/shell-sheet-light.png")
    }

    @Test
    fun shellSheetDark() {
        launchShell(dark = true)
        rule.onNodeWithTag("nav.menu").performClick()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/shell-sheet-dark.png")
    }

    @Test
    fun aTeamsRowBodyOpensThatClubPageAndBackReturnsToTheList() {
        launchShell()
        barItem("Teams").performClick()
        rule.waitForTeamsRows()

        rule.onNodeWithTag("teams.row.body.ANA").performClick()
        waitForTeamPage("ANA")
        rule.onNodeWithTag("team.name").assertIsDisplayed().assertTextEquals("Anaheim Ducks")

        rule.onNodeWithTag("team.back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("teams.row.ANA").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theTeamStatsTabOpensTheLeagueTableForTheClubFromTheTeamsList() {
        launchShell()
        barItem("Teams").performClick()
        rule.waitForTeamsRows()

        rule.onNodeWithTag("teams.row.body.ANA").performClick()
        waitForTeamPage("ANA")
        rule.onNodeWithTag("team.tab.TeamStats").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.stats.table", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("team.tab.TeamStats").assertIsSelected()
        rule.onNodeWithTag("team.stats.header", useUnmergedTree = true).assertTextEquals("14 GP · 6-7-1 · 13 pts")
        rule.onAllNodesWithTag("team.stats.label", useUnmergedTree = true).assertCountEquals(10)
    }

    @Test
    fun switchingTabsAndComingBackKeepsThePlayersScrollPosition() {
        // The nav back stack restores a page; this is the other half — the tab state holder that keeps each tab's own
        // list state while you move between tabs. Without it the Players list is rebuilt at the top.
        launchShell()
        barItem("Teams").performClick()
        rule.waitForTeamsRows()

        rule.onNodeWithTag("teams.row.body.ANA").performClick()
        waitForTeamPage("ANA")
        rule.onNodeWithTag("team.tab.Players").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.players.list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("team.players.list").performScrollToIndex(4)
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.players.player.8476316").fetchSemanticsNodes().isNotEmpty()
        }
        val rowBefore = rule.onNodeWithTag("team.players.player.8476316").fetchSemanticsNode().boundsInRoot

        rule.onNodeWithTag("team.tab.Schedule").performClick()
        rule.onNodeWithTag("team.tab.Schedule").assertIsSelected()

        rule.onNodeWithTag("team.tab.Players").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.players.list").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "the Players tab must come back where it was left",
            rowBefore,
            rule.onNodeWithTag("team.players.player.8476316").fetchSemanticsNode().boundsInRoot,
        )
    }

    @Test
    fun aPlayerOpensFromThePlayersTabAndBackReturnsToThatTabAtTheSameScroll() {
        launchShell()
        barItem("Teams").performClick()
        rule.waitForTeamsRows()

        rule.onNodeWithTag("teams.row.body.ANA").performClick()
        waitForTeamPage("ANA")
        rule.onNodeWithTag("team.tab.Players").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.players.list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("team.players.list").performScrollToIndex(4)
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.players.player.8476316").fetchSemanticsNodes().isNotEmpty()
        }
        val rowBefore = rule.onNodeWithTag("team.players.player.8476316").fetchSemanticsNode().boundsInRoot

        rule.onNodeWithTag("team.players.player.8476316").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("player.name").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("player.name").assertTextEquals("Laurent Brossoit")

        rule.onNodeWithTag("player.back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.players.list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("team.tab.Players").assertIsSelected()
        assertEquals(rowBefore, rule.onNodeWithTag("team.players.player.8476316").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun aClubInTheGameHeroOpensTheSamePageAndBackReturnsToTheGame() {
        launchShell(leagueMode = true)
        val first = ScoreFixture.games.first()
        val id = first.getValue("id").jsonPrimitive.content
        val away =
            first
                .getValue("awayTeam")
                .jsonObject
                .getValue("abbrev")
                .jsonPrimitive.content

        rule.onNodeWithTag("scores.game.$id").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("detail.back").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("detail.hero.away.club").performClick()
        waitForTeamPage(away)

        rule.onNodeWithTag("team.back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("detail.back").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTeamPage(abbrev: String) {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("team.page.$abbrev").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun teamsTabLight() {
        launchShell()
        barItem("Teams").performClick()
        // The rows come from a repository flow outside the composition, so idle alone can still be the
        // loading frame. Wait for the list, then settle the page transition before measuring pixels.
        rule.waitForTeamsRows()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/shell/teams-tab-light.png")
    }

    @Test
    fun teamsTabDark() {
        launchShell(dark = true)
        barItem("Teams").performClick()
        // Same load gate as the light capture: the bounds below would otherwise throw on a missing tag if
        // they ran while the tab was still showing "Loading teams…".
        rule.waitForTeamsRows()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/shell/teams-tab-dark.png")
        // The list runs to the bar, less the page's own 16 dp margin: a one-row band of empty page under the
        // last row is the strip that reads as a missing shelf on this tab, and it is what this pins.
        val list = rule.onNodeWithTag("teams.list").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag("nav.bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val margin = with(rule.density) { 20.dp.toPx() }
        assertTrue("list ends ${list.bottom} but the bar starts ${bar.top}", list.bottom > bar.top - margin)
    }

    @Test
    fun shellLight() {
        launchShell(dark = false)
        rule.onRoot().captureRoboImage("src/test/screenshots/shell-light.png")
    }

    @Test
    fun theMenuGlyphMatchesTheDestinationIcons() {
        launchShell(leagueMode = true)
        val glyph = rule.onNodeWithTag("nav.menu.icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bar = barBounds()
        // Same box as the destination glyphs, so the button is not a different weight in the same row.
        assertEquals(24f, (glyph.right - glyph.left).value, 0.5f)
        assertEquals(24f, (glyph.bottom - glyph.top).value, 0.5f)
        // Wholly inside the bar: an earlier version was clipped by its slot, which is what made it look wrong.
        val glyphTopPx = with(rule.density) { glyph.top.toPx() }
        val glyphBottomPx = with(rule.density) { glyph.bottom.toPx() }
        assertTrue("the glyph must not be clipped by the bar", glyphTopPx >= bar.top && glyphBottomPx <= bar.bottom)
    }

    @Test
    fun aHorizontalSwipeNoLongerTurnsThePage() {
        launchShell(leagueMode = true)
        // Swiping forward used to land on Teams. The swipe belongs to the screen now, so this one - on a screen with no
        // swipe of its own - must leave the destination exactly where it was.
        barItem("Teams").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(PAGER_TAG, useUnmergedTree = true).performTouchInput { swipeRight() }
        rule.waitForIdle()
        barItem("Teams").assertIsSelected()
    }

    @Test
    fun theBarLeavesAirUnderItsLabels() {
        launchShell(leagueMode = true)
        // Robolectric reports no system bars, so this pins the part of the gap the bar owns itself: the seats must not
        // sit on the bottom of the window even before the gesture inset is added on a phone.
        val bar = barBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(window.bottom, bar.bottom, 1f)
        assertTrue(
            "labels need air above the gesture area",
            bar.height >= with(rule.density) { (80f + Reach.BAR_BOTTOM_AIR_DP).dp.toPx() } - 1f,
        )
    }

    @Test
    fun shellLeagueModeLight() {
        launchShell(leagueMode = true)
        rule.onRoot().captureRoboImage("src/test/screenshots/shell-league-mode-light.png")
    }

    @Test
    fun shellDark() {
        launchShell(dark = true)
        rule.onRoot().captureRoboImage("src/test/screenshots/shell-dark.png")
    }

    @Test
    fun theSheetRisesAboveTheBarAndThePageStaysWhereItIs() {
        launchShell(leagueMode = true)
        val pageBefore = rule.onNodeWithTag(PAGER_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        pullSheetOpen()

        val pageAfter = rule.onNodeWithTag(PAGER_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // No sideways push any more: the sheet comes up over a page that stays exactly where it was, and it runs to the
        // bottom of the window, covering the tabs and the dial rather than stopping on top of them.
        assertEquals(pageBefore.left, pageAfter.left, 1f)
        assertEquals(pageBefore.top, pageAfter.top, 1f)
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(window.bottom, sheetBounds().bottom, 1f)
        assertTrue("the sheet must cover the bar", sheetBounds().top < barBounds().top)
    }

    @Test
    fun tappingOutsideTheSheetShutsIt() {
        launchShell(leagueMode = true)
        pullSheetOpen()
        rule.onNodeWithTag("leagues.sheet").assertExists()

        rule.onNodeWithTag("leagues.scrim").performClick()
        rule.waitForIdle()

        // Shut again: the panel stops offering its content at all.
        rule.onNodeWithTag("leagues.myTeams", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSheetNeverReachesTheTopOfTheWindow() {
        launchShell(leagueMode = true)
        pullSheetOpen()
        val sheet = sheetBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot

        assertTrue("sheet top ${sheet.top} leaves too little page", sheet.top - window.top > window.height * 0.3f)
    }
}
