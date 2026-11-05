@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GoalieDecision
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.model.ageOn
import net.montycasa.roux.domain.model.newestFirst
import net.montycasa.roux.domain.model.newestNhlRegularSeasonRows
import net.montycasa.roux.domain.model.newestSeasonRows
import net.montycasa.roux.domain.model.playerPositionLabel
import net.montycasa.roux.domain.policy.PlayerCareerTable
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.SubNavigationRow
import net.montycasa.roux.ui.detail.EmptySectionLine
import net.montycasa.roux.ui.detail.SectionCard
import net.montycasa.roux.ui.nav.NavSwipe
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.team.formatGoalsAgainstAverage
import net.montycasa.roux.ui.team.formatPlusMinus
import net.montycasa.roux.ui.team.formatSavePercentage
import net.montycasa.roux.ui.team.formatShootingPercentage
import net.montycasa.roux.ui.team.formatStatNumber
import net.montycasa.roux.ui.team.formatStatsIceTime
import net.montycasa.roux.ui.theme.LocalRouxAccents
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private val HEADSHOT_SIZE = 112.dp
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private const val TAB_SETTLE_MILLIS = 250

/** What a fact the feed does not have prints in its place. */
private const val MISSING_FACT = "—"

/**
 * The four tabs, in the order the bottom navigation shows them. The pager and the bar both count pages by this order,
 * and the bar's test tags are the enum names.
 */
internal enum class PlayerTab {
    Season,
    Career,
    GameLog,
    Info,
}

/**
 * A player's page: the back row and the header stay put, then one of four tabs in a horizontal pager, then the bottom
 * navigation. Each tab scrolls vertically on its own. A swipe turns the page and the bar follows it.
 *
 * The Season, Career and Info tabs read the profile alone. The Game log tab reads its own request, so a failed or empty
 * log leaves the other three exactly as they were. [today] is the date the Info tab's age is counted to. [onGameClick]
 * receives the id of a game the Game log tab's row names.
 */
@Composable
fun PlayerDetailScreen(
    state: PlayerDetailUiState,
    currentSeason: Int,
    today: LocalDate,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onGameClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("player.page"),
    ) {
        PlayerBackRow(onBack = onBack)
        val loaded = state.profile
        val profile = loaded?.data
        when {
            profile != null -> {
                PlayerBody(
                    profile = profile,
                    currentSeason = currentSeason,
                    today = today,
                    gameLog = state.gameLog,
                    onRetry = onRetry,
                    onGameClick = onGameClick,
                    modifier = Modifier.weight(1f),
                )
            }

            loaded?.error != null -> {
                PlayerFailure(isRefreshing = state.isLoading, onRetry = onRetry)
            }

            else -> {
                Text(
                    stringResource(R.string.player_loading),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("player.loading"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerBackRow(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp).testTag("player.back").reachTarget("player.back"),
        ) {
            Text(stringResource(R.string.player_back))
        }
    }
}

@Composable
private fun PlayerBody(
    profile: PlayerProfile,
    currentSeason: Int,
    today: LocalDate,
    gameLog: Loadable<List<PlayerGame>>?,
    onRetry: () -> Unit,
    onGameClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState { PlayerTab.entries.size }
    val scope = rememberCoroutineScope()
    // During a swipe the bar follows the page the pager is heading to, not the one it is leaving, so the bar never lags
    // behind the finger.
    val shown = PlayerTab.entries[(if (pager.isScrollInProgress) pager.targetPage else pager.currentPage)]
    Column(modifier.fillMaxWidth()) {
        PlayerHeader(
            profile = profile,
            currentSeason = currentSeason,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalPager(
            state = pager,
            // Off, horizontal drags belong to the pager; on, the shell's pager would claim them instead.
            userScrollEnabled = !NavSwipe.TURNS_PAGES,
            // The neighbouring tab is composed ahead of the drag, so a swipe reveals a finished page, not an empty one.
            beyondViewportPageCount = 1,
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = tween(durationMillis = TAB_SETTLE_MILLIS, easing = FastOutSlowInEasing),
                ),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("player.pager"),
        ) { page ->
            PlayerTabPage(
                tab = PlayerTab.entries[page],
                profile = profile,
                currentSeason = currentSeason,
                today = today,
                gameLog = gameLog,
                onRetry = onRetry,
                onGameClick = onGameClick,
            )
        }
        SubNavigationRow(
            entries = PlayerTab.entries,
            selected = shown,
            onSelect = { tab -> scope.launch { pager.animateScrollToPage(tab.ordinal) } },
            tagPrefix = "player.tab",
            wrapLabels = true,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            label = { tab -> tabLabel(tab) },
        )
    }
}

@Composable
private fun PlayerTabPage(
    tab: PlayerTab,
    profile: PlayerProfile,
    currentSeason: Int,
    today: LocalDate,
    gameLog: Loadable<List<PlayerGame>>?,
    onRetry: () -> Unit,
    onGameClick: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        when (tab) {
            PlayerTab.Season -> {
                SeasonTab(profile = profile, currentSeason = currentSeason)
            }

            PlayerTab.Career -> {
                CareerTab(profile = profile)
            }

            PlayerTab.GameLog -> {
                GameLogTab(
                    profile = profile,
                    currentSeason = currentSeason,
                    gameLog = gameLog,
                    onRetry = onRetry,
                    onGameClick = onGameClick,
                )
            }

            PlayerTab.Info -> {
                InfoTab(profile = profile, today = today)
            }
        }
    }
}

@Composable
private fun tabLabel(tab: PlayerTab): String =
    when (tab) {
        PlayerTab.Season -> stringResource(R.string.player_tab_season)
        PlayerTab.Career -> stringResource(R.string.player_tab_career)
        PlayerTab.GameLog -> stringResource(R.string.player_tab_gamelog)
        PlayerTab.Info -> stringResource(R.string.player_tab_info)
    }

@Composable
private fun PlayerHeader(
    profile: PlayerProfile,
    currentSeason: Int,
    modifier: Modifier = Modifier,
) {
    val newest = profile.seasons.newestSeasonRows().firstOrNull()
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Headshot(url = profile.headshotUrl)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.player_full_name, profile.firstName, profile.lastName),
            modifier = Modifier.testTag("player.name"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            numberAndPosition(profile),
            modifier = Modifier.padding(top = 4.dp).testTag("player.number"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val team = newest?.teamName
        val season = newest?.season
        if (team != null && season != null) {
            Text(
                stringResource(R.string.player_context, team, seasonHeading(season, currentSeason)),
                modifier = Modifier.padding(top = 2.dp).testTag("player.context"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun numberAndPosition(profile: PlayerProfile): String {
    val position = playerPositionLabel(profile.position)
    val number = profile.sweaterNumber
    return if (number != null) {
        stringResource(R.string.player_number_position, number, position)
    } else {
        position
    }
}

@Composable
private fun Headshot(url: String?) {
    Box(
        Modifier
            .size(HEADSHOT_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("player.headshot"),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            // The name is printed beside the picture, so the picture itself has nothing to add for a screen reader.
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag("player.headshot.image"),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun SeasonTab(
    profile: PlayerProfile,
    currentSeason: Int,
) {
    // One season, one card: the NHL regular-season rows of the newest NHL season. Other leagues and playoffs live in Career.
    val rows = profile.seasons.newestNhlRegularSeasonRows()
    val season = rows.firstOrNull()?.season
    val title =
        when {
            season == null -> stringResource(R.string.player_tab_season)
            season == currentSeason -> stringResource(R.string.player_this_season)
            else -> seasonYears(season)
        }
    SectionCard(
        title = title,
        tag = "player.season",
        loadable = loaded(rows),
        failureLabel = "",
    ) { current ->
        if (current.isEmpty()) {
            EmptySectionLine(stringResource(R.string.player_empty))
        } else {
            current.forEachIndexed { index, line ->
                SeasonLineBlock(line = line, isGoalie = profile.isGoalie, tagPrefix = "player.season.row.$index")
            }
        }
    }
}

/**
 * One season line, as a grid of its stats. The card prints no league or club line. A traded season is one grid per
 * club, all inside the one season card.
 */
@Composable
private fun SeasonLineBlock(
    line: PlayerSeasonLine,
    isGoalie: Boolean,
    tagPrefix: String,
) {
    StatGrid(cells = statCells(isGoalie, line.totals), tagPrefix = tagPrefix)
}

@Composable
private fun CareerTab(profile: PlayerProfile) {
    val rows = PlayerCareerTable.rows(profile.seasons)
    val regularRows = rows.filter { !it.isPlayoffs }
    val playoffRows = rows.filter { it.isPlayoffs }
    // Each total is summed from its own rows and checked against the feed's career line for that kind of game. The
    // regular total is the career card's; the playoff total is the playoff card's, and it has no card without rows.
    val regular = PlayerCareerTable.total(regularRows, profile.career.regularSeason)
    val playoffs = PlayerCareerTable.playoffTotal(rows, profile.career.playoffs)
    SectionCard(
        title = stringResource(R.string.player_career),
        tag = "player.career",
        loadable = loaded(regularRows),
        failureLabel = "",
    ) { current ->
        if (current.isEmpty() && regular == null) {
            EmptySectionLine(stringResource(R.string.player_empty))
        } else {
            CareerTable(
                isGoalie = profile.isGoalie,
                rows = current,
                total = regular,
                tagPrefix = "player.career",
            )
        }
    }
    if (playoffRows.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionCard(
            title = stringResource(R.string.player_playoffs),
            tag = "player.playoffs",
            loadable = loaded(playoffRows),
            failureLabel = "",
        ) { current ->
            CareerTable(
                isGoalie = profile.isGoalie,
                rows = current,
                total = playoffs,
                tagPrefix = "player.playoffs",
            )
        }
    }
}

/**
 * One career card's table: a season row per line, then the card's total, which is labelled "Total" on both cards. The
 * season is frozen and sized to its widest label; the club comes next.
 */
@Composable
private fun CareerTable(
    isGoalie: Boolean,
    rows: List<PlayerSeasonLine>,
    total: PlayerTotals?,
    tagPrefix: String,
) {
    val seasonRows =
        rows.mapIndexed { index, line ->
            StatRow(
                tag = "$tagPrefix.row.$index",
                label = line.season?.let { seasonYears(it) }.orEmpty(),
                cells = careerCells(isGoalie, club = clubLabel(line), totals = line.totals),
            )
        }
    val totalLabel = stringResource(R.string.player_total)
    val totalRows =
        total
            ?.let {
                listOf(StatRow(tag = "$tagPrefix.total", label = totalLabel, cells = careerCells(isGoalie, club = "", totals = it)))
            }.orEmpty()
    val allRows = seasonRows + totalRows
    val headers = careerCells(isGoalie, club = "", totals = null)
    val measurer = rememberTextMeasurer()
    PlayerStatTable(
        headers = headers,
        rows = allRows,
        headerTag = tagPrefix,
        labelWidth = widestLineWidth(allRows.map { it.label }, measurer, MaterialTheme.typography.bodySmall),
        // Each column is only as wide as the wider of its header and its widest number, so the table spends the card's
        // width on numbers rather than on air. A wider table still scrolls, but it needs far less of it.
        // The career table carries thirteen columns, so it takes the tighter gap as well: enough to keep neighbouring
        // numbers apart, not the wider air the shorter tables can afford.
        statWidths = fittedStatWidths(headers, allRows.map { it.cells }, measurer, gap = 5.dp, minimum = 28.dp, paddingShare = 0.10f),
    )
}

@Composable
private fun GameLogTab(
    profile: PlayerProfile,
    currentSeason: Int,
    gameLog: Loadable<List<PlayerGame>>?,
    onRetry: () -> Unit,
    onGameClick: (Long) -> Unit,
) {
    // A log that has not arrived yet reads as loading; the page's retry reloads it along with the profile.
    val loadable = gameLog ?: Loadable(null, Freshness.Missing, null)
    // The season being played now, whatever the profile's newest season is: a log is never last season's games.
    val title = stringResource(R.string.player_gamelog_title, seasonFullYears(currentSeason))
    val measurer = rememberTextMeasurer()
    val lineStyle = MaterialTheme.typography.bodySmall
    SectionCard(
        title = title,
        tag = "player.gamelog",
        loadable = loadable,
        failureLabel = stringResource(R.string.player_gamelog_error),
        onRetry = onRetry,
    ) { games ->
        val newest = games.newestFirst()
        if (newest.isEmpty()) {
            Text(
                stringResource(R.string.player_gamelog_empty),
                modifier = Modifier.padding(top = 6.dp).testTag("player.gamelog.empty"),
                style = MaterialTheme.typography.bodySmall,
                color = LocalRouxAccents.current.muted,
            )
        } else {
            val headers = gameCells(profile.isGoalie, game = null)
            val rows =
                newest.mapIndexed { index, game ->
                    StatRow(
                        tag = "player.gamelog.row.$index",
                        label = gameLabel(game),
                        cells = gameCells(profile.isGoalie, game = game),
                        gameId = game.gameId,
                    )
                }
            PlayerStatTable(
                headers = headers,
                rows = rows,
                headerTag = "player.gamelog",
                labelWidth = widestLineWidth(rows.map { it.label }, measurer, lineStyle),
                statWidths = fittedStatWidths(headers, rows.map { it.cells }, measurer),
                onRowClick = onGameClick,
            )
        }
    }
}

/** A game's frozen line: its date and its opponent on one line, as "Nov 1 · vs OTT". A missing part prints the dash. */
@Composable
private fun gameLabel(game: PlayerGame): String {
    val date = game.gameDate?.let { formatShortDate(it) } ?: statCount(null)
    val opponent = opponentLabel(game.opponentAbbrev, game.isHome) ?: statCount(null)
    return "$date${stringResource(R.string.player_separator)}$opponent"
}

/**
 * The frozen column's width: the widest of its lines as the style prints it, so the column is only as wide as its
 * content needs and no line wraps. Every row shares it, so the rows' numbers stay in line with one another.
 */
@Composable
private fun widestLineWidth(
    lines: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
): Dp {
    val widest = lines.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
    return with(LocalDensity.current) { widest.toDp() }
}

@Composable
private fun InfoTab(
    profile: PlayerProfile,
    today: LocalDate,
) {
    val facts = infoFacts(profile, today)
    SectionCard(
        title = stringResource(R.string.player_tab_info),
        tag = "player.info",
        loadable = loaded(facts),
        failureLabel = "",
    ) { current ->
        // A fixed table of five facts: two across, two across, then the draft across the full width. Reading across and
        // then down is the order a screen reader hears them, the same order they are seen.
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                InfoCell(current.heightWeight, Modifier.weight(1f))
                InfoCell(current.shoots, Modifier.weight(1f).padding(start = 12.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                InfoCell(current.born, Modifier.weight(1f))
                InfoCell(current.birthplace, Modifier.weight(1f).padding(start = 12.dp))
            }
            InfoCell(current.draft, Modifier.fillMaxWidth())
        }
    }
}

/** One label above its value, the same shape as the game page's Game info card. */
@Composable
private fun InfoCell(
    item: InfoItem,
    modifier: Modifier,
) {
    Column(modifier.padding(vertical = 4.dp).testTag("player.info.${item.key}.cell")) {
        Text(
            item.label,
            modifier = Modifier.testTag("player.info.${item.key}.label"),
            style = MaterialTheme.typography.labelMedium,
            color = LocalRouxAccents.current.muted,
        )
        Text(
            item.value,
            modifier = Modifier.testTag("player.info.${item.key}"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The five facts of the Info card, each with its value or the dash. Age is counted on [today] from the birth date, so it
 * is never stored and turns over on the birthday itself.
 */
@Composable
private fun infoFacts(
    profile: PlayerProfile,
    today: LocalDate,
): InfoFacts {
    val height =
        profile.heightInches?.let { stringResource(R.string.player_height_value, it / 12, it % 12) } ?: MISSING_FACT
    val weight = profile.weightPounds?.let { stringResource(R.string.player_weight_value, it) } ?: MISSING_FACT
    val bornText =
        profile.birthDate?.let { iso ->
            val age = profile.ageOn(today)
            if (age != null) {
                stringResource(R.string.player_born_value, formatDate(iso), age)
            } else {
                formatDate(iso)
            }
        } ?: MISSING_FACT
    val city = profile.birthCity
    val country = profile.birthCountry
    val birthplaceText =
        when {
            city != null && country != null -> stringResource(R.string.player_birthplace_value, city, country)
            else -> city ?: country
        } ?: MISSING_FACT
    val draftText =
        profile.draft
            ?.let { draft ->
                listOfNotNull(
                    draft.year?.toString(),
                    draft.teamAbbrev,
                    draft.round?.let { stringResource(R.string.player_draft_round, it) },
                    draft.pickInRound?.let { stringResource(R.string.player_draft_pick, it) },
                    draft.overallPick?.let { stringResource(R.string.player_draft_overall, it) },
                ).takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = stringResource(R.string.player_separator))
            }
            ?: MISSING_FACT
    return InfoFacts(
        heightWeight =
            InfoItem(
                "heightweight",
                stringResource(R.string.player_info_height_weight),
                stringResource(R.string.player_height_weight_value, height, weight),
            ),
        // Handedness is "Shoots" for every position, a goalie included.
        shoots = InfoItem("shoots", stringResource(R.string.player_info_shoots), profile.shoots ?: MISSING_FACT),
        born = InfoItem("born", stringResource(R.string.player_info_born), bornText),
        birthplace = InfoItem("birthplace", stringResource(R.string.player_info_birthplace), birthplaceText),
        draft = InfoItem("draft", stringResource(R.string.player_info_draft), draftText),
    )
}

/** The club a career row names: its abbreviation, or the feed's own name when the club is not one the app knows. */
private fun clubLabel(line: PlayerSeasonLine): String = line.teamAbbrev ?: line.teamName.orEmpty()

/** The career columns: the club first, then the stats. Headers pass no totals and carry labels only. */
@Composable
private fun careerCells(
    isGoalie: Boolean,
    club: String,
    totals: PlayerTotals?,
): List<StatCell> = listOf(StatCell("club", stringResource(R.string.player_stat_team), club)) + statCells(isGoalie, totals)

/**
 * A game row's numbers, which scroll. The date and the opponent sit in the frozen column beside them, so they are not
 * here. Headers pass no game and carry labels only.
 */
@Composable
private fun gameCells(
    isGoalie: Boolean,
    game: PlayerGame?,
): List<StatCell> =
    when (game) {
        is PlayerGame.Skater -> skaterLogCells(game)
        is PlayerGame.Goalie -> goalieLogCells(game)
        null -> if (isGoalie) goalieLogCells(null) else skaterLogCells(null)
    }

@Composable
private fun opponentLabel(
    opponent: String?,
    isHome: Boolean?,
): String? =
    when {
        opponent == null -> null
        isHome == true -> stringResource(R.string.player_home_opponent, opponent)
        isHome == false -> stringResource(R.string.player_away_opponent, opponent)
        else -> opponent
    }

@Composable
private fun PlayerFailure(
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.player_error),
            modifier = Modifier.testTag("player.error"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RetryButton(onRetry = onRetry, isRefreshing = isRefreshing, retryTag = "player.retry")
    }
}

private data class InfoItem(
    val key: String,
    val label: String,
    val value: String,
)

/** The five facts of the Info card, in card order. */
private data class InfoFacts(
    val heightWeight: InfoItem,
    val shoots: InfoItem,
    val born: InfoItem,
    val birthplace: InfoItem,
    val draft: InfoItem,
)

/** The season or career stat columns, in one order. Headers pass `null` so they carry labels and no numbers. */
@Composable
private fun statCells(
    isGoalie: Boolean,
    totals: PlayerTotals?,
): List<StatCell> =
    if (isGoalie) {
        goalieStats(totals as? PlayerTotals.Goalie)
    } else {
        skaterStats(totals as? PlayerTotals.Skater)
    }

@Composable
private fun skaterStats(totals: PlayerTotals.Skater?): List<StatCell> =
    listOf(
        StatCell("gp", stringResource(R.string.player_stat_gp), statCount(totals?.gamesPlayed)),
        StatCell("g", stringResource(R.string.player_stat_g), statCount(totals?.goals)),
        StatCell("ast", stringResource(R.string.player_stat_a), statCount(totals?.assists)),
        StatCell("pts", stringResource(R.string.player_stat_pts), statCount(totals?.points)),
        StatCell("pm", stringResource(R.string.player_stat_pm), formatPlusMinus(totals?.plusMinus)),
        StatCell("pim", stringResource(R.string.player_stat_pim), statCount(totals?.penaltyMinutes)),
        StatCell("ppg", stringResource(R.string.player_stat_ppg), statCount(totals?.powerPlayGoals)),
        StatCell("shg", stringResource(R.string.player_stat_shg), statCount(totals?.shorthandedGoals)),
        StatCell("sog", stringResource(R.string.player_stat_s), statCount(totals?.shots)),
        StatCell("shpct", stringResource(R.string.player_stat_shpct), formatShootingPercentage(totals?.shootingPctg)),
        StatCell("fopct", stringResource(R.string.player_stat_fopct), formatShootingPercentage(totals?.faceoffWinningPctg)),
        StatCell("toi", stringResource(R.string.player_stat_toi_per_game), formatStatsIceTime(totals?.avgTimeOnIceSeconds)),
    )

@Composable
private fun goalieStats(totals: PlayerTotals.Goalie?): List<StatCell> =
    listOf(
        StatCell("gp", stringResource(R.string.player_stat_gp), statCount(totals?.gamesPlayed)),
        StatCell("gs", stringResource(R.string.player_stat_gs), statCount(totals?.gamesStarted)),
        StatCell("w", stringResource(R.string.player_stat_w), statCount(totals?.wins)),
        StatCell("l", stringResource(R.string.player_stat_l), statCount(totals?.losses)),
        StatCell("otl", stringResource(R.string.player_stat_otl), statCount(totals?.overtimeLosses)),
        StatCell("gaa", stringResource(R.string.player_stat_gaa), formatGoalsAgainstAverage(totals?.goalsAgainstAverage)),
        StatCell("sv", stringResource(R.string.player_stat_sv), formatSavePercentage(totals?.savePercentage)),
        StatCell("so", stringResource(R.string.player_stat_so), statCount(totals?.shutouts)),
        StatCell("ga", stringResource(R.string.player_stat_ga), statCount(totals?.goalsAgainst)),
        StatCell("sa", stringResource(R.string.player_stat_sa), statCount(totals?.shotsAgainst)),
        StatCell("toi", stringResource(R.string.player_stat_toi), formatStatsIceTime(totals?.timeOnIceSeconds)),
        StatCell("pim", stringResource(R.string.player_stat_pim), statCount(totals?.penaltyMinutes)),
    )

/** A skater's game: the columns the Game log tab shows for a skater. */
@Composable
private fun skaterLogCells(game: PlayerGame.Skater?): List<StatCell> =
    listOf(
        StatCell("g", stringResource(R.string.player_stat_g), statCount(game?.goals)),
        StatCell("ast", stringResource(R.string.player_stat_a), statCount(game?.assists)),
        StatCell("pts", stringResource(R.string.player_stat_pts), statCount(game?.points)),
        StatCell("pm", stringResource(R.string.player_stat_pm), formatPlusMinus(game?.plusMinus)),
        StatCell("sog", stringResource(R.string.player_stat_s), statCount(game?.shots)),
        StatCell("toi", stringResource(R.string.player_stat_toi), formatStatsIceTime(game?.timeOnIceSeconds)),
    )

/** A goalie's game: the decision, not goals or assists, which a goalie does not have. */
@Composable
private fun goalieLogCells(game: PlayerGame.Goalie?): List<StatCell> =
    listOf(
        StatCell("dec", stringResource(R.string.player_stat_dec), decisionLabel(game?.decision)),
        StatCell("sa", stringResource(R.string.player_stat_sa), statCount(game?.shotsAgainst)),
        StatCell("ga", stringResource(R.string.player_stat_ga), statCount(game?.goalsAgainst)),
        StatCell("sv", stringResource(R.string.player_stat_sv), formatSavePercentage(game?.savePercentage)),
        StatCell("toi", stringResource(R.string.player_stat_toi), formatStatsIceTime(game?.timeOnIceSeconds)),
    )

@Composable
private fun decisionLabel(decision: GoalieDecision?): String =
    when (decision) {
        GoalieDecision.Win -> stringResource(R.string.player_decision_win)
        GoalieDecision.Loss -> stringResource(R.string.player_decision_loss)
        GoalieDecision.OvertimeLoss -> stringResource(R.string.player_decision_overtime)
        null -> statCount(null)
    }

/** A count in a stat cell. An absent count prints the em dash; the feed's zero prints as zero. */
private fun statCount(value: Int?): String = formatStatNumber(value)

/** A card's data is already in hand, so it is handed over as fresh rather than fetched. */
private fun <T> loaded(value: T): Loadable<T> = Loadable(value, Freshness.Fresh, null)

/** The season as the header names it: "This season" for the current one, otherwise `2026-27`. */
@Composable
private fun seasonHeading(
    season: Int,
    currentSeason: Int,
): String =
    if (season == currentSeason) {
        stringResource(R.string.player_this_season)
    } else {
        seasonYears(season)
    }

/** The feed's `20262027` as `2026-27`. */
private fun seasonYears(season: Int): String {
    val start = season / 10_000
    val end = season % 100
    return "$start-${end.toString().padStart(2, '0')}"
}

/** The feed's `20262027` as `2026-2027`, the form the game log heading uses. */
private fun seasonFullYears(season: Int): String = "${season / 10_000}-${season % 10_000}"

/** An ISO `YYYY-MM-DD` day as the locale prints a date. A value that does not parse is shown as the feed sent it. */
private fun formatDate(iso: String): String =
    try {
        java.time.LocalDate
            .parse(iso)
            .format(DATE_FORMAT)
    } catch (_: DateTimeParseException) {
        iso
    }

/**
 * An ISO `YYYY-MM-DD` day as a short date, such as "Nov 1", in the device's language. Unparseable input is kept as sent.
 */
private fun formatShortDate(iso: String): String =
    try {
        java.time.LocalDate
            .parse(iso)
            .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    } catch (_: DateTimeParseException) {
        iso
    }
