package net.montycasa.roux.ui.scores

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.policy.DateStrip
import net.montycasa.roux.domain.policy.DateStripItem
import net.montycasa.roux.ui.reach.PrimaryControlTag
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w600dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateStripBarTest {
    @get:Rule
    val rule = createComposeRule()

    private val week =
        DateStrip.items(
            dateIndex = listOf(DateIndexEntry("2026-10-26", 3), DateIndexEntry("2026-10-27", 1), DateIndexEntry("2026-10-28", 0)),
            selectedDate = "2026-10-26",
        )
    private val selection = mutableStateOf("2026-10-26")
    private val fontScale = mutableStateOf(1f)
    private val suppliedItems = mutableStateOf(week)
    private lateinit var localDensity: Density

    private fun chip(date: String) = rule.onNode(SemanticsMatcher.expectValue(PrimaryControlTag, "scores.dateChip.$date"))

    private fun launchStrip(
        items: List<DateStripItem> = week,
        selectedDate: String = "2026-10-26",
        onSelect: (String) -> Unit = {},
        dark: Boolean = false,
    ) {
        suppliedItems.value = items
        selection.value = selectedDate
        rule.setContent {
            RouxTheme(dark = dark) {
                val base = LocalDensity.current
                localDensity = Density(base.density, fontScale.value)
                CompositionLocalProvider(LocalDensity provides localDensity) {
                    // The host owns vertical placement. Frequent controls must start below 70%.
                    Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                        DateStripBar(suppliedItems.value, selection.value, onSelect, Modifier.testTag("dateStrip"))
                    }
                }
            }
        }
    }

    private fun assertMinimumChipSizes(items: List<DateStripItem>) {
        items.forEach { item ->
            val bounds = chip(item.date).getUnclippedBoundsInRoot()
            // The test API returns dp bounds; convert through the composition's LocalDensity to
            // compare the actual measured dimensions in pixels, including at non-unit density.
            with(localDensity) {
                assertTrue("${item.date} width: ${bounds.width}", bounds.width.toPx() >= 56.dp.toPx())
                assertTrue("${item.date} height: ${bounds.height}", bounds.height.toPx() >= 56.dp.toPx())
            }
        }
    }

    @Test
    fun everyChipMeasuresAtLeastFiftySixDpInBothDirections() {
        launchStrip()
        assertMinimumChipSizes(week)
    }

    @Test
    fun missingAndPublishedEmptyDaysAnnounceTheFullDayAndNoGames() {
        launchStrip()
        chip("2026-10-25").assert(hasContentDescription("Sunday, October 25, 2026, no games"))
        chip("2026-10-28").assert(hasContentDescription("Wednesday, October 28, 2026, no games"))
    }

    @Test
    fun gameDaysAnnounceTheirFullDayAndCountWithoutNoGames() {
        launchStrip()
        chip("2026-10-26").assert(hasContentDescription("Monday, October 26, 2026, 3 games"))
        chip("2026-10-27").assert(hasContentDescription("Tuesday, October 27, 2026, 1 game"))
        listOf("2026-10-26", "2026-10-27").forEach { date ->
            val descriptions = chip(date).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
            assertFalse(descriptions.any { it.contains("no games") })
        }
    }

    @Test
    fun tappingOctoberTwentySixReturnsTheExactIsoDate() {
        val selected = mutableListOf<String>()
        launchStrip(onSelect = { selected += it })
        chip("2026-10-26").performClick()
        assertEquals(listOf("2026-10-26"), selected)
    }

    @Test
    fun emptyDaysRemainTappable() {
        val selected = mutableListOf<String>()
        launchStrip(onSelect = { selected += it })
        chip("2026-10-25").performClick()
        assertEquals(listOf("2026-10-25"), selected)
    }

    @Test
    fun theFirstChipStartsAtLeastSixteenDpInsideTheContainer() {
        launchStrip(selectedDate = week.first().date)
        val container = rule.onNodeWithTag("dateStrip").getUnclippedBoundsInRoot()
        val first = chip(week.first().date).getUnclippedBoundsInRoot()
        assertTrue("left inset: ${first.left - container.left}", first.left - container.left >= Reach.EDGE_INSET_DP.dp)
    }

    @Test
    fun selectionSemanticsFollowTheSuppliedDate() {
        launchStrip()
        chip("2026-10-26").assertIsSelected()
        chip("2026-10-25").assertIsNotSelected()
        rule.runOnIdle { selection.value = "2026-10-25" }
        chip("2026-10-25").assertIsSelected()
        chip("2026-10-26").assertIsNotSelected()
    }

    @Test
    fun aPolicyWeekRendersExactlySevenChips() {
        launchStrip()
        assertEquals(7, week.size)
        rule.onAllNodes(isSelectable()).assertCountEquals(7)
        week.forEach { chip(it.date).assertIsDisplayed() }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-420dpi")
    fun aSinglePolicyItemRendersAndPassesTheReachAudit() {
        val single = DateStrip.items(emptyList(), "2026-10-26", daysEitherSide = 0)
        launchStrip(items = single)
        rule.onAllNodes(isSelectable()).assertCountEquals(1)
        chip("2026-10-26").assertIsDisplayed().assertIsSelected()
        assertMinimumChipSizes(single)
        rule.assertPrimaryControlsReachable("scores.dateChip.2026-10-26")
    }

    @Test
    fun everyChipIsDeclaredAsAFrequentPrimaryAndTheWeekPassesTheReachAudit() {
        launchStrip()
        rule.assertPrimaryControlsReachable(*week.map { "scores.dateChip.${it.date}" }.toTypedArray())
        val targets = rule.reachTargets()
        assertEquals(7, targets.size)
        assertTrue(targets.all { it.primary && it.frequent })
    }

    @Test
    fun doubleTypeGrowsTheChipsAndStillPassesTheReachAuditInTheDarkTheme() {
        launchStrip(dark = true)
        val normalHeight = chip("2026-10-26").getUnclippedBoundsInRoot().height
        rule.runOnIdle { fontScale.value = 2f }
        assertTrue(chip("2026-10-26").getUnclippedBoundsInRoot().height > normalHeight)
        assertMinimumChipSizes(week)
        rule.assertPrimaryControlsReachable(*week.map { "scores.dateChip.${it.date}" }.toTypedArray())
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-420dpi")
    fun theSelectedChipIsRevealedInitiallyAndWhenSelectionChanges() {
        launchStrip(selectedDate = week.last().date)
        assertFullyInsideViewport(week.last().date)
        rule.runOnIdle { selection.value = week.first().date }
        assertFullyInsideViewport(week.first().date)
        chip(week.last().date).performScrollTo()
        assertFullyInsideViewport(week.last().date)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-420dpi")
    fun replacingThePolicyWindowRevealsTheNewSelectedChip() {
        launchStrip(selectedDate = week.last().date)
        rule.runOnIdle {
            suppliedItems.value = DateStrip.items(emptyList(), "2026-11-26")
            selection.value = "2026-11-29"
        }
        assertFullyInsideViewport("2026-11-29")
        chip("2026-11-29").assertIsSelected()
    }

    @Test
    fun anEmptyPolicyWindowRendersWithoutInventingDates() {
        launchStrip(items = DateStrip.items(emptyList(), "2026-10-26", seasonEnd = "2026-10-01"))
        rule.onAllNodes(isSelectable()).assertCountEquals(0)
    }

    private fun assertFullyInsideViewport(date: String) {
        val container = rule.onNodeWithTag("dateStrip").getUnclippedBoundsInRoot()
        val bounds = chip(date).getUnclippedBoundsInRoot()
        chip(date).assertIsDisplayed()
        assertTrue("$date left edge: ${bounds.left}", bounds.left >= container.left + Reach.EDGE_INSET_DP.dp)
        assertTrue("$date right edge: ${bounds.right}", bounds.right <= container.right - Reach.EDGE_INSET_DP.dp)
    }
}
