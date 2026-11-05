@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.player

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.GoalieDecision
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.model.newestFirst
import net.montycasa.roux.domain.model.newestSeasonRows
import net.montycasa.roux.domain.policy.PlayerCareerTable
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.detail.EmptySectionLine
import net.montycasa.roux.ui.detail.SectionCard
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.team.formatGoalsAgainstAverage
import net.montycasa.roux.ui.team.formatPlusMinus
import net.montycasa.roux.ui.team.formatSavePercentage
import net.montycasa.roux.ui.team.formatShootingPercentage
import net.montycasa.roux.ui.team.formatStatNumber
import net.montycasa.roux.ui.team.formatStatsIceTime
import net.montycasa.roux.ui.theme.LocalRouxAccents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

private val HEADSHOT_SIZE = 112.dp
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** The four tabs, in the order the page shows them. [key] names the tab's test tag, so no test reads the label. */
internal enum class PlayerTab(
    val key: String,
) {
    Season("season"),
    Career("career"),
    GameLog("gamelog"),
    Info("info"),
}

/**
 * A player's page: the header, then one of four tabs. Only the body scrolls; the back row stays put.
 *
 * The Season, Career and Info tabs read the profile alone. The Game log tab reads its own request, so a failed or empty
 * log leaves the other three exactly as they were.
 */
@Composable
fun PlayerDetailScreen(
    state: PlayerDetailUiState,
    currentSeason: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
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
                    gameLog = state.gameLog,
                    onRetry = onRetry,
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
    gameLog: Loadable<List<PlayerGame>>?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(PlayerTab.Season) }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        PlayerHeader(profile = profile, currentSeason = currentSeason)
        PlayerTabRow(selected = selected, onSelect = { selected = it })
        when (selected) {
            PlayerTab.Season -> SeasonTab(profile = profile, currentSeason = currentSeason)
            PlayerTab.Career -> CareerTab(profile = profile)
            PlayerTab.GameLog -> GameLogTab(gameLog = gameLog, isGoalie = profile.isGoalie, onRetry = onRetry)
            PlayerTab.Info -> InfoTab(profile = profile)
        }
    }
}

@Composable
private fun PlayerTabRow(
    selected: PlayerTab,
    onSelect: (PlayerTab) -> Unit,
) {
    // A Material tab row, not the team page's sub-navigation: its labels wrap rather than cut off at one line, so
    // "Season stats" stays whole at 360 dp.
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("player.tabs"),
    ) {
        PlayerTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag("player.tab.${tab.key}"),
                text = { Text(tabLabel(tab)) },
            )
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
) {
    val newest = profile.seasons.newestSeasonRows().firstOrNull()
    Column(
        Modifier
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
    val number = profile.sweaterNumber
    return if (number != null) {
        stringResource(R.string.player_number_position, number, profile.position)
    } else {
        profile.position
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
    val rows = profile.seasons.newestSeasonRows()
    val season = rows.firstOrNull()?.season
    val league = rows.singleOrNull()?.leagueAbbrev
    val title =
        when {
            season == null -> stringResource(R.string.player_tab_season)
            season == currentSeason -> stringResource(R.string.player_this_season)
            league != null -> stringResource(R.string.player_season_title_league, seasonYears(season), league)
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
            PlayerStatTable(
                headers = statCells(profile.isGoalie, totals = null),
                rows =
                    current.mapIndexed { index, line ->
                        StatRow(
                            tag = "player.season.row.$index",
                            label = seasonLabel(line),
                            cells = statCells(profile.isGoalie, line.totals),
                        )
                    },
                headerTag = "player.season",
            )
        }
    }
}

@Composable
private fun CareerTab(profile: PlayerProfile) {
    val rows = PlayerCareerTable.rows(profile.seasons)
    val total = PlayerCareerTable.total(rows, profile.career)
    val playoffs = profile.career.playoffs
    SectionCard(
        title = stringResource(R.string.player_career),
        tag = "player.career",
        loadable = loaded(rows),
        failureLabel = "",
    ) { current ->
        if (current.isNotEmpty()) {
            val careerRows =
                current.mapIndexed { index, line ->
                    StatRow(
                        tag = "player.career.row.$index",
                        label = careerLabel(line),
                        cells = statCells(profile.isGoalie, line.totals),
                    )
                }
            val totalRows =
                total
                    ?.let {
                        listOf(
                            StatRow(
                                tag = "player.career.total",
                                label = stringResource(R.string.player_total),
                                cells = statCells(profile.isGoalie, it),
                            ),
                        )
                    }.orEmpty()
            PlayerStatTable(
                headers = statCells(profile.isGoalie, totals = null),
                rows = careerRows + totalRows,
                headerTag = "player.career",
            )
        } else if (playoffs == null) {
            EmptySectionLine(stringResource(R.string.player_empty))
        }
        playoffs?.let { totals ->
            Text(
                stringResource(R.string.player_playoffs),
                modifier = Modifier.padding(top = 12.dp).testTag("player.playoffs.label"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PlayerStatTable(
                headers = statCells(profile.isGoalie, totals = null),
                rows =
                    listOf(
                        StatRow(
                            tag = "player.playoffs.row",
                            label = stringResource(R.string.player_total),
                            cells = statCells(profile.isGoalie, totals),
                        ),
                    ),
                headerTag = "player.playoffs",
            )
        }
    }
}

@Composable
private fun GameLogTab(
    gameLog: Loadable<List<PlayerGame>>?,
    isGoalie: Boolean,
    onRetry: () -> Unit,
) {
    // A log that has not arrived yet reads as loading; the page's retry reloads it along with the profile.
    val loadable = gameLog ?: Loadable(null, Freshness.Missing, null)
    SectionCard(
        title = stringResource(R.string.player_tab_gamelog),
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
            PlayerStatTable(
                headers = if (isGoalie) goalieLogCells(null) else skaterLogCells(null),
                rows =
                    newest.mapIndexed { index, game ->
                        StatRow(
                            tag = "player.gamelog.row.$index",
                            label = gameLabel(game),
                            cells =
                                when (game) {
                                    is PlayerGame.Skater -> skaterLogCells(game)
                                    is PlayerGame.Goalie -> goalieLogCells(game)
                                },
                        )
                    },
                headerTag = "player.gamelog",
            )
        }
    }
}

@Composable
private fun InfoTab(profile: PlayerProfile) {
    val items = infoItems(profile)
    SectionCard(
        title = stringResource(R.string.player_tab_info),
        tag = "player.info",
        loadable = loaded(items),
        failureLabel = "",
    ) { current ->
        if (current.isEmpty()) {
            EmptySectionLine(stringResource(R.string.player_empty))
        } else {
            current.forEach { item -> InfoRowBlock(item) }
        }
    }
}

/** One label above its value, the same shape as the game page's Game info card. */
@Composable
private fun InfoRowBlock(item: InfoItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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

@Composable
private fun infoItems(profile: PlayerProfile): List<InfoItem> =
    buildList {
        profile.heightInches?.let {
            add(
                InfoItem(
                    "height",
                    stringResource(R.string.player_info_height),
                    stringResource(R.string.player_height_value, it / 12, it % 12),
                ),
            )
        }
        profile.weightPounds?.let {
            add(InfoItem("weight", stringResource(R.string.player_info_weight), stringResource(R.string.player_weight_value, it)))
        }
        // Handedness is "Shoots" for every position, a goalie included.
        profile.shoots?.let {
            add(InfoItem("shoots", stringResource(R.string.player_info_shoots), it))
        }
        profile.birthDate?.let {
            add(InfoItem("born", stringResource(R.string.player_info_born), formatDate(it)))
        }
        val city = profile.birthCity
        val country = profile.birthCountry
        val place =
            when {
                city != null && country != null -> stringResource(R.string.player_birthplace_value, city, country)
                else -> city ?: country
            }
        if (place != null) {
            add(InfoItem("birthplace", stringResource(R.string.player_info_birthplace), place))
        }
        profile.draft?.let { draft ->
            val parts =
                listOfNotNull(
                    draft.year?.toString(),
                    draft.teamAbbrev,
                    draft.round?.let { stringResource(R.string.player_draft_round, it) },
                    draft.pickInRound?.let { stringResource(R.string.player_draft_pick, it) },
                    draft.overallPick?.let { stringResource(R.string.player_draft_overall, it) },
                )
            if (parts.isNotEmpty()) {
                add(
                    InfoItem(
                        "draft",
                        stringResource(R.string.player_info_draft),
                        parts.joinToString(separator = stringResource(R.string.player_separator)),
                    ),
                )
            }
        }
    }

@Composable
private fun seasonLabel(line: PlayerSeasonLine): String {
    val league = line.leagueAbbrev
    val team = line.teamName
    return when {
        league != null && team != null -> stringResource(R.string.player_league_team, league, team)
        else -> league ?: team ?: ""
    }
}

/** A career row names its season and its club, so a traded season shows one row per club, each with its own club. */
@Composable
private fun careerLabel(line: PlayerSeasonLine): String =
    listOfNotNull(line.season?.let { seasonYears(it) }, line.teamName)
        .joinToString(separator = stringResource(R.string.player_separator))

@Composable
private fun gameLabel(game: PlayerGame): String {
    val date = game.gameDate?.let { formatDate(it) }
    val opponent = opponentLabel(game.opponentAbbrev, game.isHome)
    return listOfNotNull(date, opponent).joinToString(separator = stringResource(R.string.player_separator))
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

/** An ISO `YYYY-MM-DD` day as the locale prints a date. A value that does not parse is shown as the feed sent it. */
private fun formatDate(iso: String): String =
    try {
        LocalDate.parse(iso).format(DATE_FORMAT)
    } catch (_: DateTimeParseException) {
        iso
    }
