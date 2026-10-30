@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.standings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.policy.StandingsGroups
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.StaleBanner
import net.montycasa.roux.ui.nav.NavSwipe
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTarget

private const val VIEW_SETTLE_MILLIS = 360

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    state: StandingsUiState,
    onRefresh: () -> Unit,
    onSetView: (StandingsView) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One page per view, so a drag tracks the finger and the neighbouring table is already on screen as you move
    // towards it. The view list is fixed - four entries, known at compile time - so no arriving data can re-point a
    // page underneath the finger, which is the trap the scores pager had to be fixed for.
    val views = StandingsView.entries
    val pager = rememberPagerState(initialPage = state.view.ordinal) { views.size }
    // Only a settle at a page we did not scroll to ourselves is a gesture; without it the pager reports its opening
    // page as if the viewer had chosen it.
    val syncedPage = remember { intArrayOf(-1) }
    LaunchedEffect(state.view) {
        if (pager.currentPage != state.view.ordinal) pager.scrollToPage(state.view.ordinal)
        syncedPage[0] = pager.currentPage
    }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != syncedPage[0]) {
            views.getOrNull(pager.settledPage)?.let(onSetView)
            syncedPage[0] = pager.settledPage
        }
    }
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StandingsColumns(
            null,
            Modifier.padding(horizontal = 16.dp).heightIn(min = 56.dp).testTag("standings.header"),
        )
        HorizontalPager(
            state = pager,
            // Off, horizontal drags belong to the shell's pager, exactly as before.
            userScrollEnabled = !NavSwipe.TURNS_PAGES,
            // Same reason as the scores day pager: the default settle spring covers the rest of the page in a handful
            // of frames, which reads as the table being yanked away at the end of the gesture.
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = tween(durationMillis = VIEW_SETTLE_MILLIS, easing = FastOutSlowInEasing),
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) { page ->
            val view = views[page]
            // Only the page being viewed owns the tags: four tables in the tree would otherwise answer every query.
            val primary = view == state.view
            val groups = state.groupsByView[view] ?: if (primary) state.groups else StandingsGroups(emptyList())
            PullToRefreshBox(
                state.isRefreshing && primary,
                onRefresh,
                Modifier
                    .fillMaxSize()
                    .then(if (primary) Modifier.testTag("standings.refresh").reachTarget("standings.refresh") else Modifier),
            ) {
                // Only 32 teams: composing the whole collection also keeps every row available to accessibility.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .then(if (primary) Modifier.testTag("standings.list") else Modifier)
                        .padding(horizontal = 16.dp),
                    // A pixel of rounding headroom keeps every measured gap above the 8 dp minimum.
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    when {
                        state.loading -> {
                            if (primary) Text(stringResource(R.string.standings_loading))
                        }

                        groups.sections.isEmpty() -> {
                            if (primary) {
                                Text(
                                    stringResource(if (state.error != null) R.string.standings_error else R.string.standings_empty),
                                )
                            }
                        }

                        else -> {
                            groups.sections.forEachIndexed { sectionIndex, section ->
                                Text(section.title, style = MaterialTheme.typography.titleMedium)
                                section.rows.forEachIndexed { index, row ->
                                    StandingsColumns(
                                        row,
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 49.dp)
                                            .then(
                                                if (primary) {
                                                    Modifier
                                                        .testTag("standings.row.$sectionIndex.${row.team.abbrev}")
                                                        .reachTarget("standings.row.$sectionIndex.${row.team.abbrev}")
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    )
                                    if (section.playoffCutAfter == index) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            HorizontalDivider(Modifier.weight(1f))
                                            Text(
                                                stringResource(R.string.standings_playoff_line),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                            HorizontalDivider(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.stale != null -> {
                    StaleBanner(
                        since = state.stale.since,
                        onRetry = onRefresh,
                        isRefreshing = state.isRefreshing,
                        supportingText = if (state.error != null) stringResource(R.string.standings_refresh_error) else null,
                        retryTag = "standings.retry",
                    )
                }

                state.error != null && !state.loading && state.groups.sections.isNotEmpty() -> {
                    // Rows without a stale marker: say what went wrong rather than offering a bare Retry,
                    // and do not invent an update time the repository never claimed.
                    Text(
                        stringResource(R.string.standings_refresh_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    RetryButton(onRefresh, state.isRefreshing, "standings.retry")
                }

                state.error != null && !state.loading -> {
                    RetryButton(onRefresh, state.isRefreshing, "standings.retry")
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 56.dp)
                .selectableGroup()
                .primaryControl("standings.view", frequent = true),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StandingsView.entries.forEach { view ->
                Surface(
                    color = if (view == state.view) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .selectable(view == state.view, role = Role.Tab, onClick = { onSetView(view) })
                            .testTag("standings.view.$view"),
                ) {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(
                                when (view) {
                                    StandingsView.League -> R.string.standings_league
                                    StandingsView.Conference -> R.string.standings_conference
                                    StandingsView.Division -> R.string.standings_division
                                    StandingsView.Wildcard -> R.string.standings_wild_card
                                },
                            ),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/** Header and values share weights so every statistic occupies the same column at every font size. */
@Composable
private fun StandingsColumns(
    row: StandingRow?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(row?.team?.abbrev ?: stringResource(R.string.standings_team), Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
        // record is the domain's null-aware display form; split it to align W/L/OTL with their headers.
        val record = row?.record?.split('-')
        val cells =
            if (row == null) {
                listOf(
                    R.string.standings_gp,
                    R.string.standings_w,
                    R.string.standings_l,
                    R.string.standings_otl,
                    R.string.standings_pts,
                ).map { stringResource(it) }
            } else {
                listOf(row.gp.toString(), record!![0], record[1], record[2], row.pts.toString())
            }
        cells.forEachIndexed { index, value ->
            Text(
                value,
                Modifier.weight(1f).testTag("standings.cell.$index"),
                textAlign = TextAlign.End,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (index == 4) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
