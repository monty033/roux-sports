@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The sub-navigation pills. The three are equal width and fill the strip, and each centres its crest and label as one
 * pair. A label is 14 sp, shrinks by half a point toward 12 sp when it cannot fit, and ellipsizes only at 12 sp. The
 * strip never scrolls. Normal scale and the largest font are both checked, since the floor has to hold at each.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailTabStripTest {
    @get:Rule
    val compose = createComposeRule()

    private val away = TeamRef(TeamKey("CBJ"), name = "Blue Jackets")
    private val home = TeamRef(TeamKey("UTA"), name = "Utah Mammoth")

    private fun show(
        width: Dp,
        fontScale: Float,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                RouxTheme(dark = false) {
                    Box(Modifier.width(width)) {
                        DetailTabStrip(away = away, home = home, selected = 0, onSelect = {})
                    }
                }
            }
        }
    }

    private val pills = listOf("Game", "CBJ", "UTA")

    private fun strip(): SemanticsNode = compose.onNodeWithTag("detail.tab.strip").fetchSemanticsNode()

    private fun pill(tag: String): SemanticsNode = compose.onNodeWithTag("detail.tab.$tag").fetchSemanticsNode()

    /** How far the strip can scroll sideways, in pixels. Zero when it does not scroll at all. */
    private fun scrollRange(): Float = strip().config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.maxValue() ?: 0f

    private fun SemanticsNode.isWithin(outer: SemanticsNode): Boolean =
        boundsInRoot.left >= outer.boundsInRoot.left - 0.5f && boundsInRoot.right <= outer.boundsInRoot.right + 0.5f

    /** The label's layout, which holds its size, its line count and whether it was ellipsized. */
    private fun labelLayout(tag: String): TextLayoutResult {
        val node = compose.onNodeWithTag("detail.tab.$tag.label", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(
            "$tag has no text layout",
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(layouts) == true,
        )
        return layouts.single()
    }

    /**
     * A label is at least 12 sp and at most 14 sp. A label drawn whole is on one line with its last character present and
     * its full width. Any other label is ellipsized, and that is allowed only at the 12 sp floor.
     */
    private fun assertLabelRule(tag: String) {
        val layout = labelLayout(tag)
        val size = layout.layoutInput.style.fontSize.value
        val text = layout.layoutInput.text.text
        assertTrue("$tag is $size sp, below the 12 sp floor", size >= 12f)
        assertTrue("$tag is $size sp, above the 14 sp label size", size <= 14f)
        val whole =
            layout.lineCount == 1 &&
                layout.size.width + 1f >= layout.multiParagraph.maxIntrinsicWidth &&
                layout.getLineEnd(0) == text.length
        if (!whole) {
            assertEquals("$tag ('$text') is cut short at $size sp; only 12 sp may end in an ellipsis", 12f, size, 0.01f)
        }
    }

    /** The label's centre sits on the pill's centre, with the crest, when there is one, on the label's left. */
    private fun assertCentred(tag: String) {
        val box = pill(tag).boundsInRoot
        val label = compose.onNodeWithTag("detail.tab.$tag.label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val left =
            if (tag == "Game") {
                label.left
            } else {
                compose
                    .onNodeWithTag("detail.tab.$tag.crest", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot.left
            }
        val centre = (left + label.right) / 2f
        assertTrue("$tag's crest and label centre at $centre, not on the pill's ${box.center.x}", abs(centre - box.center.x) <= 1f)
    }

    @Test
    fun `at 411 dp and normal scale the three pills are equal width, on screen, and the strip does not scroll`() {
        show(width = 411.dp, fontScale = 1f)

        // The three share the strip's width by weight, so they can differ by a pixel of rounding and no more.
        val widths = pills.map { pill(it).boundsInRoot.width }
        assertTrue("the pills are not equal width: $widths", widths.max() - widths.min() <= 1f)
        assertEquals("the strip scrolls at 411 dp", 0f, scrollRange(), 0.5f)
        val outside = pills.filterNot { pill(it).isWithin(strip()) }
        assertTrue("pills outside the strip at 411 dp: $outside", outside.isEmpty())
    }

    @Test
    fun `at 360 dp with font scale 2 the three pills are still equal width and the strip still does not scroll`() {
        show(width = 360.dp, fontScale = 2f)

        val widths = pills.map { pill(it).boundsInRoot.width }
        assertTrue("the pills are not equal width: $widths", widths.max() - widths.min() <= 1f)
        assertEquals("the strip scrolls at 360 dp, font scale 2", 0f, scrollRange(), 0.5f)
    }

    @Test
    fun `at 411 dp each pill centres its crest and label as one pair`() {
        show(width = 411.dp, fontScale = 1f)

        pills.forEach { assertCentred(it) }
    }

    @Test
    fun `at 360 dp with font scale 2 each pill centres its crest and label as one pair`() {
        show(width = 360.dp, fontScale = 2f)

        pills.forEach { assertCentred(it) }
    }

    @Test
    fun `at 411 dp and normal scale every label is 12 to 14 sp, and ellipsized only at 12 sp`() {
        show(width = 411.dp, fontScale = 1f)

        pills.forEach { assertLabelRule(it) }
    }

    @Test
    fun `at 360 dp and normal scale every label is 12 to 14 sp, and ellipsized only at 12 sp`() {
        show(width = 360.dp, fontScale = 1f)

        pills.forEach { assertLabelRule(it) }
    }

    @Test
    fun `at 360 dp with font scale 2 every label is 12 to 14 sp, and ellipsized only at 12 sp`() {
        show(width = 360.dp, fontScale = 2f)

        pills.forEach { assertLabelRule(it) }
    }
}
