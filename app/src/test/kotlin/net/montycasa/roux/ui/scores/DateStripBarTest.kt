package net.montycasa.roux.ui.scores

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.policy.DateStripItem
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateStripBarTest {
    @get:Rule val rule = createComposeRule()
    private val scale = mutableStateOf(1f)
    private val selected = mutableStateOf("2026-10-23")
    private val dates = listOf("2026-10-22", "2026-10-23", "2026-10-24")
    private var opened = 0
    private val width = mutableStateOf(360.dp)

    private fun launch(today: String = "2026-10-24") {
        rule.setContent {
            RouxTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                    DateShelf(
                        dates.map { DateStripItem(it, 0) },
                        selected.value,
                        today,
                        { selected.value = it },
                        { opened++ },
                        Modifier.width(width.value),
                    )
                }
            }
        }
    }

    @Test fun shelfIsExactly72DpAtBothFontScales() {
        launch()
        listOf(1f, 2f).forEach { font ->
            rule.runOnIdle { scale.value = font }
            rule.onNodeWithTag("scores.shelf").assertHeightIsEqualTo(72.dp)
            listOf(
                "scores.previous",
                "scores.next",
            ).forEach { rule.onNodeWithTag(it).assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp) }
            rule.onNodeWithTag("scores.calendar").assertDoesNotExist()
            rule.onNodeWithContentDescription("Open calendar").assertDoesNotExist()
            val chip = rule.onNodeWithTag("scores.dateLabel")
            chip.assertHeightIsEqualTo(56.dp).assertContentDescriptionEquals("Change date, Fri, Oct 23")
            val bounds = chip.getUnclippedBoundsInRoot()
            val shelf = rule.onNodeWithTag("scores.shelf").getUnclippedBoundsInRoot()
            assertEquals((shelf.left.value + shelf.right.value) / 2, (bounds.left.value + bounds.right.value) / 2, 0.5f)
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag("scores.dateLabel").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse(
                "font=$font text=${layouts.single().layoutInput.text} size=${layouts.single().size} paragraph=${layouts.single().multiParagraph.width}x${layouts.single().multiParagraph.height}",
                layouts.single().hasVisualOverflow,
            )
            assertFalse(layouts.single().isLineEllipsized(0))
        }
    }

    @Test fun labelUsesLongestFittingFallback() {
        launch()
        rule.onNodeWithText("Fri, Oct 23").assertExists()
        rule.runOnIdle { scale.value = 2f }
        rule.onNodeWithText("Oct 23").assertExists()
        rule.runOnIdle { width.value = 200.dp }
        rule.onNodeWithText("23").assertExists()
        rule
            .onNodeWithTag(
                "scores.dateLabel",
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Friday, October 23, 2026"))
    }

    @Test fun todayShortensToToday() {
        launch(today = "2026-10-23")
        rule.onNodeWithText("Today, Oct 23").assertExists()
        rule.runOnIdle { scale.value = 2f }
        rule.onNodeWithText("Today").assertExists()
    }

    @Test fun arrowsStepAndDisableAtEnds() {
        launch()
        rule.onNodeWithTag("scores.previous").performClick().assertIsNotEnabled()
        assertEquals(dates.first(), selected.value)
        rule
            .onNodeWithTag("scores.next")
            .performClick()
            .performClick()
            .assertIsNotEnabled()
        assertEquals(dates.last(), selected.value)
    }

    @Test fun dateChipOpensTheSheet() {
        launch()
        rule.onNodeWithTag("scores.dateLabel").performClick()
        assertEquals(1, opened)
    }
}
