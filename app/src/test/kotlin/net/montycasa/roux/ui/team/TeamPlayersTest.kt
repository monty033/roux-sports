package net.montycasa.roux.ui.team

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieStat
import net.montycasa.roux.domain.model.RosterPlayer
import net.montycasa.roux.domain.model.SkaterStat
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
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
class TeamPlayersTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val longName = "Jean-Sebastien DeLaurentis-Whitmore MacAllister-Fitzgerald"

    @Test
    fun scheduleIsSelectedFirstAndPlayersReplacesItWhenTapped() {
        showScreen()

        rule.onNodeWithTag("team.tab.Schedule").assertIsSelected()
        rule.onNodeWithTag("team.schedule.list").assertExists()

        rule.onNodeWithTag("team.tab.Players").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("team.tab.Players").assertIsSelected()
        rule.onNodeWithTag("team.tab.Schedule").assertIsNotSelected()
        rule.onNodeWithTag("team.players.list").assertExists()
        rule.onNodeWithTag("team.schedule.list").assertDoesNotExist()
    }

    @Test
    fun aPlayerInBothSourcesIsComplete() {
        showPlayers()

        rule.onNodeWithTag("team.players.player.91.name", useUnmergedTree = true).assertTextEquals("Leo Carlsson")
        rule.onNodeWithTag("team.players.player.91.headshot", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("team.players.player.91.gp", useUnmergedTree = true).assertTextEquals("14")
        rule.onNodeWithTag("team.players.player.91.pts", useUnmergedTree = true).assertTextEquals("21")
        rule.onNodeWithTag("team.players.player.91.+/-", useUnmergedTree = true).assertTextEquals("+8")
        rule.onNodeWithTag("team.players.player.91.pim", useUnmergedTree = true).assertTextEquals("8")
    }

    @Test
    fun theSweaterNumberIsGoneAndTheHeadshotTakesThatSpace() {
        showPlayers()

        rule.onNodeWithTag("team.players.player.91.number", useUnmergedTree = true).assertDoesNotExist()

        val headshot =
            rule
                .onNodeWithTag("team.players.player.91.headshot", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val name =
            rule
                .onNodeWithTag("team.players.player.91.name", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val minimum = with(rule.density) { 40.dp.toPx() }

        assertTrue("the headshot must be at least 40 dp: ${headshot.width} against $minimum", headshot.width >= minimum - 1f)
        assertTrue(
            "the headshot must sit left of the name: ${headshot.right} against ${name.left}",
            headshot.right <= name.left + 1f,
        )
    }

    @Test
    fun aRosterOnlyPlayerHasDashesForStats() {
        showPlayers()

        rule.onNodeWithTag("team.players.player.12.name", useUnmergedTree = true).assertTextEquals("Sam Colangelo")
        listOf("gp", "g", "a", "pts", "+/-", "pim").forEach { column ->
            rule.onNodeWithTag("team.players.player.12.$column", useUnmergedTree = true).assertTextEquals("—")
        }
    }

    @Test
    fun aStatsOnlyPlayerIsNotListedWhenTheRosterIsPresent() {
        // He may have been traded away or sent down this season; Patrick's ruling is that a player
        // appears only on the club he is currently on, so the stats feed must not add a row here.
        showPlayers()

        rule.onNodeWithTag("team.players.player.3").assertDoesNotExist()
        rule.onNodeWithTag("team.players.player.40").assertDoesNotExist()
    }

    @Test
    fun aRosterOnlyPlayerKeepsHisHeadshotAndDashesWithoutStats() {
        showPlayers()

        rule.onNodeWithTag("team.players.player.12.name", useUnmergedTree = true).assertTextEquals("Sam Colangelo")
        rule.onNodeWithTag("team.players.player.12.headshot", useUnmergedTree = true).assertExists()
        listOf("gp", "g", "a", "pts", "+/-", "pim").forEach { column ->
            rule.onNodeWithTag("team.players.player.12.$column", useUnmergedTree = true).assertTextEquals("—")
        }
    }

    @Test
    fun aStatsOnlyDefensemanIsListedInTheDefenseGroupWhenTheRosterIsAbsent() {
        showScreen(roster = Loadable(data = null, freshness = Freshness.Fresh, error = null))
        openPlayers()

        rule.onNodeWithTag("team.players.player.3.name", useUnmergedTree = true).assertTextEquals("Stats Only D")
        rule.onNodeWithTag("team.players.player.3.pts", useUnmergedTree = true).assertTextEquals("4")

        val defenseTop =
            rule
                .onNodeWithTag("team.players.group.defense.label")
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val rowTop =
            rule
                .onNodeWithTag("team.players.player.3")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val goaliesTop =
            rule
                .onNodeWithTag("team.players.group.goalies.label")
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        assertTrue("stats-only defenceman must follow the Defensemen label", rowTop > defenseTop)
        assertTrue("stats-only defenceman must precede the Goalies label", rowTop < goaliesTop)
    }

    @Test
    fun theJoinIsByPlayerIdSoTwoPlayersWithOneNameAreNotMerged() {
        showPlayers()

        rule.onNodeWithTag("team.players.player.10.name", useUnmergedTree = true).assertTextEquals("Same Name")
        rule.onNodeWithTag("team.players.player.11.name", useUnmergedTree = true).assertTextEquals("Same Name")
        rule.onNodeWithTag("team.players.player.10.pts", useUnmergedTree = true).assertTextEquals("5")
        rule.onNodeWithTag("team.players.player.11.pts", useUnmergedTree = true).assertTextEquals("—")
    }

    @Test
    fun aLongNameIsRenderedInFullAndNeverEllipsised() {
        showPlayers()

        val name = rule.onNodeWithTag("team.players.player.13.name", useUnmergedTree = true)
        name.assertTextEquals(longName)

        // Whether it wraps is answered in TeamPlayersNameWrapTest, where the harness width and type are a phone's.
        // This harness cannot see it: at its scale a long name lays out on one line, so a wrapping assertion here
        // would pass against a clipped name as well.
    }

    @Test
    fun aSidewaysDragMovesTheNumbersButNotTheNameColumn() {
        showPlayers()

        val nameBefore = rule.onNodeWithTag("team.players.player.91.name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pointsBefore = rule.onNodeWithTag("team.players.player.91.pts", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        rule.onNodeWithTag("team.players.player.91.numbers", useUnmergedTree = true).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        val nameAfter = rule.onNodeWithTag("team.players.player.91.name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pointsAfter = rule.onNodeWithTag("team.players.player.91.pts", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertEquals("the pinned name must not move sideways", nameBefore.left, nameAfter.left, 0.5f)
        assertEquals("the pinned name must not move sideways", nameBefore.right, nameAfter.right, 0.5f)
        assertTrue(
            "the number column must move under the drag: ${pointsBefore.left} -> ${pointsAfter.left}",
            pointsAfter.left < pointsBefore.left - 1f,
        )
    }

    @Test
    fun groupsAreLabelledInOrderAndRowsAreOrderedBySweaterNumber() {
        showPlayers()

        val forwards =
            rule
                .onNodeWithTag("team.players.group.forwards.label")
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val defense =
            rule
                .onNodeWithTag("team.players.group.defense.label")
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val goalies =
            rule
                .onNodeWithTag("team.players.group.goalies.label")
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        assertTrue("Forwards must precede Defensemen", forwards < defense)
        assertTrue("Defensemen must precede Goalies", defense < goalies)

        val twelve =
            rule
                .onNodeWithTag("team.players.player.12")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val ninetyOne =
            rule
                .onNodeWithTag("team.players.player.91")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val numberless =
            rule
                .onNodeWithTag("team.players.player.13")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("12 must precede 91", twelve < ninetyOne)
        assertTrue("a numberless player sorts last", numberless > ninetyOne)
    }

    @Test
    fun goalieRowsCarryGoalieColumnsAndSkaterRowsDoNot() {
        showPlayers()

        rule.onNodeWithTag("team.players.group.goalies.header.GAA").assertExists()
        rule.onNodeWithTag("team.players.group.goalies.header.SV%").assertExists()
        rule.onNodeWithTag("team.players.group.forwards.header.GAA").assertDoesNotExist()

        rule.onNodeWithTag("team.players.player.31.gaa", useUnmergedTree = true).assertTextEquals("3.00")
        rule.onNodeWithTag("team.players.player.31.sv%", useUnmergedTree = true).assertTextEquals(".870")
        rule.onNodeWithTag("team.players.player.91.gaa", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun onlyTwoFailuresReplaceTheTabAndOfferAWorkingRetry() {
        var retries = 0
        showScreen(
            roster = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
            stats = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
            onPlayersRetry = { retries++ },
        )
        openPlayers()

        rule.onNodeWithTag("team.name").assertIsDisplayed()
        rule.onNodeWithTag("team.tab.Schedule").assertIsDisplayed()
        rule.onNodeWithTag("team.tab.Players").assertIsSelected()
        rule.onNodeWithTag("team.players.error").assertIsDisplayed()
        rule.onNodeWithTag("team.players.retry").assertExists()

        rule.onNodeWithTag("team.players.retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun oneFailureLeavesTheOtherSourcesTableStanding() {
        var retries = 0
        showScreen(
            stats = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
            onPlayersRetry = { retries++ },
        )
        openPlayers()

        rule.onNodeWithTag("team.players.error").assertIsDisplayed()
        rule.onNodeWithTag("team.players.player.91.name", useUnmergedTree = true).assertTextEquals("Leo Carlsson")
        rule.onNodeWithTag("team.players.player.91.pts", useUnmergedTree = true).assertTextEquals("—")
        rule.onNodeWithTag("team.players.retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun aRosterFailureStillShowsTheStatsOnlyRows() {
        showScreen(
            roster = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
        )
        openPlayers()

        rule.onNodeWithTag("team.players.error").assertIsDisplayed()
        rule.onNodeWithTag("team.players.player.3.name", useUnmergedTree = true).assertTextEquals("Stats Only D")
        rule.onNodeWithTag("team.players.player.91.name", useUnmergedTree = true).assertTextEquals("Leo Carlsson")
    }

    @Test
    fun switchingToPlayersDoesNotRefetchTheSchedule() {
        var scheduleRetries = 0
        showScreen(onScheduleRetry = { scheduleRetries++ })

        openPlayers()

        assertEquals(0, scheduleRetries)
    }

    @Test
    fun theHeaderLabelsScrollWithTheirNumbers() {
        showPlayers()

        fun headerLeft() =
            rule
                .onNodeWithTag("team.players.group.forwards.header.PTS", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot.left

        fun cellLeft() =
            rule
                .onNodeWithTag("team.players.player.91.pts", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot.left

        val headerBefore = headerLeft()
        val cellBefore = cellLeft()
        rule.onNodeWithTag("team.players.player.91.numbers", useUnmergedTree = true).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertTrue(
            "the drag must move the labels too: $headerBefore -> ${headerLeft()}",
            headerLeft() < headerBefore - 1f,
        )
        assertEquals(
            "a label must stay above its own column",
            headerBefore - cellBefore,
            headerLeft() - cellLeft(),
            1f,
        )
    }

    @Test
    fun tappingAPlayerRowEmitsThatPlayersId() {
        val opened = mutableListOf<Long>()
        showScreen(onPlayerClick = { opened += it })
        openPlayers()

        rule.onNodeWithTag("team.players.player.91").performClick()

        assertEquals(listOf(91L), opened)
    }

    @Test
    fun aPlayerRowSpeaksTheNameAndThatItOpensTheDetails() {
        showScreen()
        openPlayers()

        rule
            .onNodeWithTag("team.players.player.91")
            .assertContentDescriptionEquals("Leo Carlsson, open player details")
    }

    private fun openPlayers() {
        rule.onNodeWithTag("team.tab.Players").performClick()
        rule.waitForIdle()
    }

    private fun showPlayers() {
        showScreen()
        openPlayers()
    }

    private fun showScreen(
        roster: Loadable<ClubRoster> = Loadable(sampleRoster, Freshness.Fresh, null),
        stats: Loadable<ClubStats> = Loadable(sampleStats, Freshness.Fresh, null),
        onPlayersRetry: () -> Unit = {},
        onScheduleRetry: () -> Unit = {},
        onPlayerClick: (Long) -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                TeamDetailScreen(
                    state =
                        TeamDetailUiState(
                            teamKey = TeamKey("TOR"),
                            row = toronto,
                            isLoading = false,
                            schedule = Loadable(listOf(game), Freshness.Fresh, null),
                            roster = roster,
                            stats = stats,
                        ),
                    onBack = {},
                    onRetry = onScheduleRetry,
                    onPlayersRetry = onPlayersRetry,
                    onPlayerClick = onPlayerClick,
                    now = now,
                )
            }
        }
    }

    private val sampleRoster =
        ClubRoster(
            forwards =
                listOf(
                    RosterPlayer(id = 91, name = "Leo Carlsson", sweaterNumber = 91, positionCode = "C"),
                    RosterPlayer(id = 12, name = "Sam Colangelo", sweaterNumber = 12, positionCode = "R"),
                    RosterPlayer(id = 10, name = "Same Name", sweaterNumber = 10, positionCode = "C"),
                    RosterPlayer(id = 11, name = "Same Name", sweaterNumber = 11, positionCode = "L"),
                    RosterPlayer(id = 13, name = longName, sweaterNumber = null, positionCode = "L"),
                ),
            defense = listOf(RosterPlayer(id = 14, name = "Drew Helleson", sweaterNumber = 14, positionCode = "D")),
            goalies = listOf(RosterPlayer(id = 31, name = "Laurent Brossoit", sweaterNumber = 31, positionCode = "G")),
        )

    private val sampleStats =
        ClubStats(
            skaters =
                listOf(
                    SkaterStat(
                        playerId = 91,
                        name = "Leo Carlsson",
                        positionCode = "C",
                        gamesPlayed = 14,
                        goals = 4,
                        assists = 17,
                        points = 21,
                        plusMinus = 8,
                        penaltyMinutes = 8,
                    ),
                    SkaterStat(
                        playerId = 10,
                        name = "Same Name",
                        positionCode = "C",
                        gamesPlayed = 7,
                        goals = 2,
                        assists = 3,
                        points = 5,
                        plusMinus = 1,
                        penaltyMinutes = 2,
                    ),
                    SkaterStat(
                        playerId = 14,
                        name = "Drew Helleson",
                        positionCode = "D",
                        gamesPlayed = 12,
                        goals = 1,
                        assists = 1,
                        points = 2,
                        plusMinus = 0,
                        penaltyMinutes = 4,
                    ),
                    SkaterStat(
                        playerId = 3,
                        name = "Stats Only D",
                        positionCode = "D",
                        gamesPlayed = 6,
                        goals = 2,
                        assists = 2,
                        points = 4,
                        plusMinus = -1,
                        penaltyMinutes = 0,
                    ),
                    SkaterStat(
                        playerId = 13,
                        name = longName,
                        positionCode = "L",
                        gamesPlayed = 1,
                        goals = 0,
                        assists = 1,
                        points = 1,
                        plusMinus = 0,
                        penaltyMinutes = 0,
                    ),
                ),
            goalies =
                listOf(
                    GoalieStat(
                        playerId = 31,
                        name = "Laurent Brossoit",
                        gamesPlayed = 1,
                        wins = 1,
                        losses = 0,
                        overtimeLosses = 0,
                        goalsAgainstAverage = 3.0,
                        savePercentage = 0.869565,
                        shutouts = 0,
                    ),
                    GoalieStat(
                        playerId = 40,
                        name = "Stats Only G",
                        gamesPlayed = 2,
                        wins = 0,
                        losses = 2,
                        overtimeLosses = 0,
                        goalsAgainstAverage = 4.0,
                        savePercentage = 0.85,
                        shutouts = 0,
                    ),
                ),
        )

    private val game =
        ClubGame(
            id = GameId("next"),
            date = "2026-11-05",
            startTimeUtc = Instant.parse("2026-11-05T20:00:00Z"),
            home = TeamRef(TeamKey("TOR")),
            away = TeamRef(TeamKey("BOS")),
            state = GameState.Scheduled,
        )

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
}
