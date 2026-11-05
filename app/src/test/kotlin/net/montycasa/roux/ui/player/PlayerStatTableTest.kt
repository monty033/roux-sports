package net.montycasa.roux.ui.player

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerStatTableTest {
    @get:Rule
    val rule = createComposeRule()

    /** A line with a game id is a 48 dp button, and a tap reports that id. */
    @Test
    fun aLineWithAGameIdIsA48dpButtonThatReportsItsId() {
        val clicked = mutableListOf<Long>()
        show(rows = listOf(row(gameId = 2026020188L)), onRowClick = { clicked += it })

        val node = rule.onNodeWithTag("row.0")
        node.assertHasClickAction()
        assertEquals(Role.Button, node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        val minimum = with(rule.density) { 48.dp.toPx() }
        val height =
            node
                .fetchSemanticsNode()
                .size.height
                .toFloat()
        assertTrue("the line is $height px tall, below $minimum px", height >= minimum)

        node.performClick()

        assertEquals(listOf(2026020188L), clicked)
    }

    /** A line with no game id is plain text: it takes no tap and offers no click action. */
    @Test
    fun aLineWithNoGameIdIsNotTappableAndHasNoClickAction() {
        val clicked = mutableListOf<Long>()
        show(rows = listOf(row(gameId = null)), onRowClick = { clicked += it })

        rule.onNodeWithTag("row.0").assertHasNoClickAction()
        assertTrue("a line with no id reported a tap: $clicked", clicked.isEmpty())
    }

    /** A line that has an id but no handler to receive it is not tappable either. */
    @Test
    fun aLineWithAGameIdButNoHandlerIsNotTappable() {
        show(rows = listOf(row(gameId = 2026020188L)), onRowClick = null)

        rule.onNodeWithTag("row.0").assertHasNoClickAction()
    }

    private fun show(
        rows: List<StatRow>,
        onRowClick: ((Long) -> Unit)?,
    ) {
        rule.setContent {
            RouxTheme {
                PlayerStatTable(
                    headers = listOf(StatCell("g", "G", "")),
                    rows = rows,
                    headerTag = "table",
                    onRowClick = onRowClick,
                )
            }
        }
    }

    private fun row(gameId: Long?) =
        StatRow(
            tag = "row.0",
            label = "Nov 1 · vs OTT",
            cells = listOf(StatCell("g", "G", "1")),
            gameId = gameId,
        )
}
