@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.time.Instant

/**
 * P4: the state rail is the narrowest column on the densest screen, and it is the part of the card that carries
 * three-digit scores and an overtime clock at the same time. At 2.0 type on a 360 dp phone there is nowhere left
 * for it to go, so this is where a rail that has quietly been allowed to shrink finally shows it.
 *
 * Two things are asserted, because either one alone can pass while the rail is broken: every text node in the
 * rail sits inside the rail's bounds (a node can overflow its parent and still paint), and no text node reports
 * visual overflow (a node can stay inside its bounds by ellipsising). Together they say the rail fits, not that
 * it merely clipped politely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameRowStressTest {
    @get:Rule
    val rule = createComposeRule()

    // Three digits a side, overtime, and a clock: the widest the rail is ever asked to be.
    private val stress =
        Game(
            id = GameId("2026020001"),
            date = "2026-10-26",
            state = GameState.Live(critical = true),
            away = TeamRef(TeamKey("BOS"), name = "Boston Bruins"),
            home = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs"),
            startTimeUtc = Instant.parse("2026-10-26T23:00:00Z"),
            awayScore = 103,
            homeScore = 102,
            period = PeriodInfo(ordinal = 4, periodType = "OT", clock = "12:34"),
        )

    private fun launch(
        fontScale: Float,
        shootout: Boolean = false,
    ) {
        rule.setContent {
            RouxTheme {
                val density = Density(LocalDensity.current.density, fontScale)
                CompositionLocalProvider(LocalDensity provides density) {
                    BoxWithConstraints {
                        GameRow(
                            if (shootout) stress.copy(period = stress.period!!.copy(periodType = "SO")) else stress,
                            narrow =
                                maxWidth <= 380.dp,
                            modifier = Modifier.testTag("game-row"),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun railTexts(): List<SemanticsNode> {
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        return rail.descendants().filter { it.config.getOrNull(SemanticsProperties.Text) != null && it.layoutInfo.isPlaced }
    }

    @Test
    fun atTwoXTypeOnA360dpPhoneTheRailKeepsEveryTextInsideItsBounds() {
        launch(fontScale = 2f)
        rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).assertWidthIsEqualTo(128.dp)

        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).assertIsDisplayed()
        val railBounds = rail.boundsInRoot

        val texts = railTexts()
        // A vacuous pass would read as "the rail is fine" while measuring nothing: the live capsule and the
        // overtime clock are two separate nodes, and both have to exist before their bounds mean anything.
        assertTrue("expected at least two text nodes in the rail, found ${texts.size}", texts.size >= 2)

        texts.forEach { node ->
            val b = node.boundsInRoot
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
            val slack = 0.5f * rule.density.density
            assertFalse(
                "'$text' starts at ${b.left} dp-ish, left of the rail at ${railBounds.left}",
                b.left < railBounds.left - slack,
            )
            assertFalse(
                "'$text' ends at ${b.right}, past the rail right edge ${railBounds.right}",
                b.right > railBounds.right + slack,
            )
            assertFalse(
                "'$text' runs past the rail bottom ${railBounds.bottom}",
                b.bottom > railBounds.bottom + slack,
            )
            assertFalse(
                "'$text' starts above the rail top ${railBounds.top}",
                b.top < railBounds.top - slack,
            )
        }
    }

    @Test
    fun atTwoXTypeOnA360dpPhoneNoRailTextIsEllipsised() {
        launch(fontScale = 2f)

        val texts = railTexts()
        assertTrue("expected at least two text nodes in the rail, found ${texts.size}", texts.size >= 2)

        texts.forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
            val results = mutableListOf<TextLayoutResult>()
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            assertTrue("no layout result for '$text' - the assertion would be vacuous", results.isNotEmpty())
            results.forEach { layout ->
                assertFalse(
                    "'$text' is ellipsised (${layout.lineCount} line(s), overflow=${layout.hasVisualOverflow}, size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height})",
                    layout.hasVisualOverflow,
                )
            }
        }
    }

    @Test
    fun atTwoXTypeOnA360dpPhoneNoRailTextWraps() {
        launch(fontScale = 2f)
        val texts = railTexts()
        assertTrue("expected at least two text nodes in the rail, found ${texts.size}", texts.size >= 2)
        val wrapped =
            texts.mapNotNull { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
                val results = mutableListOf<TextLayoutResult>()
                node.config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(results)
                val lines = results.firstOrNull()?.lineCount ?: 0
                if (lines > 1) "'$text' is laid out on $lines lines" else null
            }
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "at 2.0 type on 360 dp the rail text does not fit on one line: " +
                "${wrapped.joinToString("; ")} (rail is ${rail.boundsInRoot.width / rule.density.density} dp wide)",
            wrapped.isEmpty(),
        )
    }

    @Test
    fun normalTypeAndShootoutAlsoFitOnOneLine() {
        launch(fontScale = 1f, shootout = true)
        railTexts().forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            assertTrue(results.isNotEmpty())
            results.forEach {
                assertEquals(1, it.lineCount)
                assertFalse(it.hasVisualOverflow)
            }
        }
    }

    private fun snapshot(
        scale: Float,
        shootout: Boolean,
    ) {
        launch(scale, shootout)
        rule.onNodeWithTag("game-row").assertHeightIsEqualTo(if (scale == 1f) 110.dp else 170.dp)
        railTexts().forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            assertTrue(results.isNotEmpty())
            results.forEach {
                assertEquals(1, it.lineCount)
                assertFalse(it.hasVisualOverflow)
            }
        }
        val kind = if (shootout) "so" else "ot"
        rule.onNodeWithTag("game-row").captureRoboImage("src/test/screenshots/game-row/narrow-$kind-$scale.png")
    }

    @Test fun narrowOvertimeNormal() = snapshot(1f, false)

    @Test fun narrowOvertimeLarge() = snapshot(2f, false)

    @Test fun narrowShootoutNormal() = snapshot(1f, true)

    @Test fun narrowShootoutLarge() = snapshot(2f, true)

    @Test
    fun theRailIsNotWiderThanTheCardItSitsIn() {
        launch(fontScale = 2f)
        val card = rule.onNodeWithTag("game-row", useUnmergedTree = true).fetchSemanticsNode()
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "rail ${rail.boundsInRoot.width} is as wide as the card ${card.boundsInRoot.width}: " +
                "it has swallowed the row",
            rail.boundsInRoot.width < card.boundsInRoot.width,
        )
        assertTrue(abs(rail.boundsInRoot.right - card.boundsInRoot.right) < 2f)
    }
}

private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }
