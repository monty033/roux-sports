package net.montycasa.roux.ui.teams

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.ReachTargetTag
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
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
class TeamsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val teams = teamStandings().map { it.team }
    private val fresh = TeamsUiState(rows = teams, isRefreshing = false, freshness = Freshness.Fresh)
    private val suppliedState = mutableStateOf(fresh)
    private val fontScale = mutableStateOf(1f)
    private val rowMatcher =
        SemanticsMatcher("team row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("teams.row.") == true }

    private fun launchScreen(
        state: TeamsUiState = fresh,
        onToggle: (TeamKey) -> Unit = {},
        onRetry: () -> Unit = {},
        dark: Boolean = false,
    ) {
        suppliedState.value = state
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale.value)) {
                RouxTheme(dark) { TeamsScreen(suppliedState.value, onToggle, onRetry) }
            }
        }
    }

    @Test
    fun allThirtyTwoRowsCanBeScrolledIntoViewWithFullSizeToggles() {
        launchScreen()
        rule.onAllNodes(rowMatcher, useUnmergedTree = true).assertCountEquals(32)
        val seen = mutableSetOf<TeamKey>()
        teams.forEach { team ->
            toggle(team).performScrollTo()
            rule.onNodeWithTag("teams.row.${team.abbrev}").assertIsDisplayed()
            rule.onNodeWithText(team.name!!).assertIsDisplayed()
            toggle(team)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not favorite"))
            assertReach()
            seen += team.key
        }
        assertEquals(32, seen.size)
        assertEquals(teams.map { it.key }.toSet(), seen)
    }

    @Test
    fun tappingEachToggleCallsOnlyItsTeamKeyAndStateComesFromTheCaller() {
        val calls = mutableListOf<TeamKey>()
        launchScreen(onToggle = { calls += it })
        teams.forEachIndexed { index, team ->
            toggle(team).performScrollTo()
            toggle(team).assertIsOff().performClick().assertIsOff()
            assertEquals(teams.take(index + 1).map { it.key }, calls)
        }
        val last = teams.last()
        rule.runOnIdle { suppliedState.value = fresh.copy(favourites = setOf(last.key)) }
        toggle(last)
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Favorite"))
            .performClick()
        assertEquals(teams.map { it.key } + last.key, calls)
    }

    @Test
    fun aNullNameShowsTheAbbreviationAndLabelsItsToggle() {
        val team = TeamRef(TeamKey("TOR"))
        launchScreen(fresh.copy(rows = listOf(team)))
        // Twice on purpose: without a name the abbreviation is the row's label, and the crest falls back to the same
        // abbreviation when the feed has no artwork. Both are correct; the toggle still names itself with it.
        rule.onAllNodesWithText("TOR").onFirst().assertIsDisplayed()
        toggle(team).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("TOR")))
        assertReach()
    }

    /**
     * The classification is a deliberate contract decision, not an accident: R1 would put every primary
     * control below the vertical midpoint, which a thirty-two item list cannot do. Pinned so that changing
     * one without the other fails here instead of quietly dropping the rows out of the audit.
     */
    @Test
    fun togglesAreReachTargetsAndOnlyTheRetryActionIsPrimary() {
        var retries = 0
        launchScreen(fresh.copy(error = LoadError.Network), onRetry = { retries++ })
        val targets = rule.reachTargets()
        val toggles = targets.filter { it.tag.startsWith("teams.toggle.") }
        assertEquals("every row must declare a reach target", 32, toggles.size)
        assertTrue("a row toggle must not claim to be a primary control", toggles.none { it.primary })
        assertEquals(listOf("teams.retry"), targets.filter { it.primary }.map { it.tag })
    }

    @Test
    fun aReadOnlyStoreDisablesTheTogglesAndExplainsWhy() {
        var toggles = 0
        launchScreen(fresh.copy(canEdit = false), onToggle = { toggles++ })
        rule
            .onNodeWithText(
                "Your saved settings come from a newer version of Roux Sports, so favorites can't be changed here.",
            ).assertIsDisplayed()
        // A null onCheckedChange removes the click action instead of setting a Disabled flag, so that is
        // what to assert: the control is not merely dimmed, it cannot be pressed.
        toggle(teams.first()).assertHasNoClickAction()
        assertReach()
        assertEquals(0, toggles)
    }

    /**
     * When the saved selection could not be read, every checkbox would otherwise say "Not favorite" - a
     * claim about teams we never looked up. Indeterminate says the only true thing: it is not known.
     */
    @Test
    fun anUnknownSelectionIsIndeterminateRatherThanNotFavourite() {
        launchScreen(fresh.copy(canEdit = false, selectionKnown = false))
        toggle(teams.first())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not known"))
            .assertHasNoClickAction()
        assertEquals(
            "no toggle may claim a state it does not know",
            0,
            rule
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not favorite"))
                .fetchSemanticsNodes()
                .size,
        )
        assertReach()
    }

    @Test
    fun aFailedSaveIsReportedRatherThanLookingIgnored() {
        launchScreen(fresh.copy(saveFailed = true))
        rule.onNodeWithText("Couldn't save that change. Try again.").assertIsDisplayed()
        // The toggles stay usable: one failed write is not a reason to lock the picker.
        toggle(teams.first()).assertIsEnabled()
        assertReach()
    }

    /**
     * A picker is judged by how much of it you can see at once. This screen once split the height 50/50
     * between a two-line header and the list, which left five of thirty-two teams visible under a large blank
     * block - and every other test still passed, because they measure controls rather than the screen.
     */
    @Test
    fun theListOwnsTheScreenAndShowsAtLeastNineTeamsAtOnce() {
        launchScreen()
        val list = rule.onNodeWithTag("teams.list").fetchSemanticsNode().boundsInRoot
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        val listHeightDp = list.height / density
        val windowHeightDp = window.height / density
        assertTrue(
            "the list is $listHeightDp dp of a $windowHeightDp dp window, which is not most of the screen",
            listHeightDp > windowHeightDp * 0.6f,
        )
        val visible =
            teams.count { team ->
                val row = rule.onNodeWithTag("teams.row.${team.abbrev}").fetchSemanticsNode().boundsInRoot
                row.top >= list.top - 0.5f && row.bottom <= list.bottom + 0.5f
            }
        assertTrue("only $visible of ${teams.size} teams are fully visible at 411x923", visible >= 9)
    }

    @Test
    fun missingOffersRetryAndLoadingDoesNotClaimAnError() {
        var retries = 0
        launchScreen(TeamsUiState(isRefreshing = false, error = LoadError.Network), onRetry = { retries++ })
        rule.onNodeWithText("Couldn’t load teams. Try again.").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        assertReach(retry = true)
        rule.runOnIdle { suppliedState.value = TeamsUiState() }
        rule.onNodeWithText("Loading teams…").assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load teams. Try again.").assertDoesNotExist()
        rule.onNodeWithText("Retry").assertDoesNotExist()
        assertReach()
    }

    @Test
    fun staleRowsStayVisibleAndRetryIsDisabledWhileRefreshing() {
        val stale = fresh.copy(freshness = Freshness.Stale(Instant.parse("2026-10-26T23:00:00Z")), error = LoadError.Network)
        var retries = 0
        launchScreen(stale, onRetry = { retries++ })
        rule.onNodeWithText("Showing saved teams. They may be out of date.").assertIsDisplayed()
        rule.onNodeWithTag("teams.row.ANA").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        assertReach(retry = true)
        rule.runOnIdle { suppliedState.value = stale.copy(isRefreshing = true) }
        rule.onNodeWithText("Loading teams…").assertIsNotEnabled()
        rule.onNodeWithTag("teams.row.ANA").assertIsDisplayed()
        assertReach(retry = true)
    }

    @Test
    fun reachPassesThroughoutTheListAtNormalAndDoubleType() = auditAtBothFontScales()

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-420dpi")
    fun reachPassesOnThePlanSmallPhoneAtNormalAndDoubleType() = auditAtBothFontScales()

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-420dpi")
    fun reachPassesOnTheShellSmallPhoneAtNormalAndDoubleType() = auditAtBothFontScales()

    private fun auditAtBothFontScales() {
        launchScreen()
        listOf(1f, 2f).forEach { scale ->
            rule.runOnIdle { fontScale.value = scale }
            listOf(0, 16, 31).forEach { index ->
                toggle(teams[index]).performScrollTo()
                toggle(teams[index]).assertIsDisplayed()
                assertReach()
            }
            toggle(teams.first()).performScrollTo()
            // A gesture leaves partially clipped rows; aligned scrolls alone would miss them.
            rule.onNodeWithTag("teams.list").performTouchInput { swipeUp() }
            assertReach()
            rule.runOnIdle { suppliedState.value = TeamsUiState(isRefreshing = false, error = LoadError.Network) }
            assertReach(retry = true)
            rule.runOnIdle { suppliedState.value = fresh }
        }
    }

    private fun toggle(team: TeamRef) = rule.onNode(SemanticsMatcher.expectValue(ReachTargetTag, "teams.toggle.${team.abbrev}"))

    private fun assertReach(retry: Boolean = false) {
        // A toggle is a reach target, not a primary control: R1 belongs to fixed actions, and putting a
        // thirty-two item list in the bottom half is how this screen ended up showing five teams at once.
        // Losing the modifier still has to fail, so every composed row must declare one.
        val rows = suppliedState.value.rows.size
        val declared = rule.reachTargets().count { it.tag.startsWith("teams.toggle.") }
        if (rows > 0) assertEquals("every row must declare a reach target", rows, declared)
        // R4 size, gap and R5 containment still apply to those targets inside the audit.
        val primary = if (retry) listOf("teams.retry") else emptyList()
        rule.assertPrimaryControlsReachable(*primary.toTypedArray())
    }

    @Test
    fun togglingAFavouriteLeavesTheRowWhereItWas() {
        launchScreen()
        val tor = teams.first { it.key == TeamKey("TOR") }
        val before = rule.onNodeWithTag("teams.row.${tor.abbrev}").fetchSemanticsNode().boundsInRoot

        rule.runOnIdle {
            suppliedState.value = fresh.copy(favourites = fresh.favourites + tor.key)
        }

        val after = rule.onNodeWithTag("teams.row.${tor.abbrev}").fetchSemanticsNode().boundsInRoot
        // The switch changed, not the list: the row a thumb is on must not move out from under it.
        assertEquals(before, after)
    }

    @Test
    fun listLight() {
        launchScreen()
        rule.onRoot().captureRoboImage("src/test/screenshots/teams/list-light.png")
    }

    @Test
    fun listDark() {
        launchScreen(dark = true)
        rule.onRoot().captureRoboImage("src/test/screenshots/teams/list-dark.png")
    }
}
