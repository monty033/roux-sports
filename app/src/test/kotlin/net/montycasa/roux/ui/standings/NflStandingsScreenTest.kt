@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.standings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflStandingTable
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * Football's table at 360 dp - the narrowest phone the layout promises - where eight numeric columns have to fit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflStandingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val shown = mutableStateOf(NflStandingsFixtures.state(NflStandingsTab.Division))
    private val tabs = mutableListOf<NflStandingsTab>()
    private val conferences = mutableListOf<NflConference>()
    private val opened = mutableListOf<TeamKey>()

    private fun launch(
        state: NflStandingsUiState,
        fontScale: Float = 1f,
    ) {
        shown.value = state
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) {
                RouxTheme {
                    NflStandingsFixtures.Page(
                        shown.value,
                        onSelectTab = { tabs += it },
                        onSelectConference = { conferences += it },
                        onTeamClick = { opened += it },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun conference(
        tab: NflStandingsTab,
        conference: NflConference = NflConference.AFC,
    ) = NflStandingsFixtures.state(tab, conference)

    private fun cell(
        abbrev: String,
        column: Int,
        section: Int = 0,
    ) = rule.onNode(
        hasTestTag("nfl.standings.cell.$column") and hasAnyAncestor(hasTestTag("nfl.standings.row.$section.$abbrev")),
        useUnmergedTree = true,
    )

    private fun ranks() = rule.onAllNodesWithTag("nfl.standings.rank", useUnmergedTree = true)

    private fun top(tag: String) =
        rule
            .onNodeWithTag(tag)
            .getUnclippedBoundsInRoot()
            .top.value

    private fun bottom(tag: String) =
        rule
            .onNodeWithTag(tag)
            .getUnclippedBoundsInRoot()
            .bottom.value

    @Test
    fun theHeaderNamesEightColumnsAndNoPointsColumn() {
        launch(conference(NflStandingsTab.Division))

        Columns.forEach { name -> rule.onNodeWithTag("nfl.standings.col.$name").assertTextEquals(name) }
        assertEquals(8, Columns.size)
        // Football has no points column: nothing of hockey's header, and no zero-filled stand-in.
        listOf("PTS", "GP", "OTL").forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun theThreeTabsAreOfferedAndReportTheirSelection() {
        launch(conference(NflStandingsTab.Conference))

        rule.onNodeWithTag("nfl.standings.tab.Conference").assertIsSelected()
        listOf("Division", "Conference", "League").forEach { name ->
            rule.onNodeWithTag("nfl.standings.tab.$name").performClick()
        }
        assertEquals(listOf(NflStandingsTab.Division, NflStandingsTab.Conference, NflStandingsTab.League), tabs)
    }

    @Test
    fun theSwitcherChoosesAConferenceOnTheTwoPerConferenceTabsAndLeavesOnTheLeague() {
        launch(conference(NflStandingsTab.Division))
        rule.onNodeWithTag("nfl.standings.conference.NFC").performClick()
        assertEquals(listOf(NflConference.NFC), conferences)

        shown.value = conference(NflStandingsTab.League)
        rule.waitForIdle()
        rule.onAllNodesWithTag("nfl.standings.conference.AFC").assertCountEquals(0)
        rule.onAllNodesWithTag("nfl.standings.conference.NFC").assertCountEquals(0)
    }

    @Test
    fun theDivisionTabGroupsByTheFeedsDivisionNamesFourClubsEach() {
        launch(conference(NflStandingsTab.Division))

        listOf("AFC East", "AFC North", "AFC South", "AFC West").forEach { rule.onNodeWithText(it).assertExists() }
        ranks().assertCountEquals(16)
        (0..3).forEach { section ->
            val clubs =
                shown.value.tables[section]
                    .rows
                    .map { it.team.abbrev }
            assertEquals(4, clubs.size)
            clubs.forEach { rule.onNodeWithTag("nfl.standings.row.$section.$it").assertExists() }
        }
    }

    @Test
    fun theLeagueTabIsOneUntitledTableOfThirtyTwoClubs() {
        launch(conference(NflStandingsTab.League))

        ranks().assertCountEquals(32)
        assertEquals("1", ranks()[0].fetchSemanticsNode().textOf())
        // No conference or division name over it: the tab already says what the page is.
        rule.onAllNodesWithText("American Football Conference").assertCountEquals(0)
        rule.onAllNodesWithTag("nfl.standings.playoffLine").assertCountEquals(0)
    }

    @Test
    fun aTiedRecordShowsThreeNumbers() {
        launch(conference(NflStandingsTab.Conference, NflConference.NFC))

        // New York is 5-3-1 and the line must say so, with the tie, in the three columns.
        cell("NYG", 0).assertTextEquals("5")
        cell("NYG", 1).assertTextEquals("3")
        cell("NYG", 2).assertTextEquals("1")
        // And a club with no ties prints its zero rather than leaving the column blank: a football record is W-L-T.
        cell("SF", 2).assertTextEquals("0")
    }

    @Test
    fun theFigureColumnsDrawTheFeedsOwnStrings() {
        launch(conference(NflStandingsTab.Conference, NflConference.AFC))

        cell("BUF", 3).assertTextEquals(".889")
        cell("BUF", 4).assertTextEquals("287")
        cell("BUF", 5).assertTextEquals("199")
        cell("BUF", 6).assertTextEquals("+88")
        cell("BUF", 7).assertTextEquals("W5")
        cell("CLE", 6).assertTextEquals("-32")
    }

    @Test
    fun aBlankStaysBlankAndARealZeroStaysZero() {
        val missing = NflStandingsFixtures.bareRow("AAA")
        val zero = NflStandingsFixtures.bareRow("BBB").copy(wins = 0, losses = 0, ties = 0, pointsFor = 0, differential = 0)
        launch(
            conference(NflStandingsTab.League).copy(tables = listOf(NflStandingTable("", listOf(missing, zero)))),
        )

        (0..7).forEach { column -> cell("AAA", column).assertTextEquals("") }
        // A club that really has scored nothing says 0, the one thing a missing figure must never say.
        cell("BBB", 0).assertTextEquals("0")
        cell("BBB", 4).assertTextEquals("0")
        cell("BBB", 6).assertTextEquals("0")
        cell("BBB", 3).assertTextEquals("")
        cell("BBB", 7).assertTextEquals("")
    }

    @Test
    fun aMissingTieBlanksTheWholeRecordRatherThanInventingAZero() {
        val partial = NflStandingsFixtures.bareRow("CCC").copy(wins = 5, losses = 3)
        launch(conference(NflStandingsTab.League).copy(tables = listOf(NflStandingTable("", listOf(partial)))))

        (0..2).forEach { column -> cell("CCC", column).assertTextEquals("") }
    }

    @Test
    fun theNfcPlayoffLineSitsAfterTheSeventhSeedAndTheSeededClubsAreAboveIt() {
        launch(conference(NflStandingsTab.Conference, NflConference.NFC))

        val line = "nfl.standings.playoffLine"
        // Seven clubs above it, in seed order, and the eighth below. The feed's own table lists the third and fourth
        // seeds under the eighth and ninth; the line would have crossed two playoff clubs without the seed order.
        listOf("SF", "DET", "NYG", "CAR", "MIN", "CHI", "GB").forEach { above ->
            assertTrue("$above is above the line", bottom("nfl.standings.row.0.$above") <= top(line))
        }
        listOf("SEA", "LAR", "DAL").forEach { below ->
            assertTrue("$below is below the line", top("nfl.standings.row.0.$below") >= bottom(line))
        }
        rule.onAllNodesWithTag(line).assertCountEquals(1)
    }

    @Test
    fun theAfcPlayoffLineSitsBetweenClevelandAndNewEngland() {
        launch(conference(NflStandingsTab.Conference, NflConference.AFC))

        assertTrue(bottom("nfl.standings.row.0.CLE") <= top("nfl.standings.playoffLine"))
        assertTrue(top("nfl.standings.row.0.NE") >= bottom("nfl.standings.playoffLine"))
        // The gutter shows the feed's seed, not the row's place.
        assertEquals("7", ranks()[6].fetchSemanticsNode().textOf())
    }

    @Test
    fun aConferenceWhoseSeventhSeedTheFeedDidNotStateDrawsNoLine() {
        val unseeded = NflStandingsFixtures.afc.rows.map { it.copy(playoffSeed = null) }
        launch(conference(NflStandingsTab.Conference).copy(tables = listOf(NflStandingTable("American Football Conference", unseeded))))

        rule.onAllNodesWithTag("nfl.standings.playoffLine").assertCountEquals(0)
        // And its gutter is blank rather than counting rows: that would be a seed this app invented.
        assertEquals("", ranks()[0].fetchSemanticsNode().textOf())
    }

    @Test
    fun theDivisionTabDrawsNoPlayoffLine() {
        launch(conference(NflStandingsTab.Division))

        rule.onAllNodesWithTag("nfl.standings.playoffLine").assertCountEquals(0)
    }

    @Test
    fun aRowIsAtLeastFortyEightDpAndOpensItsClub() {
        launch(conference(NflStandingsTab.Conference))

        val rows =
            shown.value.tables
                .single()
                .rows
        rows.forEach { row ->
            val bounds = rule.onNodeWithTag("nfl.standings.row.0.${row.team.abbrev}").getUnclippedBoundsInRoot()
            assertTrue("${row.team.abbrev} is ${bounds.height}", bounds.height >= 48.dp)
        }
        rule.onNodeWithTag("nfl.standings.row.0.BUF").performClick()
        assertEquals(listOf(rows.first().team.key), opened)
    }

    @Test
    fun theControlsPassTheReachAuditOnEveryTab() {
        launch(conference(NflStandingsTab.Division))
        NflStandingsTab.entries.forEach { tab ->
            shown.value = conference(tab)
            rule.waitForIdle()
            // The conference strip is the one primary control; the league page has none to offer.
            val primaries = if (tab == NflStandingsTab.League) emptyArray() else arrayOf("nfl.standings.conference")
            // The bar the test reserves declares its own three, which the screen is audited against.
            rule.assertPrimaryControlsReachable(*(primaries + arrayOf("test.nav.Scores", "test.nav.Standings", "test.nav.Teams")))
        }
    }

    @Test
    fun eightColumnsFitA360DpPhoneWithoutSideways() {
        launch(conference(NflStandingsTab.League))

        val widths =
            Columns.map { name ->
                name to
                    rule
                        .onNodeWithTag("nfl.standings.col.$name")
                        .getUnclippedBoundsInRoot()
                        .width.value
            }
        val table = rule.onNodeWithTag("nfl.standings.scroll").fetchSemanticsNode()
        val travel =
            table.config
                .getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                ?.maxValue
                ?.invoke() ?: 0f
        val team =
            rule
                .onAllNodesWithText("Bills", useUnmergedTree = true)[0]
                .fetchSemanticsNode()
                .size.width / rule.density.density
        println("MEASURED 360 dp: columns=$widths sum=${widths.sumOf { it.second.toDouble() }} travel=$travel px, first club name=$team dp")
        assertEquals("the table slides sideways by $travel px at 360 dp", 0f, travel, 0.5f)
    }

    @Test
    fun atDoubleTypeTheTableSlidesAsOnePieceRatherThanClippingFigures() {
        launch(conference(NflStandingsTab.League), fontScale = 2f)

        val table = rule.onNodeWithTag("nfl.standings.scroll").fetchSemanticsNode()
        val travel =
            table.config
                .getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                ?.maxValue
                ?.invoke() ?: 0f
        println("MEASURED 2x type: travel=$travel px")
        assertTrue("double type must scroll, travel=$travel", travel > 0f)
        // The header and the rows share the one scroll container, so they cannot drift apart.
        rule.onAllNodesWithTag("nfl.standings.scroll").assertCountEquals(1)
    }

    @Test
    fun loadingAndEmptyAndErrorSayWhatTheyAre() {
        launch(NflStandingsUiState())
        rule.onNodeWithText("Loading standings…").assertExists()

        shown.value = NflStandingsUiState(loading = false)
        rule.waitForIdle()
        rule.onNodeWithText("No table yet.").assertExists()

        shown.value = NflStandingsUiState(loading = false, error = LoadError.Http(503))
        rule.waitForIdle()
        rule.onNodeWithText("Couldn’t load standings. Try again.").assertExists()
        rule.onNodeWithTag("nfl.standings.retry").assertExists()
    }

    @Test
    fun aStaleTableKeepsItsRowsAndOffersARetry() {
        launch(
            conference(NflStandingsTab.Conference)
                .copy(stale = Freshness.Stale(Instant.parse("2026-11-24T14:00:00Z")), error = LoadError.Http(503)),
        )

        rule.onNodeWithText("Couldn’t refresh. Showing saved standings.").assertExists()
        rule.onNodeWithTag("nfl.standings.retry").assertExists()
        rule.onNodeWithTag("nfl.standings.row.0.BUF").assertExists()
    }
}

private val Columns = listOf("W", "L", "T", "PCT", "PF", "PA", "DIFF", "STRK")

/** The text a node draws, from the semantics tree. */
private fun SemanticsNode.textOf(): String =
    config
        .getOrNull(SemanticsProperties.Text)
        ?.joinToString("") { it.text }
        .orEmpty()
