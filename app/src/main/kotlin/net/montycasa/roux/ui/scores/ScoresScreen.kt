@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.policy.DateStrip
import net.montycasa.roux.domain.policy.FavoritesHint
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.StaleBanner
import net.montycasa.roux.ui.nav.NavSwipe
import net.montycasa.roux.ui.nav.stepOnHorizontalSwipe
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTarget
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DAY_SETTLE_MILLIS = 360

/**
 * Renders the selected day's state without owning loading or filtering. Callbacks map to refresh,
 * selectDate on ScoresViewModel; game navigation belongs to the host.
 * Cached rows survive every refresh failure, with recovery and frequent controls near the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoresScreen(
    state: ScoresUiState,
    onRefresh: () -> Unit,
    onGameClick: (Game) -> Unit,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLeagues: () -> Unit = {},
    onOpenCalendar: (() -> Unit)? = null,
    showHeader: Boolean = true,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter =
        remember(locale) {
            DateTimeFormatter.ofPattern("EEE, MMM d", locale).withZone(ZoneId.of("America/Toronto"))
        }
    val dateLabel = remember(state.selectedDate, formatter) { LocalDate.parse(state.selectedDate).format(formatter) }
    val missing = state.freshness == Freshness.Missing && state.rows.isEmpty()
    val loading = missing && state.isRefreshing && state.error == null
    val emptyDate = !missing && state.rows.isEmpty() && state.favoritesHint != FavoritesHint.NoFavouriteGames
    val nextDate = if (emptyDate) DateStrip.nextGameDate(state.dateIndex, state.selectedDate) else null
    // The plan's window is a week either way; the strip scrolls, so a partially visible chip is the
    // affordance, not a defect to be designed away.
    val dates =
        remember(state.dateIndex, state.selectedDate) {
            DateStrip.items(state.dateIndex, state.selectedDate)
        }

    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(if (calendarOpen) Modifier.clearAndSetSemantics { } else Modifier)) {
            if (showHeader) ScoresHeader(state.liveCount, Modifier.fillMaxWidth())
            // Left and right step through the loaded dates, which is the axis this screen already has. The chip row
            // and the list keep their own gestures: children claim their drags before this sees them.
            val dateOrder = state.dateIndex.map { it.date }.distinct()
            // One page per day, so a drag tracks the finger and the day being moved towards is already on screen.
            // A state with no index yet still needs a page: with none, the pager shows nothing and the list - including
            // its error and empty states - disappears with it.
            //
            // The list of pages is rebuilt only when the day changes, never when a load delivers a new day index. A pager
            // addresses its pages by position, so a rebuilt list re-points every page after the first difference: the
            // page under the finger then holds a different date, and the list shows one day's games beneath another
            // day's date until the pager is re-scrolled. Days the index adds at the far end are appended, which leaves
            // every existing page where it is.
            val pages = remember { mutableStateListOf<String>() }
            LaunchedEffect(state.selectedDate, dateOrder) {
                val selected = state.selectedDate
                when {
                    pages.isEmpty() -> {
                        pages.addAll(dateOrder.ifEmpty { listOf(selected) })
                    }

                    // Moved by a chip, the calendar or a swipe past the end of the list: re-anchor around the selection.
                    selected !in pages || pages.size == 1 -> {
                        pages.clear()
                        pages.addAll(dateOrder.ifEmpty { listOf(selected) })
                    }

                    // New days only ever arrive at the far end of the window.
                    else -> {
                        dateOrder.filter { it > pages.last() }.forEach { pages.add(it) }
                    }
                }
                if (selected !in pages) pages.add(selected)
            }
            val dayPager = rememberPagerState(initialPage = pages.indexOf(state.selectedDate).coerceAtLeast(0)) { pages.size }
            // Chips move the pager; a drag moves the selection. The remembered page is what keeps those two apart: only a
            // settle at a page we did not scroll to ourselves is a user gesture, and only that may change the day. Without
            // it the pager reports its opening page as if the viewer had chosen it, and overrides a selection it was
            // never told about.
            // A plain holder, not snapshot state: these effects only read it, and making it observable would recompose
            // the whole screen every time the pager settles.
            val syncedPage = remember { intArrayOf(-1) }
            LaunchedEffect(state.selectedDate, pages.size) {
                val target = pages.indexOf(state.selectedDate)
                if (target >= 0 && dayPager.currentPage != target) dayPager.scrollToPage(target)
                syncedPage[0] = dayPager.currentPage
            }
            LaunchedEffect(dayPager.settledPage) {
                if (dayPager.settledPage != syncedPage[0]) {
                    pages.getOrNull(dayPager.settledPage)?.let(onSelectDate)
                    syncedPage[0] = dayPager.settledPage
                }
            }
            val stepDate: (Int) -> Unit = { by ->
                val dates = state.dateIndex.map { it.date }.distinct()
                val at = dates.indexOf(state.selectedDate)
                if (at >= 0) dates.getOrNull(at + by)?.let(onSelectDate)
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
            ) {
                HorizontalPager(
                    state = dayPager,
                    // Off, horizontal drags belong to the shell's pager, exactly as before.
                    userScrollEnabled = !NavSwipe.TURNS_PAGES,
                    // The settle is the part you could not drag: the default spring covers it in a handful of frames,
                    // which reads as the page being yanked away at the end of the gesture. A tween eases that last
                    // stretch at the same pace the eye is already following.
                    flingBehavior =
                        PagerDefaults.flingBehavior(
                            state = dayPager,
                            snapAnimationSpec = tween(durationMillis = DAY_SETTLE_MILLIS, easing = FastOutSlowInEasing),
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) { page ->
                    val pageDate = pages.getOrNull(page)
                    // The selected day prefers its own rows but keeps the cached copy until those land: swapping the list
                    // for a loading state at the instant the page settles is the other half of the end-of-swipe jolt.
                    // Rows belong to a day, not to "the selected one": state.rows is whatever day was last loaded, so
                    // during the frames after a settle it can still be the day you just left. Reading it for a page that
                    // is not that day is a flash of the wrong slate; the page shows its own cached copy until the load
                    // lands, and only then swaps to state.rows - the same games, same keys, no rebuild.
                    // A row list with no day recorded on it can only belong to the day being shown; once a day is
                    // named, only that day may use it.
                    val rowsAreThisPage =
                        state.loadedDate == pageDate || (state.loadedDate == null && pageDate == state.selectedDate)
                    // Whatever the state hands this page, fall back to the day's known rows rather than draw nothing: a
                    // page that renders empty for one frame is the flicker, and rows we already have are always better
                    // than a gap while the same day is being fetched again.
                    val pageRows =
                        (if (rowsAreThisPage) state.rows else emptyList()).ifEmpty {
                            state.rowsByDate[pageDate].orEmpty()
                        }
                    // The indicator belongs to the page you are looking at, and only while that same day is being
                    // refreshed. A neighbour page loading in the background used to paint a spinner on whatever page
                    // happened to be on screen - that flash just after the settle is precisely this, not a layout jump.
                    val refreshingThisPage =
                        state.isRefreshing && pageDate == state.selectedDate && state.loadedDate == pageDate
                    PullToRefreshBox(
                        isRefreshing = refreshingThisPage,
                        onRefresh = onRefresh,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("scores.refresh")
                                .reachTarget("scores.pullToRefresh"),
                    ) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            // The reference queries .listwrap before the list's 12 dp side padding.
                            val narrow = maxWidth <= 380.dp
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().testTag("scores.list"),
                                verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
                            ) {
                                if (missing) {
                                    item {
                                        Column(Modifier.padding(Reach.EDGE_INSET_DP.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                stringResource(if (loading) R.string.scores_loading else R.string.scores_load_error),
                                                modifier = Modifier.testTag(if (loading) "scores.loading" else "scores.error"),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            state.error?.let { Text(errorMessage(it), style = MaterialTheme.typography.bodyLarge) }
                                        }
                                    }
                                } else {
                                    state.favoritesHint?.let { hint ->
                                        item {
                                            Text(
                                                stringResource(
                                                    when (hint) {
                                                        FavoritesHint.PickTeams -> R.string.scores_pick_teams
                                                        FavoritesHint.NoFavouriteGames -> R.string.scores_no_favourite_games
                                                    },
                                                ),
                                                modifier = Modifier.padding(Reach.EDGE_INSET_DP.dp),
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        }
                                    }
                                    if (emptyDate) {
                                        item {
                                            Text(
                                                stringResource(R.string.scores_empty_date, dateLabel),
                                                modifier = Modifier.padding(Reach.EDGE_INSET_DP.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    }
                                    items(pageRows, key = { it.id.value }) { game ->
                                        GameRow(
                                            game,
                                            narrow = narrow,
                                            modifier =
                                                Modifier
                                                    .padding(horizontal = 12.dp)
                                                    .testTag("scores.game.${game.id.value}")
                                                    .clickable { onGameClick(game) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Reach.EDGE_INSET_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
                ) {
                    val stale = state.freshness as? Freshness.Stale
                    when {
                        stale != null -> {
                            StaleBanner(
                                since = stale.since,
                                onRetry = onRefresh,
                                isRefreshing = state.isRefreshing,
                                supportingText = if (state.error != null) stringResource(R.string.scores_refresh_error) else null,
                            )
                        }

                        missing && !loading -> {
                            RetryButton(onRefresh, state.isRefreshing)
                        }

                        state.error != null && state.rows.isNotEmpty() -> {
                            Text(stringResource(R.string.scores_refresh_error), style = MaterialTheme.typography.bodyMedium)
                            RetryButton(onRefresh, state.isRefreshing)
                        }
                    }
                    // Recovery takes priority over date navigation so a dead screen has one clear exit.
                    if (nextDate != null && stale == null && state.error == null) {
                        Button(
                            onClick = { onSelectDate(nextDate) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                                    .primaryControl("scores.nextGames"),
                        ) {
                            Text(stringResource(R.string.scores_next_games, LocalDate.parse(nextDate).format(formatter)))
                        }
                    }
                }

                // The shell lifts the modal over its tabs; a standalone screen keeps the same overlay here.
                DateShelf(dates, state.selectedDate, state.today, onSelectDate, onOpenCalendar ?: { calendarOpen = true })
            }
        }
        if (calendarOpen) {
            CalendarMonthSheet(state.selectedDate, state.today, state.dateIndex, onSelectDate, { calendarOpen = false })
        }
    }
}

@Composable
private fun errorMessage(error: LoadError): String =
    stringResource(
        when (error) {
            LoadError.Network -> R.string.scores_network_error
            is LoadError.Http -> R.string.scores_http_error
            LoadError.Parse -> R.string.scores_parse_error
            is LoadError.MismatchedDay -> R.string.scores_wrong_day_error
            LoadError.Local -> R.string.scores_local_error
            is LoadError.Blocked -> R.string.scores_blocked_error
        },
    )
