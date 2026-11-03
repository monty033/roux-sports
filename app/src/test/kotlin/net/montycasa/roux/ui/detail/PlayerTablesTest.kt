@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * The club page's player card is three tables, and the most important promise is where the split comes from: the feed's
 * own `forwards` and `defense` lists. Position is no longer printed or used to regroup a line, the group name sits in the
 * table's header row, and consecutive groups are separated by the shared period rule.
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

        val forwardsHeading = topOfHeading("detail.section.forwards")
        val defenseHeading = topOfHeading("detail.section.defense")
        val goaliesHeading = topOfHeading("detail.section.goalies")
        assertTrue("FORWARDS is not above DEFENSEMEN", forwardsHeading < defenseHeading)
        assertTrue("DEFENSEMEN is not above GOALIES", defenseHeading < goaliesHeading)

        // The group word shares the header row with the stat headings, aligned with the name column.
        assertHeadingSharesHeaderRow("detail.section.forwards", "FORWARDS", "G")
        assertHeadingSharesHeaderRow("detail.section.defense", "DEFENSEMEN", "G")
        assertHeadingSharesHeaderRow("detail.section.goalies", "GOALIES", "SA")

        // Groups - 1 dividers, each between its two neighbours and never above the first.
        compose.onAllNodesWithTag(PLAYER_DIVIDER_TAG).assertCountEquals(2)
        assertDividerBetween("detail.section.forwards", "detail.section.defense", 0)
        assertDividerBetween("detail.section.defense", "detail.section.goalies", 1)

        // Feed order is kept inside each table.
        val forwardRows = listOf("F. First", "F. Second").map { topOf(it) }
        assertTrue("the forwards were reordered", forwardRows.zipWithNext().all { (a, b) -> a < b })
        val defenseRows = listOf("D. First", "D. Second").map { topOf(it) }
        assertTrue("the defencemen were reordered", defenseRows.zipWithNext().all { (a, b) -> a < b })

        // And the goalies still sit below both skater tables.
        assertTrue("the goalie table is not below the defencemen", topOf("G. Only") > defenseHeading)
    }

    @Test
    fun `POS is gone from the skater headers and rows`() {
        show(
            forwards =
                listOf(
                    skater(
                        "F. First",
                        position = "D",
                        goals = 1,
                        assists = 2,
                        points = 3,
                        plusMinus = 4,
                        shotsOnGoal = 5,
                    ),
                ),
            defense = listOf(skater("D. First", position = "C")),
            goalies = listOf(goalie("G. Only")),
        )

        compose.onAllNodesWithText("POS").assertCountEquals(0)
        listOf("C", "D", "L", "R").forEach { code ->
            compose.onAllNodesWithText(code).assertCountEquals(0)
        }

        val skaterHeaders = listOf("G", "A", "P", "+/-", "SOG", "PIM", "TOI", "FO%")
        val lefts = skaterHeaders.map { headerBounds("detail.section.forwards", it).left.value }
        assertTrue(
            "skater headers are not G, A, P, +/-, SOG, PIM, TOI, FO% in order",
            lefts.zipWithNext().all { (left, right) -> left < right },
        )
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

        val defenseHeading = topOfHeading("detail.section.defense")
        val goaliesHeading = topOfHeading("detail.section.goalies")

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
    fun `an empty defense list hides its heading and leaves one divider`() {
        show(
            forwards = listOf(skater("F. First"), skater("F. Second")),
            defense = emptyList(),
            goalies = listOf(goalie("G. Only")),
        )

        compose.onNodeWithText("DEFENSEMEN").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.defense").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.defense.heading").assertDoesNotExist()

        compose.onAllNodesWithTag(PLAYER_DIVIDER_TAG).assertCountEquals(1)
        assertDividerBetween("detail.section.forwards", "detail.section.goalies", 0)
    }

    @Test
    fun `an empty forwards list starts with the defensemen and one divider`() {
        show(
            forwards = emptyList(),
            defense = listOf(skater("D. First")),
            goalies = listOf(goalie("G. Only")),
        )

        compose.onNodeWithText("FORWARDS").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.forwards").assertDoesNotExist()
        assertTrue("DEFENSEMEN is not above GOALIES", topOfHeading("detail.section.defense") < topOfHeading("detail.section.goalies"))

        compose.onAllNodesWithTag(PLAYER_DIVIDER_TAG).assertCountEquals(1)
        assertDividerBetween("detail.section.defense", "detail.section.goalies", 0)
    }

    @Test
    fun `only goalies means no divider at all`() {
        show(
            forwards = emptyList(),
            defense = emptyList(),
            goalies = listOf(goalie("G. Only")),
        )

        compose.onNodeWithText("FORWARDS").assertDoesNotExist()
        compose.onNodeWithText("DEFENSEMEN").assertDoesNotExist()
        compose.onNodeWithText("GOALIES").assertExists()
        compose.onAllNodesWithTag(PLAYER_DIVIDER_TAG).assertCountEquals(0)
    }

    private fun assertHeadingSharesHeaderRow(
        sectionTag: String,
        heading: String,
        firstStatHeader: String,
    ) {
        val headingBounds = compose.onNodeWithTag("$sectionTag.heading").getUnclippedBoundsInRoot()
        val statHeadingBounds = headerBounds(sectionTag, firstStatHeader)
        assertEquals("$heading is not on the header row", headingBounds.top.value, statHeadingBounds.top.value, 1f)
        assertEquals("$heading is not on the header row", headingBounds.bottom.value, statHeadingBounds.bottom.value, 1f)
        assertTrue("$heading is not left of the stat headings", headingBounds.right.value <= statHeadingBounds.left.value)
    }

    private fun assertDividerBetween(
        upperBlock: String,
        lowerBlock: String,
        index: Int,
    ) {
        val divider = compose.onAllNodesWithTag(PLAYER_DIVIDER_TAG)[index].getUnclippedBoundsInRoot()
        assertTrue("divider $index is not below $upperBlock", divider.top.value > bottomOfBlock(upperBlock))
        assertTrue("divider $index is not above $lowerBlock", divider.bottom.value < topOfBlock(lowerBlock))
    }

    private fun headerBounds(
        sectionTag: String,
        text: String,
    ) = compose
        .onNode(hasText(text) and hasAnyAncestor(hasTestTag(sectionTag)))
        .getUnclippedBoundsInRoot()

    private fun topOf(text: String): Float =
        compose
            .onNodeWithText(text)
            .getUnclippedBoundsInRoot()
            .top
            .value

    private fun topOfHeading(sectionTag: String): Float = topOfTag("$sectionTag.heading")

    private fun topOfBlock(sectionTag: String): Float = topOfTag("$sectionTag.block")

    private fun bottomOfBlock(sectionTag: String): Float =
        compose
            .onNodeWithTag("$sectionTag.block")
            .getUnclippedBoundsInRoot()
            .bottom
            .value

    private fun topOfTag(tag: String): Float =
        compose
            .onNodeWithTag(tag)
            .getUnclippedBoundsInRoot()
            .top
            .value

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
        goals: Int = 0,
        assists: Int = 0,
        points: Int = 0,
        plusMinus: Int = 0,
        shotsOnGoal: Int = 0,
    ) = SkaterLine(
        skater = PlayerRef(name, "PHI"),
        position = position,
        goals = goals,
        assists = assists,
        points = points,
        plusMinus = plusMinus,
        shotsOnGoal = shotsOnGoal,
        penaltyMinutes = 0,
        toiSeconds = 60,
        faceoffFraction = null,
    )

    private fun goalie(name: String) = GoalieLine(goalie = PlayerRef(name, "PHI"), saves = 1, shotsAgainst = 1)
}
