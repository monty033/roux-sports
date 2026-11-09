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

/**
 * The sub-navigation pills. Each pill is as wide as its crest and label; the strip scrolls sideways when the three do not
 * fit, and no label is ever cut short to make room. Normal scale is checked as well as the largest font, since a label
 * clipped at normal scale is the same defect.
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

    private fun strip(): SemanticsNode = compose.onNodeWithTag("detail.tab.strip").fetchSemanticsNode()

    private fun pill(tag: String): SemanticsNode = compose.onNodeWithTag("detail.tab.$tag").fetchSemanticsNode()

    /** How far the strip can scroll sideways, in pixels. Zero when every pill fits. */
    private fun scrollRange(): Float = strip().config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.maxValue() ?: 0f

    private fun SemanticsNode.isWithin(outer: SemanticsNode): Boolean =
        boundsInRoot.left >= outer.boundsInRoot.left - 0.5f && boundsInRoot.right <= outer.boundsInRoot.right + 0.5f

    /** The label's layout, which says whether the whole club name is on one line with nothing cut short. */
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

    /** Names of labels that are not whole: more than one line, drawn past their box, or with the end cut off. */
    private fun truncatedLabels(): List<String> =
        listOf("CBJ", "UTA").mapNotNull { tag ->
            val layout = labelLayout(tag)
            val text = layout.layoutInput.text.text
            // Drawn at least as wide as the label wants, on one line, with its last character present. A clipped or
            // ellipsized label is drawn narrower than its natural width.
            val whole =
                layout.lineCount == 1 &&
                    layout.size.width + 1f >= layout.multiParagraph.maxIntrinsicWidth &&
                    layout.getLineEnd(0) == text.length
            if (whole) {
                null
            } else {
                "$tag: '$text' on ${layout.lineCount} line(s), drawn ${layout.size.width} of ${layout.multiParagraph.maxIntrinsicWidth}, " +
                    "ends at ${layout.getLineEnd(0)}"
            }
        }

    @Test
    fun `at 411 dp and normal scale all three pills are on screen without scrolling, and no label is cut short`() {
        show(width = 411.dp, fontScale = 1f)

        assertEquals("the strip scrolls at 411 dp", 0f, scrollRange(), 0.5f)
        val outside =
            listOf("Game", "CBJ", "UTA").filterNot { tag -> pill(tag).isWithin(strip()) }
        assertTrue("pills outside the strip at 411 dp: $outside", outside.isEmpty())
        assertTrue("labels cut short at 411 dp: ${truncatedLabels()}", truncatedLabels().isEmpty())
    }

    @Test
    fun `at 360 dp and normal scale no club label is cut short`() {
        show(width = 360.dp, fontScale = 1f)

        assertTrue("labels cut short at 360 dp: ${truncatedLabels()}", truncatedLabels().isEmpty())
    }

    @Test
    fun `at 360 dp with font scale 2 the strip scrolls and every label carries its full text`() {
        show(width = 360.dp, fontScale = 2f)

        assertTrue("the strip does not scroll at 360 dp, font scale 2", scrollRange() > 0f)
        assertTrue("labels cut short at 360 dp, font scale 2: ${truncatedLabels()}", truncatedLabels().isEmpty())
    }
}
