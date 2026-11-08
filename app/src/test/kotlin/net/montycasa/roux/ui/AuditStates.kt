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
import net.montycasa.roux.domain.model.Broadcast
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
import net.montycasa.roux.domain.model.PenaltyEvent
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.PeriodShots
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.SeriesStatus
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
import net.montycasa.roux.ui.player.PlayerDetailScreen
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.scores.OffSeasonUi
import net.montycasa.roux.ui.scores.OpeningNightUi
import net.montycasa.roux.ui.scores.ScoresScreen
import net.montycasa.roux.ui.scores.ScoresUiState
import net.montycasa.roux.ui.standings.StandingsScreen
import net.montycasa.roux.ui.standings.StandingsUiState
import net.montycasa.roux.ui.standings.capturedStandings
import net.montycasa.roux.ui.team.TeamDetailScreen
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
            // The audit needs a real networks line so the rail's supporting type is measured too, not only
            // the start time above it.
            broadcasts =
                listOf(
                    Broadcast("NHLN", "US", "N"),
                    Broadcast("SN", "CA", "N"),
                ),
        )
    private val scoresFresh = ScoresUiState(scoreDate, today = scoreDate, freshness = Freshness.Fresh, dateIndex = scoreIndex)
    private val scoresData = scoresFresh.copy(rows = listOf(scoreGame))
    private val scoresStale =
        scoresData.copy(freshness = Freshness.Stale(Instant.parse("2025-10-25T23:42:00Z")))

    // The off-season landing: the last game day of 2025–26, with the card naming the next games.
    private val scoresOffSeason =
        ScoresUiState(
            selectedDate = "2026-06-14",
            today = "2026-07-20",
            rows =
                listOf(
                    scoreGame.copy(
                        id = GameId("2025030411"),
                        date = "2026-06-14",
                        state = GameState.Final,
                        homeScore = 2,
                        awayScore = 1,
                    ),
                ),
            freshness = Freshness.Fresh,
            dateIndex = listOf(DateIndexEntry("2026-06-14", 1), DateIndexEntry("2026-07-20", 0)),
            offSeason = OffSeasonUi("2026-06-14", "2026-09-19", "2025–26"),
        )

    // The same window with opening night resolved: the card names the coming season instead of the finals.
    private val scoresOffSeasonOpener =
        ScoresUiState(
            selectedDate = "2026-09-29",
            today = "2026-07-20",
            rows =
                listOf(
                    scoreGame.copy(
                        id = GameId("2026020001"),
                        date = "2026-09-29",
                        state = GameState.Final,
                        homeScore = 4,
                        awayScore = 2,
                    ),
                ),
            freshness = Freshness.Fresh,
            dateIndex = listOf(DateIndexEntry("2026-06-14", 1), DateIndexEntry("2026-09-29", 1)),
            offSeason =
                OffSeasonUi(
                    "2026-06-14",
                    "2026-09-19",
                    "2025–26",
                    openingNight = OpeningNightUi("2026-09-29", "2026–27"),
                ),
        )

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

    internal val detailGame =
        Game(
            id = GameId("2026020229"),
            date = "2026-11-01",
            state = GameState.Final,
            home = TeamRef(TeamKey("PHI"), name = "Flyers"),
            away = TeamRef(TeamKey("UTA"), name = "Mammoth"),
            homeScore = 6,
            awayScore = 3,
        )

    // Game 4 of a first-round series tied 2–2: the hero's line is the standing after this game, not a finished series.
    private val playoffGame =
        detailGame.copy(
            id = GameId("2026030104"),
            series =
                SeriesStatus(
                    round = 1,
                    seriesTitle = "1st Round",
                    neededToWin = 4,
                    topSeedTeamAbbrev = "PHI",
                    topSeedWins = 2,
                    bottomSeedTeamAbbrev = "UTA",
                    bottomSeedWins = 2,
                    gameNumberOfSeries = 4,
                ),
        )

    // Real landing data for UTA @ PHI, 2026-11-01, final 3-6. The feed's clocks are elapsed time and the card prints
    // them verbatim. Period 3 is the sequence this audit exists to photograph.
    internal val fullGoals =
        listOf(
            auditGoal(
                1,
                "12:49",
                isHome = true,
                scorer = "Matvei Michkov",
                goalsToDate = 10,
                assists = listOf(GoalAssist("Noah Cates", 3), GoalAssist("Porter Martone", 4)),
                away = 0,
                home = 1,
            ),
            auditGoal(
                1,
                "14:24",
                isHome = false,
                scorer = "Dmitri Simashev",
                goalsToDate = 3,
                assists = listOf(GoalAssist("Vincent Trocheck", 6), GoalAssist("Logan Cooley", 9)),
                away = 1,
                home = 1,
            ),
            auditGoal(
                2,
                "01:06",
                isHome = true,
                scorer = "Travis Konecny",
                goalsToDate = 1,
                assists = listOf(GoalAssist("Jamie Drysdale", 3), GoalAssist("Christian Dvorak", 4)),
                away = 1,
                home = 2,
            ),
            auditGoal(
                2,
                "05:50",
                isHome = true,
                scorer = "Noah Cates",
                goalsToDate = 2,
                assists = listOf(GoalAssist("Travis Sanheim", 5), GoalAssist("Jamie Drysdale", 4)),
                away = 1,
                home = 3,
            ),
            auditGoal(
                3,
                "01:02",
                isHome = true,
                scorer = "Christian Dvorak",
                goalsToDate = 3,
                assists = listOf(GoalAssist("Travis Konecny", 4), GoalAssist("Travis Sanheim", 6)),
                away = 1,
                home = 4,
            ),
            auditGoal(
                3,
                "05:35",
                isHome = true,
                scorer = "Oliver Bonk",
                goalsToDate = 1,
                assists = listOf(GoalAssist("Cam York", 1), GoalAssist("Sean Couturier", 4)),
                away = 1,
                home = 5,
            ),
            auditGoal(
                3,
                "16:00",
                isHome = false,
                scorer = "Mikhail Sergachev",
                goalsToDate = 5,
                assists = listOf(GoalAssist("Nick Schmaltz", 8), GoalAssist("Clayton Keller", 13)),
                strength = GoalStrength.PowerPlay,
                away = 2,
                home = 5,
            ),
            auditGoal(
                3,
                "17:07",
                isHome = false,
                scorer = "Logan Cooley",
                goalsToDate = 8,
                assists = listOf(GoalAssist("Nick Schmaltz", 9), GoalAssist("Clayton Keller", 14)),
                strength = GoalStrength.PowerPlay,
                away = 3,
                home = 5,
            ),
            auditGoal(
                3,
                "18:26",
                isHome = true,
                scorer = "Cole Knuble",
                goalsToDate = 3,
                assists = listOf(GoalAssist("Noel Acciari", 1), GoalAssist("Jamie Drysdale", 5)),
                modifier = GoalModifier.EmptyNet,
                away = 3,
                home = 6,
            ),
        )

    // The live state is the same game after period 1, 1-1: the real two goals, not invented clocks.
    internal val liveGoals = fullGoals.take(2)

    // Real penalty calls for the same UTA @ PHI game: three blocks, thirteen calls, 2 / 5 / 6. The four calls at 08:43
    // and the fighting majors at 11:55 are deliberate: equal clocks must keep feed order.
    internal val fullPenalties =
        listOf(
            penalty(1, "05:15", "PHI", "Jamie Drysdale", "holding", 2, "Liam O'Brien"),
            penalty(1, "15:28", "PHI", "Porter Martone", "hooking", 2, "Kevin Stenlund"),
            penalty(2, "08:43", "UTA", "Logan Cooley", "roughing", 2, "Trevor Zegras"),
            penalty(2, "08:43", "PHI", "Trevor Zegras", "roughing", 2, "Logan Cooley"),
            penalty(2, "08:43", "UTA", "Mikhail Sergachev", "roughing", 2, "Travis Konecny"),
            penalty(2, "08:43", "PHI", "Travis Konecny", "unsportsmanlike conduct", 2, "Mikhail Sergachev"),
            penalty(2, "10:53", "UTA", "Andrew Peeke", "interference", 2, "Cole Knuble"),
            penalty(3, "08:22", "PHI", "Simon Benoit", "tripping", 2, "Jack McBain"),
            penalty(3, "11:55", "UTA", "Lawson Crouse", "roughing", 2, "Simon Benoit", servedBy = "M. Carcone"),
            penalty(3, "11:55", "UTA", "Lawson Crouse", "fighting", 5, "Simon Benoit"),
            penalty(3, "11:55", "PHI", "Simon Benoit", "fighting", 5, "Lawson Crouse"),
            penalty(3, "14:59", "PHI", "Sean Couturier", "hooking", 2, "Clayton Keller"),
            penalty(3, "16:26", "PHI", "Noah Cates", "tripping", 2, "Barrett Hayton"),
        )

    // After one period the page has only the first block: the same two real calls, not invented ones.
    internal val livePenalties = fullPenalties.take(2)

    @Suppress("LongParameterList")
    private fun penalty(
        period: Int,
        elapsed: String,
        team: String,
        player: String,
        call: String,
        duration: Int,
        drawnBy: String?,
        servedBy: String? = null,
    ) = PenaltyEvent(
        period = period,
        periodType = "REG",
        timeInPeriod = elapsed,
        teamAbbrev = team,
        player = player,
        call = call,
        durationMinutes = duration,
        drawnBy = drawnBy,
        servedBy = servedBy,
    )

    @Suppress("LongParameterList")
    private fun auditGoal(
        period: Int,
        elapsed: String,
        isHome: Boolean,
        scorer: String,
        goalsToDate: Int,
        assists: List<GoalAssist> = emptyList(),
        strength: GoalStrength = GoalStrength.EvenStrength,
        modifier: GoalModifier = GoalModifier.None,
        away: Int,
        home: Int,
    ) = GoalEvent(
        period = period,
        timeInPeriod = elapsed,
        isHome = isHome,
        scorer = scorer,
        goalsToDate = goalsToDate,
        assists = assists,
        strength = strength,
        modifier = modifier,
        awayScore = away,
        homeScore = home,
    )

    private val fullDetail =
        GameDetail(
            landing =
                Loadable(
                    GameLanding(
                        periodScoring =
                            listOf(
                                PeriodScoring(1, 1, 1),
                                PeriodScoring(2, 0, 2),
                                PeriodScoring(3, 2, 3),
                            ),
                        goals = fullGoals,
                        penalties = fullPenalties,
                        venue =
                            net.montycasa.roux.domain.model
                                .Venue("Xfinity Mobile Arena", "Philadelphia"),
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

    /**
     * One audited state. [tab] is a tab a sweep taps into view before it measures the state, for a page whose content
     * sits behind a tab. A state that opens on its first tab leaves it null.
     */
    internal class ContentCase(
        val name: String,
        val tab: String? = null,
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
            ContentCase("scores.offseason") { ScoresScreen(scoresOffSeason, {}, {}, {}) },
            ContentCase("scores.offseason.opener") { ScoresScreen(scoresOffSeasonOpener, {}, {}, {}) },
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
            // One favorited and one alerting, on different rows: both controls' state changes are visible beside
            // their off state, so the star's fill and the bell's accent can be compared in one capture.
            ContentCase("teams.favourites") {
                TeamsScreen(
                    teamsList.copy(
                        favourites = setOf(teams.first().key),
                        alerting = setOf(teams[1].key),
                    ),
                    {},
                    {},
                )
            },
            ContentCase("teams.alerts") {
                TeamsScreen(
                    teamsList.copy(
                        alerts =
                            TeamAlerts(
                                teams.first(),
                                start = true,
                                goals = true,
                                intermission = true,
                                final = true,
                                upcoming = true,
                                leadMinutes = 30,
                            ),
                        alerting = setOf(teams.first().key),
                    ),
                    {},
                    {},
                )
            },
            // The other end of the same sheet: five rows, all off, so the switch graphics are compared with every
            // state visible rather than inferred from the on case.
            ContentCase("teams.alerts.off") {
                TeamsScreen(
                    teamsList.copy(alerts = TeamAlerts(teams.first())),
                    {},
                    {},
                )
            },
            ContentCase("detail.full") {
                GameDetailScreen(detailGame, DetailUiState(detail = fullDetail, refreshing = false, unexpectedFailure = false), {}, {})
            },
            ContentCase("detail.live") {
                // A live game in the second period: the period rows must add up to the score on the board, because a
                // screenshot whose periods disagree with the hero is unauditable.
                GameDetailScreen(
                    detailGame.copy(state = GameState.Live(), period = PeriodInfo(2, "REG", "12:34", false), awayScore = 1, homeScore = 1),
                    DetailUiState(
                        detail =
                            fullDetail.copy(
                                landing =
                                    fullDetail.landing.copy(
                                        data =
                                            fullDetail.landing.data!!.copy(
                                                periodScoring = listOf(PeriodScoring(1, 1, 1), PeriodScoring(2, 0, 0)),
                                                goals = liveGoals,
                                                penalties = livePenalties,
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
                                                penalties = emptyList(),
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
            ContentCase("detail.playoff") {
                GameDetailScreen(playoffGame, DetailUiState(detail = fullDetail, refreshing = false, unexpectedFailure = false), {}, {})
            },
            // The two newest pages, built from the domain types rather than from JSON. The pager's chosen tab is
            // internal view state, so this state photographs the tab each page opens on; the other tabs are covered
            // by DetailTabsScreenshotTest, which taps them into view.
            ContentCase("player.season") {
                PlayerDetailScreen(
                    state = DetailPageFixtures.playerState,
                    currentSeason = DetailPageFixtures.CURRENT_SEASON,
                    today = DetailPageFixtures.TODAY,
                    onBack = {},
                    onRetry = {},
                    onGameClick = {},
                )
            },
            // A season with no games yet: the log is the 2025–26 regular season, behind the Game log tab.
            ContentCase("player.gamelog.fallback", tab = "player.tab.GameLog") {
                PlayerDetailScreen(
                    state = DetailPageFixtures.playerState.copy(gameLogFallbackSeason = 20252026),
                    currentSeason = DetailPageFixtures.CURRENT_SEASON,
                    today = DetailPageFixtures.TODAY,
                    onBack = {},
                    onRetry = {},
                    onGameClick = {},
                )
            },
            ContentCase("team.schedule") {
                TeamDetailScreen(
                    state = DetailPageFixtures.teamState,
                    onBack = {},
                    now = DetailPageFixtures.NOW,
                )
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
