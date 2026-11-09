@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.ui.AuditStates
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The four two-club cards on the pre-game page, each with a header tag for its away and home club. */
private val TWO_CLUB_CARDS =
    listOf(
        "detail.pregame.last10",
        "detail.pregame.season",
        "detail.pregame.performers",
        "detail.pregame.goalies",
    )

/** Every name the pre-game page prints: each club's nickname and each player's name. */
private val PRE_GAME_NAMES =
    SemanticsMatcher("a pre-game name") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
            tag.startsWith("detail.pregame.") && tag.endsWith(".name")
        } == true
    }

/** Every figure line on a goaltender or top-performer row: each is one line under its player's name. */
private val FIGURE_LINES =
    SemanticsMatcher("a pre-game figure line") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
            tag.startsWith("detail.pregame.") && tag.contains(".figure.")
        } == true
    }

/** The pre-game audit state at the given font scale, in the light theme. */
private fun preGameAt(fontScale: Float): @Composable () -> Unit {
    val screen = AuditStates.screens().single { it.name == "detail.pregame" }
    return {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
            RouxTheme(dark = false) {
                screen.content()
            }
        }
    }
}

private fun textLayout(node: SemanticsNode): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    assertTrue(
        "${node.config.getOrNull(SemanticsProperties.TestTag)} has no text layout",
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts) == true,
    )
    return layouts.single()
}

/**
 * A name is whole when it is on one line or wraps only at spaces, and when its last character is drawn. A break inside a
 * word, or a cut-off end, is what this catches.
 */
private fun assertWholeName(node: SemanticsNode) {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
    val layout = textLayout(node)
    val text = layout.layoutInput.text.text
    if (layout.lineCount == 1) {
        assertTrue("$tag is cut short: '$text' (${describe(layout)})", layout.size.width + 1f >= layout.multiParagraph.maxIntrinsicWidth)
    }
    assertEquals("$tag is cut short: '$text' (${describe(layout)})", text.length, layout.getLineEnd(layout.lineCount - 1))
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        assertTrue(
            "$tag breaks inside a word on line ${line + 1}: '$text'",
            text[end - 1] == ' ' || text[end] == ' ',
        )
    }
}

/**
 * Every figure line is one line under its own player's name. [figures] and [names] are the figure lines and the names on
 * the page; the figure lines are the goaltenders' two each and the six top performers' one each. A figure line never
 * wraps. When [drawnWhole] is set it also must not be cut short: a half too narrow for it ellipsizes it, and that fails.
 */
private fun assertFiguresUnderNames(
    figures: List<SemanticsNode>,
    names: List<SemanticsNode>,
    drawnWhole: Boolean,
) {
    assertTrue("expected the goaltender and top-performer figure lines, found ${figures.size}", figures.size >= 14)
    figures.forEach { figure ->
        val tag = figure.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        val nameTag = tag.substringBefore(".figure.") + ".name"
        val name = names.single { it.config.getOrNull(SemanticsProperties.TestTag) == nameTag }
        assertTrue(
            "$tag is not under its name: name ${name.boundsInRoot}, figure ${figure.boundsInRoot}",
            figure.boundsInRoot.top >= name.boundsInRoot.bottom - 0.5f,
        )
        val layout = textLayout(figure)
        val text = layout.layoutInput.text.text
        assertEquals("$tag wraps: '$text' (${describe(layout)})", 1, layout.lineCount)
        if (drawnWhole) {
            assertTrue(
                "$tag is cut short: '$text' (${describe(layout)})",
                layout.size.width + 1f >= layout.multiParagraph.maxIntrinsicWidth,
            )
        }
    }
}

/**
 * The text's metrics, for a failure message. A single line drawn narrower than its natural width has been cut short:
 * `hasVisualOverflow` and `didOverflowWidth` both fire on whole text here, so they are not used.
 */
private fun describe(layout: TextLayoutResult): String =
    "size ${layout.size.width}, natural ${layout.multiParagraph.maxIntrinsicWidth}, lines ${layout.lineCount}, " +
        "line end ${layout.getLineEnd(layout.lineCount - 1)} of ${layout.layoutInput.text.text.length}"

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreGameTwoClubLayoutAt360Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `at 360 dp with font scale 2 every two-club card stacks, home club under the away club, both on the left edge`() {
        compose.setContent(preGameAt(2f))

        TWO_CLUB_CARDS.forEach { card ->
            val away = compose.onNodeWithTag("$card.club.away").getUnclippedBoundsInRoot()
            val home = compose.onNodeWithTag("$card.club.home").getUnclippedBoundsInRoot()
            assertTrue("$card: the home club is not under the away club: away $away, home $home", home.top >= away.bottom - 0.5.dp)
            assertEquals("$card: the home club does not start at the left edge", away.left.value, home.left.value, 0.5f)
        }
    }

    @Test
    fun `at 360 dp with font scale 2 no club name and no player name is cut short or broken mid-word`() {
        compose.setContent(preGameAt(2f))

        val names = compose.onAllNodes(PRE_GAME_NAMES, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected the pre-game names on the page, found ${names.size}", names.size >= 8)
        names.forEach { assertWholeName(it) }
    }

    @Test
    fun `at 360 dp and normal scale every two-club card keeps its two clubs side by side`() {
        compose.setContent(preGameAt(1f))

        TWO_CLUB_CARDS.forEach { card ->
            val away = compose.onNodeWithTag("$card.club.away").getUnclippedBoundsInRoot()
            val home = compose.onNodeWithTag("$card.club.home").getUnclippedBoundsInRoot()
            assertTrue("$card: the clubs are stacked, away $away, home $home", away.right <= home.left + 0.5.dp)
        }
    }

    @Test
    fun `at 360 dp and normal scale no name is cut short or broken mid-word`() {
        compose.setContent(preGameAt(1f))

        val names = compose.onAllNodes(PRE_GAME_NAMES, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected the pre-game names on the page, found ${names.size}", names.size >= 8)
        names.forEach { assertWholeName(it) }
    }

    @Test
    fun `at 360 dp and normal scale every figure line is one line under its name and never wraps`() {
        compose.setContent(preGameAt(1f))

        // Three top-performer lines need 117.5 dp and their half leaves 110 dp, so they ellipsize here. The check holds
        // that a figure never wraps; it does not require the ellipsized lines to be whole.
        assertFiguresUnderNames(
            figures = compose.onAllNodes(FIGURE_LINES, useUnmergedTree = true).fetchSemanticsNodes(),
            names = compose.onAllNodes(PRE_GAME_NAMES, useUnmergedTree = true).fetchSemanticsNodes(),
            drawnWhole = false,
        )
    }

    @Test
    fun `at 360 dp with font scale 2 every figure line is one whole line under its name`() {
        compose.setContent(preGameAt(2f))

        assertFiguresUnderNames(
            figures = compose.onAllNodes(FIGURE_LINES, useUnmergedTree = true).fetchSemanticsNodes(),
            names = compose.onAllNodes(PRE_GAME_NAMES, useUnmergedTree = true).fetchSemanticsNodes(),
            drawnWhole = true,
        )
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h923dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreGameTwoClubLayoutAt411Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `at 411 dp and normal scale every two-club card keeps its two clubs side by side`() {
        compose.setContent(preGameAt(1f))

        TWO_CLUB_CARDS.forEach { card ->
            val away = compose.onNodeWithTag("$card.club.away").getUnclippedBoundsInRoot()
            val home = compose.onNodeWithTag("$card.club.home").getUnclippedBoundsInRoot()
            assertTrue("$card: the clubs are stacked, away $away, home $home", away.right <= home.left + 0.5.dp)
            assertEquals("$card: the clubs are not on one row", away.top.value, home.top.value, 0.5f)
        }
    }

    @Test
    fun `at 411 dp and normal scale no name is cut short or broken mid-word`() {
        compose.setContent(preGameAt(1f))

        val names = compose.onAllNodes(PRE_GAME_NAMES, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected the pre-game names on the page, found ${names.size}", names.size >= 8)
        names.forEach { assertWholeName(it) }
    }

    @Test
    fun `at 411 dp and normal scale every figure line is one whole line under its name`() {
        compose.setContent(preGameAt(1f))

        assertFiguresUnderNames(
            figures = compose.onAllNodes(FIGURE_LINES, useUnmergedTree = true).fetchSemanticsNodes(),
            names = compose.onAllNodes(PRE_GAME_NAMES, useUnmergedTree = true).fetchSemanticsNodes(),
            drawnWhole = true,
        )
    }

    @Test
    fun `at 411 dp and normal scale each goaltender and top performer shows its season figures as written`() {
        compose.setContent(preGameAt(1f))

        val expected =
            mapOf(
                "detail.pregame.goalies.away.A. Vanecek.figure.0" to "14 GP · 8–4–2",
                "detail.pregame.goalies.away.A. Vanecek.figure.1" to "2.71 GAA · 90.6%",
                "detail.pregame.goalies.away.K. Kochetkov.figure.0" to "9 GP",
                "detail.pregame.goalies.away.K. Kochetkov.figure.1" to "3.12 GAA · 88.9%",
                "detail.pregame.goalies.home.C. Talbot.figure.0" to "15 GP · 10–4–1",
                "detail.pregame.goalies.home.C. Talbot.figure.1" to "2.58 GAA · 91.4%",
                "detail.pregame.goalies.home.S. Ersson.figure.0" to "4 GP · 2–2–0",
                "detail.pregame.goalies.home.S. Ersson.figure.1" to "3.40 GAA · 87.9%",
                "detail.pregame.performers.away.J. Kessel.figure.0" to "16 G · 19 A · 35 PTS",
                "detail.pregame.performers.away.C. Hayes.figure.0" to "12 G · 22 A · 34 PTS",
                "detail.pregame.performers.away.N. Schmidt.figure.0" to "4 G · 17 A · 21 PTS",
                "detail.pregame.performers.home.T. Konecny.figure.0" to "11 G · 15 A · 26 PTS",
                "detail.pregame.performers.home.S. Couturier.figure.0" to "9 G · 13 A · 22 PTS",
                "detail.pregame.performers.home.J. Foote.figure.0" to "3 G · 6 A · 9 PTS",
            )
        expected.forEach { (tag, text) -> compose.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text) }
    }
}
