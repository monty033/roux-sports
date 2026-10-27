// Compose names composables in PascalCase; ktlint's function-naming rule reads that as a misnamed
// function. Suppressed for this file only, rather than changing the rule for the whole repository.
@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.montycasa.roux.R
import net.montycasa.roux.RouxApp
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.scores.ScoresScreen
import net.montycasa.roux.ui.scores.ScoresViewModel

/**
 * The three places the app goes. Scores is the reason the app exists; the other two are peers, not
 * sub-screens of it, so they sit on the bar rather than behind a menu (R6).
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @StringRes val placeholder: Int,
    val tag: String,
) {
    Scores("scores", R.string.nav_scores, R.string.nav_scores_placeholder, "nav.scores"),
    Standings("standings", R.string.nav_standings, R.string.nav_standings_placeholder, "nav.standings"),
    Teams("teams", R.string.nav_teams, R.string.nav_teams_placeholder, "nav.teams"),
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                    if (destination == TopLevelDestination.Scores) {
                        ScoresDestination()
                    } else {
                        PlaceholderScreen(title = stringResource(destination.label), body = stringResource(destination.placeholder))
                    }
                }
            }
        }
    }
}

/**
 * The back stack entry owns this ViewModel; the application supplies its shared dependencies.
 * Lifecycle collection suspends polling when Scores leaves the foreground. Row clicks are a no-op
 * until Stage 7 supplies game detail, so an otherwise useful row cannot crash on an unfinished route.
 */
@Composable
private fun ScoresDestination() {
    val container = (LocalContext.current.applicationContext as RouxApp).container
    val scores: ScoresViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        ScoresViewModel(container.scores, container.preferences, container.clock)
                    }
                },
        )
    val state by scores.uiState.collectAsStateWithLifecycle()
    ScoresScreen(
        state = state,
        onRefresh = scores::onPullToRefresh,
        onGameClick = {},
        onSelectDate = scores::selectDate,
        onToggleFavourites = scores::setFavouritesOnly,
    )
}

/**
 * What a destination shows until its stage lands. The title is the destination's own name so a
 * screenshot cannot be mistaken for the wrong tab, and the body says plainly that this is not the
 * feature yet rather than showing an empty list that reads as "no games today".
 */
@Composable
private fun PlaceholderScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
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
