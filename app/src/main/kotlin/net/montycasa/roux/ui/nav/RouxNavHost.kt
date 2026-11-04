// Compose names composables in PascalCase; ktlint's function-naming rule reads that as a misnamed
// function. Suppressed for this file only, rather than changing the rule for the whole repository.
@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.nav

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import net.montycasa.roux.AppContainer
import net.montycasa.roux.R
import net.montycasa.roux.RouxApp
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.notify.GameWatchReconciler
import net.montycasa.roux.notify.WorkManagerGameWatchScheduler
import net.montycasa.roux.notify.hasNotificationPermission
import net.montycasa.roux.notify.notificationsAllowed
import net.montycasa.roux.notify.openNotificationSettings
import net.montycasa.roux.ui.detail.GameDetailScreen
import net.montycasa.roux.ui.detail.GameDetailViewModel
import net.montycasa.roux.ui.drawer.BottomLeaguesSheet
import net.montycasa.roux.ui.drawer.LeaguesPanel
import net.montycasa.roux.ui.drawer.LeaguesSheetState
import net.montycasa.roux.ui.drawer.rememberLeaguesSheetSettler
import net.montycasa.roux.ui.player.PlayerDetailScreen
import net.montycasa.roux.ui.player.PlayerDetailViewModel
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.scores.CalendarMonthSheet
import net.montycasa.roux.ui.scores.MY_TEAMS_LEAGUE
import net.montycasa.roux.ui.scores.ScoresHeader
import net.montycasa.roux.ui.scores.ScoresScreen
import net.montycasa.roux.ui.scores.ScoresViewModel
import net.montycasa.roux.ui.standings.StandingsScreen
import net.montycasa.roux.ui.standings.StandingsViewModel
import net.montycasa.roux.ui.team.TeamDetailScreen
import net.montycasa.roux.ui.team.TeamDetailViewModel
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
fun RouxNavHost(
    modifier: Modifier = Modifier,
    /** A game a notification was tapped for, opened once the slate it belongs to is loaded. */
    requestedGame: GameRequest? = null,
    /** Called once the request has been acted on, so the same tap twice is a second navigation rather than none. */
    onRequestHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val appContext = LocalContext.current.applicationContext
    val container = (appContext as RouxApp).container
    val scores: ScoresViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer { ScoresViewModel(container.scores, container.preferences, container.clock) }
                },
        )
    // My Teams is a destination that spans leagues, not a league: there is no table to read there, so Standings
    // leaves both the bar and the pager while it is selected.
    val drawerUi = scores.drawerState.collectAsState().value
    val starMode = drawerUi.selectedLeagueId == MY_TEAMS_LEAGUE
    // Remembered, not rebuilt per composition: a fresh list on every frame hands the pager a new page count
    // mid-gesture, and a drag in progress is then cancelled before it can move a page.
    val pages =
        remember(starMode) {
            if (starMode) {
                listOf(TopLevelDestination.Scores, TopLevelDestination.Teams)
            } else {
                TopLevelDestination.entries.toList()
            }
        }
    // The destinations are pages of one surface, so a horizontal swipe moves between them the way the bar does.
    val pagerState = key(starMode) { rememberPagerState(initialPage = 0) { pages.size } }
    // A page that no longer exists cannot stay selected: entering My Teams from Standings lands on Scores.
    LaunchedEffect(pages.size) {
        if (pagerState.currentPage > pages.lastIndex) pagerState.animateScrollToPage(0)
    }
    // Only Scores subscribes: leaving its page still stops polling after WhileSubscribed's grace.
    val onScoresPage = currentRoute == HOME_ROUTE && pages.getOrNull(pagerState.currentPage) == TopLevelDestination.Scores
    val scoresState =
        if (onScoresPage) {
            scores.uiState.collectAsStateWithLifecycle().value
        } else {
            // Freeze the last Scores state while another tab owns the active collection.
            remember(scores, currentRoute) { scores.uiState.value }
        }
    // The sheet's position is the state: a surface that follows a finger cannot be a boolean.
    val sheetState = remember { LeaguesSheetState() }
    val settle = rememberLeaguesSheetSettler(sheetState)
    // The bar's own height, measured: the sheet stops above it instead of covering it, and the strip of bar that
    // would otherwise be left showing is the reason the height is measured rather than assumed.
    val scope = rememberCoroutineScope()
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    // Editing is a mode of the panel, not of the app: however the sheet closes, it reopens read-only.
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState.isOpen) { if (!sheetState.isOpen) editing = false }
    val openLeagues: () -> Unit = {
        scope.launch {
            val target = sheetState.capPx
            animate(sheetState.reveal, target, animationSpec = tween(durationMillis = 220)) { value, _ ->
                sheetState.set(value)
            }
        }
        Unit
    }
    val closeLeagues: () -> Unit = {
        scope.launch {
            animate(sheetState.reveal, 0f, animationSpec = tween(durationMillis = 220)) { value, _ ->
                sheetState.set(value)
            }
        }
        Unit
    }
    // Back dismisses the menu before it dismisses the screen: an open surface owns the button.
    BackHandler(enabled = sheetState.isOpen) { closeLeagues() }
    Box(modifier.fillMaxSize()) {
        // While the menu is out the page is dimmed and untouchable, so nothing behind it is read out either.
        Box(Modifier.then(if (calendarOpen || sheetState.isOpen) Modifier.clearAndSetSemantics { } else Modifier)) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (currentRoute == HOME_ROUTE) {
                        ScoresHeader(scoresState.liveCount, Modifier.fillMaxWidth())
                    }
                },
                bottomBar = {
                    val tracker = remember { VelocityTracker() }
                    // The bar never moves. It is the sheet's handle, not part of the sheet, so the seats the thumb
                    // already knows do not slide away when the menu opens.
                    Column(
                        Modifier
                            // The bar owns the window's bottom now that the dial is gone. The colour runs to the edge
                            // behind the gesture pill, and the air below the labels is inside that colour so the strip
                            // under the seats is never a slice of page showing through.
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            // The tag covers the whole strip, air included: the bar is the thing that reaches the
                            // edge of the window, so measuring it from the labels inward would understate its height.
                            .testTag("nav.bar")
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = Reach.BAR_BOTTOM_AIR_DP.dp)
                            .pointerInput(sheetState) {
                                detectVerticalDragGestures(
                                    onDragStart = { tracker.resetTracking() },
                                    // Up is negative: a flick upward has to carry the sheet the same way.
                                    onDragEnd = { settle(-tracker.calculateVelocity().y) },
                                    onDragCancel = { settle(0f) },
                                ) { change, dragAmount ->
                                    change.consume()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    sheetState.drag(-dragAmount)
                                }
                            },
                    ) {
                        NavigationBar(
                            // A stock NavigationBar sits on Material's tonal grey, which is the one cool surface the
                            // warm palette does not have. The bar and the dial below it are one piece.
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            // The bar's own colour and padding carry the window's bottom inset; the tabs would
                            // otherwise add it a second time.
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            // R5 keeps primary controls 16 dp clear of the screen edges, where the back gesture
                            // lives. A stock NavigationBar puts its outer items flush to the edges, so the bar's
                            // own background stays full-bleed and the items are pushed inside it by spacers -
                            // padding the bar itself would inset its background and leave a strip of page showing.
                            Spacer(Modifier.width(Reach.EDGE_INSET_DP.dp))
                            // The menu is a button, not a seat. It is drawn as one for a concrete reason: inside a
                            // NavigationBarItem, an unlabelled icon gets the space left over once the label is
                            // reserved, and a 36 dp circle was being clipped to about 23 dp tall. Here it owns its
                            // slot, sits its circle on the same row as the tab icons, and carries no selected state -
                            // it opens a surface, it does not become one.
                            Box(
                                modifier =
                                    Modifier
                                        // An explicit height, not fillMaxHeight: inside NavigationBar's Row an
                                        // unconstrained fill measured against the bar's own content and squeezed the
                                        // circle's padding away.
                                        .width(72.dp)
                                        .height(Reach.BAR_HEIGHT_DP.dp)
                                        .clickable(
                                            role = Role.Button,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { if (sheetState.isOpen) closeLeagues() else openLeagues() },
                                        ).primaryControl("nav.menu", frequent = true)
                                        .testTag("nav.menu"),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Icon(
                                    imageVector = menuGlyph(),
                                    contentDescription = stringResource(R.string.nav_menu),
                                    // Same 24 dp as the destination glyphs, sat on the same row: the accent colour is
                                    // what sets it apart, not its size. A follower that is bigger than the things it
                                    // sits beside reads as a control that is trying too hard.
                                    modifier =
                                        Modifier
                                            .padding(top = Reach.BAR_ICON_TOP_DP.dp)
                                            .size(24.dp)
                                            .testTag("nav.menu.icon"),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            pages.forEachIndexed { index, destination ->
                                val label = stringResource(destination.label)
                                NavigationBarItem(
                                    // Primary navigation is the most-used control on every screen, so it is
                                    // tagged frequent: R2 holds it to the thumb-zone ceiling, not the looser one.
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            // The default selected pill is Material's secondary container, the one
                                            // lavender surface in a warm palette: a wash of the app's own orange.
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        ),
                                    modifier = Modifier.primaryControl(destination.tag, frequent = true),
                                    selected = currentRoute == HOME_ROUTE && pagerState.currentPage == index,
                                    onClick = {
                                        // A tab press from inside a game returns to the pages, then lands on the tab.
                                        if (currentRoute != HOME_ROUTE) {
                                            navController.popBackStack(HOME_ROUTE, inclusive = false)
                                        }
                                        // The menu is not a place you stay: a tab press leaves it.
                                        if (sheetState.isOpen) closeLeagues()
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    icon = {
                                        Icon(imageVector = destination.icon(), contentDescription = null)
                                    },
                                    label = { Text(label) },
                                )
                            }

                            Spacer(Modifier.width(Reach.EDGE_INSET_DP.dp))
                        }
                    }
                },
            ) { innerPadding ->
                // A tapped notification names a game, not a route: the detail route carries the whole card, so
                // the game is looked up in the slate first and the route is built from what the feed says now.
                // An id that is not in today's games - a stale notification, or a game from an earlier day -
                // opens nothing rather than an empty screen.
                LaunchedEffect(requestedGame) {
                    val request = requestedGame ?: return@LaunchedEffect
                    val today = deviceDate(container.clock.now())
                    val game =
                        listOf(today, previousDay(today))
                            .firstNotNullOfOrNull { date ->
                                container.scores
                                    .loadDay(date, today)
                                    .data
                                    ?.games
                                    ?.firstOrNull { it.id.value == request.gameId }
                            }
                    if (game != null) navController.navigate(detailRoute(game))
                    onRequestHandled()
                }

                NavHost(
                    navController = navController,
                    startDestination = HOME_ROUTE,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable("settings") {
                        val mode by remember(container.preferences) { container.preferences.observeThemeMode() }
                            .collectAsStateWithLifecycle(initialValue = ThemeMode.System)
                        net.montycasa.roux.ui.settings.SettingsScreen(
                            mode = mode,
                            onMode = { mode -> scope.launch { container.preferences.setThemeMode(mode) } },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(HOME_ROUTE) {
                        HorizontalPager(
                            state = pagerState,
                            // Off, the pager ignores horizontal drags and each screen keeps them for itself.
                            userScrollEnabled = NavSwipe.TURNS_PAGES,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .testTag(PAGER_TAG),
                        ) { page ->
                            when (pages[page]) {
                                TopLevelDestination.Scores -> {
                                    ScoresScreen(
                                        state = scoresState,
                                        onRefresh = scores::onPullToRefresh,
                                        onGameClick = { game -> navController.navigate(detailRoute(game)) },
                                        onSelectDate = scores::selectDate,
                                        onOpenLeagues = openLeagues,
                                        onOpenCalendar = { calendarOpen = true },
                                        // The shell draws the bar once, above the sheet.
                                        showHeader = false,
                                    )
                                }

                                TopLevelDestination.Teams -> {
                                    TeamsDestination(onTeamClick = { key -> navController.navigate(teamRoute(key)) })
                                }

                                TopLevelDestination.Standings -> {
                                    StandingsDestination()
                                }
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
                                onTeamClick = { team -> navController.navigate(teamRoute(team.key)) },
                            )
                        }
                    }

                    composable(
                        route = PLAYER_ROUTE,
                        arguments = playerArguments(),
                    ) { entry ->
                        val playerId = playerIdFromRoute(entry)
                        if (playerId == null) {
                            // A link that names no player is not a crash; it goes back to where it came from.
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            PlayerDetailDestination(
                                container = container,
                                playerId = playerId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }

                    composable(
                        route = TEAM_ROUTE,
                        arguments = teamArguments(),
                    ) { entry ->
                        val teamKey = teamKeyFromRoute(entry)
                        if (teamKey == null) {
                            // A link that names no club is not a crash; it goes back to where it came from.
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            TeamDetailDestination(
                                container = container,
                                teamKey = teamKey,
                                onBack = { navController.popBackStack() },
                                onGameClick = { game -> navController.navigate(detailRoute(game)) },
                                onPlayerClick = { playerId -> navController.navigate(playerRoute(playerId)) },
                            )
                        }
                    }
                }
            }
        }
        BottomLeaguesSheet(
            state = sheetState,
            onDismissRequest = closeLeagues,
        ) {
            LeaguesPanel(
                drawer = drawerUi,
                seasonDate = scoresState.loadedDate,
                onSettings = {
                    closeLeagues()
                    navController.navigate("settings") { launchSingleTop = true }
                },
                editing = editing,
                onEditing = { editing = it },
                onLeagueOrder = scores::setLeagueOrder,
                onLeagueSelected = { id ->
                    scores.selectLeague(id)
                    closeLeagues()
                },
                onFavouritesOnly = { only ->
                    scores.setFavouritesOnly(only)
                    closeLeagues()
                },
            )
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
private fun TeamsDestination(onTeamClick: (TeamKey) -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val container = (appContext as RouxApp).container
    val teams: TeamsViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        TeamsViewModel(container.standings, container.preferences) {
                            // Saving an alert changes what should be watched, so the watches are rebuilt from
                            // the setting that was just stored - not left until the next periodic reconcile.
                            GameWatchReconciler(
                                preferences = container.preferences,
                                scores = container.scores,
                                scheduler = WorkManagerGameWatchScheduler(WorkManager.getInstance(appContext)),
                                clock = container.clock,
                            ).reconcile()
                        }
                    }
                },
        )
    val state by teams.uiState.collectAsStateWithLifecycle()

    // Whether a notification could land at all. Re-read on resume, because the user can change it in system
    // settings and come back, and a cached "off" would leave the switches dead with no explanation.
    var permissionGranted by remember { mutableStateOf(hasNotificationPermission(appContext)) }
    var notificationsEnabled by remember { mutableStateOf(notificationsAllowed(appContext)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    permissionGranted = hasNotificationPermission(appContext)
                    notificationsEnabled = notificationsAllowed(appContext)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The permission is asked for when the user turns an alert on - the moment their intent exists - and the
    // setting they asked for is applied only after it is granted, so a refusal does not silently store an alert
    // that can never be delivered.
    var pendingEnable by remember { mutableStateOf<(() -> Unit)?>(null) }
    // A dialog that comes back on the next recomposition is a dialog the user has to escape twice.
    var promptDismissed by remember { mutableStateOf(false) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            notificationsEnabled = notificationsAllowed(appContext)
            if (granted) pendingEnable?.invoke()
            pendingEnable = null
        }
    val withPermission: (() -> Unit) -> Unit = { action ->
        if (permissionGranted) {
            action()
        } else {
            pendingEnable = action
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    TeamsScreen(
        state = state,
        onToggle = teams::toggle,
        onRetry = teams::retry,
        onTeamClick = onTeamClick,
        onOpenAlerts = teams::openAlerts,
        onCloseAlerts = teams::closeAlerts,
        onSetStartAlert = { enabled -> withPermission { teams.setAlert(start = enabled) } },
        onSetGoalAlert = { enabled -> withPermission { teams.setAlert(goals = enabled) } },
        permissionGranted = permissionGranted,
        notificationsEnabled = notificationsEnabled,
        onOpenNotificationSettings = { openNotificationSettings(appContext) },
        onEnableNotifications = {
            // A missing permission can be asked for here; notifications switched off for the whole app cannot,
            // and the system screen is the only place that can fix it.
            if (!permissionGranted) {
                pendingEnable = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                openNotificationSettings(appContext)
            }
        },
        onDismissPrompt = { promptDismissed = true },
        promptDismissed = promptDismissed,
    )
}

/** The back stack entry owns the table; the app supplies the shared repository and preferences. */
@Composable
private fun StandingsDestination() {
    val appContext = LocalContext.current.applicationContext
    val container = (appContext as RouxApp).container
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
        onSelect = standings::select,
    )
}

private fun menuGlyph(): ImageVector = glyph("Menu", "M2,6.5 h20 v2.5 h-20 z M2,10.75 h20 v2.5 h-20 z M2,15 h20 v2.5 h-20 z")

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
 * The club page's route
 * ------------------------------------------------------------------------------------------------------------ */

/**
 * A club's page as a URL.
 *
 * The abbreviation is the only identity every entry point has, and it is small enough to survive a process
 * restoration in the back stack. Everything else on the page comes from the standings cache.
 */
internal const val TEAM_ROUTE = "team/{teamKey}"

internal fun teamRoute(team: TeamKey): String = "team/${Uri.encode(team.abbrev)}"

private fun teamArguments(): List<NamedNavArgument> = listOf(navArgument("teamKey") { type = NavType.StringType })

/** Null when the route carries no club, so the caller backs out rather than opening a page with no identity. */
internal fun teamKeyFromRoute(entry: NavBackStackEntry): TeamKey? {
    val abbrev = entry.arguments?.getString("teamKey")?.takeIf { it.isNotBlank() } ?: return null
    return TeamKey(abbrev)
}

/* ---------------------------------------------------------------------------------------------------------------
 * The game page's route
 * ------------------------------------------------------------------------------------------------------------ */

/**
 * A player's page as a URL.
 *
 * The id is the only identity a Players row has, and it is enough: the page loads its own profile from the id.
 */
internal const val PLAYER_ROUTE = "player/{playerId}"

internal fun playerRoute(playerId: Long): String = "player/$playerId"

private fun playerArguments(): List<NamedNavArgument> = listOf(navArgument("playerId") { type = NavType.LongType })

/** Null when the route carries no usable id, so the caller backs out rather than opening a page with no player. */
internal fun playerIdFromRoute(entry: NavBackStackEntry): Long? = entry.arguments?.getLong("playerId")?.takeIf { it > 0 }

/** The three destinations share one route: they are pages of a single surface, not separate destinations. */
internal const val HOME_ROUTE = "home"

/** The swipe surface: the pager itself, not the window, so a drag has somewhere to start. */
internal const val PAGER_TAG = "pages.pager"

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

/**
 * A game a notification asked for.
 *
 * [nonce] exists because the same game can be tapped twice: an id alone would be an unchanged value the second
 * time, the effect would not run, and the tap would do nothing. The nonce is the difference between "the same
 * request still standing" and "a new request for the same game".
 */
data class GameRequest(
    val gameId: String,
    val nonce: Long,
)

/** The previous civil day, for a game that is still being played after midnight. */
internal fun previousDay(date: String): String =
    java.time.LocalDate
        .parse(date)
        .minusDays(1)
        .toString()

/** The device's civil date, the same day the scores screen would show. */
private fun deviceDate(instant: kotlin.time.Instant): String =
    java.time.Instant
        .ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toString()

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

/**
 * A schedule row opens the same page as a scores row. Finished schedule games carry their scores on
 * the team objects, so the route can pass them through; an absent score stays absent rather than
 * becoming zero.
 */
internal fun detailRoute(game: ClubGame): String = detailRoute(game.toGame())

internal fun ClubGame.toGame(): Game =
    Game(
        id = id,
        date = date,
        state = state,
        home = home,
        away = away,
        startTimeUtc = startTimeUtc,
        homeScore = homeScore,
        awayScore = awayScore,
    )

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
    onTeamClick: (TeamRef) -> Unit,
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
        onTeamClick = onTeamClick,
    )
}

@Composable
private fun TeamDetailDestination(
    container: AppContainer,
    teamKey: TeamKey,
    onBack: () -> Unit,
    onGameClick: (ClubGame) -> Unit,
    onPlayerClick: (Long) -> Unit,
) {
    // Keyed by club so a second club cannot inherit the first one's loaded row.
    val detail: TeamDetailViewModel =
        viewModel(
            key = "team:${teamKey.abbrev}",
            factory =
                viewModelFactory {
                    initializer {
                        TeamDetailViewModel(
                            teamKey = teamKey,
                            standings = container.standings,
                            schedule = container.clubSchedule,
                            roster = container.clubRoster,
                            stats = container.clubStats,
                            leagueStats = container.leagueStats,
                            clock = container.clock,
                        )
                    }
                },
        )
    val state by detail.uiState.collectAsStateWithLifecycle()
    TeamDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = detail::retry,
        onPlayersRetry = detail::retryPlayers,
        onLeagueStatsRetry = detail::retryLeagueStats,
        onGameClick = onGameClick,
        onPlayerClick = onPlayerClick,
    )
}

@Composable
private fun PlayerDetailDestination(
    container: AppContainer,
    playerId: Long,
    onBack: () -> Unit,
) {
    // Keyed by player so a second player cannot inherit the first one's loaded profile.
    val detail: PlayerDetailViewModel =
        viewModel(
            key = "player:$playerId",
            factory =
                viewModelFactory {
                    initializer {
                        PlayerDetailViewModel(
                            playerId = playerId,
                            repository = container.playerProfile,
                            clock = container.clock,
                        )
                    }
                },
        )
    val state by detail.uiState.collectAsStateWithLifecycle()
    PlayerDetailScreen(
        state = state,
        currentSeason = detail.currentSeason,
        onBack = onBack,
        onRetry = detail::retry,
    )
}
