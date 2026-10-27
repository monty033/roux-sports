package net.montycasa.roux.ui.scores

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.ui.reach.PrimaryControlTag
import net.montycasa.roux.ui.theme.RouxDarkAccents
import net.montycasa.roux.ui.theme.RouxDarkColors
import net.montycasa.roux.ui.theme.RouxLightAccents
import net.montycasa.roux.ui.theme.RouxLightColors
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
class DateShelfCalendarTest {
    @get:Rule val rule = createComposeRule()
    private val state =
        mutableStateOf(
            ScoresUiState(
                selectedDate = "2026-11-15",
                today = "2026-11-16",
                freshness = Freshness.Fresh,
                dateIndex = listOf(DateIndexEntry("2026-11-15", 2), DateIndexEntry("2026-11-16", 1), DateIndexEntry("2026-11-17", 0)),
            ),
        )

    private fun launch(
        dark: Boolean = false,
        selected: String = "2026-11-15",
    ) {
        state.value = state.value.copy(selectedDate = selected)
        rule.setContent {
            RouxTheme(dark) {
                ScoresScreen(state.value, {}, {}, { state.value = state.value.copy(selectedDate = it) })
            }
        }
    }

    private fun chip(date: String) = rule.onNode(SemanticsMatcher.expectValue(PrimaryControlTag, "scores.dateChip.$date"))

    private fun tile() = rule.onNodeWithContentDescription("Open calendar")

    private fun open() = tile().performClick()

    @Test
    fun shelfMeasuresExactChipsGapsInsetsAndPinnedCalendarAt411Dp() {
        launch()
        val first = chip("2026-11-12")
        val next = chip("2026-11-13")
        first.assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        next.assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        assertEquals(8f, (next.getUnclippedBoundsInRoot().left - first.getUnclippedBoundsInRoot().right).value, 0.01f)
        assertEquals(16f, first.getUnclippedBoundsInRoot().left.value, 0.01f)
        tile().assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        val pinned = tile().getUnclippedBoundsInRoot()
        assertEquals(395f, pinned.right.value, 0.01f)
        val shelf = rule.onNodeWithTag("scores.shelf").getUnclippedBoundsInRoot()
        assertEquals(73f, shelf.height.value, 0.01f)
        assertEquals(9f, (pinned.top - shelf.top).value, 0.01f)
        assertEquals(8f, (shelf.bottom - pinned.bottom).value, 0.01f)
        chip("2026-11-14").performTouchInput { swipeLeft() }
        assertEquals(pinned, tile().getUnclippedBoundsInRoot())
    }

    private fun textStyle(
        text: String,
        date: String = "2026-11-15",
    ): androidx.compose.ui.text.TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        rule
            .onNode(
                hasText(text) and hasAnyAncestor(SemanticsMatcher.expectValue(PrimaryControlTag, "scores.dateChip.$date")),
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(results)
            }
        return results.single().layoutInput.style
    }

    @Test
    fun todayHasOnlyItsLabelAndSelectionPaintsAllThreeDateLines() {
        launch()
        assertEquals(listOf("Today"), chip("2026-11-16").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
        assertEquals(12f, textStyle("Today", "2026-11-16").fontSize.value, 0f)
        assertEquals(FontWeight.ExtraBold, textStyle("Today", "2026-11-16").fontWeight)
        listOf("Sun", "15", "Nov").forEach { assertEquals(RouxLightAccents.onFill, textStyle(it).color) }
        assertEquals(9f, textStyle("Sun").fontSize.value, 0f)
        assertEquals(19f, textStyle("15").fontSize.value, 0f)
        assertEquals(8f, textStyle("Nov").fontSize.value, 0f)
        assertEquals("tnum", textStyle("15").fontFeatureSettings)
        assertPaint("2026-11-15", RouxLightAccents.fill)
        chip("2026-11-16").performClick()
        assertPaint("2026-11-16", RouxLightAccents.fill)
        assertPaint("2026-11-15", RouxLightColors.surfaceVariant)
        assertEquals(RouxLightColors.onSurfaceVariant, textStyle("Sun").color)
        assertEquals(RouxLightColors.onSurface, textStyle("15").color)
        assertEquals(RouxLightAccents.muted, textStyle("Nov").color)
        assertEquals(RouxLightAccents.onFill, textStyle("Today", "2026-11-16").color)
    }

    private fun assertPaint(
        date: String,
        color: Color,
    ) {
        chip(date).performScrollTo()
        val pixels = chip(date).captureToImage().toPixelMap()
        assertColor(color, pixels[pixels.width / 2, 2])
    }

    private fun assertColor(
        expected: Color,
        actual: Color,
    ) {
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
    }

    @Test
    fun calendarOpensAtSelectionAndArrowsCrossYearBoundariesOneMonthAtATime() {
        launch(selected = "2026-12-15")
        open()
        rule.onNodeWithTag("calendar.sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.IsDialog, Unit))
        rule.onNodeWithText("December 2026").assertExists()
        rule.onNodeWithContentDescription("Next month").performClick()
        rule.onNodeWithText("January 2027").assertExists()
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.onNodeWithText("December 2026").assertExists()
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.onNodeWithText("November 2026").assertExists()
        assertEquals("2026-12-15", state.value.selectedDate)
    }

    @Test
    fun tappingADayOnlyMovesTheDraftAndDoneAppliesIt() {
        launch(selected = "2026-11-15")
        open()
        rule.onNodeWithTag("calendar.day.2026-11-19").performClick()
        // The sheet shows the draft as selected, but the screen behind it has not moved.
        rule.onNodeWithTag("calendar.day.2026-11-19").assertIsSelected()
        assertEquals("2026-11-15", state.value.selectedDate)
        rule.onNodeWithText("DONE").performClick()
        rule.onNodeWithTag("calendar.sheet").assertDoesNotExist()
        assertEquals("2026-11-19", state.value.selectedDate)
    }

    @Test
    fun todayJumpsTheDraftAndOnlyDoneApplies() {
        launch(selected = "2026-12-15")
        open()
        rule.onNodeWithText("TODAY").performClick()
        rule.onNodeWithText("November 2026").assertExists()
        rule.onNodeWithTag("calendar.day.2026-11-16").assertIsSelected()
        // TODAY is a draft too: nothing is applied until DONE.
        assertEquals("2026-12-15", state.value.selectedDate)
        rule.onNodeWithTag("calendar.day.2026-11-20").performClick()
        rule.onNodeWithText("DONE").performClick()
        rule.onNodeWithTag("calendar.sheet").assertDoesNotExist()
        assertEquals("2026-11-20", state.value.selectedDate)
        open()
        rule.onNodeWithText("November 2026").assertExists()
        rule.onNodeWithTag("calendar.day.2026-11-20").assertIsSelected()
    }

    @Test
    fun scrimTapDiscardsTheDraftAndLeavesTheScoresWhereTheyWere() {
        launch()
        open()
        rule.onNodeWithTag("calendar.day.2026-11-19").performClick()
        rule.onNodeWithTag("calendar.day.2026-11-19").assertIsSelected()
        rule.onNodeWithTag("calendar.scrim").performTouchInput {
            click(
                androidx.compose.ui.geometry
                    .Offset(8f, 8f),
            )
        }
        rule.onNodeWithTag("calendar.sheet").assertDoesNotExist()
        assertEquals("2026-11-15", state.value.selectedDate)
        // Reopening starts from the applied date, not from the discarded draft.
        open()
        rule.onNodeWithTag("calendar.day.2026-11-15").assertIsSelected()
        rule.onNodeWithTag("calendar.day.2026-11-19").assertIsNotSelected()
    }

    @Test
    fun dialogDismissActionDiscardsTheDraft() {
        launch()
        open()
        rule.onNodeWithTag("calendar.day.2026-11-19").performClick()
        rule.onNodeWithTag("calendar.sheet").performSemanticsAction(SemanticsActions.Dismiss)
        rule.onNodeWithTag("calendar.sheet").assertDoesNotExist()
        assertEquals("2026-11-15", state.value.selectedDate)
    }

    @Test
    fun gameDotsUseExistingCountsAndSelectedInkWhileEmptyAndUnknownDaysHaveNone() {
        launch()
        open()
        val selected = rule.onNodeWithTag("calendar.dot.2026-11-15", useUnmergedTree = true)
        selected.assertWidthIsEqualTo(4.dp).assertHeightIsEqualTo(4.dp)
        // Capture the screen host: this is an in-screen semantic dialog, not an Android window.
        val pixels = rule.onNodeWithTag("calendar.overlay").captureToImage().toPixelMap()
        val dot = selected.getUnclippedBoundsInRoot()
        val overlay = rule.onNodeWithTag("calendar.overlay").getUnclippedBoundsInRoot()
        val x = ((dot.left - overlay.left).value + 2).toInt()
        val y = ((dot.top - overlay.top).value + 2).toInt()
        val expected = RouxLightAccents.onFill.copy(alpha = 0.85f)
        // The 85% dot is composited over the selected orange cell by captureToImage.
        val fill = RouxLightAccents.fill
        assertColor(
            Color(
                expected.red * 0.85f + fill.red * 0.15f,
                expected.green * 0.85f + fill.green * 0.15f,
                expected.blue * 0.85f + fill.blue * 0.15f,
            ),
            pixels[x, y],
        )
        rule.onNodeWithTag("calendar.dot.2026-11-16", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("calendar.dot.2026-11-17", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("calendar.dot.2026-11-18", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("calendar.day.2026-11-16").performClick()
        rule.onNodeWithTag("calendar.day.2026-11-16").assertIsSelected()
        assertTrue(state.value.dateIndex.isNotEmpty())
    }

    @Test
    fun calendarDarkUsesInkForItsTitleAndOnAccentForTheSelectedDay() {
        launch(dark = true)
        open()
        val titleLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("calendar.title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertEquals(
            RouxDarkColors.onSurface,
            titleLayouts
                .single()
                .layoutInput.style.color,
        )
        val selectedLayouts = mutableListOf<TextLayoutResult>()
        rule
            .onNode(
                hasText("15") and
                    hasAnyAncestor(
                        androidx.compose.ui.test
                            .hasTestTag("calendar.day.2026-11-15"),
                    ),
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(selectedLayouts) }
        assertEquals(
            RouxDarkAccents.onFill,
            selectedLayouts
                .single()
                .layoutInput.style.color,
        )
        assertEquals(
            FontWeight.ExtraBold,
            selectedLayouts
                .single()
                .layoutInput.style.fontWeight,
        )
    }

    @Test fun shelfLight() = capture(false, false)

    @Test fun shelfDark() = capture(true, false)

    @Test fun calendarLight() = capture(false, true)

    @Test fun calendarDark() = capture(true, true)

    private fun capture(
        dark: Boolean,
        sheet: Boolean,
    ) {
        launch(dark)
        if (sheet) open()
        val surface = if (sheet) "calendar.overlay" else "scores.shelf"
        val name = if (sheet) "sheet" else "shelf"
        val theme = if (dark) "dark" else "light"
        rule.onNodeWithTag(surface).captureRoboImage("src/test/screenshots/calendar/$name-$theme.png")
    }
}
