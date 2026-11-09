package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The fallback initials in a player's photo circle, measured as painted. The painted extent is the whole line, unclamped:
 * its natural width and its line height, placed where the text node sits. It is not the text node's own bounds, which
 * clamp to the box the text is laid out in and so passed while the glyphs overflowed. The natural width includes the
 * trailing letter spacing, so it is a slight overestimate, which errs toward failing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerInitialsSizeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `at font scale 1 the circle is 28 dp and the painted initials sit inside it at 12 sp`() {
        assertCircleHoldsInitialsAt(fontScale = 1f, circleDp = 28f)
    }

    @Test
    fun `at font scale 1 point 3 the circle is 36 point 4 dp and the painted initials sit inside it at 12 sp`() {
        assertCircleHoldsInitialsAt(fontScale = 1.3f, circleDp = 36.4f)
    }

    @Test
    fun `at font scale 2 the circle is 56 dp and the painted initials sit inside it at 12 sp`() {
        assertCircleHoldsInitialsAt(fontScale = 2f, circleDp = 56f)
    }

    private fun assertCircleHoldsInitialsAt(
        fontScale: Float,
        circleDp: Float,
    ) {
        compose.setContent(avatarContent(fontScale))
        val circle = compose.onNodeWithTag("avatar.mugshot", useUnmergedTree = true).fetchSemanticsNode()
        val initials = compose.onNodeWithTag("avatar.initials", useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(initials)
        val painted = paintedBounds(initials, layout)
        val referenceNode = compose.onNodeWithTag("avatar.ref12", useUnmergedTree = true).fetchSemanticsNode()
        val reference = paintedBounds(referenceNode, layoutOf(referenceNode))
        val fontSize = layout.layoutInput.style.fontSize
        val bounds = circle.boundsInRoot

        assertTrue(
            "the circle is ${bounds.width} px, not ${circleDp * compose.density.density} px",
            abs(bounds.width - circleDp * compose.density.density) < 0.5f,
        )
        assertTrue("the initials are $fontSize, below 12 sp", fontSize.type == TextUnitType.Sp && fontSize.value >= 12f)
        assertTrue(
            "the painted initials are ${painted.width} px wide; the 12 sp reference paints ${reference.width} px",
            abs(painted.width - reference.width) < 0.5f,
        )
        assertTrue(
            "the painted initials $painted fall outside the circle $bounds",
            painted.left >= bounds.left - 0.5f &&
                painted.right <= bounds.right + 0.5f &&
                painted.top >= bounds.top - 0.5f &&
                painted.bottom <= bounds.bottom + 0.5f,
        )
    }

    /** The whole unclamped line, in root coordinates: its natural width by its line height. */
    private fun paintedBounds(
        node: SemanticsNode,
        layout: TextLayoutResult,
    ): Rect {
        val origin = node.positionInRoot
        return Rect(
            left = origin.x,
            top = origin.y,
            right = origin.x + layout.multiParagraph.maxIntrinsicWidth,
            bottom = origin.y + layout.multiParagraph.height,
        )
    }

    private fun avatarContent(fontScale: Float): @Composable () -> Unit =
        {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                RouxTheme(dark = false) {
                    Column {
                        PlayerMugshotImage(url = null, name = "M. Wood", tag = "avatar")
                        Text(
                            "MW",
                            modifier = Modifier.testTag("avatar.ref12"),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        return layouts.single()
    }
}
