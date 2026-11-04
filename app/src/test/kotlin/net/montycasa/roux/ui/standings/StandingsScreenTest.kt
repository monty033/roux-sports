@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.standings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.policy.StandingsGrouping
import net.montycasa.roux.domain.policy.StandingsGroups
import net.montycasa.roux.domain.policy.StandingsMode
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.scores.CREST_TAG
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val rows = capturedStandings().sortedBy { it.leagueSequence }

    // The app opens on the Eastern conference, playoffs ranking.
    private val fresh =
        StandingsUiState(
            view = StandingsView.Eastern,
            mode = StandingsMode.Playoffs,
            groups = StandingsGrouping.group(rows, StandingsView.Eastern, StandingsMode.Playoffs),
            loading = false,
        )
    private val leagueState =
        fresh.copy(view = StandingsView.League, groups = StandingsGrouping.group(rows, StandingsView.League))
    private val suppliedState = mutableStateOf(fresh)
    private val navTags = arrayOf("test.nav.Scores", "test.nav.Standings", "test.nav.Teams")
    private val rowMatcher =
        SemanticsMatcher("standings row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("standings.row.") == true
        }

    private fun eastState() =
        fresh.copy(
            view = StandingsView.Eastern,
            groups = StandingsGrouping.group(rows, StandingsView.Eastern, StandingsMode.Playoffs),
        )

    private fun launchScreen(
        state: StandingsUiState = fresh,
        onRefresh: () -> Unit = {
        },
        selected: MutableList<StandingsView> = mutableListOf(),
        modes: MutableList<StandingsMode> = mutableListOf(),
        applySelection: Boolean = true,
        fontScale: Float = 1f,
        opened: MutableList<TeamKey> = mutableListOf(),
    ) {
        suppliedState.value = state
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) {
                RouxTheme {
                    // S6.3 owns production wiring. Reserve a real M3 three-destination bar of the same
                    // geometry here so density and reach tests cannot silently borrow its space.
                    Scaffold(bottomBar = {
                        NavigationBar {
                            Spacer(Modifier.width(16.dp))
                            listOf("Scores", "Standings", "Teams").forEach { label ->
                                NavigationBarItem(
                                    selected = label == "Standings",
                                    onClick = {},
                                    icon = { Text(label.take(1)) },
                                    label = { Text(label) },
                                    modifier = Modifier.primaryControl("test.nav.$label", frequent = true),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                    }) { insets ->
                        StandingsScreen(
                            suppliedState.value,
                            onRefresh,
                            { view, mode ->
                                selected += view
                                modes += mode
                                if (!applySelection) return@StandingsScreen
                                suppliedState.value =
                                    suppliedState.value.copy(
                                        view = view,
                                        mode = mode,
                                        groups = StandingsGrouping.group(rows, view, mode),
                                    )
                            },
                            Modifier.padding(insets),
                            onTeamClick = { team -> opened += team },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aSwipeWalksTheRankingsAndThenTheNextConference() {
        val picked = mutableListOf<StandingsView>()
        launchScreen(eastState(), selected = picked)
        // Eastern playoffs -> Eastern division -> Eastern conference -> Western playoffs: one list, walked in order.
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(StandingsView.Eastern, suppliedState.value.view)
        assertEquals(StandingsMode.Division, suppliedState.value.mode)
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(StandingsMode.Conference, suppliedState.value.mode)
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        // Past the last ranking of a conference the swipe lands on the next conference's first ranking.
        assertEquals(StandingsView.Western, suppliedState.value.view)
        assertEquals(StandingsMode.Playoffs, suppliedState.value.mode)
        assertEquals(listOf(StandingsView.Eastern, StandingsView.Eastern, StandingsView.Western), picked)
        rule.onAllNodes(rowMatcher).assertCountEquals(16)
    }

    @Test
    fun aBackwardSwipeOnTheFirstPageDoesNothing() {
        val picked = mutableListOf<StandingsView>()
        launchScreen(eastState(), selected = picked)
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(emptyList<StandingsView>(), picked)
        assertEquals(StandingsMode.Playoffs, suppliedState.value.mode)
    }

    @Test
    fun aForwardSwipeOnLeagueDoesNothing() {
        val picked = mutableListOf<StandingsView>()
        launchScreen(leagueState, selected = picked)
        // League is the last page, so there is nothing to its right.
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(emptyList<StandingsView>(), picked)
    }

    @Test
    fun headerAndRowColumnsAlignAndNullOvertimeLossesUseAnEnDash() {
        val nullOtl = rows.first().copy(otl = null)
        launchScreen(fresh.copy(groups = StandingsGrouping.group(listOf(nullOtl), StandingsView.League)))
        val rowTag = "standings.row.0.${nullOtl.team.abbrev}"
        listOf("GP", "W", "L", "OTL", "PTS").forEachIndexed { index, label ->
            rule.onNodeWithText(label).assertIsDisplayed()
            val cell = hasTestTag("standings.cell.$index")
            val header = rule.onNode(cell and hasAnyAncestor(hasTestTag("standings.header")), useUnmergedTree = true).fetchSemanticsNode()
            val value = rule.onNode(cell and hasAnyAncestor(hasTestTag(rowTag)), useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(header.boundsInRoot.left, value.boundsInRoot.left, 0.5f)
            assertEquals(header.boundsInRoot.right, value.boundsInRoot.right, 0.5f)
        }
        rule.onNodeWithText("–").assertIsDisplayed()
        rule.onNodeWithText(nullOtl.pts.toString()).assertIsDisplayed()
    }

    @Test
    fun switcherIsFrequentTableRowsOpenTheirClubAndViewsSwitch() {
        val selected = mutableListOf<StandingsView>()
        launchScreen(selected = selected)
        val targets = rule.reachTargets()
        val switcher = targets.single { it.tag == "standings.view" }
        assertTrue(switcher.primary)
        assertTrue(switcher.frequent)
        // Rows are controls now: one target per club, each measuring the 48 dp minimum the audit enforces.
        assertEquals(16, targets.count { it.tag.startsWith("standings.row.") })
        // The screen opens on a conference, so sixteen rows, and the league table is one tap away.
        rule.onAllNodes(rowMatcher).assertCountEquals(16)
        assertEquals((navTags.toList() + "standings.view").sorted(), targets.filter { it.primary }.map { it.tag }.sorted())
        StandingsView.entries.forEach { view ->
            rule.onNodeWithTag("standings.view.$view").performClick().assertIsSelected()
            assertEquals(view, suppliedState.value.view)
            assertReach()
        }
        assertEquals(StandingsView.entries, selected)
    }

    @Test
    fun tappingARowOpensThatClub() {
        val opened = mutableListOf<TeamKey>()
        val team = rows.first().team
        // One known row, so the tag names the club the assertion expects rather than whichever club heads the table.
        launchScreen(
            fresh.copy(view = StandingsView.League, groups = StandingsGrouping.group(listOf(rows.first()), StandingsView.League)),
            opened = opened,
        )
        rule.onNodeWithTag("standings.row.0.${team.abbrev}").performClick()
        // The row hands over the club, not the row: the page it opens is addressed by team key.
        assertEquals(listOf(team.key), opened)
    }

    @Test
    fun wildcardModeDrawsTheCutBelowWcTwo() {
        launchScreen(
            fresh.copy(
                view = StandingsView.Eastern,
                mode = StandingsMode.Playoffs,
                groups = StandingsGrouping.group(rows, StandingsView.Eastern, StandingsMode.Playoffs),
            ),
        )
        // The fixture is the league's actual 3/3/10 layout for one conference, with no conference prefix: the page
        // already says which conference this is.
        val groups = suppliedState.value.groups
        assertEquals(listOf("Atlantic", "Metropolitan", "Wild Card"), groups.sections.map { it.title })
        assertEquals(listOf(3, 3, 10), groups.sections.map { it.rows.size })
        rule.onAllNodesWithText("Playoff line").assertCountEquals(1)
        rule.onAllNodesWithText("Playoff line")[0].performScrollTo().assertIsDisplayed()
        val wild = groups.sections[2]
        val second = rule.onNodeWithTag("standings.row.2.${wild.rows[1].team.abbrev}").fetchSemanticsNode()
        val third = rule.onNodeWithTag("standings.row.2.${wild.rows[2].team.abbrev}").fetchSemanticsNode()
        val line = rule.onAllNodesWithText("Playoff line")[0].fetchSemanticsNode()
        assertTrue(second.positionInRoot.y + second.size.height <= line.positionInRoot.y)
        assertTrue(line.positionInRoot.y + line.size.height <= third.positionInRoot.y)
        assertReach()
    }

    @Test
    fun theTableIsOneRankingWithNoFavouritesSection() {
        // The view model never builds a pinned block; given a plain league table the screen must render one
        // continuous ranking, with the numbering running straight through the group.
        launchScreen(fresh.copy(groups = StandingsGrouping.group(rows, StandingsView.League)))
        rule.onAllNodesWithText("Your teams").assertCountEquals(0)
        rule.onAllNodes(rowMatcher).assertCountEquals(32)
        // Numbering runs straight through the table rather than restarting for a pinned block.
        val ranks = rule.onAllNodesWithTag("standings.rank", useUnmergedTree = true)
        ranks.assertCountEquals(32)
        ranks[0].assertTextEquals("1")
        ranks[31].assertTextEquals("32")
    }

    @Test
    fun staleBannerRetainsRowsShowsRefreshErrorAndRetries() {
        var refreshes = 0
        launchScreen(
            leagueState.copy(
                stale = Freshness.Stale(Instant.parse("2026-10-26T23:42:00Z")),
                error = LoadError.Network,
            ),
            onRefresh = {
                refreshes++
            },
        )
        rule.onNodeWithText("Updated", substring = true).assertIsDisplayed()
        rule.onNodeWithText("7:42 PM", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t refresh. Showing saved standings.").assertIsDisplayed()
        rule.onAllNodes(rowMatcher).assertCountEquals(32)
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, refreshes)
        assertReach(retry = true)
        rule.runOnIdle { suppliedState.value = suppliedState.value.copy(isRefreshing = true) }
        rule.onNodeWithText("Refreshing…").assertIsNotEnabled()
        assertReach(retry = true)
    }

    @Test
    fun rowsWithAnErrorAndNoStaleMarkerExplainTheFailureAndOfferRetry() {
        var refreshes = 0
        launchScreen(leagueState.copy(error = LoadError.Local), onRefresh = { refreshes++ })
        // No stale marker means no update time to show; the copy must still say what went wrong.
        rule.onNodeWithText("Couldn’t refresh. Showing saved standings.").assertIsDisplayed()
        rule.onAllNodes(rowMatcher).assertCountEquals(32)
        rule.onNodeWithText("Updated", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, refreshes)
        assertReach(retry = true)
    }

    @Test
    fun loadingEmptyAndErrorAreDistinctAndErrorOffersRetry() {
        var refreshes = 0
        launchScreen(StandingsUiState(view = StandingsView.League), onRefresh = { refreshes++ })
        rule.onNodeWithText("Loading standings…").assertIsDisplayed()
        rule.onNodeWithText("No table yet.").assertDoesNotExist()
        rule.runOnIdle { suppliedState.value = StandingsUiState(view = StandingsView.League, loading = false) }
        rule.onNodeWithText("No table yet.").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertDoesNotExist()
        rule.runOnIdle {
            suppliedState.value = StandingsUiState(view = StandingsView.League, loading = false, error = LoadError.Network)
        }
        rule.onNodeWithText("Couldn’t load standings. Try again.").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, refreshes)
        assertReach(retry = true)
    }

    @Test
    fun pullToRefreshCallsTheSuppliedCallback() {
        var refreshes = 0
        launchScreen(onRefresh = { refreshes++ })
        rule.onNodeWithTag("standings.refresh").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue(refreshes > 0)
    }

    @Test
    fun theLeagueTableShowsThirteenRowsAt411By923BecauseEveryRowIsATouchTarget() {
        // The shipped density claim is about the league table: twenty teams on one screen.
        launchScreen(leagueState)
        rule.onNodeWithTag("standings.header").assertIsDisplayed()
        StandingsView.entries.forEach { rule.onNodeWithTag("standings.view.$it").assertIsDisplayed() }
        listOf("Scores", "Standings", "Teams").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        val viewport = rule.onNodeWithTag("standings.list").fetchSemanticsNode().boundsInRoot
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val fullyVisible =
            rule.onAllNodes(rowMatcher).fetchSemanticsNodes().count { row ->
                row.positionInRoot.y >= viewport.top - 0.5f && row.positionInRoot.y + row.size.height <= viewport.bottom + 0.5f
            }
        println(
            "STANDINGS_MEASUREMENT: fullyVisible=$fullyVisible windowDp=${window.width / rule.density.density}x${window.height / rule.density.density} listHeightDp=${viewport.height / rule.density.density}",
        )
        // The old promise was the whole league table on one screen at 30 dp a row. A row that opens its club is a
        // control and takes 48 dp, so the table scrolls instead: thirteen is what the same phone now holds, and a
        // floor keeps a future redesign from quietly spending more of the screen on chrome.
        assertTrue("Fully visible league rows measured at 411x923: $fullyVisible", fullyVisible >= 13)
        rule.onAllNodes(rowMatcher).fetchSemanticsNodes().forEach { row ->
            assertTrue(
                "a tappable row measures at least 48 dp, was ${row.size.height / rule.density.density}",
                row.size.height >= 48 * rule.density.density - 0.5f,
            )
        }
    }

    @Test
    fun reachPassesForEveryViewAcrossTheCollectionAtPixelNineSize() = auditAllViews()

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-420dpi")
    fun reachPassesForEveryViewAcrossTheCollectionAtSmallPhoneSize() = auditAllViews()

    private fun auditAllViews() {
        launchScreen()
        StandingsView.entries.forEach { view ->
            rule.onNodeWithTag("standings.view.$view").performClick()
            val sections = suppliedState.value.groups.sections
            sections.forEachIndexed { index, section ->
                section.rows.forEach { row ->
                    rule.onNodeWithTag("standings.row.$index.${row.team.abbrev}").performScrollTo()
                    assertReach()
                }
            }
            rule.onNodeWithTag("standings.list").performTouchInput { swipeUp() }
            assertReach()
        }
        listOf(
            StandingsUiState(),
            StandingsUiState(loading = false),
            StandingsUiState(loading = false, error = LoadError.Network),
            fresh.copy(stale = Freshness.Stale(Instant.parse("2026-10-26T23:42:00Z")), error = LoadError.Network),
        ).forEach { state ->
            rule.runOnIdle { suppliedState.value = state }
            assertReach(retry = state.error != null)
        }
    }

    private fun assertReach(retry: Boolean = false) {
        // A table row now opens its club, so it is a control and the audit measures it at 48 dp like any other.
        // The pull-to-refresh gesture is reachable and measured, but it is a gesture rather than a control.
        val gesture = rule.reachTargets().single { it.tag == "standings.refresh" }
        assertFalse("Pull-to-refresh is a gesture, not a primary control", gesture.primary)
        val primaries = navTags.toList() + "standings.view"
        if (retry) {
            val recovery = rule.reachTargets().single { it.tag == "standings.retry" }
            // §5a: a fixed action is a primary control.
            assertTrue("The retry is a fixed action, so it is a primary control", recovery.primary)
        }
        rule.assertPrimaryControlsReachable(*(primaries + if (retry) listOf("standings.retry") else emptyList()).toTypedArray())
    }

    @Test
    fun everyRowCarriesItsCrestAndItsClubName() {
        launchScreen(leagueState)
        // Identity comes from the crest and the club's own name; the abbreviation is only a fallback for a payload
        // that carries no name at all.
        rule.onAllNodesWithTag(CREST_TAG, useUnmergedTree = true).assertCountEquals(32)
        // 34 dp, the size the score cards use: the row is 48 dp now, so the crest no longer has to be a token.
        rule.onAllNodesWithTag(CREST_TAG, useUnmergedTree = true)[0].assertWidthIsEqualTo(34.dp).assertHeightIsEqualTo(34.dp)
        val leader = rows.first()
        rule.onNodeWithText(leader.team.shortName!!).assertIsDisplayed()
    }

    @Test
    fun atTwiceTheFontScaleTheRowsGrowInsteadOfClippingTheName() {
        // Dense rows must not become clipped rows: the height is a floor, so at 2x the text decides it.
        launchScreen(leagueState, fontScale = 2f)
        val nodes = rule.onAllNodes(rowMatcher).fetchSemanticsNodes()
        assertEquals(32, nodes.size)
        val leader = rows.first()
        val tallest = nodes.maxOf { it.size.height } / rule.density.density
        assertTrue("At 2x the rows must grow past the compact 40 dp floor, tallest was $tallest", tallest > 44f)
        rule.onAllNodesWithText(leader.team.shortName!!, useUnmergedTree = true).onFirst().assertIsDisplayed()
        rule.onAllNodesWithText(leader.pts.toString(), useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun theLeaguePageHasNoRankingRow() {
        // The ranking is a conference question. On the league page there is nothing to rank differently, so a
        // control there would be three ways to do nothing.
        launchScreen(leagueState)
        StandingsMode.entries.forEach { mode ->
            rule.onAllNodesWithTag("standings.mode.$mode").assertCountEquals(0)
        }
        rule.onAllNodes(rowMatcher).assertCountEquals(32)
    }

    @Test
    fun aConferencePageOffersTheThreeRankingsAndSwitchingOneRedrawsTheTable() {
        launchScreen(
            fresh.copy(
                view = StandingsView.Eastern,
                mode = StandingsMode.Conference,
                groups = StandingsGrouping.group(rows, StandingsView.Eastern, StandingsMode.Conference),
            ),
        )
        StandingsMode.entries.forEach { mode ->
            rule.onNodeWithTag("standings.mode.$mode").assertIsDisplayed()
        }
        rule.onNodeWithTag("standings.mode.Conference").assertIsSelected()
        rule.onAllNodes(rowMatcher).assertCountEquals(16)

        rule.onNodeWithTag("standings.mode.Division").performClick().assertIsSelected()
        rule.waitForIdle()
        assertEquals(StandingsMode.Division, suppliedState.value.mode)
        assertEquals(
            listOf("Atlantic", "Metropolitan"),
            suppliedState.value.groups.sections
                .map { it.title },
        )
        // The number restarts in each division block: 1-8 twice, not 1-16 across the conference.
        val ranks = rule.onAllNodesWithTag("standings.rank", useUnmergedTree = true)
        ranks.assertCountEquals(16)
        ranks[0].assertTextEquals("1")
        ranks[7].assertTextEquals("8")
        ranks[8].assertTextEquals("1")
        assertReach()

        rule.onNodeWithTag("standings.mode.Playoffs").performClick().assertIsSelected()
        rule.waitForIdle()
        rule.onAllNodes(rowMatcher).assertCountEquals(16)
        rule.onAllNodesWithText("Playoff line").assertCountEquals(1)
    }

    @Test
    fun aConferencePageWithNoTeamsSaysWhyInsteadOfLookingEmpty() {
        launchScreen(fresh.copy(view = StandingsView.Eastern, groups = StandingsGroups(emptyList()), loading = false))
        rule.onAllNodesWithText("did not name a conference", substring = true).assertCountEquals(1)
    }

    @Test
    fun everyConferencePageHoldsSixteenClubsAndScrollsRatherThanShrinkingItsRows() {
        // This used to demand the whole conference above the fold at 30 dp a row. Rows open clubs now, and a control
        // is 48 dp, so a sixteen-club conference is taller than the screen: the table scrolls and the rows stay legible.
        launchScreen()
        listOf(StandingsView.Eastern, StandingsView.Western).forEach { view ->
            rule.onNodeWithTag("standings.view.$view").performClick()
            rule.waitForIdle()
            StandingsMode.entries.forEach { mode ->
                rule.onNodeWithTag("standings.mode.$mode").performClick()
                rule.waitForIdle()
                val viewport = rule.onNodeWithTag("standings.list").fetchSemanticsNode().boundsInRoot
                val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
                val fullyVisible =
                    rule.onAllNodes(rowMatcher).fetchSemanticsNodes().count { row ->
                        row.positionInRoot.y >= viewport.top - 0.5f &&
                            row.positionInRoot.y + row.size.height <= viewport.bottom + 0.5f
                    }
                val rowsNow = rule.onAllNodes(rowMatcher).fetchSemanticsNodes()
                val overflow =
                    (rowsNow.maxOfOrNull { it.positionInRoot.y + it.size.height } ?: 0f) - viewport.bottom
                println(
                    "CONFERENCE_MEASUREMENT: ${view.name}/${mode.name} fullyVisible=$fullyVisible " +
                        "window=${window.width / rule.density.density}x${window.height / rule.density.density} " +
                        "listHeight=${viewport.height / rule.density.density} " +
                        "overflowDp=${overflow / rule.density.density}",
                )
                assertEquals("${view.name}/${mode.name} still lists sixteen clubs", 16, rowsNow.size)
                assertTrue(
                    "${view.name}/${mode.name} needs a scroll once rows are touch targets",
                    fullyVisible < 16,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-420dpi")
    fun bothControlRowsFitLabelsOnASmallPhone() {
        launchScreen()
        listOf("Eastern" to StandingsView.Eastern, "Western" to StandingsView.Western, "League" to StandingsView.League)
            .forEach { (label, view) ->
                val chip = rule.onNodeWithTag("standings.view.$view").fetchSemanticsNode().boundsInRoot
                val text =
                    rule
                        .onNode(
                            hasText(label) and hasAnyAncestor(hasTestTag("standings.view.$view")),
                            useUnmergedTree = true,
                        ).fetchSemanticsNode()
                assertTrue(
                    "$label: ${text.size.width / rule.density.density} dp inside a ${chip.width / rule.density.density} dp tab",
                    text.size.width <= chip.width,
                )
            }
        rule.onNodeWithTag("standings.view.Eastern").performClick()
        rule.waitForIdle()
        listOf("Playoffs" to StandingsMode.Playoffs, "Division" to StandingsMode.Division, "Conference" to StandingsMode.Conference)
            .forEach { (label, mode) ->
                val chip = rule.onNodeWithTag("standings.mode.$mode").fetchSemanticsNode().boundsInRoot
                val text =
                    rule
                        .onNode(
                            hasText(label) and hasAnyAncestor(hasTestTag("standings.mode.$mode")),
                            useUnmergedTree = true,
                        ).fetchSemanticsNode()
                assertTrue(
                    "$label: ${text.size.width / rule.density.density} dp inside a ${chip.width / rule.density.density} dp segment",
                    text.size.width <= chip.width,
                )
            }
    }

    @Test
    fun theRankingRowStaysPutWhileTheTableIsDragged() {
        // The strip is chrome, not page content: it must not travel with the drag the way the table does.
        launchScreen()
        val before = rule.onNodeWithTag("standings.mode.Playoffs").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("standings.list").performTouchInput {
            down(center)
            moveBy(Offset(-160f, 0f))
        }
        rule.waitForIdle()
        val during = rule.onNodeWithTag("standings.mode.Playoffs").fetchSemanticsNode().boundsInRoot
        assertEquals("the ranking strip must not move horizontally", before.left, during.left, 0.5f)
        assertEquals("the ranking strip must not move vertically", before.top, during.top, 0.5f)
        rule.onNodeWithTag("standings.list").performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test
    fun theScreenOpensOnTheEasternPlayoffPicture() {
        launchScreen()
        rule.onNodeWithTag("standings.view.Eastern").assertIsSelected()
        rule.onNodeWithTag("standings.mode.Playoffs").assertIsSelected()
        rule.onNodeWithText("Atlantic").assertIsDisplayed()
    }

    @Test
    fun theIndicatorIsDrivenByThePagerNotByTheSettleCallback() {
        // The reported delay: the table arrived, the highlight waited for the settle round trip. Prove the highlight
        // no longer depends on that callback at all - here the callback deliberately never updates the state, and the
        // strip still lands on the ranking whose table is on screen.
        launchScreen(fresh, applySelection = false)
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("standings.mode.Division").assertIsSelected()
        rule.onNodeWithTag("standings.mode.Playoffs").assertIsNotSelected()
        // The state never heard about it, which is exactly the point: the indicator moved on its own.
        assertEquals(StandingsMode.Playoffs, suppliedState.value.mode)
    }
}
