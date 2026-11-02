@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import net.montycasa.roux.domain.model.GoalAssist
import net.montycasa.roux.domain.model.GoalEvent
import net.montycasa.roux.domain.model.GoalModifier
import net.montycasa.roux.domain.model.GoalStrength
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PeriodShots
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.ShotTotals
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.model.ThreeStar
import net.montycasa.roux.domain.policy.StandingsGrouping
import net.montycasa.roux.domain.policy.StandingsMode
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.ui.detail.DetailUiState
import net.montycasa.roux.ui.detail.GameDetailScreen
import net.montycasa.roux.ui.drawer.LeaguesPanel
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.scores.ScoresScreen
import net.montycasa.roux.ui.scores.ScoresUiState
import net.montycasa.roux.ui.standings.StandingsScreen
import net.montycasa.roux.ui.standings.StandingsUiState
import net.montycasa.roux.ui.standings.capturedStandings
import net.montycasa.roux.ui.teams.TeamAlerts
import net.montycasa.roux.ui.teams.TeamsScreen
import net.montycasa.roux.ui.teams.TeamsUiState
import net.montycasa.roux.ui.teams.teamStandings
import kotlin.time.Instant

/**
 * The screen states the audit knows about, in one place. ContrastAndTypeTest and ScreenshotMatrixTest both read
 * [screens], so a screen added here is measured for contrast and type size AND photographed in both themes without
 * touching either test. Fixtures carry NO crest/logo URLs: a real image URL drags Coil into the JVM and changes other
 * screenshot goldens later in the same worker.
 */
internal object AuditStates {
    // ----- Fixtures (copied from ReachAuditTest so these cases are the same screens the reach audit measures) ----

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

    // No crest URLs, as with the teams fixture: a real logo URL lets Coil load (or not) depending on what ran earlier
    // in the JVM, which swaps the crest's abbreviation fallback in and out of the frame and flakes the golden.
    private val standingRows =
        capturedStandings().sortedBy { it.leagueSequence }.map {
            it.copy(team = it.team.copy(logoUrl = null, darkLogoUrl = null))
        }

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
    internal fun StandingsAuditScreen(state: StandingsUiState) {
        Scaffold(bottomBar = {
            NavigationBar {
                listOf("Scores", "Standings", "Teams").forEach { label ->
                    NavigationBarItem(
                        selected = label == "Standings",
                        onClick = {},
                        icon = { Text(label.take(1)) },
                        label = { Text(label) },
                    )
                }
            }
        }) { insets ->
            StandingsScreen(state, onRefresh = {}, onSelect = { _, _ -> }, modifier = Modifier.padding(insets))
        }
    }

    // No crest URLs: a real logo URL drags Coil into the JVM and changes the shell goldens later in the same worker.
    private val teams = teamStandings().map { it.team.copy(logoUrl = null, darkLogoUrl = null) }
    private val teamsList = TeamsUiState(rows = teams, isRefreshing = false, freshness = Freshness.Fresh)

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

    // The goals sum to the same 3-4 as the hero and the period table: period 1 is 2-1, period 2 is 0-1, period 3 is
    // 1-2. Each running score is the feed's own, not a replay of these rows.
    private val fullGoals =
        listOf(
            GoalEvent(
                1,
                "05:12",
                isHome = false,
                scorer = "A. Fantilli",
                goalsToDate = 4,
                assists = listOf(GoalAssist("K. Johnson", 4), GoalAssist("J. Christiansen", 1)),
                strength = GoalStrength.EvenStrength,
                modifier = GoalModifier.None,
                awayScore = 1,
                homeScore = 0,
            ),
            GoalEvent(
                1,
                "09:30",
                isHome = true,
                scorer = "T. Konecny",
                goalsToDate = 6,
                assists = listOf(GoalAssist("T. Zegras", 5)),
                strength = GoalStrength.PowerPlay,
                modifier = GoalModifier.None,
                awayScore = 1,
                homeScore = 1,
            ),
            GoalEvent(
                1,
                "14:22",
                isHome = false,
                scorer = "K. Johnson",
                goalsToDate = 5,
                assists = listOf(GoalAssist("Z. Werenski", 7)),
                strength = GoalStrength.EvenStrength,
                modifier = GoalModifier.None,
                awayScore = 2,
                homeScore = 1,
            ),
            GoalEvent(
                2,
                "08:15",
                isHome = true,
                scorer = "N. Cates",
                goalsToDate = 1,
                assists = emptyList(),
                strength = GoalStrength.EvenStrength,
                modifier = GoalModifier.None,
                awayScore = 2,
                homeScore = 2,
            ),
            GoalEvent(
                3,
                "06:44",
                isHome = true,
                scorer = "T. Zegras",
                goalsToDate = 4,
                assists = listOf(GoalAssist("J. Drysdale", 2)),
                strength = GoalStrength.PowerPlay,
                modifier = GoalModifier.None,
                awayScore = 2,
                homeScore = 3,
            ),
            GoalEvent(
                3,
                "12:11",
                isHome = false,
                scorer = "Z. Werenski",
                goalsToDate = 2,
                assists = listOf(GoalAssist("K. Johnson", 6), GoalAssist("A. Fantilli", 3)),
                strength = GoalStrength.EvenStrength,
                modifier = GoalModifier.None,
                awayScore = 3,
                homeScore = 3,
            ),
            GoalEvent(
                3,
                "18:57",
                isHome = true,
                scorer = "J. Drysdale",
                goalsToDate = 1,
                assists = listOf(GoalAssist("N. Cates", 2)),
                strength = GoalStrength.EvenStrength,
                modifier = GoalModifier.EmptyNet,
                awayScore = 3,
                homeScore = 4,
            ),
        )
    private val liveGoals =
        listOf(
            GoalEvent(
                1,
                "07:12",
                isHome = false,
                scorer = "A. Fantilli",
                goalsToDate = 4,
                assists = listOf(GoalAssist("K. Johnson", 4)),
                strength = GoalStrength.EvenStrength,
                modifier = GoalModifier.None,
                awayScore = 1,
                homeScore = 0,
            ),
            GoalEvent(
                1,
                "11:40",
                isHome = true,
                scorer = "T. Konecny",
                goalsToDate = 6,
                assists = listOf(GoalAssist("T. Zegras", 5)),
                strength = GoalStrength.PowerPlay,
                modifier = GoalModifier.None,
                awayScore = 1,
                homeScore = 1,
            ),
            GoalEvent(
                1,
                "16:05",
                isHome = false,
                scorer = "K. Johnson",
                goalsToDate = 5,
                assists = listOf(GoalAssist("Z. Werenski", 7), GoalAssist("J. Christiansen", 1)),
                strength = GoalStrength.EvenStrength,
                modifier = GoalModifier.None,
                awayScore = 2,
                homeScore = 1,
            ),
        )

    private val fullDetail =
        GameDetail(
            landing =
                Loadable(
                    GameLanding(
                        periodScoring =
                            listOf(
                                PeriodScoring(1, 2, 1),
                                PeriodScoring(2, 0, 1),
                                PeriodScoring(3, 1, 2),
                            ),
                        goals = fullGoals,
                        venue =
                            net.montycasa.roux.domain.model
                                .Venue("Centre Bell", "Montreal"),
                        broadcasts =
                            listOf(
                                net.montycasa.roux.domain.model
                                    .Broadcast("TSN2", "CA", "H"),
                                net.montycasa.roux.domain.model
                                    .Broadcast("NHLN", "US", "N"),
                            ),
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
            // shots card stays absent so this state's golden keeps showing only that failure.
            rightRail = Loadable(GameRightRail(shots = ShotTotals(awayShots = 21, homeShots = 28)), Freshness.Fresh, null),
        )

    internal class ContentCase(
        val name: String,
        val content: @Composable () -> Unit,
    )

    internal fun screens(): List<ContentCase> =
        listOf(
            ContentCase("settings") {
                net.montycasa.roux.ui.settings
                    .SettingsScreen(ThemeMode.System, {}, {})
            },
            // Scores cases include the centred, actionable date shelf in both themes.
            ContentCase("scores.data") { ScoresScreen(scoresData, {}, {}, {}) },
            ContentCase("scores.empty") { ScoresScreen(scoresFresh, {}, {}, {}) },
            ContentCase("scores.stale") { ScoresScreen(scoresStale, {}, {}, {}) },
            ContentCase("standings.eastPlayoffs") { StandingsAuditScreen(standings(StandingsView.Eastern, StandingsMode.Playoffs)) },
            ContentCase("standings.eastDivision") { StandingsAuditScreen(standings(StandingsView.Eastern, StandingsMode.Division)) },
            ContentCase("standings.westPlayoffs") { StandingsAuditScreen(standings(StandingsView.Western, StandingsMode.Playoffs)) },
            ContentCase("standings.league") {
                StandingsAuditScreen(
                    StandingsUiState(
                        view = StandingsView.League,
                        groups = StandingsGrouping.group(standingRows, StandingsView.League),
                        loading = false,
                    ),
                )
            },
            ContentCase("teams.list") { TeamsScreen(teamsList, {}, {}) },
            ContentCase("teams.alerts") {
                TeamsScreen(teamsList.copy(alerts = TeamAlerts(teams.first(), start = true, goals = false)), {}, {})
            },
            ContentCase("detail.full") {
                GameDetailScreen(detailGame, DetailUiState(detail = fullDetail, refreshing = false, unexpectedFailure = false), {}, {})
            },
            ContentCase("detail.live") {
                // A live game in the second period: the period rows must add up to the score on the board, because a
                // screenshot whose periods disagree with the hero is unauditable.
                GameDetailScreen(
                    detailGame.copy(state = GameState.Live(), period = PeriodInfo(2, "REG", "12:34", false), awayScore = 2, homeScore = 1),
                    DetailUiState(
                        detail =
                            fullDetail.copy(
                                landing =
                                    fullDetail.landing.copy(
                                        data =
                                            fullDetail.landing.data!!.copy(
                                                periodScoring = listOf(PeriodScoring(1, 2, 1), PeriodScoring(2, 0, 0)),
                                                goals = liveGoals,
                                            ),
                                    ),
                                // The game is in the second period, so the shots card stops at the second: a
                                // screenshot with a third-period cell for a period not played would be unauditable.
                                rightRail =
                                    Loadable(
                                        GameRightRail(
                                            shots = ShotTotals(awayShots = 12, homeShots = 19),
                                            shotsByPeriod =
                                                listOf(
                                                    PeriodShots(1, "REG", away = 7, home = 8),
                                                    PeriodShots(2, "REG", away = 5, home = 11),
                                                ),
                                        ),
                                        Freshness.Fresh,
                                        null,
                                    ),
                            ),
                        refreshing = false,
                        unexpectedFailure = false,
                    ),
                    {},
                    {},
                )
            },
            ContentCase("detail.scheduled") {
                GameDetailScreen(
                    detailGame.copy(
                        state = GameState.Scheduled,
                        homeScore = null,
                        awayScore = null,
                        startTimeUtc = Instant.parse("2026-10-26T23:00:00Z"),
                    ),
                    DetailUiState(
                        detail =
                            fullDetail.copy(
                                landing =
                                    fullDetail.landing.copy(
                                        data =
                                            fullDetail.landing.data!!.copy(
                                                periodScoring = emptyList(),
                                                threeStars = emptyList(),
                                                goals = emptyList(),
                                            ),
                                    ),
                                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
                                boxscore = Loadable(GameBoxscore(), Freshness.Fresh, null),
                            ),
                        refreshing = false,
                        unexpectedFailure = false,
                    ),
                    {},
                    {},
                )
            },
            ContentCase("detail.partial") {
                GameDetailScreen(detailGame, DetailUiState(detail = partialDetail, refreshing = false, unexpectedFailure = false), {}, {})
            },
            ContentCase("drawer.leagues") {
                LeaguesPanel(
                    drawer = DrawerUi(),
                    editing = false,
                    onEditing = {},
                    onLeagueOrder = {},
                    onLeagueSelected = {},
                    onFavouritesOnly = {},
                    seasonDate = scoreDate,
                )
            },
        )
}
