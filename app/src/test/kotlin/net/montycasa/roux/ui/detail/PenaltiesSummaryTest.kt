@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.PenaltyEvent
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.ThreeStar
import net.montycasa.roux.ui.AuditStates
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The penalties card is the scoring card without score columns. These tests pin the parts that are genuinely different
 * - the prose call, the optional drawn and served lines, and the full-width row - while sharing the crest and period
 * mechanisms that the scoring card already proves.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class PenaltiesSummaryTest {
    @get:Rule
    val compose = createComposeRule()

    private val testDensity = 2.75f

    @Test
    fun `period blocks and calls render in elapsed order even when the feed is reversed`() {
        show(detailWith(penalties = AuditStates.fullPenalties.reversed()))

        assertEquals("1st", labelOf("detail.penalties.period.1.label"))
        assertEquals("2nd", labelOf("detail.penalties.period.2.label"))
        assertEquals("3rd", labelOf("detail.penalties.period.3.label"))

        // First block: the two real calls climb down the card. The reversed input must not reverse the card.
        assertEquals("05:15", rowTime(0))
        assertEquals("15:28", rowTime(1))
        assertEquals("08:43", rowTime(2))
        assertEquals("10:53", rowTime(6))
        assertEquals("08:22", rowTime(7))
        assertEquals("11:55", rowTime(8))
        assertEquals("16:26", rowTime(12))
    }

    @Test
    fun `the clock is printed verbatim from the feed`() {
        show(detailWith(penalties = listOf(penalty(period = 1, time = "9:30"))))

        // Not 09:30 and not a derived remaining value: exactly the string the payload sent.
        assertEquals("9:30", rowTime(0))
    }

    @Test
    fun `the call is prose with its duration and the call name wraps under itself`() {
        show(
            detailWith(
                penalties =
                    listOf(
                        penalty(player = "Jamie Drysdale", call = "holding", duration = 2),
                        penalty(player = "Lawson Crouse", call = "fighting", duration = 5),
                        penalty(player = "Travis Konecny", call = "unsportsmanlike conduct", duration = 2),
                    ),
            ),
        )

        assertEquals("Jamie Drysdale — holding\u00A0(2\u00A0min)", rowSentence(0))
        assertEquals("Lawson Crouse — fighting\u00A0(5\u00A0min)", rowSentence(1))
        assertTrue(rowSentence(2).contains("unsportsmanlike conduct\u00A0(2\u00A0min)"))
    }

    @Test
    fun `drawn and served lines appear only when the payload carries them`() {
        show(
            detailWith(
                penalties =
                    listOf(
                        penalty(player = "Drawn Only", drawnBy = "Liam O'Brien", servedBy = null),
                        penalty(player = "Drawn And Served", drawnBy = "Simon Benoit", servedBy = "M. Carcone"),
                        penalty(player = "Neither", drawnBy = null, servedBy = null),
                    ),
            ),
        )

        assertEquals("Drawn by Liam O'Brien", labelOf("detail.penalties.row.00.drawn"))
        compose.onNodeWithTag("detail.penalties.row.00.served", useUnmergedTree = true).assertDoesNotExist()

        assertEquals("Drawn by Simon Benoit", labelOf("detail.penalties.row.01.drawn"))
        assertEquals("Served by M. Carcone", labelOf("detail.penalties.row.01.served"))

        compose.onNodeWithTag("detail.penalties.row.02.drawn", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail.penalties.row.02.served", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `there are no score columns and the duration lives inside the row's own text column`() {
        show(detailWith(penalties = listOf(penalty())))

        // The scoring card's column tags must not appear in this card, whether copied or renamed under its prefix.
        compose.onNodeWithTag("detail.scoring.row.00.away", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail.scoring.row.00.home", useUnmergedTree = true).assertDoesNotExist()
        compose
            .onAllNodes(
                SemanticsMatcher("penalty row score cell") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag != null &&
                        tag.startsWith("detail.penalties.row.") &&
                        (tag.endsWith(".away") || tag.endsWith(".home"))
                },
            ).assertCountEquals(0)

        val row = compose.onNodeWithTag("detail.penalties.row.00").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithTag("detail.penalties.row.00.text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val sentence = compose.onNodeWithTag("detail.penalties.row.00.sentence", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // The text column is the whole row because there is no right-hand column to take width from it.
        assertEquals(row.right.value, text.right.value, 1f)
        assertTrue("the sentence must start inside the text column", sentence.left.value >= text.left.value - 0.5f)
        assertTrue("the sentence must end inside the text column", sentence.right.value <= text.right.value + 0.5f)
        assertTrue("the duration must be in the sentence, not a column", rowSentence(0).contains("(2\u00A0min)"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `the crest centres on the name at 411 dp font scale one`() {
        show(detailWith(penalties = listOf(crestPenalty())), fontScale = 1f)
        assertCrestCentresOnName()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `the crest centres on the name at 411 dp font scale one point three`() {
        show(detailWith(penalties = listOf(crestPenalty())), fontScale = 1.3f)
        assertCrestCentresOnName()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `the crest centres on the name at 411 dp font scale two`() {
        show(detailWith(penalties = listOf(crestPenalty())), fontScale = 2f)
        assertCrestCentresOnName()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `a long call wraps under itself rather than under the crest at 411 dp font scale two`() {
        show(
            detailWith(
                penalties =
                    listOf(
                        penalty(
                            // The newline makes the wrap deterministic under Robolectric's font metrics; the point
                            // of the test is where the wrapped lines land, not what forces the break.
                            player = "Alexandre Texier-Delorme\nWilliamson",
                            call = "unsportsmanlike conduct",
                            duration = 5,
                            drawnBy = "Another Long Drawn-By Name",
                            servedBy = "M. Carcone",
                        ),
                    ),
            ),
            fontScale = 2f,
        )

        val layout = textLayout("detail.penalties.row.00.sentence")
        assertTrue(
            "the name must wrap for this test: text=${layout.layoutInput.text.text} " +
                "size=${layout.size} constraints=${layout.layoutInput.constraints} lines=${layout.lineCount}",
            layout.lineCount > 1,
        )
        val sentence = compose.onNodeWithTag("detail.penalties.row.00.sentence", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val text = compose.onNodeWithTag("detail.penalties.row.00.text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val crest = compose.onNodeWithTag("detail.penalties.row.00.crest.PHI", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the wrapped line must stay in the text column", sentence.left.value >= text.left.value - 0.5f)
        assertTrue("the wrapped line must clear the crest", sentence.left.value >= crest.right.value)
        assertTrue(!layout.hasVisualOverflow)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `rows fit at 411 dp font scale one`() = assertRowsFit(1f)

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `rows fit at 411 dp font scale one point three`() = assertRowsFit(1.3f)

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `rows fit at 411 dp font scale two`() = assertRowsFit(2f)

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `rows fit at 360 dp font scale one`() = assertRowsFit(1f)

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `rows fit at 360 dp font scale one point three`() = assertRowsFit(1.3f)

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `rows fit at 360 dp font scale two`() = assertRowsFit(2f)

    @Test
    fun `the card hides when the payload has no calls`() {
        show(
            GameDetail(
                landing = Loadable(GameLanding(), Freshness.Fresh, null),
                boxscore = Loadable(GameBoxscore(), Freshness.Fresh, null),
                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
            ),
        )

        compose.onNodeWithTag("detail.section.penalties-summary").assertDoesNotExist()
        compose.onNodeWithText("PENALTIES").assertDoesNotExist()
    }

    @Test
    fun `the penalties card sits directly after the scoring summary and before three stars`() {
        show(
            GameDetail(
                landing =
                    Loadable(
                        GameLanding(
                            periodScoring = listOf(PeriodScoring(1, 1, 0)),
                            goals = AuditStates.fullGoals.take(1),
                            penalties = AuditStates.fullPenalties.take(1),
                            threeStars = listOf(ThreeStar(1, PlayerRef("Matvei Michkov", "PHI"))),
                        ),
                        Freshness.Fresh,
                        null,
                    ),
                boxscore = Loadable(GameBoxscore(), Freshness.Fresh, null),
                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
            ),
        )

        val tops =
            listOf("scoring-summary", "penalties-summary", "stars").map {
                compose
                    .onNodeWithTag("detail.section.$it")
                    .getUnclippedBoundsInRoot()
                    .top.value
            }
        assertTrue("penalties must follow scoring summary", tops[0] < tops[1])
        assertTrue("penalties must precede three stars", tops[1] < tops[2])
    }

    @Test
    fun `the prose lines stay at or above fourteen sp and the time at twelve`() {
        show(detailWith(penalties = listOf(crestPenalty())), fontScale = 2f)

        val card = compose.onNodeWithTag("detail.section.penalties-summary", useUnmergedTree = true).fetchSemanticsNode()
        val sizes = textSizes(card)
        assertTrue("no text nodes found in the penalties summary", sizes.isNotEmpty())
        sizes.forEach { size -> assertTrue("text at $size sp is under the 12 sp floor", size >= 12f) }

        listOf(
            "detail.penalties.row.00.sentence",
            "detail.penalties.row.00.drawn",
        ).forEach { tag ->
            val size =
                textLayout(tag)
                    .layoutInput.style.fontSize.value
            assertTrue("$tag is $size sp, under the 14 sp prose floor", size >= 14f)
        }
        assertTrue(
            textLayout("detail.penalties.row.00.time")
                .layoutInput.style.fontSize.value >= 12f,
        )
    }

    @Test
    fun `overtime and shootout headings come from the feed's own period type`() {
        assertEquals("OT", penaltyGroups(listOf(penalty(period = 4, periodType = "OT"))).single().label)
        assertEquals("SO", penaltyGroups(listOf(penalty(period = 5, periodType = "SO"))).single().label)
    }

    private fun assertRowsFit(fontScale: Float) {
        show(detailWith(penalties = AuditStates.fullPenalties), fontScale = fontScale)
        val rows = AuditStates.fullPenalties.indices
        rows.forEach { index ->
            val padded = index.toString().padStart(2, '0')
            listOf("time", "sentence").forEach { part ->
                val layout = textLayout("detail.penalties.row.$padded.$part")
                assertTrue(
                    "detail.penalties.row.$padded.$part overflowed: lines=${layout.lineCount} " +
                        "text=${layout.layoutInput.text.text}",
                    !layout.hasVisualOverflow,
                )
            }
            val drawn = compose.onNodeWithTag("detail.penalties.row.$padded.drawn", useUnmergedTree = true)
            runCatching { drawn.fetchSemanticsNode() }.getOrNull()?.let {
                val layout = textLayout("detail.penalties.row.$padded.drawn")
                assertTrue("detail.penalties.row.$padded.drawn overflowed", !layout.hasVisualOverflow)
            }
            val row = compose.onNodeWithTag("detail.penalties.row.$padded").getUnclippedBoundsInRoot()
            val text = compose.onNodeWithTag("detail.penalties.row.$padded.text", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("the text column must own the full row at $fontScale", row.right.value, text.right.value, 1f)
        }
    }

    private fun assertCrestCentresOnName() {
        val row = compose.onNodeWithTag("detail.penalties.row.00").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithTag("detail.penalties.row.00.text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val crest = compose.onNodeWithTag("detail.penalties.row.00.crest.PHI", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val name = compose.onNodeWithTag("detail.penalties.row.00.sentence", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals("the crest must not add row height", text.bottom.value - text.top.value, row.bottom.value - row.top.value, 1f)
        assertEquals(row.left.value, crest.left.value, 1f)
        assertEquals(44f, crest.bottom.value - crest.top.value, 1f)

        val nameLayout = textLayout("detail.penalties.row.00.sentence")
        val firstLineTop = name.top.value + nameLayout.getLineTop(0) / testDensity
        val firstLineBottom = name.top.value + nameLayout.getLineBottom(0) / testDensity
        val crestCentre = (crest.top.value + crest.bottom.value) / 2f
        val nameCentre = (firstLineTop + firstLineBottom) / 2f
        assertEquals("crest centre $crestCentre vs name centre $nameCentre", nameCentre, crestCentre, 2f)
        assertTrue("the crest must stay in its gutter", crest.right.value <= name.left.value + 0.5f)
    }

    private fun crestPenalty() =
        penalty(
            player = "Jamie Drysdale",
            call = "holding",
            duration = 2,
            drawnBy = "Liam O'Brien",
        )

    private fun detailWith(penalties: List<PenaltyEvent>) =
        GameDetail(
            landing = Loadable(GameLanding(penalties = penalties), Freshness.Fresh, null),
            boxscore = Loadable(GameBoxscore(), Freshness.Fresh, null),
            rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
        )

    private fun show(
        detail: GameDetail,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(testDensity, fontScale)) {
                RouxTheme(dark = true) {
                    GameDetailScreen(
                        game = AuditStates.detailGame,
                        state = DetailUiState(detail = detail, refreshing = false, unexpectedFailure = false),
                        onBack = {},
                        onRetry = {},
                        zone = java.time.ZoneId.of("America/New_York"),
                    )
                }
            }
        }
    }

    @Suppress("LongParameterList")
    private fun penalty(
        period: Int = 1,
        time: String = "10:00",
        team: String = "PHI",
        player: String = "Jamie Drysdale",
        call: String = "holding",
        duration: Int? = 2,
        drawnBy: String? = "Liam O'Brien",
        servedBy: String? = null,
        periodType: String? = "REG",
    ) = PenaltyEvent(
        period = period,
        periodType = periodType,
        timeInPeriod = time,
        teamAbbrev = team,
        player = player,
        call = call,
        durationMinutes = duration,
        drawnBy = drawnBy,
        servedBy = servedBy,
    )

    private fun labelOf(tag: String): String =
        compose
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    private fun Int.padded(): String = toString().padStart(2, '0')

    private fun rowTime(index: Int): String = labelOf("detail.penalties.row.${index.padded()}.time")

    private fun rowSentence(index: Int): String = labelOf("detail.penalties.row.${index.padded()}.sentence")

    private fun textLayout(tag: String): TextLayoutResult {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        return requireNotNull(layouts.firstOrNull()) { "$tag has no text layout" }
    }

    private fun textSizes(node: SemanticsNode): List<Float> {
        val sizes = mutableListOf<Float>()
        node.config.getOrNull(SemanticsProperties.Text)?.let {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(layouts)
            layouts
                .firstOrNull()
                ?.layoutInput
                ?.style
                ?.fontSize
                ?.value
                ?.let(sizes::add)
        }
        node.children.forEach { sizes += textSizes(it) }
        return sizes
    }
}
