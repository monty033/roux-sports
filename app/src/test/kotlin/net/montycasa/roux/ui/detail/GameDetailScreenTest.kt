@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalAssist
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PeriodShots
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.PowerPlaySummary
import net.montycasa.roux.domain.model.ShotTotals
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.TeamStat
import net.montycasa.roux.domain.model.ThreeStar
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the page promises: every card answers for its own endpoint, the bars never lie about a lopsided game, and the
 * controls stay pressable at the narrow size and at a doubled font scale.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class GameDetailScreenTest {
    @Test
    fun `finished info follows every other summary section`() {
        show(
            fullDetail.copy(
                landing =
                    fullDetail.landing.copy(
                        data =
                            fullDetail.landing.data!!.copy(
                                venue =
                                    net.montycasa.roux.domain.model
                                        .Venue("Centre Bell", "Montreal"),
                            ),
                    ),
            ),
        )
        compose.onNodeWithText("Centre Bell · Montreal").assert(hasAnyAncestor(hasTestTag("detail.section.info")))
        val tops =
            listOf("scoring", "stats", "stars", "info").map {
                compose
                    .onNodeWithTag("detail.section.$it")
                    .getUnclippedBoundsInRoot()
                    .top.value
            }
        assertTrue(tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `scheduled empty sections leave only game info`() {
        show(
            fullDetail.copy(
                landing =
                    Loadable(
                        GameLanding(
                            venue =
                                net.montycasa.roux.domain.model
                                    .Venue("Centre Bell", null),
                        ),
                        Freshness.Fresh,
                        null,
                    ),
                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
            ),
            shownGame = game.copy(state = GameState.Scheduled),
        )
        listOf("scoring", "stats", "stars").forEach { compose.onNodeWithTag("detail.section.$it").assertDoesNotExist() }
        compose.onNodeWithTag("detail.section.info").assertExists()
        compose.onNodeWithText("Centre Bell").assertExists()
        compose.onNodeWithText("Where to watch").assertDoesNotExist()
        compose.onAllNodesWithText("No ", substring = true).assertCountEquals(0)
    }

    @Test
    fun `absent venue and broadcasts omit info`() {
        show(fullDetail)
        compose.onNodeWithTag("detail.section.info").assertDoesNotExist()
    }

    @Test
    fun `scheduled landing failure retains retry`() {
        show(
            fullDetail.copy(landing = Loadable(null, Freshness.Missing, LoadError.Http(503))),
            shownGame = game.copy(state = GameState.Scheduled),
        )
        compose.onNodeWithText("Scoring could not be loaded").assertExists()
        compose.onNodeWithTag("detail.section.scoring.retry").assertExists().performClick()
        compose.onNodeWithTag("detail.section.info.retry").performScrollTo().assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun `broadcast countries keep feed order with national first and three distinct networks`() {
        val broadcasts =
            listOf(
                "TSN2" to "H",
                "TVAS" to "N",
                "TVAS" to "H",
                "RDS" to "A",
                "Extra" to "H",
            ).map { (network, market) ->
                net.montycasa.roux.domain.model
                    .Broadcast(network, "CA", market)
            } +
                net.montycasa.roux.domain.model
                    .Broadcast("NHLN", "US", "N")
        show(fullDetail.copy(landing = fullDetail.landing.copy(data = GameLanding(broadcasts = broadcasts))))
        compose.onNodeWithText("CA: TVAS · TSN2 · RDS\nUS: NHLN").assertExists()
        compose.onNodeWithText("Arena").assertDoesNotExist()
    }

    @Test
    fun `finished empty summary also hides all empty cards`() {
        show(
            fullDetail.copy(
                landing = Loadable(GameLanding(), Freshness.Fresh, null),
                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
            ),
        )
        listOf("scoring", "stats", "stars", "info").forEach {
            compose.onNodeWithTag("detail.section.$it").assertDoesNotExist()
        }
    }

    @Test
    fun `empty club roster hides the whole player card`() {
        show(fullDetail.copy(boxscore = Loadable(GameBoxscore(), Freshness.Fresh, null)))
        compose.onNodeWithTag("detail.tab.PHI").performClick()
        compose.onNodeWithTag("detail.section.players").assertDoesNotExist()
        compose.onNodeWithText("No player lines for this club").assertDoesNotExist()
    }

    @get:Rule
    val compose = createComposeRule()

    private val game =
        Game(
            id = GameId("2026020188"),
            date = "2026-10-26",
            state = GameState.Final,
            home = TeamRef(TeamKey("PHI"), name = "Flyers"),
            away = TeamRef(TeamKey("CBJ"), name = "Blue Jackets"),
            homeScore = 4,
            awayScore = 3,
        )

    private val fullDetail =
        GameDetail(
            landing =
                Loadable(
                    GameLanding(
                        periodScoring = listOf(PeriodScoring(1, 2, 1), PeriodScoring(2, 0, 1)),
                        threeStars = listOf(ThreeStar(1, PlayerRef("T. Konecny", "PHI"))),
                    ),
                    Freshness.Fresh,
                    null,
                ),
            boxscore =
                Loadable(
                    GameBoxscore(
                        goalies =
                            listOf(
                                GoalieLine(PlayerRef("C. Talbot", "PHI"), 25, 28),
                                GoalieLine(PlayerRef("Away goalie", "CBJ"), 20, 24),
                            ),
                    ),
                    Freshness.Fresh,
                    null,
                ),
            rightRail = Loadable(GameRightRail(shots = ShotTotals(awayShots = 21, homeShots = 28)), Freshness.Fresh, null),
        )

    private fun withShots(
        periods: List<PeriodShots>,
        totals: ShotTotals? = ShotTotals(awayShots = 15, homeShots = 32),
    ) = fullDetail.copy(
        rightRail =
            Loadable(
                GameRightRail(shots = totals, shotsByPeriod = periods),
                Freshness.Fresh,
                null,
            ),
    )

    private fun withGoals(goals: List<GoalEvent>) =
        fullDetail.copy(landing = fullDetail.landing.copy(data = fullDetail.landing.data!!.copy(goals = goals)))

    @Suppress("LongParameterList")
    private fun goal(
        period: Int,
        time: String,
        isHome: Boolean,
        scorer: String = "A. Scorer",
        goalsToDate: Int? = null,
        assists: List<GoalAssist> = emptyList(),
        strength: GoalStrength = GoalStrength.EvenStrength,
        modifier: GoalModifier = GoalModifier.None,
        awayScore: Int? = null,
        homeScore: Int? = null,
    ) = GoalEvent(
        period = period,
        timeInPeriod = time,
        isHome = isHome,
        scorer = scorer,
        goalsToDate = goalsToDate,
        assists = assists,
        strength = strength,
        modifier = modifier,
        awayScore = awayScore,
        homeScore = homeScore,
    )

    private fun show(
        detail: GameDetail,
        fontScale: Float = 1f,
        shownGame: Game = game,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, fontScale)) {
                RouxTheme(dark = true) {
                    GameDetailScreen(
                        game = shownGame,
                        state = DetailUiState(detail = detail, refreshing = false, unexpectedFailure = false),
                        onBack = {},
                        onRetry = {},
                        zone = java.time.ZoneId.of("America/New_York"),
                    )
                }
            }
        }
    }

    @Test
    fun `landing supplies live moment missing from route snapshot`() {
        show(
            fullDetail.copy(
                landing =
                    fullDetail.landing.copy(
                        data =
                            GameLanding(
                                state = GameState.Live(),
                                period =
                                    net.montycasa.roux.domain.model
                                        .PeriodInfo(2, "REG", "12:34"),
                            ),
                    ),
            ),
            shownGame = game.copy(state = GameState.Scheduled, period = null, finalKind = null),
        )
        compose.onAllNodesWithText("2nd 12:34").assertCountEquals(1)
        compose.onAllNodesWithText("Live").assertCountEquals(0)
    }

    @Test
    fun `landing supplies final overtime missing from route snapshot`() {
        show(
            fullDetail.copy(
                landing =
                    fullDetail.landing.copy(
                        data =
                            GameLanding(
                                state = GameState.Final,
                                finalKind = FinalKind.OT,
                            ),
                    ),
            ),
            shownGame = game.copy(state = GameState.Live(), period = null, finalKind = null),
        )
        compose.onAllNodesWithText("Final/OT").assertCountEquals(1)
    }

    @Test
    fun `snapshot period remains when landing has no moment`() {
        show(
            fullDetail.copy(landing = fullDetail.landing.copy(data = GameLanding())),
            shownGame =
                game.copy(
                    state = GameState.Live(),
                    period =
                        net.montycasa.roux.domain.model
                            .PeriodInfo(2, "REG", "12:34"),
                ),
        )
        compose.onAllNodesWithText("2nd 12:34").assertCountEquals(1)
    }

    @Test
    fun `live period and clock appear once without duplicate status`() {
        show(
            fullDetail,
            shownGame =
                game.copy(
                    state = GameState.Live(),
                    period =
                        net.montycasa.roux.domain.model
                            .PeriodInfo(2, "REG", "12:34"),
                ),
        )
        compose.onAllNodesWithText("2nd 12:34").assertCountEquals(1)
        compose.onNodeWithTag("detail.hero.live.capsule").assertIsDisplayed()
        compose.onNodeWithText("LIVE").assertIsDisplayed()
        compose.onNodeWithText("12:34", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2nd", substring = true).assertIsDisplayed()
        compose
            .onNodeWithTag("detail.hero.live.capsule", useUnmergedTree = true)
            .assert(hasAnyAncestor(hasTestTag("detail.hero")))
        compose
            .onNodeWithText("2nd 12:34", useUnmergedTree = true)
            .assert(hasAnyAncestor(hasTestTag("detail.hero")))
    }

    @Test
    fun `live overtime is OT rather than fourth`() {
        show(
            fullDetail,
            shownGame =
                game.copy(
                    state = GameState.Live(),
                    period =
                        net.montycasa.roux.domain.model
                            .PeriodInfo(4, "OT", "03:21"),
                ),
        )
        compose.onNodeWithTag("detail.hero.live.capsule").assertIsDisplayed()
        compose.onNodeWithText("OT").assertIsDisplayed()
        compose.onNodeWithText("03:21").assertIsDisplayed()
        compose.onNodeWithText("OT 03:21").assertDoesNotExist()
        compose.onNodeWithText("period", substring = true).assertDoesNotExist()
        compose.onNodeWithText("4th", substring = true).assertDoesNotExist()
    }

    @Test
    fun `live intermission replaces the clock with the score row break wording`() {
        show(fullDetail, shownGame = game.copy(state = GameState.Live(), period = PeriodInfo(2, "REG", "12:34", true)))
        compose.onNodeWithTag("detail.hero.live.capsule").assertIsDisplayed()
        compose.onNodeWithText("Intermission").assertIsDisplayed()
        compose.onNodeWithText("12:34", substring = true).assertDoesNotExist()
    }

    @Test
    fun `final overtime is explicit`() = checkFinal(FinalKind.OT, "Final/OT")

    @Test
    fun `final shootout is explicit`() = checkFinal(FinalKind.SO, "Final/SO")

    @Test
    fun `final without kind is plain`() = checkFinal(null, "Final")

    private fun checkFinal(
        kind: FinalKind?,
        expected: String,
    ) {
        show(fullDetail, shownGame = game.copy(finalKind = kind))
        compose.onNodeWithTag("detail.hero.live.capsule").assertDoesNotExist()
        compose.onAllNodesWithText(expected).assertCountEquals(1)
        compose.onAllNodesWithText("FINAL").assertCountEquals(0)
    }

    @Test
    fun `scheduled shows local start without stale period`() {
        show(
            fullDetail,
            shownGame =
                game.copy(
                    state = GameState.Scheduled,
                    startTimeUtc = kotlin.time.Instant.parse("2026-10-26T23:00:00Z"),
                    period =
                        net.montycasa.roux.domain.model
                            .PeriodInfo(2, "REG", "12:34"),
                ),
        )
        compose.onNodeWithTag("detail.hero.live.capsule").assertDoesNotExist()
        compose.onAllNodesWithText("Puck drops 7:00pm").assertCountEquals(1)
        compose.onNodeWithText("12:34", substring = true).assertDoesNotExist()
        compose.onNodeWithText("2nd", substring = true).assertDoesNotExist()
    }

    @Test
    fun `total uses score even when periods disagree`() {
        show(
            fullDetail.copy(landing = fullDetail.landing.copy(data = GameLanding(periodScoring = listOf(PeriodScoring(1, 4, 4))))),
            shownGame = game.copy(awayScore = 3, homeScore = 2),
        )
        assertEquals("3", labelOf("detail.period.CBJ.T"))
        assertEquals("2", labelOf("detail.period.PHI.T"))
    }

    @Test
    fun `five periods combine all overtime into one of five columns`() {
        show(
            fullDetail.copy(
                landing =
                    fullDetail.landing.copy(
                        data =
                            GameLanding(
                                periodScoring =
                                    (1..5).map {
                                        PeriodScoring(it, 1, 1)
                                    },
                            ),
                    ),
            ),
            shownGame = game.copy(awayScore = 5, homeScore = 5),
        )
        listOf("1", "2", "3", "OT", "T").forEach {
            assertEquals(it, labelOf("detail.period.header.$it"))
        }
        compose
            .onAllNodes(
                SemanticsMatcher("period table heading") {
                    it.config.contains(SemanticsProperties.TestTag) &&
                        it.config[SemanticsProperties.TestTag].startsWith("detail.period.header.")
                },
            ).assertCountEquals(5)
        compose.onNodeWithTag("detail.period.header.5").assertDoesNotExist()
        assertEquals("2", labelOf("detail.period.CBJ.4"))
        assertEquals("2", labelOf("detail.period.PHI.4"))
        assertEquals("5", labelOf("detail.period.CBJ.T"))
        assertEquals("5", labelOf("detail.period.PHI.T"))
    }

    @Test
    fun `missing score is a dash`() {
        show(fullDetail, shownGame = game.copy(homeScore = null, awayScore = null))
        assertEquals("—", labelOf("detail.period.CBJ.T"))
        assertEquals("—", labelOf("detail.period.PHI.T"))
    }

    @Test
    fun `shots card reads every period and the published total`() {
        show(
            withShots(
                listOf(
                    PeriodShots(1, "REG", away = 7, home = 2),
                    PeriodShots(2, "REG", away = 5, home = 13),
                    PeriodShots(3, "REG", away = 1, home = 16),
                ),
            ),
        )

        compose.onNodeWithTag("detail.section.shots").assertExists()
        listOf("1", "2", "3", "T").forEach { assertEquals(it, labelOf("detail.shots.header.$it")) }
        assertEquals("7", labelOf("detail.shots.CBJ.1"))
        assertEquals("5", labelOf("detail.shots.CBJ.2"))
        assertEquals("1", labelOf("detail.shots.CBJ.3"))
        assertEquals("15", labelOf("detail.shots.CBJ.T"))
        assertEquals("2", labelOf("detail.shots.PHI.1"))
        assertEquals("13", labelOf("detail.shots.PHI.2"))
        assertEquals("16", labelOf("detail.shots.PHI.3"))
        assertEquals("32", labelOf("detail.shots.PHI.T"))
    }

    @Test
    fun `shots card sits between scoring and game stats`() {
        show(
            withShots(
                listOf(
                    PeriodShots(1, "REG", away = 7, home = 2),
                    PeriodShots(2, "REG", away = 5, home = 13),
                    PeriodShots(3, "REG", away = 1, home = 16),
                ),
            ),
        )

        val tops =
            listOf("scoring", "shots", "stats").map {
                compose
                    .onNodeWithTag("detail.section.$it")
                    .getUnclippedBoundsInRoot()
                    .top.value
            }
        assertTrue(tops.zipWithNext().all { (a, b) -> a < b })
    }

    /** The total is the league's, not the cells' sum: the cells here add to 9 and 9, and T must still say 15 and 32. */
    @Test
    fun `shots T shows the published total when the cells disagree`() {
        show(
            withShots(
                listOf(
                    PeriodShots(1, "REG", away = 3, home = 3),
                    PeriodShots(2, "REG", away = 4, home = 4),
                    PeriodShots(3, "REG", away = 2, home = 2),
                ),
                totals = ShotTotals(awayShots = 15, homeShots = 32),
            ),
        )

        assertEquals("15", labelOf("detail.shots.CBJ.T"))
        assertEquals("32", labelOf("detail.shots.PHI.T"))
    }

    @Test
    fun `an overtime game gets one combined shots OT column`() {
        show(
            withShots(
                listOf(
                    PeriodShots(1, "REG", away = 3, home = 3),
                    PeriodShots(2, "REG", away = 4, home = 4),
                    PeriodShots(3, "REG", away = 2, home = 2),
                    PeriodShots(4, "OT", away = 1, home = 2),
                    PeriodShots(5, "OT2", away = 2, home = 1),
                ),
                totals = ShotTotals(awayShots = 12, homeShots = 12),
            ),
        )

        listOf("1", "2", "3", "OT", "T").forEach { assertEquals(it, labelOf("detail.shots.header.$it")) }
        compose
            .onAllNodes(
                SemanticsMatcher("shots period table heading") {
                    it.config.contains(SemanticsProperties.TestTag) &&
                        it.config[SemanticsProperties.TestTag].startsWith("detail.shots.header.")
                },
            ).assertCountEquals(5)
        compose.onNodeWithTag("detail.shots.header.5").assertDoesNotExist()
        assertEquals("3", labelOf("detail.shots.CBJ.4"))
        assertEquals("3", labelOf("detail.shots.PHI.4"))
    }

    @Test
    fun `empty shots by period draws no shots card even with published totals`() {
        show(withShots(emptyList(), totals = ShotTotals(awayShots = 15, homeShots = 32)))

        compose.onNodeWithTag("detail.section.shots").assertDoesNotExist()
        compose.onNodeWithText("SHOTS BY PERIOD").assertDoesNotExist()
    }

    @Test
    fun `scoring summary runs periods ascending and first goal first inside a period`() {
        show(
            withGoals(
                listOf(
                    goal(period = 2, time = "08:00", isHome = true),
                    goal(period = 1, time = "05:00", isHome = false),
                    goal(period = 1, time = "15:00", isHome = false),
                ),
            ),
        )

        val first = compose.onNodeWithTag("detail.scoring.period.1").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("detail.scoring.period.2").getUnclippedBoundsInRoot()
        assertTrue(first.top.value < second.top.value)
        assertEquals("1ST", labelOf("detail.scoring.period.1"))
        assertEquals("2ND", labelOf("detail.scoring.period.2"))

        // Rows are numbered in display order: the first goal of the first period (the most time remaining) comes
        // first, then the later 05:00 goal, then the second period's goal.
        val rows =
            listOf("detail.scoring.row.00", "detail.scoring.row.01", "detail.scoring.row.02").map {
                compose
                    .onNodeWithTag(it)
                    .getUnclippedBoundsInRoot()
                    .top.value
            }
        assertTrue(rows.zipWithNext().all { (a, b) -> a < b })
        assertTrue(mergedLabelOf("detail.scoring.row.00").contains("15:00"))
        assertTrue(mergedLabelOf("detail.scoring.row.01").contains("05:00"))
        assertTrue(mergedLabelOf("detail.scoring.row.02").contains("08:00"))
    }

    @Test
    fun `a scoring row carries its own running score not the final score`() {
        show(
            withGoals(
                listOf(
                    goal(period = 1, time = "10:00", isHome = false, awayScore = 2, homeScore = 0),
                    goal(period = 1, time = "15:00", isHome = false, awayScore = 1, homeScore = 0),
                    goal(period = 2, time = "05:00", isHome = true, awayScore = 2, homeScore = 1),
                ),
            ),
            shownGame = game.copy(awayScore = 3, homeScore = 1),
        )

        // First goal first, so the 15:00 goal owns the first row and its own 1-0, not the 3-1 on the board.
        val firstRow = mergedLabelOf("detail.scoring.row.00")
        assertTrue(firstRow.contains("1–0"))
        assertTrue(!firstRow.contains("3–1"))
        assertTrue(mergedLabelOf("detail.scoring.row.02").contains("2–1"))
    }

    @Test
    fun `unassisted goals and missing season counts are not invented`() {
        show(
            withGoals(
                listOf(
                    goal(
                        period = 1,
                        time = "15:00",
                        isHome = false,
                        scorer = "K. Marchenko",
                        goalsToDate = 12,
                        assists = listOf(GoalAssist("J. Voracek", 4), GoalAssist("Z. Werenski", null)),
                        awayScore = 1,
                        homeScore = 0,
                    ),
                    goal(period = 1, time = "10:00", isHome = true, scorer = "A. Fantilli", awayScore = 1, homeScore = 1),
                ),
            ),
        )

        val assisted = mergedLabelOf("detail.scoring.row.00")
        assertTrue(assisted.contains("(12)"))
        assertTrue(assisted.contains("CBJ · A: J. Voracek (4), Z. Werenski"))
        // Werenski's count is absent in the feed, so it is absent here rather than printed as (null) or (0).
        assertTrue(!assisted.contains("Z. Werenski ("))

        val unassisted = mergedLabelOf("detail.scoring.row.01")
        assertTrue(unassisted.contains("PHI · Unassisted"))
        assertTrue(!unassisted.contains("("))
    }

    @Test
    fun `empty goal list draws no scoring summary card`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.section.scoring-summary").assertDoesNotExist()
        compose.onNodeWithText("SCORING SUMMARY").assertDoesNotExist()
    }

    @Test
    fun `all overtime periods share one OT header`() {
        show(
            withGoals(
                listOf(
                    goal(period = 4, time = "02:22", isHome = false),
                    goal(period = 5, time = "01:15", isHome = true),
                ),
            ),
        )

        compose.onNodeWithTag("detail.scoring.period.4").assertExists()
        compose.onNodeWithTag("detail.scoring.period.5").assertDoesNotExist()
        assertEquals("OT", labelOf("detail.scoring.period.4"))
        compose
            .onAllNodes(
                SemanticsMatcher("scoring period headings") {
                    it.config.contains(SemanticsProperties.TestTag) &&
                        it.config[SemanticsProperties.TestTag].startsWith("detail.scoring.period.")
                },
            ).assertCountEquals(1)
        // Latest extra period first, then its own largest time remaining first.
        assertTrue(mergedLabelOf("detail.scoring.row.00").contains("01:15"))
        assertTrue(mergedLabelOf("detail.scoring.row.01").contains("02:22"))
    }

    @Test
    fun `strength and modifier tags print in strength then modifier order`() {
        show(
            withGoals(
                listOf(
                    goal(
                        period = 1,
                        time = "15:00",
                        isHome = false,
                        strength = GoalStrength.ShortHanded,
                        modifier = GoalModifier.EmptyNet,
                    ),
                    goal(period = 1, time = "10:00", isHome = true, strength = GoalStrength.PowerPlay),
                    goal(
                        period = 1,
                        time = "05:00",
                        isHome = false,
                        strength = GoalStrength.EvenStrength,
                        modifier = GoalModifier.PenaltyShot,
                    ),
                ),
            ),
        )

        val both = mergedLabelOf("detail.scoring.row.00")
        assertTrue(both.contains("SH"))
        assertTrue(both.contains("EN"))
        assertTrue(both.indexOf("SH") < both.indexOf("EN"))
        assertTrue(mergedLabelOf("detail.scoring.row.01").contains("PP"))
        assertTrue(mergedLabelOf("detail.scoring.row.02").contains("PS"))
        assertTrue(!mergedLabelOf("detail.scoring.row.02").contains("EV"))
    }

    @Test
    fun `the scoring summary sits after shots and before game stats`() {
        show(
            withGoals(listOf(goal(period = 1, time = "15:00", isHome = false))).let { detail ->
                detail.copy(
                    rightRail =
                        Loadable(
                            GameRightRail(
                                shots = ShotTotals(awayShots = 15, homeShots = 32),
                                shotsByPeriod = listOf(PeriodShots(1, "REG", away = 7, home = 8)),
                            ),
                            Freshness.Fresh,
                            null,
                        ),
                )
            },
        )

        val tops =
            listOf("scoring", "shots", "scoring-summary", "stats").map {
                compose
                    .onNodeWithTag("detail.section.$it")
                    .getUnclippedBoundsInRoot()
                    .top.value
            }
        assertTrue(tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `scoring summary text stays at or above twelve sp at font scale two`() {
        show(
            withGoals(
                listOf(
                    goal(
                        period = 1,
                        time = "15:00",
                        isHome = false,
                        goalsToDate = 12,
                        assists = listOf(GoalAssist("J. Voracek", 4)),
                        strength = GoalStrength.PowerPlay,
                    ),
                ),
            ),
            fontScale = 2f,
        )

        val card = compose.onNodeWithTag("detail.section.scoring-summary", useUnmergedTree = true).fetchSemanticsNode()
        val sizes = textSizes(card)
        assertTrue("no text nodes found in the scoring summary", sizes.isNotEmpty())
        sizes.forEach { size -> assertTrue("text at $size sp is under the 12 sp floor", size >= 12f) }
    }

    @Test
    fun `first tab is Game and Summary is absent`() {
        show(fullDetail)
        assertEquals("Game", labelOf("detail.tab.Game.label"))
        compose.onAllNodesWithText("Summary", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a failed landing leaves the stats and the player cards standing`() {
        val error = LoadError.Http(503)
        show(
            fullDetail.copy(
                landing = Loadable(null, Freshness.Missing, error),
            ),
        )

        // The two failures sit inside the cards that failed, and say so there - the rail's card is untouched.
        compose.onNodeWithText("Scoring could not be loaded").assertExists()
        compose.onNodeWithText("Three stars could not be loaded").assertExists()
        compose.onNodeWithText("Game stats could not be loaded").assertDoesNotExist()
    }

    @Test
    fun `a failed right rail leaves the scoring card standing`() {
        show(
            fullDetail.copy(
                rightRail = Loadable(null, Freshness.Missing, LoadError.Http(503)),
            ),
        )

        compose.onNodeWithText("Game stats could not be loaded").assertExists()
        // A failed rail does not reach across and blank the landing's cards.
        compose.onNodeWithText("Scoring could not be loaded").assertDoesNotExist()
        compose.onNodeWithText("Three stars could not be loaded").assertDoesNotExist()
    }

    @Test
    fun `a failed boxscore takes the player cards with it and nothing else`() {
        show(
            fullDetail.copy(
                boxscore = Loadable(null, Freshness.Missing, LoadError.Parse),
            ),
        )

        compose.onNodeWithTag("detail.section.scoring").assertExists()
        compose.onNodeWithTag("detail.section.stats").assertExists()
        compose.onNodeWithTag("detail.tab.CBJ").performClick()
        // One card now carries the whole roster, so its failure is the failure of both lists.
        compose.onNodeWithText("Player stats could not be loaded").assertExists()
        compose.onNodeWithTag("detail.section.goalies").assertDoesNotExist()
        compose.onNodeWithTag("detail.section.skaters").assertDoesNotExist()
    }

    @Test
    fun `each period heading sits over its own column of numbers`() {
        show(fullDetail)

        listOf("1", "2", "T").forEach { label ->
            val header = compose.onNodeWithTag("detail.period.header.$label").getUnclippedBoundsInRoot()
            val awayCell = compose.onNodeWithTag("detail.period.CBJ.$label").getUnclippedBoundsInRoot()
            val homeCell = compose.onNodeWithTag("detail.period.PHI.$label").getUnclippedBoundsInRoot()

            // The heading is centred over the column, so its centre is the column's centre - not a fixed 56 dp offset.
            val headerCentre = (header.left.value + header.right.value) / 2f
            assertEquals(headerCentre, (awayCell.left.value + awayCell.right.value) / 2f, 1f)
            assertEquals(headerCentre, (homeCell.left.value + homeCell.right.value) / 2f, 1f)
        }
    }

    @Test
    fun `the crest, the club and the score share a line and the city hangs above it`() {
        show(fullDetail)

        listOf("away", "home").forEach { side ->
            val crest = compose.onNodeWithTag("detail.hero.$side.crest").getUnclippedBoundsInRoot()
            val name = compose.onNodeWithTag("detail.hero.$side.name").getUnclippedBoundsInRoot()
            val score = compose.onNodeWithTag("detail.hero.$side.score").getUnclippedBoundsInRoot()
            val city = compose.onNodeWithTag("detail.hero.$side.city").getUnclippedBoundsInRoot()

            val crestCentre = (crest.top.value + crest.bottom.value) / 2f
            assertEquals(crestCentre, (name.top.value + name.bottom.value) / 2f, 2f)
            assertEquals(crestCentre, (score.top.value + score.bottom.value) / 2f, 2f)
            // Above the line, and never alongside it.
            assertTrue(city.bottom.value <= name.top.value + 1f)
            assertEquals(city.left.value, name.left.value, 1f)
        }
    }

    // The pills are clickable, so their contents merge into the clickable node: read the unmerged tree.
    private fun labelOf(tag: String): String =
        compose
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    /** A scoring row merges its two lines, so the merged tree is where its whole text lives. */
    private fun mergedLabelOf(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    /** Every resolvable font size under a node, from the unmerged tree, so a label style cannot hide under a parent. */
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

    @Test
    fun `the club pills carry a crest and the club's name, not its abbreviation`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.tab.PHI").assertIsDisplayed()
        compose.onNodeWithTag("detail.tab.PHI.crest", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Flyers", labelOf("detail.tab.PHI.label"))
        assertEquals("Blue Jackets", labelOf("detail.tab.CBJ.label"))
        // The summary pill has no club, so it takes no crest.
        assertEquals("Game", labelOf("detail.tab.Game.label"))
    }

    @Test
    fun `the players card is headed by the crest and the club's name`() {
        show(fullDetail)
        compose.onNodeWithTag("detail.tab.PHI").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("FLYERS PLAYER STATS").assertIsDisplayed()
        val crest = compose.onNodeWithTag("detail.section.players.crest").getUnclippedBoundsInRoot()
        assertEquals(30f, crest.bottom.value - crest.top.value, 1f)
    }

    @Test
    fun `the club name in the scoreboard is set larger than body text`() {
        show(fullDetail)

        val name = compose.onNodeWithTag("detail.hero.home.name").getUnclippedBoundsInRoot()
        // titleLarge, not titleMedium: the club is the thing the eye should land on first.
        assertTrue("club name is only ${name.bottom.value - name.top.value} dp tall", name.bottom.value - name.top.value >= 20f)
    }

    @Test
    fun `the crests in the scoring rows and the pills are big enough to read`() {
        show(fullDetail)

        val rowCrest = compose.onNodeWithTag("detail.scoring.crest.PHI").getUnclippedBoundsInRoot()
        assertEquals(24f, rowCrest.bottom.value - rowCrest.top.value, 1f)

        val pillCrest = compose.onNodeWithTag("detail.tab.PHI.crest", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(26f, pillCrest.bottom.value - pillCrest.top.value, 1f)
    }

    @Test
    fun `each scoring row carries its club crest`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.scoring.crest.CBJ").assertIsDisplayed()
        compose.onNodeWithTag("detail.scoring.crest.PHI").assertIsDisplayed()
    }

    @Test
    fun `the club name stays put while the stat columns scroll`() {
        show(fullDetail)

        // The rosters live on a club's own destination, so open one before measuring.
        compose.onNodeWithTag("detail.tab.PHI").performClick()
        compose.waitForIdle()

        val before = compose.onNodeWithText("C. Talbot").getUnclippedBoundsInRoot()
        val headerBefore = compose.onNodeWithText("SA").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("detail.section.goalies").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val after = compose.onNodeWithText("C. Talbot").getUnclippedBoundsInRoot()
        val headerAfter = compose.onNodeWithText("SA").getUnclippedBoundsInRoot()

        // The stats really moved - otherwise "the name did not move" proves nothing.
        assertTrue(headerAfter.left.value < headerBefore.left.value)

        // The frozen column is the point: reaching a column must never cost you sight of whose row it is.
        assertEquals(before.left.value, after.left.value, 0.5f)
        compose.onNodeWithText("C. Talbot").assertIsDisplayed()
    }

    @Test
    fun `swiping the page moves between the summary and the two clubs`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        // Page 1 is the away club, so its roster heading is on screen.
        compose.onNodeWithText("BLUE JACKETS PLAYER STATS").assertIsDisplayed()

        compose.onNodeWithTag("detail.pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("SCORING BY PERIOD").assertIsDisplayed()
    }

    @Test
    fun `the pills follow a swipe`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        // The selected pill is the picture of where you are, so it has to move with the page.
        compose.onNodeWithTag("detail.tab.CBJ").assertIsSelected()
    }

    @Test
    fun `the back control and the three destinations stay pressable`() {
        show(fullDetail)

        compose.onNodeWithTag("detail.back").assertTouchHeightIsEqualTo(48.dp)
        listOf("detail.tab.Game", "detail.tab.CBJ", "detail.tab.PHI").forEach { tag ->
            // reachTarget pins the 48 dp minimum, so a tab is pressable even when its visual is shorter.
            compose.onNodeWithTag(tag).assertTouchHeightIsEqualTo(48.dp)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `the controls survive the narrow size at double the font scale`() {
        show(fullDetail, fontScale = 2f)

        compose.onNodeWithTag("detail.back").assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("detail.tab.Game").assertTouchHeightIsEqualTo(48.dp)
    }
}

/** The bar arithmetic, away from Compose - this is where the floor either holds or does not. */
class BarSharesTest {
    @Test
    fun `a one-sided game keeps a sliver for the side that never scored`() {
        val (away, home) = barShares(awayValue = 1, homeValue = 0, trackDp = 371f)

        // The floor wins over the raw share: 371 - 13 leaves the scoring side the rest.
        assertEquals(13f, home * 371f, 0.01f)
        assertEquals(371f - 13f, away * 371f, 0.01f)
        assertEquals(1f, away + home, 0.0001f)
    }

    @Test
    fun `an even split is half the track each`() {
        val (away, home) = barShares(awayValue = 3, homeValue = 3, trackDp = 360f)

        assertEquals(0.5f, away, 0.0001f)
        assertEquals(0.5f, home, 0.0001f)
    }

    @Test
    fun `proportions follow the goals, not the chance count`() {
        val (away, home) = barShares(awayValue = 2, homeValue = 1, trackDp = 371f)

        assertEquals(2f / 3f, away, 0.0001f)
        assertEquals(1f / 3f, home, 0.0001f)
    }

    @Test
    fun `no power play goals at all draws no fill`() {
        val (away, home) = barShares(awayValue = 0, homeValue = 0, trackDp = 371f)

        assertEquals(0f, away, 0f)
        assertEquals(0f, home, 0f)
    }

    @Test
    fun `neither side can vanish on a lopsided row`() {
        listOf(0 to 9, 9 to 0, 1 to 40, 40 to 1).forEach { (awayGoals, homeGoals) ->
            val (away, home) = barShares(awayGoals, homeGoals, trackDp = 300f)
            assertTrue(away * 300f >= MIN_BAR_SIDE_DP - 0.01f)
            assertTrue(home * 300f >= MIN_BAR_SIDE_DP - 0.01f)
            assertEquals(1f, away + home, 0.0001f)
        }
    }

    @Test
    fun `ice time reads as minutes and seconds, and nothing as a dash`() {
        assertEquals("62:22", formatIceTime(3742))
        assertEquals("0:07", formatIceTime(7))
        assertEquals("—", formatIceTime(0))
    }

    @Test
    fun `an average keeps its decimals even when the value is whole`() {
        assertEquals("2.89", formatAverage(2.886f.toDouble(), 2))
        assertEquals("3.00", formatAverage(3.0, 2))
        assertEquals("92.0", formatAverage(91.96, 1))
    }
}

/** The stats card is judged on the payload the league actually sends, which is ten rows in three notations. */
class ComparisonRowsTest {
    private val captured =
        GameRightRail(
            shots = ShotTotals(awayShots = 15, homeShots = 32),
            powerPlay = PowerPlaySummary(awayGoals = 0, awayChances = 1, homeGoals = 0, homeChances = 2),
            teamStats =
                listOf(
                    TeamStat("sog", "15", "32"),
                    TeamStat("faceoffWinningPctg", "0.56", "0.44"),
                    TeamStat("faceoffWins", "28/50", "22/50"),
                    TeamStat("powerPlay", "0/1", "0/2"),
                    TeamStat("powerPlayPctg", "0.0", "0.0"),
                    TeamStat("pim", "6", "4"),
                    TeamStat("hits", "18", "15"),
                    TeamStat("blockedShots", "19", "12"),
                    TeamStat("giveaways", "13", "15"),
                    TeamStat("takeaways", "5", "1"),
                ),
        )

    @Test
    fun `every published stat becomes a row, and the duplicate notations do not`() {
        val labels = captured.comparisonRows().map { it.label }

        assertEquals(
            listOf("Shots", "Power play", "Faceoffs", "Penalty minutes", "Hits", "Blocked shots", "Giveaways", "Takeaways"),
            labels,
        )
    }

    @Test
    fun `a faceoff fraction of one is read as a percentage, not as fifty-six`() {
        val faceoffs = captured.comparisonRows().first { it.label == "Faceoffs" }

        assertEquals("56%", faceoffs.awayText)
        assertEquals("44%", faceoffs.homeText)
        assertEquals(56, faceoffs.awayValue)
    }

    @Test
    fun `a made over chances row is drawn from the goals, and keeps its own notation`() {
        val powerPlay = captured.comparisonRows().first { it.label == "Power play" }

        assertEquals("0/1", powerPlay.awayText)
        assertEquals("0/2", powerPlay.homeText)
        assertEquals(0, powerPlay.awayValue)
        assertEquals(0, powerPlay.homeValue)
    }
}
