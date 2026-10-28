@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.standings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.policy.StandingsGrouping
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTargets
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
    private val fresh = StandingsUiState(groups = StandingsGrouping.group(rows, StandingsView.League), loading = false)
    private val suppliedState = mutableStateOf(fresh)
    private val navTags = arrayOf("test.nav.Scores", "test.nav.Standings", "test.nav.Teams")
    private val rowMatcher =
        SemanticsMatcher("standings row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("standings.row.") == true
        }

    private fun launchScreen(
        state: StandingsUiState = fresh,
        onRefresh: () -> Unit = {
        },
        selected: MutableList<StandingsView> = mutableListOf(),
    ) {
        suppliedState.value = state
        rule.setContent {
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
                    StandingsScreen(suppliedState.value, onRefresh, { view ->
                        selected += view
                        suppliedState.value = suppliedState.value.copy(view = view, groups = StandingsGrouping.group(rows, view))
                    }, Modifier.padding(insets))
                }
            }
        }
    }

    @Test
    fun leagueContainsThirtyTwoRowsAndEveryTeamCanBeReached() {
        launchScreen()
        rule.onAllNodes(rowMatcher).assertCountEquals(32)
        rows.forEach { row ->
            rule.onNodeWithTag("standings.row.0.${row.team.abbrev}").performScrollTo().assertIsDisplayed()
        }
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
    fun switcherIsFrequentPrimaryRowsAreReachTargetsAndViewsSwitch() {
        val selected = mutableListOf<StandingsView>()
        launchScreen(selected = selected)
        val targets = rule.reachTargets()
        val switcher = targets.single { it.tag == "standings.view" }
        assertTrue(switcher.primary)
        assertTrue(switcher.frequent)
        assertEquals(32, targets.count { it.tag.startsWith("standings.row.") && !it.primary })
        assertEquals((navTags.toList() + "standings.view").sorted(), targets.filter { it.primary }.map { it.tag }.sorted())
        StandingsView.entries.forEach { view ->
            rule.onNodeWithTag("standings.view.$view").performClick().assertIsSelected()
            assertEquals(view, suppliedState.value.view)
            assertReach()
        }
        assertEquals(StandingsView.entries, selected)
    }

    @Test
    fun wildcardCutIsLabelledPlayoffLineBelowWcTwo() {
        launchScreen(fresh.copy(view = StandingsView.Wildcard, groups = StandingsGrouping.group(rows, StandingsView.Wildcard)))
        // The fixture is the league's actual 3/3/10 layout in each conference.
        val groups = suppliedState.value.groups
        assertEquals(listOf(3, 3, 10, 3, 3, 10), groups.sections.map { it.rows.size })
        rule.onAllNodesWithText("Playoff line").assertCountEquals(2)
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
    fun pinnedSectionAppearsBeforeTheUnchangedLeagueTable() {
        launchScreen(fresh.copy(groups = StandingsGrouping.group(rows, StandingsView.League, rows.take(2)), pinnedShown = true))
        rule.onNodeWithText("Your teams").assertIsDisplayed()
        rule.onAllNodes(rowMatcher).assertCountEquals(34)
        assertTrue(
            rule
                .onNodeWithText("Your teams")
                .fetchSemanticsNode()
                .positionInRoot.y <
                rule
                    .onNodeWithText("NHL")
                    .fetchSemanticsNode()
                    .positionInRoot.y,
        )
        assertReach()
    }

    @Test
    fun staleBannerRetainsRowsShowsRefreshErrorAndRetries() {
        var refreshes = 0
        launchScreen(fresh.copy(stale = Freshness.Stale(Instant.parse("2026-10-26T23:42:00Z")), error = LoadError.Network), onRefresh = {
            refreshes++
        })
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
        launchScreen(fresh.copy(error = LoadError.Local), onRefresh = { refreshes++ })
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
        launchScreen(StandingsUiState(), onRefresh = { refreshes++ })
        rule.onNodeWithText("Loading standings…").assertIsDisplayed()
        rule.onNodeWithText("No table yet.").assertDoesNotExist()
        rule.runOnIdle { suppliedState.value = StandingsUiState(loading = false) }
        rule.onNodeWithText("No table yet.").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertDoesNotExist()
        rule.runOnIdle { suppliedState.value = StandingsUiState(loading = false, error = LoadError.Network) }
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
    fun elevenRowsAreFullyVisibleWithSwitcherHeaderAndBottomNavigationAt411By923() {
        launchScreen()
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
        assertEquals("Fully visible team rows measured at 411x923 with bottom navigation", 11, fullyVisible)
        assertTrue("Measured only $fullyVisible fully visible team rows", fullyVisible >= 9)
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
        assertFalse(rule.reachTargets().filter { it.tag.startsWith("standings.row.") }.any { it.primary })
        // The pull-to-refresh gesture is reachable and measured, but it is a gesture rather than a control.
        val gesture = rule.reachTargets().single { it.tag == "standings.refresh" }
        assertFalse("Pull-to-refresh is a gesture, not a primary control", gesture.primary)
        val primaries = navTags.toList() + "standings.view"
        if (retry) {
            val recovery = rule.reachTargets().single { it.tag == "standings.retry" }
            // §5a: a fixed action is a primary control. Only collection rows are reach targets.
            assertTrue("The retry is a fixed action, so it is a primary control", recovery.primary)
        }
        rule.assertPrimaryControlsReachable(*(primaries + if (retry) listOf("standings.retry") else emptyList()).toTypedArray())
    }
}
