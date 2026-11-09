@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sub-navigation pills at the narrowest supported width and the largest font the audit draws. Each club label must
 * lay out on one line with nothing clipped, so a club name is never cut short to make the pill fit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailTabStripTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the club labels stay on one line at 360 dp with font scale 2`() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                RouxTheme(dark = false) {
                    Box(Modifier.width(360.dp)) {
                        DetailTabStrip(
                            away = TeamRef(TeamKey("CBJ"), name = "Blue Jackets"),
                            home = TeamRef(TeamKey("UTA"), name = "Utah Mammoth"),
                            selected = 0,
                            onSelect = {},
                        )
                    }
                }
            }
        }

        val problems =
            listOf("detail.tab.CBJ.label", "detail.tab.UTA.label").mapNotNull { tag ->
                val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(
                    "$tag has no text layout",
                    node.config
                        .getOrNull(SemanticsActions.GetTextLayoutResult)
                        ?.action
                        ?.invoke(layouts) == true,
                )
                val layout = layouts.single()
                // The text's natural width against the width the pill leaves it, both in pixels.
                val natural = layout.multiParagraph.maxIntrinsicWidth
                val room = layout.size.width
                if (layout.lineCount != 1 || layout.hasVisualOverflow) {
                    "$tag: ${layout.lineCount} line(s), natural $natural px, room $room px"
                } else {
                    null
                }
            }
        assertTrue("sub-nav labels do not fit at 360 dp, font scale 2: $problems", problems.isEmpty())
    }
}
