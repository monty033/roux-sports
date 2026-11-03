@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PeriodShots
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.ShotTotals
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.ThreeStar
import net.montycasa.roux.domain.policy.StandingsGrouping
import net.montycasa.roux.domain.policy.StandingsMode
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.ui.detail.DetailUiState
import net.montycasa.roux.ui.detail.GameDetailScreen
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.scores.ScoresScreen
import net.montycasa.roux.ui.scores.ScoresUiState
import net.montycasa.roux.ui.standings.StandingsScreen
import net.montycasa.roux.ui.standings.StandingsUiState
import net.montycasa.roux.ui.standings.capturedStandings
import net.montycasa.roux.ui.teams.TeamAlerts
import net.montycasa.roux.ui.teams.TeamsScreen
import net.montycasa.roux.ui.teams.TeamsUiState
import net.montycasa.roux.ui.teams.teamStandings
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * The reach audit as a matrix: every screen state the app can show, at two device sizes, at normal and doubled font
 * scale, in light and dark theme. Each combination is judged by [assertPrimaryControlsReachable] against the primary
 * controls that screen declares.
 *
 * One Robolectric method per device size. Each method composes once and reads the case, font scale and theme from
 * state, so every combination is the same composition with different inputs. Failures are collected across the whole
 * matrix and reported together, each prefixed with its own `case=... size=... scale=... theme=...` so a failing row
 * can be found without rerunning it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReachAuditTest {
    @get:Rule
    val rule = createComposeRule()

    private val fontScales = listOf(1f, 2f)
    private val themes = listOf(false, true)

    // ----- Scores ---------------------------------------------------------------------------------------------------

    private val scoreDate = "2025-10-25"
    private val scoreIndex = listOf(DateIndexEntry("2025-10-28", 2), DateIndexEntry(scoreDate, 0))
    private val scoreGame =
        Game(
            id = GameId("2025020123"),
            date = scoreDate,
            state = GameState.Scheduled,
            home = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs"),
            away = TeamRef(TeamKey("MTL"), name = "Montréal Canadiens"),
            startTimeUtc = Instant.parse("2025-10-25T23:00:00Z"),
        )
    private val scoresFresh = ScoresUiState(scoreDate, today = scoreDate, freshness = Freshness.Fresh, dateIndex = scoreIndex)
    private val scoresData = scoresFresh.copy(rows = listOf(scoreGame))
    private val scoresStale =
        scoresData.copy(freshness = Freshness.Stale(Instant.parse("2025-10-25T23:42:00Z")))

    /** The date chips the screen draws for this state, from the same policy call the screen makes. */
    private fun scoreChips(state: ScoresUiState): List<String> = listOf("scores.previous", "scores.next", "scores.dateLabel")

    // ----- Standings ------------------------------------------------------------------------------------------------

    private val standingRows = capturedStandings().sortedBy { it.leagueSequence }
    private val navTags = listOf("test.nav.Scores", "test.nav.Standings", "test.nav.Teams")

    private fun standings(
        view: StandingsView,
        mode: StandingsMode,
    ) = StandingsUiState(
        view = view,
        mode = mode,
        groups = StandingsGrouping.group(standingRows, view, mode),
        loading = false,
    )

    @Composable
    private fun StandingsAuditScreen(state: StandingsUiState) {
        Scaffold(bottomBar = {
            // Mirrors the three-destination bar the standings test reserves; production wiring lives in RouxNavHost.
            NavigationBar {
                Spacer(Modifier.width(16.dp))
                listOf("Scores", "Standings", "Teams").forEach { label ->
                    NavigationBarItem(
                        selected = label == "Standings",
                        onClick = {},
                        icon = { Text(label.take(1)) },
                        label = { Text(label) },
                        modifier = Modifier.primaryControl("test.nav.$label", frequent = true),
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
        }) { insets ->
            StandingsScreen(state, onRefresh = {}, onSelect = { _, _ -> }, modifier = Modifier.padding(insets))
        }
    }

    // ----- Teams ----------------------------------------------------------------------------------------------------

    // No crest URLs: this matrix is about geometry and semantics, and a real logo URL drags Coil's image
    // loader into the JVM - which changes what the *shell* goldens capture later in the same worker, because
    // AsyncImage settles differently once the loader has been exercised. Reach does not care what a crest
    // looks like, so the fixtures keep it unset and the matrix stays invisible to the golden tests.
    private val teams = teamStandings().map { it.team.copy(logoUrl = null, darkLogoUrl = null) }
    private val teamsList = TeamsUiState(rows = teams, isRefreshing = false, freshness = Freshness.Fresh)

    // ----- Detail ---------------------------------------------------------------------------------------------------

    private val detailGame =
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
                    GameBoxscore(goalies = listOf(GoalieLine(PlayerRef("C. Talbot", "PHI"), 25, 28))),
                    Freshness.Fresh,
                    null,
                ),
            rightRail =
                Loadable(
                    GameRightRail(
                        shots = ShotTotals(awayShots = 21, homeShots = 28),
                        shotsByPeriod =
                            listOf(
                                PeriodShots(1, "REG", away = 7, home = 8),
                                PeriodShots(2, "REG", away = 6, home = 10),
                                PeriodShots(3, "REG", away = 8, home = 10),
                            ),
                    ),
                    Freshness.Fresh,
                    null,
                ),
        )
    private val partialDetail =
        fullDetail.copy(
            landing = Loadable(null, Freshness.Missing, LoadError.Http(503)),
            // The partial state is about the landing failure; the rail loads, and with no per-period detail the
            // shots card stays absent so the reach grid is the same page as before this card existed.
            rightRail = Loadable(GameRightRail(shots = ShotTotals(awayShots = 21, homeShots = 28)), Freshness.Fresh, null),
        )

    // ----- The matrix -----------------------------------------------------------------------------------------------

    private class AuditCase(
        val name: String,
        val primaries: List<String>,
        val content: @Composable () -> Unit,
        // Tag prefixes the screen's own modal covers. An open sheet takes the touch, so the rows behind it
        // are not reachable controls; declaring them here is what stops the audit measuring the sheet
        // against them.
        val covered: List<String> = emptyList(),
    )

    private fun auditCases(): List<AuditCase> =
        listOf(
            AuditCase("scores.data", scoreChips(scoresData), { ScoresScreen(scoresData, {}, {}, {}) }),
            AuditCase("scores.empty", scoreChips(scoresFresh) + "scores.nextGames", { ScoresScreen(scoresFresh, {}, {}, {}) }),
            AuditCase("scores.stale", scoreChips(scoresStale) + "scores.retry", { ScoresScreen(scoresStale, {}, {}, {}) }),
            AuditCase(
                "standings.eastPlayoffs",
                navTags + "standings.view",
                { StandingsAuditScreen(standings(StandingsView.Eastern, StandingsMode.Playoffs)) },
            ),
            AuditCase(
                "standings.eastDivision",
                navTags + "standings.view",
                { StandingsAuditScreen(standings(StandingsView.Eastern, StandingsMode.Division)) },
            ),
            AuditCase(
                "standings.westPlayoffs",
                navTags + "standings.view",
                { StandingsAuditScreen(standings(StandingsView.Western, StandingsMode.Playoffs)) },
            ),
            AuditCase(
                "standings.league",
                navTags + "standings.view",
                {
                    StandingsAuditScreen(
                        StandingsUiState(
                            view = StandingsView.League,
                            groups = StandingsGrouping.group(standingRows, StandingsView.League),
                            loading = false,
                        ),
                    )
                },
            ),
            AuditCase("teams.list", emptyList(), { TeamsScreen(teamsList, {}, {}) }),
            AuditCase(
                "teams.favourites",
                emptyList(),
                {
                    TeamsScreen(
                        teamsList.copy(
                            favourites = setOf(teams.first().key),
                            alerting = setOf(teams[1].key),
                        ),
                        {},
                        {},
                    )
                },
            ),
            AuditCase(
                "teams.alerts",
                listOf("teams.alerts.done"),
                {
                    TeamsScreen(
                        teamsList.copy(alerts = TeamAlerts(teams.first(), start = true, goals = false)),
                        {},
                        {},
                    )
                },
                covered = listOf("teams.row.body.", "teams.toggle.", "teams.alerts.open."),
            ),
            AuditCase(
                "detail.full",
                emptyList(),
                { GameDetailScreen(detailGame, DetailUiState(detail = fullDetail, refreshing = false, unexpectedFailure = false), {}, {}) },
            ),
            AuditCase(
                "detail.live",
                emptyList(),
                AuditStates.screens().single { it.name == "detail.live" }.content,
            ),
            AuditCase(
                "detail.scheduled",
                emptyList(),
                AuditStates.screens().single { it.name == "detail.scheduled" }.content,
            ),
            AuditCase(
                "detail.partial",
                emptyList(),
                {
                    GameDetailScreen(
                        detailGame,
                        DetailUiState(detail = partialDetail, refreshing = false, unexpectedFailure = false),
                        {},
                        {},
                    )
                },
            ),
        )

    private fun audit(
        size: String,
        expectedCombinations: Int,
    ) {
        val cases = auditCases()
        val caseIndex = mutableIntStateOf(0)
        val fontScale = mutableFloatStateOf(1f)
        val dark = mutableStateOf(false)
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale.floatValue)) {
                RouxTheme(dark = dark.value) {
                    cases[caseIndex.intValue].content()
                }
            }
        }

        val failures = mutableListOf<String>()
        var combinations = 0
        cases.forEachIndexed { index, case ->
            fontScales.forEach { scale ->
                themes.forEach { isDark ->
                    val theme = if (isDark) "dark" else "light"
                    val prefix = "case=${case.name} size=$size scale=$scale theme=$theme: "
                    rule.runOnIdle {
                        caseIndex.intValue = index
                        fontScale.floatValue = scale
                        dark.value = isDark
                    }
                    rule.waitForIdle()
                    combinations++
                    try {
                        rule.assertPrimaryControlsReachable(*case.primaries.toTypedArray(), coveredTags = case.covered)
                    } catch (failure: Throwable) {
                        failures += prefix + (failure.message ?: failure.toString())
                    }
                }
            }
        }

        assertEquals("matrix size", expectedCombinations, combinations)
        if (failures.isNotEmpty()) {
            throw AssertionError(
                "${failures.size} of $combinations combinations failed at $size:\n" + failures.joinToString("\n"),
            )
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h923dp-420dpi")
    fun assertPrimaryControlsReachableAt411x923() = audit(size = "w411dp-h923dp-420dpi", expectedCombinations = 56)

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp")
    fun assertPrimaryControlsReachableAt360x780() = audit(size = "w360dp-h780dp", expectedCombinations = 56)
}
