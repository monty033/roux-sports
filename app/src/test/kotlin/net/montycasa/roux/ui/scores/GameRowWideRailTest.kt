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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import kotlin.time.Instant

/**
 * The stress test next door runs at 360 dp, where the card takes its narrow metrics. A phone at 412 dp takes the
 * wide ones instead - a different rail width, a different repair path, and until this file existed, a different
 * amount of test attention. That gap is how a two-line capsule survived a fix that only widened the narrow rail.
 *
 * So the same worst case - three digits a side, overtime, a clock - is asserted here at the width a real phone
 * reports, across the font scales a real phone can be set to, including the 1.3 that sits between the two the
 * other tests use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameRowWideRailTest {
    @get:Rule
    val rule = createComposeRule()

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
                            narrow = maxWidth <= 380.dp,
                            modifier = Modifier.testTag("game-row"),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val capsule = rule.onNodeWithTag(STATE_CAPSULE_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val clock =
            railTexts().single {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "12:34" } == true
            }
        assertTrue(
            "period capsule and clock must share a row",
            kotlin.math.abs(capsule.boundsInRoot.center.y - clock.boundsInRoot.center.y) < rule.density.density,
        )
    }

    private fun railTexts(): List<SemanticsNode> =
        rule
            .onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .descendants()
            .filter { it.config.getOrNull(SemanticsProperties.Text) != null }

    private fun assertEveryRailTextOnOneLine(
        fontScale: Float,
        shootout: Boolean,
    ) {
        launch(fontScale, shootout)
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val railWidthDp = rail.boundsInRoot.width / rule.density.density
        val texts = railTexts()
        assertTrue("expected at least two rail text nodes, found ${texts.size}", texts.size >= 2)
        texts.forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
            val results = mutableListOf<TextLayoutResult>()
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            assertTrue("no layout result for '$text' - the assertion would be vacuous", results.isNotEmpty())
            results.forEach { layout ->
                println(
                    "wide rail: scale=$fontScale shootout=$shootout label='$text' lines=${layout.lineCount} " +
                        "overflow=${layout.hasVisualOverflow} rail=${railWidthDp}dp",
                )
                assertEquals(
                    "'$text' wraps onto ${layout.lineCount} lines at ${fontScale}x on 411 dp " +
                        "(rail ${railWidthDp}dp)",
                    1,
                    layout.lineCount,
                )
                assertFalse("'$text' is ellipsised at ${fontScale}x", layout.hasVisualOverflow)
            }
        }
    }

    @Test
    fun overtimeIsOneLineAtNormalTypeOnAPhoneWidth() = assertEveryRailTextOnOneLine(1f, false)

    @Test
    fun overtimeIsOneLineAtTheIntermediateScaleUsersActuallySet() = assertEveryRailTextOnOneLine(1.3f, false)

    @Test
    fun overtimeIsOneLineAtDoubleType() = assertEveryRailTextOnOneLine(2f, false)

    @Test
    fun shootoutIsOneLineAtNormalType() = assertEveryRailTextOnOneLine(1f, true)

    @Test
    fun shootoutIsOneLineAtDoubleType() = assertEveryRailTextOnOneLine(2f, true)
}

private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }
