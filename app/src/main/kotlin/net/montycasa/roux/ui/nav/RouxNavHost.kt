// Compose names composables in PascalCase; ktlint's function-naming rule reads that as a misnamed
// function. Suppressed for this file only, rather than changing the rule for the whole repository.
@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.nav

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import net.montycasa.roux.AppContainer
import net.montycasa.roux.R
import net.montycasa.roux.RouxApp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.detail.GameDetailScreen
import net.montycasa.roux.ui.detail.GameDetailViewModel
import net.montycasa.roux.ui.drawer.LeagueNavigationDrawer
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.scores.CalendarMonthSheet
import net.montycasa.roux.ui.scores.ScoresScreen
import net.montycasa.roux.ui.scores.ScoresViewModel
import net.montycasa.roux.ui.standings.StandingsScreen
import net.montycasa.roux.ui.standings.StandingsViewModel
import net.montycasa.roux.ui.teams.TeamsScreen
import net.montycasa.roux.ui.teams.TeamsViewModel
import kotlin.time.Instant

/**
 * The three places the app goes. Scores is the reason the app exists; the other two are peers, not
 * sub-screens of it, so they sit on the bar rather than behind a menu (R6).
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val tag: String,
) {
    Scores("scores", R.string.nav_scores, "nav.scores"),
    Standings("standings", R.string.nav_standings, "nav.standings"),
    Teams("teams", R.string.nav_teams, "nav.teams"),
}

/**
 * The shell: a bottom bar with the three destinations and a host for whatever each one shows.
 *
 * Insets belong to [Scaffold] here, which is why the activity stopped padding its own content in S0.8 -
 * two padders would either double the gap or clip the bar's background.
 */
@Composable
fun RouxNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val container = (LocalContext.current.applicationContext as RouxApp).container
    val scores: ScoresViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer { ScoresViewModel(container.scores, container.preferences, container.clock) }
                },
        )
    // Only Scores subscribes: leaving its tab still stops polling after WhileSubscribed's grace.
    val scoresState =
        if (currentRoute == null || currentRoute == TopLevelDestination.Scores.route) {
            scores.uiState.collectAsStateWithLifecycle().value
        } else {
            // Freeze the last Scores state while another tab owns the active collection.
            remember(scores, currentRoute) { scores.uiState.value }
        }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.then(if (calendarOpen) Modifier.clearAndSetSemantics { } else Modifier)) {
            LeagueNavigationDrawer(
                drawerState = drawerState,
                drawer = scores.drawerState.collectAsState().value,
                seasonDate = scoresState.loadedDate,
                onFavouritesOnly = scores::setFavouritesOnly,
                onLeagueOrder = scores::setLeagueOrder,
                onLeagueSelected = scores::selectLeague,
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            // R5 keeps primary controls 16 dp clear of the screen edges, where the back gesture
                            // lives. A stock NavigationBar puts its outer items flush to the edges, so the bar's
                            // own background stays full-bleed and the items are pushed inside it by spacers -
                            // padding the bar itself would inset its background and leave a strip of page showing.
                            Spacer(Modifier.width(Reach.EDGE_INSET_DP.dp))
                            TopLevelDestination.entries.forEach { destination ->
                                val label = stringResource(destination.label)
                                NavigationBarItem(
                                    // Primary navigation is the most-used control on every screen, so it is
                                    // tagged frequent: R2 holds it to the thumb-zone ceiling, not the looser one.
                                    modifier = Modifier.primaryControl(destination.tag, frequent = true),
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        if (currentRoute != destination.route) {
                                            navController.navigate(destination.route) {
                                                // One entry per destination, and back from any tab returns to the
                                                // start destination rather than replaying a tab history.
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(imageVector = destination.icon(), contentDescription = null)
                                    },
                                    label = { Text(label) },
                                )
                            }

                            Spacer(Modifier.width(Reach.EDGE_INSET_DP.dp))
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = TopLevelDestination.Scores.route,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            composable(destination.route) {
                                when (destination) {
                                    TopLevelDestination.Scores -> {
                                        ScoresScreen(
                                            state = scoresState,
                                            onRefresh = scores::onPullToRefresh,
                                            onGameClick = { game ->
                                                navController.navigate(detailRoute(game))
                                            },
                                            onSelectDate = scores::selectDate,
                                            onOpenLeagues = { scope.launch { drawerState.open() } },
                                            onOpenCalendar = { calendarOpen = true },
                                        )
                                    }

                                    TopLevelDestination.Teams -> {
                                        TeamsDestination()
                                    }

                                    TopLevelDestination.Standings -> {
                                        StandingsDestination()
                                    }
                                }
                            }

                            composable(
                                route = DETAIL_ROUTE,
                                arguments = detailArguments(),
                            ) { entry ->
                                val game = gameFromRoute(entry)
                                if (game == null) {
                                    // A link we cannot read is not a crash; it is a dead end, so leave.
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                } else {
                                    DetailDestination(
                                        container = container,
                                        game = game,
                                        onBack = { navController.popBackStack() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (calendarOpen) {
            // Cover the entire shell, including tabs, while keeping the system gesture inset only once.
            CalendarMonthSheet(
                scoresState.selectedDate,
                scoresState.today,
                scoresState.dateIndex,
                scores::selectDate,
                { calendarOpen = false },
                bottomInsets = WindowInsets.navigationBars,
            )
        }
    }
}

/** The back stack entry owns the picker; its repositories are shared with Scores through the app. */
@Composable
private fun TeamsDestination() {
    val container = (LocalContext.current.applicationContext as RouxApp).container
    val teams: TeamsViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        TeamsViewModel(container.standings, container.preferences)
                    }
                },
        )
    val state by teams.uiState.collectAsStateWithLifecycle()
    TeamsScreen(
        state = state,
        onToggle = teams::toggle,
        onRetry = teams::retry,
    )
}

/** The back stack entry owns the table; the app supplies the shared repository and preferences. */
@Composable
private fun StandingsDestination() {
    val container = (LocalContext.current.applicationContext as RouxApp).container
    val standings: StandingsViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        StandingsViewModel(container.standings, container.preferences)
                    }
                },
        )
    val state by standings.uiState.collectAsStateWithLifecycle()
    StandingsScreen(
        state = state,
        onRefresh = standings::refresh,
        onSetView = standings::setView,
    )
}

/**
 * Hand-drawn 24 dp glyphs rather than a material-icons dependency: three shapes are cheaper than a
 * library, and the design's icon set is not settled yet.
 */
private fun TopLevelDestination.icon(): ImageVector =
    when (this) {
        // A scoreline: two stacked bars, the upper one shorter.
        TopLevelDestination.Scores -> glyph("Scores", "M4,6 h10 v3 h-10 z M4,12 h16 v3 h-16 z")

        // Ranked rows, narrowing as they go down.
        TopLevelDestination.Standings -> glyph("Standings", "M4,6 h16 v3 h-16 z M4,12 h11 v3 h-11 z M4,18 h7 v3 h-7 z")

        // Two figures side by side.
        TopLevelDestination.Teams -> glyph("Teams", "M4,7 h6 v6 h-6 z M14,7 h6 v6 h-6 z M4,15 h6 v6 h-6 z M14,15 h6 v6 h-6 z")
    }

private fun glyph(
    name: String,
    path: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

/* ---------------------------------------------------------------------------------------------------------------
 * The game page's route
 * ------------------------------------------------------------------------------------------------------------ */

/**
 * A game's page as a URL.
 *
 * The row that was tapped travels in the route rather than in a holder up the tree: a holder dies with the process,
 * and the reader would come back to an empty page. Every field is one the tapped row already had, so the page can
 * draw its hero before any endpoint answers.
 */
internal const val DETAIL_ROUTE =
    "detail/{gameId}?away={away}&home={home}&awayLogo={awayLogo}&homeLogo={homeLogo}" +
        "&awayScore={awayScore}&homeScore={homeScore}&state={state}&start={start}"

internal fun detailRoute(game: Game): String =
    "detail/${Uri.encode(game.id.value)}" +
        "?away=${Uri.encode(game.away.abbrev)}" +
        "&home=${Uri.encode(game.home.abbrev)}" +
        "&awayLogo=${Uri.encode(game.away.logoUrl.orEmpty())}" +
        "&homeLogo=${Uri.encode(game.home.logoUrl.orEmpty())}" +
        "&awayScore=${game.awayScore ?: MISSING_SCORE}" +
        "&homeScore=${game.homeScore ?: MISSING_SCORE}" +
        "&state=${Uri.encode(encodeState(game.state))}" +
        "&start=${game.startTimeUtc?.epochSeconds ?: MISSING_SCORE}"

private const val MISSING_SCORE = "-1"

private fun encodeState(state: GameState): String =
    when (state) {
        GameState.Final -> "FINAL"
        is GameState.Live -> if (state.critical) "CRIT" else "LIVE"
        GameState.Pre -> "PRE"
        GameState.Scheduled -> "SCHED"
        is GameState.Unknown -> "U:" + state.raw
    }

private fun decodeState(raw: String?): GameState =
    when {
        raw == null -> GameState.Scheduled
        raw == "FINAL" -> GameState.Final
        raw == "LIVE" -> GameState.Live()
        raw == "CRIT" -> GameState.Live(critical = true)
        raw == "PRE" -> GameState.Pre
        raw == "SCHED" -> GameState.Scheduled
        raw.startsWith("U:") -> GameState.Unknown(raw.removePrefix("U:"))
        else -> GameState.Unknown(raw)
    }

private fun detailArguments(): List<NamedNavArgument> =
    listOf(
        navArgument("gameId") { type = NavType.StringType },
        navArgument("away") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("home") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("awayScore") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("homeScore") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("state") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("start") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )

/** Null when the link carries nothing playable - the caller then backs out rather than showing a guess. */
internal fun gameFromRoute(entry: NavBackStackEntry): Game? {
    val args = entry.arguments ?: return null
    val id = args.getString("gameId")?.takeIf { it.isNotBlank() } ?: return null
    val away = args.getString("away")?.takeIf { it.isNotBlank() } ?: return null
    val home = args.getString("home")?.takeIf { it.isNotBlank() } ?: return null
    val start = args.getString("start")?.toLongOrNull()?.takeIf { it > 0 }
    return Game(
        id = GameId(id),
        date = "",
        state = decodeState(args.getString("state")),
        // The crest travels with the route so the hero is drawn from the row the reader tapped, not from an endpoint
        // that may not have answered yet. A missing URL is fine: the badge falls back to the club's abbreviation.
        home = TeamRef(TeamKey(home), logoUrl = args.getString("homeLogo")?.takeIf { it.isNotBlank() }),
        away = TeamRef(TeamKey(away), logoUrl = args.getString("awayLogo")?.takeIf { it.isNotBlank() }),
        homeScore = args.getString("homeScore")?.toIntOrNull()?.takeIf { it >= 0 },
        awayScore = args.getString("awayScore")?.toIntOrNull()?.takeIf { it >= 0 },
        startTimeUtc = start?.let { Instant.fromEpochSeconds(it) },
    )
}

@Composable
private fun DetailDestination(
    container: AppContainer,
    game: Game,
    onBack: () -> Unit,
) {
    // Keyed by the game and its state: a rotation from scheduled to live is a different polling contract.
    val detail: GameDetailViewModel =
        viewModel(
            key = "detail:${game.id.value}:${encodeState(game.state)}",
            factory =
                viewModelFactory {
                    initializer {
                        GameDetailViewModel(
                            gameId = game.id,
                            state = game.state,
                            details = container.gameDetail,
                            clock = container.clock,
                        )
                    }
                },
        )
    val state by detail.uiState.collectAsStateWithLifecycle()
    GameDetailScreen(
        game = game,
        state = state,
        onBack = onBack,
        onRetry = detail::refresh,
    )
}
