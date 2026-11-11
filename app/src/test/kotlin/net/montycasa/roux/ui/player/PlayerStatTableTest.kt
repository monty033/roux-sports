package net.montycasa.roux.ui.player

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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

    /**
     * The same buffer the club and game tables carry: once the columns overflow, there is dead travel past the last
     * column so the swipe that reveals it does not immediately hand the gesture to the page.
     */
    @Test
    fun theColumnsKeepDeadTravelPastTheLastOne() {
        showColumns(count = 8)

        val container = rule.onAllNodesWithTag("table.scroll", useUnmergedTree = true)[0]
        container.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(4000f, 0f) }
        rule.waitForIdle()

        val edge = container.getUnclippedBoundsInRoot()
        val last = rule.onNodeWithTag("table.header.c7", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val gap = edge.right - last.right

        assertTrue("the last column must sit clear of the edge: gap=$gap", gap >= 60.dp)
    }

    /** A table whose columns fit stays centred: the buffer belongs to the scrolling case only. */
    @Test
    fun aTableThatFitsStaysCentred() {
        showColumns(count = 1)

        val edge = rule.onAllNodesWithTag("table.scroll", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val cell = rule.onNodeWithTag("table.header.c0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val containerMiddle = (edge.left.value + edge.right.value) / 2f
        val cellMiddle = (cell.left.value + cell.right.value) / 2f

        assertEquals("the column must sit in the middle of its space", containerMiddle, cellMiddle, 1f)
    }

    private fun showColumns(count: Int) {
        val headers = (0 until count).map { StatCell("c$it", "H$it", "") }
        val cells = (0 until count).map { StatCell("c$it", "H$it", "12") }
        rule.setContent {
            RouxTheme {
                PlayerStatTable(
                    headers = headers,
                    rows = listOf(StatRow(tag = "row.0", label = "Nov 1 · vs OTT", cells = cells, gameId = null)),
                    headerTag = "table",
                    onRowClick = null,
                )
            }
        }
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
