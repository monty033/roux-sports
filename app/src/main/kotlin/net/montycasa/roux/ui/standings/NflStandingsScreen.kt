@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.standings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflStandingRow
import net.montycasa.roux.domain.model.NflStandingTable
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.StaleBanner
import net.montycasa.roux.ui.common.SubNavigationRow
import net.montycasa.roux.ui.reach.listRow
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.scores.TeamLogo
import net.montycasa.roux.ui.theme.Roux
import kotlin.time.Clock
import kotlin.time.Instant

/** The same touch floor the hockey table keeps: a row is a line of text, but it sits in a list a thumb scrolls. */
private val ROW_HEIGHT = 48.dp

private val RANK_WIDTH = 16.dp

// The gaps either side of the crest: tight, because at 360 dp every dp not spent here is a dp of the club's name.
private val RANK_GAP = 4.dp
private val CREST_GAP = 6.dp

// Smaller than the hockey table's 34 dp crest: eight statistic columns leave the club about 60 dp at 360 dp, and the artwork is
// the part that can give.
private val CREST_SIZE = 24.dp

private val CHIP_SHAPE = RoundedCornerShape(12.dp)

/** The last playoff place. The feed numbers seven seeds a conference and the line sits under the seventh. */
private const val LAST_PLAYOFF_SEED = 7

/**
 * The row the playoff line sits under: the one the feed seeds seventh. A conference whose seventh seed the feed did not
 * state draws no line rather than one at a position this app made up.
 */
private fun playoffLineAfter(
    table: NflStandingTable,
    drawn: Boolean,
): Int? = if (drawn) table.rows.indexOfFirst { it.playoffSeed == LAST_PLAYOFF_SEED }.takeIf { it >= 0 } else null

/** One statistic column: what it is called, and what a row says under it. A null cell draws blank, never zero. */
private enum class NflColumn(
    @StringRes val header: Int,
    val cell: (NflStandingRow, List<String>?) -> String?,
) {
    W(R.string.standings_w, { _, record -> record?.getOrNull(0) }),
    L(R.string.standings_l, { _, record -> record?.getOrNull(1) }),
    T(R.string.nfl_standings_t, { _, record -> record?.getOrNull(2) }),
    PCT(R.string.nfl_standings_pct, { row, _ -> row.winPercent }),
    PF(R.string.nfl_standings_pf, { row, _ -> row.pointsFor?.toString() }),
    PA(R.string.nfl_standings_pa, { row, _ -> row.pointsAgainst?.toString() }),
    DIFF(R.string.nfl_standings_diff, { row, _ -> row.differential?.let(::signed) }),
    STRK(R.string.nfl_standings_strk, { row, _ -> row.streak }),
}

/** A point differential the way the feed prints it: the sign is always shown, so +5 and -5 differ by more than a dash. */
private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

/** The row's eight cells in column order. The record is the domain's null-aware W-L-T, split to sit under its headers. */
private fun NflStandingRow.cells(): List<String?> {
    val record = record?.split('-')
    return NflColumn.entries.map { it.cell(this, record) }
}

/**
 * Football's standings page.
 *
 * Its own screen rather than a mode of the hockey one: football has no points column, no overtime losses and a
 * different set of questions, and a second screen keeps every hockey golden where it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NflStandingsScreen(
    state: NflStandingsUiState,
    onRefresh: () -> Unit,
    onSelectTab: (NflStandingsTab) -> Unit,
    onSelectConference: (NflConference) -> Unit,
    modifier: Modifier = Modifier,
    onTeamClick: (TeamKey) -> Unit = {},
    /**
     * The instant the stale banner measures "today" against. It is a parameter so a screenshot can render the same
     * label on any day; production takes the device clock.
     */
    now: Instant = Clock.System.now(),
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            val columns = rememberColumnWidths(state.tables)
            // The table is as wide as the viewport unless the columns need more, which is the case at large type sizes:
            // then the whole table slides sideways as one piece, so a header never drifts away from its numbers.
            val needed = columns.total + IDENTITY_MIN
            val tableWidth = if (needed > maxWidth) needed else maxWidth
            Column(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).testTag("nfl.standings.scroll")) {
                NflHeader(columns, Modifier.width(tableWidth).heightIn(min = 32.dp).testTag("nfl.standings.header"))
                Spacer(Modifier.height(8.dp))
                PullToRefreshBox(
                    state.isRefreshing,
                    onRefresh,
                    Modifier
                        .weight(1f)
                        .width(tableWidth)
                        .testTag("nfl.standings.refresh")
                        .reachTarget("nfl.standings.refresh"),
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("nfl.standings.list"),
                        // Rows sit close together on purpose: they are text, so the rhythm is set by legibility, with
                        // the 48 dp row height keeping them at the touch floor.
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        NflTables(state, columns, onTeamClick)
                    }
                }
            }
        }
        SubNavigationRow(
            entries = NflStandingsTab.entries,
            selected = state.tab,
            onSelect = onSelectTab,
            tagPrefix = "nfl.standings.tab",
            modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp),
            label = { tab ->
                stringResource(
                    when (tab) {
                        NflStandingsTab.Division -> R.string.standings_division
                        NflStandingsTab.Conference -> R.string.standings_conference
                        NflStandingsTab.League -> R.string.standings_league
                    },
                )
            },
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.stale != null -> {
                    StaleBanner(
                        since = state.stale.since,
                        onRetry = onRefresh,
                        isRefreshing = state.isRefreshing,
                        supportingText = if (state.error != null) stringResource(R.string.standings_refresh_error) else null,
                        retryTag = "nfl.standings.retry",
                        now = now,
                    )
                }

                state.error != null && !state.loading && state.tables.isNotEmpty() -> {
                    Text(stringResource(R.string.standings_refresh_error), style = MaterialTheme.typography.bodyMedium)
                    RetryButton(onRefresh, state.isRefreshing, "nfl.standings.retry")
                }

                state.error != null && !state.loading -> {
                    RetryButton(onRefresh, state.isRefreshing, "nfl.standings.retry")
                }
            }
        }
        // The league page is every club, so there is no conference to choose there and the switcher leaves.
        if (state.tab != NflStandingsTab.League) {
            ConferenceSwitcher(state.conference, onSelectConference)
        }
    }
}

/** What the page says in place of rows, or the rows themselves. */
@Composable
private fun NflTables(
    state: NflStandingsUiState,
    columns: ColumnWidths,
    onTeamClick: (TeamKey) -> Unit,
) {
    when {
        state.loading -> {
            Text(stringResource(R.string.standings_loading))
        }

        state.tables.isEmpty() -> {
            Text(stringResource(if (state.error != null) R.string.standings_error else R.string.standings_empty))
        }

        else -> {
            state.tables.forEachIndexed { tableIndex, table ->
                // A table with no title is a plain ranking whose tab already names it, so it gets neither heading nor
                // rule: the rows are the page.
                if (table.name.isNotBlank()) {
                    NflSectionTitle(table.name, Modifier.padding(top = if (tableIndex == 0) 2.dp else 8.dp))
                }
                val lineAfter = playoffLineAfter(table, drawn = state.tab == NflStandingsTab.Conference)
                table.rows.forEachIndexed { index, row ->
                    Column(Modifier.fillMaxWidth()) {
                        NflRow(
                            row = row,
                            rank =
                                if (state.tab == NflStandingsTab.Conference) {
                                    // The seed the feed states, not the row's place: a missing seed is blank.
                                    row.playoffSeed?.toString().orEmpty()
                                } else {
                                    (index + 1).toString()
                                },
                            columns = columns,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ROW_HEIGHT)
                                    .testTag("nfl.standings.row.$tableIndex.${row.team.abbrev}"),
                            onClick = { onTeamClick(row.team.key) },
                        )
                        when {
                            lineAfter == index -> {
                                PlayoffLine(Modifier.padding(vertical = 4.dp).testTag("nfl.standings.playoffLine"))
                            }

                            index < table.rows.lastIndex -> {
                                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConferenceSwitcher(
    selected: NflConference,
    onSelect: (NflConference) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 56.dp)
            .selectableGroup()
            .primaryControl("nfl.standings.conference", frequent = true),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NflConference.entries.forEach { conference ->
            val chosen = conference == selected
            Surface(
                // The same chosen treatment the hockey conference strip uses, so a selection looks selected on both.
                color = if (chosen) Roux.accents.fill else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (chosen) Roux.accents.onFill else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CHIP_SHAPE,
                border = BorderStroke(1.dp, if (chosen) Roux.accents.fill else MaterialTheme.colorScheme.outline),
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(chosen, role = Role.Tab, onClick = { onSelect(conference) })
                        .testTag("nfl.standings.conference.$conference"),
            ) {
                Column(
                    Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(if (conference == NflConference.AFC) R.string.nfl_standings_afc else R.string.nfl_standings_nfc),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ----- Columns -----------------------------------------------------------------------------------------------------

/** The least the club's name may have: its crest, a rank gutter and three words' worth of room. */
private val IDENTITY_MIN = RANK_WIDTH + RANK_GAP + CREST_SIZE + CREST_GAP + 56.dp

/** What a numeric column is drawn in: the same monospace the hockey table's figures use, at the card typography's floor. */
@Composable
private fun cellStyle(): TextStyle = Roux.cardTypography.shots.copy(fontFamily = FontFamily.Monospace)

@Composable
private fun headerStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp,
    )

/** One width per column, in column order, and what they add up to. */
internal class ColumnWidths(
    val widths: List<Dp>,
) {
    val total: Dp = widths.fold(0.dp) { sum, width -> sum + width }
}

/**
 * Each column is as wide as the wider of its header and its widest value, measured with the two styles the table
 * draws, plus a tenth of that width and a small gap. A single fixed width wastes the difference on every narrow column,
 * and that waste is what would push eight columns past a 360 dp phone.
 */
@Composable
private fun rememberColumnWidths(tables: List<NflStandingTable>): ColumnWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headers = NflColumn.entries.map { stringResource(it.header) }
    val header = headerStyle()
    val cell = cellStyle()
    return remember(tables, headers, header, cell, density) {
        measureColumns(measurer, density.density, headers, tables.flatMap { it.rows }, header, cell)
    }
}

internal fun measureColumns(
    measurer: TextMeasurer,
    density: Float,
    headers: List<String>,
    rows: List<NflStandingRow>,
    header: TextStyle,
    cell: TextStyle,
): ColumnWidths {
    val cells = rows.map { it.cells() }
    val widths =
        headers.mapIndexed { index, text ->
            val widest =
                maxOf(
                    measurer.measure(text, header, maxLines = 1).size.width,
                    cells.maxOfOrNull { row -> row[index]?.let { measurer.measure(it, cell, maxLines = 1).size.width } ?: 0 } ?: 0,
                )
            val dp = widest / density
            (dp * (1f + COLUMN_AIR) + COLUMN_GAP_DP).coerceAtLeast(COLUMN_FLOOR_DP).dp
        }
    return ColumnWidths(widths)
}

private const val COLUMN_AIR = 0.10f
private const val COLUMN_GAP_DP = 2f
private const val COLUMN_FLOOR_DP = 16f

// ----- Rows --------------------------------------------------------------------------------------------------------

/** The column header. It names the columns; freshness stays with the stale banner. */
@Composable
private fun NflHeader(
    columns: ColumnWidths,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Empty gutters where the rank and the crest sit in the rows, so the club column starts in the same place.
        Spacer(Modifier.width(RANK_WIDTH + RANK_GAP + CREST_SIZE + CREST_GAP))
        Text(
            stringResource(R.string.standings_team),
            Modifier.weight(1f),
            style = headerStyle(),
            color = Roux.accents.muted,
            maxLines = 1,
        )
        NflColumn.entries.forEachIndexed { index, column ->
            Text(
                stringResource(column.header),
                Modifier.width(columns.widths[index]).testTag("nfl.standings.col.${column.name}"),
                textAlign = TextAlign.End,
                style = headerStyle(),
                color = Roux.accents.muted,
                maxLines = 1,
            )
        }
    }
}

/** One club: rank, crest and name, then the eight figures the header names. */
@Composable
private fun NflRow(
    row: NflStandingRow,
    rank: String,
    columns: ColumnWidths,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Clickable merges the row's texts into one node, so a screen reader offers the club as one control; listRow says
    // neighbouring rows are one run rather than eight stray targets.
    Row(modifier.listRow().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rank,
            Modifier.width(RANK_WIDTH).testTag("nfl.standings.rank"),
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = Roux.accents.muted,
            maxLines = 1,
        )
        Spacer(Modifier.width(RANK_GAP))
        TeamLogo(row.team, CREST_SIZE)
        Spacer(Modifier.width(CREST_GAP))
        Text(
            // The club without its city, the abbreviation only when the feed names nothing else.
            row.team.shortName ?: row.team.name?.takeIf { it.isNotBlank() } ?: row.team.abbrev,
            Modifier.weight(1f),
            style = Roux.cardTypography.clubName.copy(fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        row.cells().forEachIndexed { index, text ->
            Text(
                // A figure the feed did not state is blank: zero would claim a club that has not scored.
                text.orEmpty(),
                Modifier.width(columns.widths[index]).testTag("nfl.standings.cell.$index"),
                textAlign = TextAlign.End,
                style = cellStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (NflColumn.entries[index] == NflColumn.PCT) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/** A section heading: small, quiet, and separated from its rows by a rule rather than by weight. */
@Composable
private fun NflSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            // Not transformed: the feed's own name stays readable by a screen reader and findable by text.
            title,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            letterSpacing = 0.8.sp,
            color = Roux.accents.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The boundary the conference is read against, drawn in the accent rather than as another row. */
@Composable
private fun PlayoffLine(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = Roux.accents.signature)
        Text(
            stringResource(R.string.standings_playoff_line),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp,
            color = Roux.accents.signature,
            maxLines = 1,
        )
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = Roux.accents.signature)
    }
}
