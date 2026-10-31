package net.montycasa.roux.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.scores.GameRow
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone
import kotlin.time.Instant

/**
 * What a card sounds like, which is the half of the layout no screenshot can show.
 *
 * The card is drawn as two team rows and a state rail; read in drawing order a live game arrives as seven
 * fragments with the score spoken twice and the state split across "Live" and "LIVE". The row therefore
 * carries one sentence, and these pin it: the exact words, and the live state spoken once rather than once
 * per label that happens to draw it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
class AccessibilityLabelsTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setTorontoTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        // The upcoming rows speak a start time, so the zone has to be fixed for the words to be.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    private val scheduled =
        Game(
            id = GameId("2026020001"),
            date = "2026-10-26",
            state = GameState.Scheduled,
            away = TeamRef(TeamKey("BOS"), name = "Boston Bruins"),
            home = TeamRef(TeamKey("NYR"), name = "New York Rangers"),
            startTimeUtc = Instant.parse("2026-10-26T23:00:00Z"),
        )

    private val live =
        scheduled.copy(
            state = GameState.Live(),
            awayScore = 3,
            homeScore = 2,
            period = PeriodInfo(ordinal = 2, periodType = "REG", clock = "7:42"),
        )

    @Test
    fun aLiveRowIsOneSentenceAndSaysLiveOnce() {
        show(live)

        rule
            .onNodeWithTag(CARD)
            .assertContentDescriptionEquals("Boston Bruins 3, New York Rangers 2, live, 2nd period, 7:42")
    }

    @Test
    fun theLiveWordIsNotRepeatedForTheCapsuleThatAlsoDrawsIt() {
        show(live)

        val sentence = description()

        assertEquals("the live state is spoken once", 1, Regex("(?i)\\blive\\b").findAll(sentence).count())
    }

    @Test
    fun aFinishedRowSaysHowItFinished() {
        show(live.copy(state = GameState.Final, finalKind = FinalKind.OT))

        rule
            .onNodeWithTag(CARD)
            .assertContentDescriptionEquals("Boston Bruins 3, New York Rangers 2, final in overtime")
    }

    @Test
    fun aRowWithoutAScoreSaysWhoIsPlayingAndWhen() {
        show(scheduled)

        rule
            .onNodeWithTag(CARD)
            .assertContentDescriptionEquals("Boston Bruins at New York Rangers, 7:00 PM")
    }

    @Test
    fun theSentenceIsTheCardAndTheVisualRowsStayTheirOwnNodes() {
        // The layout and reach audits measure the individual rows and rail, so the sentence is added to the
        // card rather than swallowing its children.
        show(live)

        rule.onNodeWithTag(CARD).assertIsDisplayed()
        rule.onNodeWithText("Boston Bruins").assertIsDisplayed()
        // The rail still draws the period and clock together, and still owns that node.
        rule.onNodeWithText("2nd 7:42").assertIsDisplayed()
    }

    private fun show(game: Game) {
        rule.setContent {
            RouxTheme {
                GameRow(game, narrow = false, modifier = Modifier.testTag(CARD))
            }
        }
    }

    private fun description(): String =
        rule
            .onNodeWithTag(CARD)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .single()

    private companion object {
        const val CARD = "game-row"
    }
}
