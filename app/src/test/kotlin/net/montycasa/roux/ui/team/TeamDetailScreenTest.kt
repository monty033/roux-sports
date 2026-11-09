package net.montycasa.roux.ui.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.StandingRow
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

/** Native graphics, so text is measured with real glyph widths; the legacy mode gives every glyph one pixel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TeamDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val toronto =
        StandingRow(
            team = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs"),
            gp = 12,
            w = 9,
            l = 2,
            otl = 1,
            pts = 19,
            leagueSequence = 7,
            conferenceSequence = 3,
            divisionSequence = 2,
            wildcardSequence = 0,
            conference = Conference.Eastern,
            division = Division.Atlantic,
        )

    private fun show(state: TeamDetailUiState) {
        rule.setContent { RouxTheme { TeamDetailScreen(state = state, onBack = {}) } }
    }

    @Test
    fun theHeaderShowsTheRecordPointsAndThreePlacesFromTheRow() {
        show(TeamDetailUiState(TeamKey("TOR"), row = toronto, isLoading = false))

        rule.onNodeWithTag("team.name").assertIsDisplayed().assertTextEquals("Toronto Maple Leafs")
        rule.onNodeWithTag("team.record").assertIsDisplayed()
        rule.onNodeWithText("9-2-1").assertIsDisplayed()
        rule.onNodeWithTag("team.points").assertIsDisplayed()
        rule.onNodeWithText("19 points").assertIsDisplayed()
        // Each place is asserted on its own: swapping any two sequence fields must fail here.
        rule.onNodeWithTag("team.place.division").assertTextEquals("2nd in Atlantic")
        rule.onNodeWithTag("team.place.conference").assertTextEquals("3rd in East")
        rule.onNodeWithTag("team.place.league").assertTextEquals("7th in League")
        rule.onNodeWithText("2nd in Atlantic").assertIsDisplayed()
        rule.onNodeWithText("3rd in East").assertIsDisplayed()
        rule.onNodeWithText("7th in League").assertIsDisplayed()
    }

    /** The division-and-conference line is gone: its own tag and its old wording are absent from the screen. */
    @Test
    fun theDivisionAndConferenceLineIsNoLongerShown() {
        show(TeamDetailUiState(TeamKey("TOR"), row = toronto, isLoading = false))

        rule.onNodeWithTag("team.division").assertDoesNotExist()
        rule.onAllNodesWithText("Eastern Conference · Atlantic Division").assertCountEquals(0)
    }

    /**
     * Every place sits within the card at 360 dp and font scale 2.0, and each line of places is centred on the card.
     * Lines are told apart by their top edge, so a place that wraps is still measured on the line it lands on.
     */
    @Test
    fun aFailedStandingsLoadSaysNothingAboutTheRecordRatherThanClaimingItIsMissing() {
        show(TeamDetailUiState(TeamKey("ANA"), row = null, isLoading = false, error = LoadError.Network))

        rule.onNodeWithTag("team.name").assertIsDisplayed().assertTextEquals("Anaheim Ducks")
        rule.onNodeWithText("The standings table doesn’t have this team’s record yet.").assertDoesNotExist()
        listOf("team.record", "team.noRecord", "team.points", "team.place.division", "team.place.conference", "team.place.league")
            .forEach { tag -> rule.onNodeWithTag(tag).assertDoesNotExist() }
    }

    @Test
    fun thePlacesLineIsCentredAndUnclippedAtNarrowWidthAndLargeText() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                RouxTheme {
                    Box(Modifier.width(360.dp)) {
                        TeamDetailScreen(
                            state = TeamDetailUiState(TeamKey("TOR"), row = toronto, isLoading = false),
                            onBack = {},
                        )
                    }
                }
            }
        }

        val card = rule.onNodeWithTag("team.header").fetchSemanticsNode().boundsInRoot
        val places =
            listOf("team.place.division", "team.place.conference", "team.place.league")
                .map { tag -> rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot }
        places.forEach { place ->
            assertTrue("a place sits outside the card: $place in $card", place.left >= card.left && place.right <= card.right)
        }
        // Wrapping is allowed; truncation is not. A truncated layout ends before its text does, so the last line must reach
        // the end of the place's text. hasVisualOverflow is not used: it reports width overflow for a centred single line
        // narrower than its constraint, which is not clipping (a centred paragraph is laid out at the full constraint width).
        listOf("team.place.division", "team.place.conference", "team.place.league").forEach { tag ->
            val layout = textLayout(tag)
            assertFalse("$tag is truncated", layout.didOverflowHeight)
            assertEquals("$tag is truncated", layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        }
        val cardCentre = (card.left + card.right) / 2f
        places.groupBy { it.top }.values.forEach { line ->
            val lineCentre = (line.minOf { it.left } + line.maxOf { it.right }) / 2f
            assertTrue(
                "a line is ${abs(lineCentre - cardCentre) / DENSITY} dp off the card centre",
                abs(lineCentre - cardCentre) <= TOLERANCE_DP * DENSITY,
            )
        }
    }

    @Test
    fun anAbsentRowShowsTheCrestAndNameButNoNumericRecord() {
        show(TeamDetailUiState(TeamKey("ANA"), row = null, isLoading = false))

        rule.onNodeWithTag("team.crest").assertIsDisplayed()
        rule.onNodeWithTag("team.name").assertIsDisplayed().assertTextEquals("Anaheim Ducks")
        rule.onNodeWithTag("team.noRecord").assertIsDisplayed()
        rule.onNodeWithText("The standings table doesn’t have this team’s record yet.").assertIsDisplayed()

        listOf("team.record", "team.points", "team.place.division", "team.place.conference", "team.place.league").forEach { tag ->
            rule.onNodeWithTag(tag).assertDoesNotExist()
        }
        assertEquals(
            "an absent row must not be rendered as a zero record",
            0,
            rule.onAllNodesWithText("0-0-0").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun placesUseTheFeedOrderRatherThanASortPosition() {
        // A different pair of values from the fixture: if either field were substituted for the other, this fails.
        show(
            TeamDetailUiState(
                TeamKey("TOR"),
                row = toronto.copy(conferenceSequence = 8, leagueSequence = 1),
                isLoading = false,
            ),
        )

        rule.onNodeWithText("8th in East").assertIsDisplayed()
        rule.onNodeWithText("1st in League").assertIsDisplayed()
    }

    /** A place the feed did not number is left out rather than printed as "0th" or a dash. */
    @Test
    fun anUnnumberedPlaceIsOmittedRatherThanPrinted() {
        show(
            TeamDetailUiState(
                TeamKey("TOR"),
                row = toronto.copy(divisionSequence = 0),
                isLoading = false,
            ),
        )

        rule.onNodeWithTag("team.place.division").assertDoesNotExist()
        rule.onNodeWithTag("team.place.conference").assertIsDisplayed()
        rule.onNodeWithTag("team.place.league").assertIsDisplayed()
    }

    /**
     * A place too long for one line wraps inside the card at 360 dp and font scale 2.0. It is never cut short: the last
     * line reaches the end of the text, and the full wording is still what the screen reads out.
     */
    @Test
    fun aLongUnrecognisedConferenceWrapsRatherThanTruncates() {
        val longConference = "Northern Continental Development and Recreation Conference of the Far Coastal Provinces"
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                RouxTheme {
                    Box(Modifier.width(360.dp)) {
                        TeamDetailScreen(
                            state =
                                TeamDetailUiState(
                                    TeamKey("TOR"),
                                    row = toronto.copy(conference = Conference.Unknown(longConference)),
                                    isLoading = false,
                                ),
                            onBack = {},
                        )
                    }
                }
            }
        }

        val text = "3rd in $longConference"
        rule.onNodeWithTag("team.place.conference").assertTextEquals(text)
        val layout = textLayout("team.place.conference")
        assertTrue("the conference place did not wrap: ${layout.lineCount} line(s)", layout.lineCount >= 2)
        assertFalse("team.place.conference is truncated", layout.didOverflowHeight)
        assertEquals("team.place.conference is truncated", text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        return layouts.single()
    }

    private companion object {
        /** The Robolectric qualifier above is 420 dpi, which is 2.625 pixels per dp. */
        const val DENSITY = 420f / 160f
        const val TOLERANCE_DP = 1f
    }
}
