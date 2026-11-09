package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
 * The fallback initials in a player's photo circle, measured as drawn. They sit beside two references in the same style,
 * one at 11 sp and one at 12 sp, so the rendered size is read from the text's width rather than from the style.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerInitialsSizeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `at normal scale the fallback initials are drawn at 12 sp and fit the 28 dp circle`() {
        compose.setContent(avatarContent(fontScale = 1f))
        assertInitialsAt12spAndInCircle()
    }

    @Test
    fun `at font scale 1 point 3 the fallback initials are still drawn at 12 sp and still fit the circle`() {
        compose.setContent(avatarContent(fontScale = 1.3f))
        assertInitialsAt12spAndInCircle()
    }

    private fun assertInitialsAt12spAndInCircle() {
        val drawn = layoutOf(compose.onNodeWithTag("avatar.initials", useUnmergedTree = true).fetchSemanticsNode())
        val at11 = layoutOf(compose.onNodeWithTag("avatar.ref11", useUnmergedTree = true).fetchSemanticsNode())
        val at12 = layoutOf(compose.onNodeWithTag("avatar.ref12", useUnmergedTree = true).fetchSemanticsNode())
        val circle = compose.onNodeWithTag("avatar.mugshot", useUnmergedTree = true).fetchSemanticsNode()
        val drawnWidth = drawn.multiParagraph.maxIntrinsicWidth
        val width12 = at12.multiParagraph.maxIntrinsicWidth
        val width11 = at11.multiParagraph.maxIntrinsicWidth
        assertTrue(
            "initials are $drawnWidth px wide; the 12 sp reference is $width12 px",
            abs(drawnWidth - width12) < 0.5f,
        )
        assertTrue(
            "initials are $drawnWidth px wide, the same as the 11 sp reference ($width11 px)",
            abs(drawnWidth - width11) > 1f,
        )
        assertTrue(
            "initials are $drawnWidth px wide, wider than the ${circle.boundsInRoot.width} px circle",
            drawnWidth <= circle.boundsInRoot.width,
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
                            modifier = Modifier.testTag("avatar.ref11"),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            maxLines = 1,
                        )
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
