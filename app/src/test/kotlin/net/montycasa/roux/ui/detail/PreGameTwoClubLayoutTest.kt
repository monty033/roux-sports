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
}
