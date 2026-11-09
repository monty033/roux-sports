@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames
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
 * pair. A label is 14 sp and shrinks by half a point toward 12 sp. A club whose nickname still does not fit at 12 sp
 * shows its abbreviation instead, so no label is ever ellipsized. Each club pill's content description is the full club
 * name. The strip never scrolls. Normal scale and the largest font are both checked.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailTabStripTest {
    @get:Rule
    val compose = createComposeRule()

    private val away = club("CBJ")
    private val home = club("UTA")

    private val pills = listOf("Game", "CBJ", "UTA")

    private fun club(abbrev: String): TeamRef = TeamRef(TeamKey(abbrev), name = abbrev)

    private fun nickname(abbrev: String): String = TeamNames.cityAndName(club(abbrev)).second

    private fun fullName(abbrev: String): String =
        TeamNames.cityAndName(club(abbrev)).let { (city, name) -> listOfNotNull(city, name).joinToString(" ") }

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

    /** Every club in the league, as pairs in one composition, so each club's pill is measured in the same run. */
    private fun showEveryClub(
        width: Dp,
        fontScale: Float,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                RouxTheme(dark = false) {
                    Column {
                        LEAGUE.chunked(2).forEach { (a, b) ->
                            Box(Modifier.width(width)) {
                                DetailTabStrip(away = club(a), home = club(b), selected = 0, onSelect = {})
                            }
                        }
                    }
                }
            }
        }
    }

    private fun strip(): SemanticsNode = compose.onNodeWithTag("detail.tab.strip").fetchSemanticsNode()

    private fun pill(tag: String): SemanticsNode = compose.onNodeWithTag("detail.tab.$tag").fetchSemanticsNode()

    /** How far the strip can scroll sideways, in pixels. Zero when it does not scroll at all. */
    private fun scrollRange(): Float = strip().config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.maxValue() ?: 0f

    private fun SemanticsNode.isWithin(outer: SemanticsNode): Boolean =
        boundsInRoot.left >= outer.boundsInRoot.left - 0.5f && boundsInRoot.right <= outer.boundsInRoot.right + 0.5f

    /** The label's layout, which holds its size, its line count and its text. */
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

    /** A label drawn whole: one line, its full width, and its last character present. */
    private fun isWhole(layout: TextLayoutResult): Boolean =
        layout.lineCount == 1 &&
            layout.size.width + 1f >= layout.multiParagraph.maxIntrinsicWidth &&
            layout.getLineEnd(0) == layout.layoutInput.text.text.length

    /**
     * A label is 12 to 14 sp, is drawn whole, and reads as either the club's nickname or its abbreviation. Nothing else
     * is allowed, and an ellipsis fails.
     */
    private fun assertWhole(
        tag: String,
        allowed: List<String>,
    ) {
        val layout = labelLayout(tag)
        val size = layout.layoutInput.style.fontSize.value
        val text = layout.layoutInput.text.text
        assertTrue("$tag is $size sp, below the 12 sp floor", size >= 12f)
        assertTrue("$tag is $size sp, above the 14 sp label size", size <= 14f)
        assertTrue("$tag reads '$text', not one of $allowed", text in allowed)
        assertTrue("$tag ('$text') is cut short at $size sp; a label is never ellipsized", isWhole(layout))
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
    fun `at 411 dp and normal scale neither club label ellipsizes`() {
        show(width = 411.dp, fontScale = 1f)

        assertWhole("CBJ", allowed = listOf(nickname("CBJ"), "CBJ"))
        assertWhole("UTA", allowed = listOf(nickname("UTA"), "UTA"))
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
    fun `at 360 dp and normal scale every label is whole, 12 to 14 sp`() {
        show(width = 360.dp, fontScale = 1f)

        assertWhole("CBJ", allowed = listOf(nickname("CBJ"), "CBJ"))
        assertWhole("UTA", allowed = listOf(nickname("UTA"), "UTA"))
    }

    @Test
    fun `at 360 dp with font scale 2 a club whose nickname cannot fit shows its abbreviation, whole, not an ellipsis`() {
        show(width = 360.dp, fontScale = 2f)

        assertWhole("CBJ", allowed = listOf("CBJ"))
        assertWhole("UTA", allowed = listOf("UTA"))
    }

    @Test
    fun `a club whose nickname cannot fit even at 12 sp shows its abbreviation, and the full name is its content description`() {
        // Narrow on purpose: at this width the nickname, even at the 12 sp floor, is wider than the room beside the crest.
        show(width = NARROW_STRIP, fontScale = 1f)

        assertWhole("CBJ", allowed = listOf("CBJ"))
        val description = pill("CBJ").config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals("the full name is not the content description", listOf(fullName("CBJ")), description)
    }

    @Test
    fun `at 411 dp each club pill's content description is the full club name`() {
        show(width = 411.dp, fontScale = 1f)

        listOf("CBJ", "UTA").forEach { abbrev ->
            val description = pill(abbrev).config.getOrNull(SemanticsProperties.ContentDescription)
            assertEquals("$abbrev's content description", listOf(fullName(abbrev)), description)
        }
    }

    @Test
    fun `the abbreviation fallback leaves the Game pill's word alone`() {
        show(width = NARROW_STRIP, fontScale = 1f)

        assertWhole("Game", allowed = listOf("Game"))
    }

    /**
     * Every club in the league, at the two measured settings. Each pair's pills are equal width, every label is whole,
     * and every club pill names the club in full. The measurements are printed to the test report, so the figures in
     * the write-up come from this run.
     */
    @Test
    fun `at 411 dp and normal scale every club label is whole and the pills in each pair are equal width`() {
        measureEveryClub(width = 411.dp, fontScale = 1f, setting = "411dp/1.0")
    }

    @Test
    fun `at 360 dp with font scale 2 every club label is whole and the pills in each pair are equal width`() {
        measureEveryClub(width = 360.dp, fontScale = 2f, setting = "360dp/2.0")
    }

    private fun measureEveryClub(
        width: Dp,
        fontScale: Float,
        setting: String,
    ) {
        showEveryClub(width, fontScale)
        val density = compose.density.density

        // Unclipped: a strip below the window still has its real width, which the clipped bounds would report as zero.
        fun pillWidthDp(abbrev: String): Float =
            compose
                .onNodeWithTag("detail.tab.$abbrev")
                .getUnclippedBoundsInRoot()
                .let { it.right - it.left }
                .value

        val report = StringBuilder("TAB-MEASURE $setting\n")
        LEAGUE.forEach { abbrev ->
            val nick = nickname(abbrev)
            val layout = labelLayout(abbrev)
            val drawn = layout.layoutInput.text.text
            val size = layout.layoutInput.style.fontSize.value
            val fellBack = drawn == abbrev && drawn != nick
            report.append(
                "%s nickname=%-14s drawn=%-14s sp=%4.1f fellBack=%-5s whole=%-5s pill=%6.1fdp label=%6.1fdp need=%6.1fdp\n".format(
                    abbrev,
                    nick,
                    drawn,
                    size,
                    fellBack,
                    isWhole(layout),
                    pillWidthDp(abbrev),
                    layout.size.width / density,
                    layout.multiParagraph.maxIntrinsicWidth / density,
                ),
            )
        }
        println(report)

        LEAGUE.chunked(2).forEach { (a, b) ->
            val pairPx = abs(pillWidthDp(a) - pillWidthDp(b)) * density
            assertTrue("$a and $b are not equal width: $pairPx px apart", pairPx <= 1f)
        }
        LEAGUE.forEach { abbrev ->
            assertWhole(abbrev, allowed = listOf(nickname(abbrev), abbrev))
            val description = pill(abbrev).config.getOrNull(SemanticsProperties.ContentDescription)
            assertEquals("$abbrev's content description", listOf(fullName(abbrev)), description)
        }
    }

    private companion object {
        /** The league's 32 clubs, by abbreviation. */
        val LEAGUE =
            listOf(
                "ANA",
                "BOS",
                "BUF",
                "CGY",
                "CAR",
                "CHI",
                "COL",
                "CBJ",
                "DAL",
                "DET",
                "EDM",
                "FLA",
                "LAK",
                "MIN",
                "MTL",
                "NSH",
                "NJD",
                "NYI",
                "NYR",
                "OTT",
                "PHI",
                "PIT",
                "SJS",
                "SEA",
                "STL",
                "TBL",
                "TOR",
                "UTA",
                "VAN",
                "VGK",
                "WSH",
                "WPG",
            )

        /** A strip narrow enough that a nickname cannot fit beside its crest even at 12 sp, at normal scale. */
        val NARROW_STRIP: Dp = 300.dp
    }
}
