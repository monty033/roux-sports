@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.SkaterLine
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The club page's player card is three tables now, and the most important promise is where the split comes from: the
 * feed's own `forwards` and `defense` lists. A position code is printed in the POS column, never used to regroup a line.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class PlayerTablesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `forwards and defensemen render in order with their own header rows`() {
        show(
            forwards = listOf(skater("F. First"), skater("F. Second")),
            defense = listOf(skater("D. First"), skater("D. Second")),
            goalies = listOf(goalie("G. Only")),
        )

        val forwardsHeading = topOf("FORWARDS")
        val defenseHeading = topOf("DEFENSEMEN")
        val goaliesHeading = topOf("GOALIES")
        assertTrue("FORWARDS is not above DEFENSEMEN", forwardsHeading < defenseHeading)
        assertTrue("DEFENSEMEN is not above GOALIES", defenseHeading < goaliesHeading)

        // Each block keeps the old 14 dp gap, and only between blocks that exist.
        assertEquals(14f, topOfBlock("detail.section.defense") - bottomOfBlock("detail.section.forwards"), 0.5f)
        assertEquals(14f, topOfBlock("detail.section.goalies") - bottomOfBlock("detail.section.defense"), 0.5f)

        // Each skater table has its own POS header row; the goalie table has its own SA row.
        compose.onAllNodesWithText("POS").assertCountEquals(2)
        listOf("detail.section.forwards", "detail.section.defense").forEach { tag ->
            compose
                .onNode(hasText("POS") and hasAnyAncestor(hasTestTag(tag)))
                .assertExists()
        }
        compose.onNode(hasText("SA") and hasAnyAncestor(hasTestTag("detail.section.goalies"))).assertExists()

        // Feed order is kept inside each table.
        val forwardedRows = listOf("F. First", "F. Second").map { topOf(it) }
        assertTrue("the forwards were reordered", forwardedRows.zipWithNext().all { (a, b) -> a < b })
        val defenseRows = listOf("D. First", "D. Second").map { topOf(it) }
        assertTrue("the defencemen were reordered", defenseRows.zipWithNext().all { (a, b) -> a < b })

        // And the goalies still sit below both skater tables.
        assertTrue("the goalie table is not below the defencemen", topOf("G. Only") > defenseHeading)
    }

    /**
     * The deliberate fixture: the feed puts F. Forward in `forwards` but labels him D, and D. Defender in `defense` but
     * labels him C. A split that filters on the position code would swap the two; a split that concatenates first and
     * repeats the result under both headings would show each name twice. Payloads do not always agree with their own
     * position codes, so the list the feed actually used is the authority.
     */
    @Test
    fun `the feed list decides the group even when the position code disagrees`() {
        show(
            forwards = listOf(skater("F. Forward", position = "D")),
            defense = listOf(skater("D. Defender", position = "C")),
            goalies = listOf(goalie("G. Only")),
        )

        val defenseHeading = topOf("DEFENSEMEN")
        val goaliesHeading = topOf("GOALIES")

        compose.onAllNodesWithText("F. Forward").assertCountEquals(1)
        compose.onAllNodesWithText("D. Defender").assertCountEquals(1)
        assertTrue(
            "F. Forward was moved into DEFENSEMEN because his position code says D",
            topOf("F. Forward") < defenseHeading,
        )
        assertTrue(
            "D. Defender was moved into FORWARDS because his position code says C",
            topOf("D. Defender") > defenseHeading && topOf("D. Defender") < goaliesHeading,
        )
    }

    @Test
    fun `an empty defense list hides its heading and leaves no gap`() {
        show(
            forwards = listOf(skater("F. First"), skater("F. Second")),
            defense = emptyList(),
            goalies = listOf(goalie("G. Only")),
        )

        compose.onNodeWithText("DEFENSEMEN").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.defense").assertDoesNotExist()

        // The next block keeps the ordinary 14 dp gap; an absent group contributes neither a heading nor spacing.
        val forwardsBottom = bottomOfBlock("detail.section.forwards")
        val goaliesTop = topOfBlock("detail.section.goalies")
        assertEquals(14f, goaliesTop - forwardsBottom, 0.5f)
    }

    @Test
    fun `an empty forwards list starts with the defensemen`() {
        show(
            forwards = emptyList(),
            defense = listOf(skater("D. First")),
            goalies = listOf(goalie("G. Only")),
        )

        compose.onNodeWithText("FORWARDS").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.forwards").assertDoesNotExist()
        assertTrue("DEFENSEMEN is not above GOALIES", topOf("DEFENSEMEN") < topOf("GOALIES"))
    }

    private fun topOf(text: String): Float =
        compose
            .onNodeWithText(text)
            .getUnclippedBoundsInRoot()
            .top.value

    private fun topOfBlock(sectionTag: String): Float =
        compose
            .onNodeWithTag("$sectionTag.block")
            .getUnclippedBoundsInRoot()
            .top.value

    private fun bottomOfBlock(sectionTag: String): Float =
        compose
            .onNodeWithTag("$sectionTag.block")
            .getUnclippedBoundsInRoot()
            .bottom.value

    private fun show(
        forwards: List<SkaterLine>,
        defense: List<SkaterLine>,
        goalies: List<GoalieLine>,
    ) {
        compose.setContent {
            RouxTheme(dark = true) {
                PlayerTables(goalies = goalies, forwards = forwards, defense = defense)
            }
        }
    }

    private fun skater(
        name: String,
        position: String = "C",
    ) = SkaterLine(
        skater = PlayerRef(name, "PHI"),
        position = position,
        goals = 0,
        assists = 0,
        points = 0,
        plusMinus = 0,
        shotsOnGoal = 0,
        penaltyMinutes = 0,
        toiSeconds = 60,
        faceoffFraction = null,
    )

    private fun goalie(name: String) = GoalieLine(goalie = PlayerRef(name, "PHI"), saves = 1, shotsAgainst = 1)
}
