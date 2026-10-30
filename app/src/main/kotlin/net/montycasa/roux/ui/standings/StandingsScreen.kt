@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.standings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.policy.StandingsGroups
import net.montycasa.roux.domain.policy.StandingsView
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.StaleBanner
import net.montycasa.roux.ui.nav.NavSwipe
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.scores.TeamLogo
import net.montycasa.roux.ui.theme.Roux

private const val VIEW_SETTLE_MILLIS = 360

/** Rank sits in a fixed gutter so the numbers form a column instead of drifting with the club name. */
private val RANK_WIDTH = 18.dp

/** The same crest the scores cards use, at table size: identity without the card's height. */
private val CREST_SIZE = 20.dp

private val CHIP_SHAPE = RoundedCornerShape(12.dp)

/** Identity takes three shares of the row, each statistic one, so the numeric block stays legible at 360 dp. */
private const val IDENTITY_WEIGHT = 3f
private const val STAT_WEIGHT = 1f

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
        StandingsHeader(
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
                    // Rows sit close together on purpose: they are text, not controls, so the table can take the
                    // tighter rhythm that keeps a whole division on one screen.
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                // More space above a section than below it is what makes the title read as a heading
                                // rather than as another row in a run of evenly spaced text.
                                StandingsSectionTitle(
                                    section.title,
                                    Modifier.padding(top = if (sectionIndex == 0) 4.dp else 10.dp, bottom = 2.dp),
                                )
                                section.rows.forEachIndexed { index, row ->
                                    val tag = "standings.row.$sectionIndex.${row.team.abbrev}"
                                    // The row and its rule are one item, so the list's spacing is spent between
                                    // teams rather than between a team and its divider.
                                    Column(Modifier.fillMaxWidth()) {
                                        StandingsRow(
                                            row = row,
                                            rank = index + 1,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 40.dp)
                                                    // A row has no action, so it is not a control: the tag is for the
                                                    // tests that read the table, and the row is not a touch target.
                                                    // That is what lets the table run at 40 dp, not a control's 48.
                                                    .then(if (primary) Modifier.testTag(tag) else Modifier),
                                            tagged = primary,
                                        )
                                        when {
                                            section.playoffCutAfter == index -> {
                                                PlayoffLine(Modifier.padding(vertical = 6.dp))
                                            }

                                            // A hairline separates rows without adding height, so the table reads
                                            // as a block rather than a list of unrelated lines.
                                            index < section.rows.lastIndex -> {
                                                HorizontalDivider(
                                                    thickness = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                )
                                            }
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
                val selected = view == state.view
                Surface(
                    // The date strip's selected treatment, so a chosen view looks chosen on both screens.
                    color = if (selected) Roux.accents.fill else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) Roux.accents.onFill else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CHIP_SHAPE,
                    border =
                        BorderStroke(
                            1.dp,
                            if (selected) Roux.accents.fill else MaterialTheme.colorScheme.outline,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .selectable(selected, role = Role.Tab, onClick = { onSetView(view) })
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
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The column header. It names the columns; freshness stays with the stale banner. */
@Composable
private fun StandingsHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Empty gutters where the rank and the crest sit in the rows, so the team column starts in the same place.
        Spacer(Modifier.width(RANK_WIDTH))
        Spacer(Modifier.width(6.dp))
        Spacer(Modifier.width(CREST_SIZE))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.standings_team),
            Modifier.weight(IDENTITY_WEIGHT),
            style = headerStyle(),
            color = Roux.accents.muted,
            maxLines = 1,
        )
        listOf(
            R.string.standings_gp,
            R.string.standings_w,
            R.string.standings_l,
            R.string.standings_otl,
            R.string.standings_pts,
        ).forEachIndexed { index, id ->
            Text(
                stringResource(id),
                Modifier.weight(STAT_WEIGHT).testTag("standings.cell.$index"),
                textAlign = TextAlign.End,
                style = headerStyle(),
                color = Roux.accents.muted,
                maxLines = 1,
            )
        }
    }
}

/** One team. Rank, crest and club name, then the same five columns the header names. */
@Composable
private fun StandingsRow(
    row: StandingRow,
    rank: Int,
    tagged: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            rank.toString(),
            Modifier.width(RANK_WIDTH).then(if (tagged) Modifier.testTag("standings.rank") else Modifier),
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = Roux.accents.muted,
        )
        Spacer(Modifier.width(6.dp))
        TeamLogo(row.team, CREST_SIZE)
        Spacer(Modifier.width(8.dp))
        Text(
            // The club without its place: a standings table is scanned by club, and the city is the part that
            // repeats. The full name is the fallback when the feed gives no place-free form, and the abbreviation
            // only when it gives no name at all - three letters cost the reader more than a clipped word.
            row.team.shortName
                ?: row.team.name?.takeIf { it.isNotBlank() }
                ?: row.team.abbrev,
            Modifier.weight(IDENTITY_WEIGHT),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // record is the domain's null-aware display form; split it to align W/L/OTL with their headers.
        val record = row.record?.split('-')
        val cells =
            listOf(
                row.gp.toString(),
                record?.getOrNull(0) ?: "–",
                record?.getOrNull(1) ?: "–",
                record?.getOrNull(2) ?: "–",
                row.pts.toString(),
            )
        cells.forEachIndexed { index, _ ->
            val last = index == cells.lastIndex
            Text(
                cells[index],
                Modifier.weight(STAT_WEIGHT).then(if (tagged) Modifier.testTag("standings.cell.$index") else Modifier),
                textAlign = TextAlign.End,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                // The points are the column the table exists for: the one place the accent is used as type.
                color = if (last) Roux.accents.signature else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

/** A section heading: small, quiet, and separated from its rows by a rule rather than by weight. */
@Composable
private fun StandingsSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            // Not transformed: the table's wording stays readable by a screen reader and findable by text.
            title,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = Roux.accents.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The boundary the whole table is read against, so it is drawn in the accent rather than as another row. */
@Composable
private fun PlayoffLine(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = Roux.accents.signature)
        Text(
            stringResource(R.string.standings_playoff_line),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
            color = Roux.accents.signature,
            maxLines = 1,
        )
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = Roux.accents.signature)
    }
}

@Composable
private fun headerStyle() =
    MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp,
    )
