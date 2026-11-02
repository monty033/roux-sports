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
import androidx.compose.ui.test.onAllNodesWithTag
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
import net.montycasa.roux.ui.AuditStates
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
    fun `period three of the real game shows its literal elapsed clocks in order`() {
        show(
            withGoals(AuditStates.fullGoals.filter { it.period == 3 }),
            shownGame =
                game.copy(
                    away = TeamRef(TeamKey("UTA"), name = "Mammoth"),
                    awayScore = 3,
                    homeScore = 6,
                ),
        )

        assertEquals("3rd", labelOf("detail.scoring.period.3.label"))
        // The feed's own elapsed clocks for period 3, printed verbatim, first goal first.
        val expected = listOf("01:02", "05:35", "16:00", "17:07", "18:26")
        val actual = expected.indices.map { index -> rowTime(index) }
        assertEquals(expected, actual)
    }

    @Test
    fun `the card prints the feed's elapsed string verbatim`() {
        show(withGoals(listOf(goal(period = 1, time = "9:30", isHome = false, awayScore = 1, homeScore = 0))))

        // Not 09:30 and not a derived remaining value: exactly the string the payload sent.
        assertEquals("9:30", rowTime(0))
    }

    @Test
    fun `the full audit fixture rises in score as its elapsed clock climbs`() {
        assertClockAndScoreInvariant(name = "detail.full", goals = AuditStates.fullGoals)
    }

    @Test
    fun `the live audit fixture rises in score as its elapsed clock climbs`() {
        assertClockAndScoreInvariant(name = "detail.live", goals = AuditStates.liveGoals)
    }

    @Test
    fun `non-padded elapsed values sort numerically and climb down the card`() {
        val nonPadded =
            listOf(
                goal(period = 1, time = "9:30", isHome = false, awayScore = 1, homeScore = 0),
                goal(period = 1, time = "14:22", isHome = true, awayScore = 1, homeScore = 1),
                goal(period = 1, time = "16:00", isHome = false, awayScore = 2, homeScore = 1),
            )

        assertClockAndScoreInvariant(name = "non-padded", goals = nonPadded)
    }

    private fun assertClockAndScoreInvariant(
        name: String,
        goals: List<GoalEvent>,
    ) {
        show(withGoals(goals))
        scoringGroups(goals).forEach { group ->
            group.goals.zipWithNext().forEach { (first, next) ->
                val firstClock = requireNotNull(clockSeconds(rowTime(first.index)))
                val nextClock = requireNotNull(clockSeconds(rowTime(next.index)))
                assertTrue(
                    "$name ${group.label}: elapsed clock fell from ${first.goal.timeInPeriod} " +
                        "to ${next.goal.timeInPeriod}",
                    nextClock >= firstClock,
                )
                val firstAway = labelOf("detail.scoring.row.${first.index.padded()}.away").toInt()
                val nextAway = labelOf("detail.scoring.row.${next.index.padded()}.away").toInt()
                val firstHome = labelOf("detail.scoring.row.${first.index.padded()}.home").toInt()
                val nextHome = labelOf("detail.scoring.row.${next.index.padded()}.home").toInt()
                assertTrue("$name ${group.label}: away score fell", nextAway >= firstAway)
                assertTrue("$name ${group.label}: home score fell", nextHome >= firstHome)
            }
        }
    }

    @Test
    fun `a scoring row carries its own running score not the final score`() {
        show(
            withGoals(
                listOf(
                    goal(period = 1, time = "10:00", isHome = false, awayScore = 1, homeScore = 0),
                    goal(period = 1, time = "15:00", isHome = false, awayScore = 2, homeScore = 0),
                    goal(period = 2, time = "05:00", isHome = true, awayScore = 2, homeScore = 1),
                ),
            ),
            shownGame = game.copy(awayScore = 3, homeScore = 1),
        )

        // First goal first, so the 10:00 goal owns the first row and its own 1-0, not the 3-1 on the board.
        assertEquals("1", labelOf("detail.scoring.row.00.away"))
        assertEquals("0", labelOf("detail.scoring.row.00.home"))
        assertEquals("2", labelOf("detail.scoring.row.02.away"))
        assertEquals("1", labelOf("detail.scoring.row.02.home"))
    }

    @Test
    fun `each goal row carries only its scorer's crest`() {
        show(
            withGoals(
                listOf(
                    goal(period = 1, time = "10:00", isHome = false, awayScore = 1, homeScore = 0),
                    goal(period = 1, time = "15:00", isHome = true, awayScore = 1, homeScore = 1),
                ),
            ),
        )

        val awayCrest =
            compose.onNodeWithTag("detail.scoring.row.00.crest.CBJ", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(44f, awayCrest.bottom.value - awayCrest.top.value, 1f)
        compose.onNodeWithTag("detail.scoring.row.00.crest.PHI", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail.scoring.row.01.crest.PHI", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("detail.scoring.row.01.crest.CBJ", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the away and home score columns hold the numbers under their own club`() {
        show(
            withGoals(
                listOf(
                    goal(period = 1, time = "10:00", isHome = false, awayScore = 3, homeScore = 1),
                    goal(period = 1, time = "15:00", isHome = true, awayScore = 3, homeScore = 2),
                ),
            ),
        )

        assertEquals("3", labelOf("detail.scoring.row.00.away"))
        assertEquals("1", labelOf("detail.scoring.row.00.home"))
        assertEquals("3", labelOf("detail.scoring.row.01.away"))
        assertEquals("2", labelOf("detail.scoring.row.01.home"))

        val awayHeader = compose.onNodeWithTag("detail.scoring.period.1.away.abbrev").getUnclippedBoundsInRoot()
        val homeHeader = compose.onNodeWithTag("detail.scoring.period.1.home.abbrev").getUnclippedBoundsInRoot()
        val awayScore =
            compose.onNodeWithTag("detail.scoring.row.01.away", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val homeScore =
            compose.onNodeWithTag("detail.scoring.row.01.home", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(awayHeader.right.value <= homeHeader.left.value)
        assertEquals(awayHeader.left.value, awayScore.left.value, 1f)
        assertEquals(awayHeader.right.value, awayScore.right.value, 1f)
        assertEquals(homeHeader.left.value, homeScore.left.value, 1f)
        assertEquals(homeHeader.right.value, homeScore.right.value, 1f)
    }

    @Test
    fun `no global header remains and each period heading names both clubs`() {
        show(withGoals(listOf(goal(period = 1, time = "15:00", isHome = false))))

        assertEquals("1st", labelOf("detail.scoring.period.1.label"))
        assertEquals("CBJ", labelOf("detail.scoring.period.1.away.abbrev"))
        assertEquals("PHI", labelOf("detail.scoring.period.1.home.abbrev"))
        compose.onNodeWithTag("detail.scoring.header.away").assertDoesNotExist()
        compose.onNodeWithTag("detail.scoring.header.home").assertDoesNotExist()

        val heading = compose.onNodeWithTag("detail.scoring.period.1").getUnclippedBoundsInRoot()
        val firstRow = compose.onNodeWithTag("detail.scoring.row.00").getUnclippedBoundsInRoot()
        assertTrue(heading.bottom.value <= firstRow.top.value + 1f)
        // The heading is not a goal: the only row is 00 and there is no 01 for it to occupy.
        compose.onNodeWithTag("detail.scoring.row.00").assertExists()
        compose.onNodeWithTag("detail.scoring.row.01").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `score columns and heading abbreviations fit at 360 dp font scale two`() {
        show(
            withGoals(listOf(goal(period = 1, time = "15:00", isHome = false, awayScore = 3, homeScore = 1))),
            fontScale = 2f,
        )
        assertScoreColumnsFit()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `score columns and heading abbreviations fit at 360 dp font scale one point three`() {
        show(
            withGoals(listOf(goal(period = 1, time = "15:00", isHome = false, awayScore = 3, homeScore = 1))),
            fontScale = 1.3f,
        )
        assertScoreColumnsFit()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
    fun `score columns and heading abbreviations fit at 360 dp font scale one`() {
        show(
            withGoals(listOf(goal(period = 1, time = "15:00", isHome = false, awayScore = 3, homeScore = 1))),
            fontScale = 1f,
        )
        assertScoreColumnsFit()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
    fun `score columns and heading abbreviations fit at 411 dp font scale two`() {
        show(
            withGoals(listOf(goal(period = 1, time = "15:00", isHome = false, awayScore = 3, homeScore = 1))),
            fontScale = 2f,
        )
        assertScoreColumnsFit()
    }

    private fun assertScoreColumnsFit() {
        listOf(
            "detail.scoring.period.1.away.abbrev",
            "detail.scoring.period.1.home.abbrev",
            "detail.scoring.row.00.away",
            "detail.scoring.row.00.home",
        ).forEach { tag ->
            val layout = textLayout(tag)
            assertTrue(
                "$tag overflowed its column: size=${layout.size} lines=${layout.lineCount} " +
                    "text=${layout.layoutInput.text.text} constraints=${layout.layoutInput.constraints}",
                !layout.hasVisualOverflow,
            )
        }
    }

    @Test
    fun `the name line parenthesises the season count and the assist line owns its own text`() {
        show(
            withGoals(
                listOf(
                    goal(
                        period = 1,
                        time = "10:00",
                        isHome = false,
                        scorer = "Kirill Marchenko",
                        goalsToDate = 12,
                        assists = listOf(GoalAssist("Jakub Voracek", 4), GoalAssist("Zach Werenski", null)),
                        awayScore = 1,
                        homeScore = 0,
                    ),
                    goal(period = 1, time = "15:00", isHome = true, scorer = "Adam Fantilli", awayScore = 1, homeScore = 1),
                ),
            ),
        )

        val nameLine = rowSentence(0)
        assertTrue(nameLine.startsWith("Kirill Marchenko (12)"))
        assertTrue(!nameLine.contains("Kirill Marchenko 12"))
        assertTrue(!nameLine.contains("Assists:"))

        val assists = labelOf("detail.scoring.row.00.assists")
        assertEquals("Assists: Jakub Voracek (4), Zach Werenski", assists)
        assertTrue(!assists.startsWith("("))
        assertTrue(!assists.contains("(Assists:"))

        // A null count on an assist leaves that name bare rather than printing (null) or (0).
        assertTrue(!assists.contains("Zach Werenski ("))
    }

    @Test
    fun `an unassisted goal has no assist line and a missing season count prints no parentheses`() {
        show(
            withGoals(
                listOf(goal(period = 1, time = "10:00", isHome = true, scorer = "Matvei Michkov", awayScore = 0, homeScore = 1)),
            ),
        )

        assertEquals("Matvei Michkov", rowSentence(0))
        assertTrue(!rowSentence(0).contains("("))
        assertTrue(!rowSentence(0).contains("()"))
        compose.onNodeWithTag("detail.scoring.row.00.assists").assertDoesNotExist()
    }

    @Test
    fun `the row prints full first and last names`() {
        show(withGoals(AuditStates.fullGoals))

        assertTrue(rowSentence(0).startsWith("Matvei Michkov (10)"))
        assertTrue(!rowSentence(0).contains("M. Michkov"))
        assertTrue(!rowSentence(0).contains("Matvei Michkov 10"))
    }

    @Test
    fun `strength is prose and the abbreviation codes are gone`() {
        show(
            withGoals(
                listOf(
                    goal(
                        period = 1,
                        time = "05:00",
                        isHome = false,
                        strength = GoalStrength.ShortHanded,
                        modifier = GoalModifier.EmptyNet,
                    ),
                    goal(period = 1, time = "10:00", isHome = true, strength = GoalStrength.PowerPlay),
                    goal(
                        period = 1,
                        time = "15:00",
                        isHome = false,
                        strength = GoalStrength.EvenStrength,
                        modifier = GoalModifier.PenaltyShot,
                    ),
                ),
            ),
        )

        val both = rowSentence(0)
        assertTrue(both.contains("short-handed empty net"))
        assertTrue(!both.contains("(short-handed)"))
        assertTrue(!both.contains("SH"))
        assertTrue(!both.contains("EN"))
        assertTrue(rowSentence(1).contains("power play"))
        assertTrue(!rowSentence(1).contains("(power play)"))
        assertTrue(!rowSentence(1).contains("PP"))
        assertTrue(rowSentence(2).contains("penalty shot"))
        assertTrue(!rowSentence(2).contains("(penalty shot)"))
        assertTrue(!rowSentence(2).contains("PS"))
    }

    @Test
    fun `one divider separates each pair of period blocks`() {
        show(withGoals(AuditStates.fullGoals))

        compose.onAllNodesWithTag("detail.scoring.divider").assertCountEquals(2)
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
        assertEquals("OT", labelOf("detail.scoring.period.4.label"))
        // Oldest extra period first, then its own earliest elapsed first: the game's last goal is the bottom row.
        assertEquals("02:22", rowTime(0))
        assertEquals("01:15", rowTime(1))
    }

    @Test
    fun `the row reads crest, then time over the sentence, then the score columns`() {
        show(
            withGoals(
                listOf(
                    goal(period = 1, time = "10:00", isHome = false, scorer = "Kirill Marchenko", awayScore = 1, homeScore = 0),
                    goal(period = 1, time = "15:00", isHome = true, scorer = "Adam Fantilli", awayScore = 1, homeScore = 1),
                ),
            ),
        )

        val row = compose.onNodeWithTag("detail.scoring.row.00").getUnclippedBoundsInRoot()
        val crest =
            compose.onNodeWithTag("detail.scoring.row.00.crest.CBJ", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val time = compose.onNodeWithTag("detail.scoring.row.00.time", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val sentence =
            compose.onNodeWithTag("detail.scoring.row.00.sentence", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val awayScore =
            compose.onNodeWithTag("detail.scoring.row.00.away", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val homeScore =
            compose.onNodeWithTag("detail.scoring.row.00.home", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals(row.left.value, crest.left.value, 1f)
        assertEquals(44f, crest.bottom.value - crest.top.value, 1f)
        assertTrue(time.top.value < sentence.top.value)
        assertTrue(time.left.value >= crest.right.value)
        assertTrue(sentence.left.value >= crest.right.value)
        assertTrue(sentence.right.value <= awayScore.left.value + 1f)
        assertEquals(time.top.value, awayScore.top.value, 1f)
        assertTrue(awayScore.right.value <= homeScore.left.value)
    }

    @Test
    fun `the side that just scored is the emphasised score`() {
        show(
            withGoals(
                listOf(
                    goal(period = 1, time = "10:00", isHome = false, awayScore = 1, homeScore = 0),
                    goal(period = 1, time = "15:00", isHome = true, awayScore = 1, homeScore = 1),
                ),
            ),
        )

        compose
            .onNodeWithTag("detail.scoring.row.00.away.emphasised", useUnmergedTree = true)
            .assertExists()
        compose
            .onNodeWithTag("detail.scoring.row.00.home.emphasised", useUnmergedTree = true)
            .assertDoesNotExist()
        compose
            .onNodeWithTag("detail.scoring.row.01.home.emphasised", useUnmergedTree = true)
            .assertExists()
        compose
            .onNodeWithTag("detail.scoring.row.01.away.emphasised", useUnmergedTree = true)
            .assertDoesNotExist()
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

    private fun Int.padded(): String = toString().padStart(2, '0')

    private fun clockSeconds(clock: String): Int? {
        val parts = clock.split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toIntOrNull() ?: return null
        val seconds = parts[1].toIntOrNull() ?: return null
        return if (minutes < 0 || seconds !in 0..59) null else minutes * 60 + seconds
    }

    /** The clock cell on a scoring row, read from its own tag rather than the row's merged text. */
    private fun rowTime(index: Int): String = labelOf("detail.scoring.row.${index.padded()}.time")

    /** The one-sentence name/strength/assists block on a scoring row. */
    private fun rowSentence(index: Int): String = labelOf("detail.scoring.row.${index.padded()}.sentence")

    private fun textLayout(tag: String): TextLayoutResult {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        return requireNotNull(layouts.firstOrNull()) { "$tag has no text layout" }
    }

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
