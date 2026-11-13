package net.montycasa.roux.ui.scores

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.Broadcast
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.CAPSULE_BORDER
import net.montycasa.roux.ui.common.CAPSULE_HORIZONTAL_PADDING
import net.montycasa.roux.ui.common.CAPSULE_PADDING
import net.montycasa.roux.ui.theme.Roux
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
 * The state rail is one column of every card, and its width has to be the same on all of them.
 *
 * It was not: the rail measured its own content, so a card reading "FINAL" came out narrower than a card
 * reading "FINAL - OT", and the right-hand edge of the list stepped in and out as the games scrolled past.
 * The live overtime row, which puts a capsule and a clock side by side, widened it too.
 *
 * These tests pin the width as a property of the card rather than of the state: a list of cards in every
 * state draws its rail at the same width, and that width is the one the widest state needs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameRowStateRailWidthTest {
    @get:Rule
    val rule = createComposeRule()

    private var density: Density = Density(1f, 1f)
    private var widestCapsuleText = 0.dp

    private val away = TeamRef(TeamKey("BOS"), name = "Boston Bruins")
    private val home = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs")

    private fun game(
        id: String,
        state: GameState,
        finalKind: FinalKind? = null,
        period: PeriodInfo? = null,
        awayName: String? = null,
    ): Game =
        Game(
            id = GameId(id),
            date = "2026-10-26",
            state = state,
            away = away.copy(name = awayName ?: away.name),
            home = home,
            awayScore = 2,
            homeScore = 3,
            finalKind = finalKind,
            period = period,
            startTimeUtc = Instant.parse("2026-10-26T23:00:00Z"),
            broadcasts = listOf(Broadcast("NHLN", "US", "N")),
        )

    /** One card per state the rail can draw: three finals, a live overtime, and a scheduled game. */
    private val mixed =
        listOf(
            game("final", GameState.Final, FinalKind.REG),
            game("final-ot", GameState.Final, FinalKind.OT),
            game("final-so", GameState.Final, FinalKind.SO),
            game("live-ot", GameState.Live(), period = PeriodInfo(ordinal = 4, periodType = "OT", clock = "12:34")),
            game("scheduled", GameState.Scheduled),
        )

    private fun launch(
        cards: List<Game> = mixed,
        fontScale: Float = 1f,
        dark: Boolean = false,
    ) {
        rule.setContent {
            RouxTheme(dark = dark) {
                density = Density(LocalDensity.current.density, fontScale)
                CompositionLocalProvider(LocalDensity provides density) {
                    BoxWithConstraints {
                        val narrow = maxWidth <= 380.dp
                        val measurer = rememberTextMeasurer()
                        // The width of the widest capsule's text, measured under the style it is drawn
                        // in: what the rail has to find room for.
                        val measured =
                            measurer
                                .measure(
                                    text = "FINAL · OT",
                                    style = Roux.cardTypography.finalState,
                                    maxLines = 1,
                                    softWrap = false,
                                    density = density,
                                ).size.width
                        widestCapsuleText = with(density) { measured.toDp() }
                        Column(Modifier.testTag("card-list")) {
                            cards.forEach { game ->
                                GameRow(
                                    game,
                                    narrow = narrow,
                                    modifier = Modifier.testTag("card-${game.id.value}"),
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun rails(): List<SemanticsNode> =
        rule
            .onAllNodesWithTag(STATE_RAIL_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()

    private fun railWidthsDp(): List<Float> = rails().map { it.boundsInRoot.width / density.density }

    private fun assertEveryRailIsTheSameWidth(cards: List<Game> = mixed) {
        val widths = railWidthsDp()
        assertEquals("expected one rail per card", cards.size, widths.size)
        val shared = widths.first()
        widths.forEachIndexed { index, width ->
            assertEquals(
                "card '${cards[index].id.value}' has a rail of $width dp, not the shared $shared dp " +
                    "(all rails: $widths)",
                shared,
                width,
                0.01f,
            )
        }
        println("rail widths in dp: $widths")
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)
        assertTrue("no layout result - the assertion would be vacuous", results.isNotEmpty())
        return results.first()
    }

    /**
     * The test that would have caught the bug: a list that mixes the states, checked for one rail width.
     */
    @Test
    fun everyCardInAMixedListHasTheSameRailWidth() {
        launch()
        assertEveryRailIsTheSameWidth()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun everyCardInAMixedListHasTheSameRailWidthOnASmallPhone() {
        launch()
        assertEveryRailIsTheSameWidth()
    }

    @Test
    fun everyCardInAMixedListHasTheSameRailWidthAtDoubleType() {
        launch(fontScale = 2f)
        assertEveryRailIsTheSameWidth()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun everyCardInAMixedListHasTheSameRailWidthOnASmallPhoneAtDoubleType() {
        launch(fontScale = 2f)
        assertEveryRailIsTheSameWidth()
    }

    @Test
    fun everyCardInAMixedListHasTheSameRailWidthInTheDarkTheme() {
        launch(dark = true)
        assertEveryRailIsTheSameWidth()
    }

    /**
     * The rail is wide enough for the state that needs the most room: the final capsule's text, the
     * chrome it is drawn in, and the rail's own insets.
     */
    private fun assertTheRailIsWideEnoughForTheWidestState() {
        val needed =
            widestCapsuleText +
                CAPSULE_PADDING * 2 +
                CAPSULE_HORIZONTAL_PADDING * 2 +
                CAPSULE_BORDER +
                RAIL_INSETS
        val railWidth = railWidthsDp().first()
        assertTrue(
            "the rail is $railWidth dp, less than the $needed dp the widest capsule needs",
            railWidth >= needed.value - 0.01f,
        )
    }

    @Test
    fun theRailIsWideEnoughForTheWidestStateOnAPhoneWidth() {
        launch()
        assertTheRailIsWideEnoughForTheWidestState()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun theRailIsWideEnoughForTheWidestStateOnASmallPhone() {
        launch()
        assertTheRailIsWideEnoughForTheWidestState()
    }

    /**
     * The rail is wide enough for the state it draws: the capsule that needs the most room, "FINAL - OT",
     * is on one line and is not clipped, in both the wide and the narrow card.
     */
    private fun assertTheWidestCapsuleFits() {
        launch(cards = listOf(game("final-ot", GameState.Final, FinalKind.OT)))
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val capsule = rule.onNodeWithTag(STATE_CAPSULE_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val railWidthDp = rail.boundsInRoot.width / density.density
        val layout = layoutOf(capsule)
        val text = capsule.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
        println(
            "widest capsule '$text' in a rail of $railWidthDp dp: lines=${layout.lineCount} " +
                "line right=${layout.getLineRight(0)} box=${layout.size.width}",
        )
        assertEquals("'$text' wraps onto ${layout.lineCount} lines in a $railWidthDp dp rail", 1, layout.lineCount)
        assertFalse("'$text' is ellipsised in a $railWidthDp dp rail", layout.isLineEllipsized(0))
        assertTrue(
            "'$text' is clipped by its own box: the line ends at ${layout.getLineRight(0)}, the box is ${layout.size.width}",
            layout.getLineRight(0) <= layout.size.width + 1f,
        )
        assertTrue(
            "the capsule runs past the rail's right edge",
            capsule.boundsInRoot.right <= rail.boundsInRoot.right + 0.5f,
        )
    }

    @Test
    fun theWidestCapsuleFitsOnAPhoneWidth() = assertTheWidestCapsuleFits()

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun theWidestCapsuleFitsOnASmallPhone() = assertTheWidestCapsuleFits()

    @Test
    fun theWidestCapsuleFitsAtDoubleType() {
        launch(cards = listOf(game("final-ot", GameState.Final, FinalKind.OT)), fontScale = 2f)
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val capsule = rule.onNodeWithTag(STATE_CAPSULE_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(capsule)
        assertEquals("the capsule wraps at 2x type", 1, layout.lineCount)
        assertFalse("the capsule is ellipsised at 2x type", layout.isLineEllipsized(0))
        assertTrue(
            "the capsule is clipped at 2x type: the line ends at ${layout.getLineRight(0)}, the box is ${layout.size.width}",
            layout.getLineRight(0) <= layout.size.width + 1f,
        )
        assertTrue(
            "the capsule runs past the rail's right edge",
            capsule.boundsInRoot.right <= rail.boundsInRoot.right + 0.5f,
        )
    }

    /**
     * At double type the widest state needs more of the card than the card has to spare, and the
     * rail is capped so the club column keeps its room.
     *
     * A card that says nothing about which team is playing is worse than one whose clock has
     * wrapped. Cutting the name was not good enough: "Bos…" could be any Boston, and a test that
     * accepts any three characters accepts exactly that. The column now answers with the club's
     * abbreviation - short, true, and never cut - so the assertion is that the row names the club
     * unmistakably rather than that it shows some of its letters.
     */
    private fun assertTheClubNameIsStillAName() {
        launch(cards = listOf(game("final-ot", GameState.Final, FinalKind.OT)), fontScale = 2f)
        val labels = clubLabels()
        assertEquals("expected a club label on each team line", 2, labels.size)
        assertEquals(
            "at 2x type the card names the clubs $labels instead of their abbreviations",
            listOf("BOS", "TOR"),
            labels,
        )
        rule.onAllNodesWithTag(CLUB_NAME_TAG, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertFalse(
                "the label ${node.config[SemanticsProperties.Text]} is itself cut",
                layoutOf(node).isLineEllipsized(0),
            )
        }
    }

    /** The abbreviation is a fallback, not the rule: at normal type the name is shown in full. */
    @Test
    fun theClubNameIsTheWholeNameWhenItFits() {
        launch(cards = listOf(game("final-ot", GameState.Final, FinalKind.OT)))
        assertEquals(
            "at normal type the card should show the names",
            listOf("Boston Bruins", "Toronto Maple Leafs"),
            clubLabels(),
        )
    }

    /**
     * The fallback must not outlive the reason for it. A card can be resized inside a window that
     * never changes - split screen, a tablet pane - and the abbreviation would then be a lie about
     * the room the column actually has.
     */
    @Test
    fun theNameComesBackWhenTheCardWidensInAFixedWindow() {
        val cardWidth = mutableStateOf(300.dp)
        rule.setContent {
            RouxTheme {
                GameRow(
                    game("final-ot", GameState.Final, FinalKind.OT),
                    narrow = true,
                    modifier = Modifier.width(cardWidth.value),
                )
            }
        }
        rule.waitForIdle()
        assertEquals("a narrow card names the clubs by abbreviation", listOf("BOS", "TOR"), clubLabels())
        cardWidth.value = 700.dp
        rule.waitForIdle()
        assertEquals(
            "the widened card should show the names again",
            listOf("Boston Bruins", "Toronto Maple Leafs"),
            clubLabels(),
        )
    }

    /**
     * The rail floor must not beat the ceiling. On a card too narrow to give the rail its floor and
     * the club column its share, the rail takes what is left - the state wraps - rather than taking
     * the floor and squeezing the club column below the width it was promised.
     */
    @Test
    fun theCeilingBeatsTheFloorOnATooNarrowCard() {
        rule.setContent {
            RouxTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    GameRow(
                        game("final-ot", GameState.Final, FinalKind.OT),
                        narrow = true,
                        cardWidth = 256.dp,
                        modifier = Modifier.width(256.dp).testTag("ceiling-card"),
                    )
                }
            }
        }
        rule.waitForIdle()
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val card = rule.onNodeWithTag("ceiling-card", useUnmergedTree = true).fetchSemanticsNode()
        val railWidth = with(Density(1f)) { rail.boundsInRoot.width.toDp() }
        val cardDp = with(Density(1f)) { card.boundsInRoot.width.toDp() }
        assertTrue(
            "the rail is $railWidth dp on a $cardDp dp card: the floor beat the ceiling",
            railWidth < 128.dp,
        )
        clubLabels().forEach { label ->
            val node =
                rule
                    .onAllNodesWithTag(CLUB_NAME_TAG, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .first { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } == label }
            assertFalse("the club label $label is itself cut", layoutOf(node).isLineEllipsized(0))
        }
    }

    private fun clubLabels(): List<String> =
        rule
            .onAllNodesWithTag(CLUB_NAME_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun theClubNameIsStillANameAtDoubleTypeOnASmallPhone() = assertTheClubNameIsStillAName()

    @Test
    fun theClubNameIsStillANameAtDoubleTypeOnAPhoneWidth() = assertTheClubNameIsStillAName()

    /** The rail keeps the floor the card's metrics give it: 128 dp on the narrow card. */
    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun theNarrowRailKeepsItsFloor() {
        launch()
        val width = railWidthsDp().first()
        assertTrue("the narrow rail is $width dp, under its 128 dp floor", width >= 128f)
    }

    /** The wide card's floor is 92 dp. */
    @Test
    fun theWideRailKeepsItsFloor() {
        launch()
        val width = railWidthsDp().first()
        assertTrue("the wide rail is $width dp, under its 92 dp floor", width >= 92f)
    }

    /**
     * The rail is fixed now, so a long club name is the thing that yields: it truncates, and the rail keeps
     * the width every other card has.
     */
    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun aLongClubNameTruncatesInsteadOfWideningTheRail() {
        val longName = "Philadelphia Flyers Memorial Hockey Club"
        launch(
            cards =
                listOf(
                    game("short", GameState.Final, FinalKind.OT),
                    game("long", GameState.Final, FinalKind.OT, awayName = longName),
                ),
        )
        val widths = railWidthsDp()
        assertEquals("expected two rails", 2, widths.size)
        assertEquals("the long club name widened the rail", widths[0], widths[1], 0.01f)
        val labels =
            rule
                .onAllNodesWithTag(CLUB_NAME_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }
        // The name is far too long for the column, so the card answers with the club's abbreviation
        // rather than a cut name. Either way it must not widen the rail.
        assertTrue("the long club name was not replaced by the abbreviation: $labels", "BOS" in labels)
    }
}

private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }
