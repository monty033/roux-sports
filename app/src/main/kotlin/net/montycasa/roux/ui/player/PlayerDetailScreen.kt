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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerRecentGame
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.model.newestSeasonRows
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.detail.SectionCard
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.team.formatGoalsAgainstAverage
import net.montycasa.roux.ui.team.formatPlusMinus
import net.montycasa.roux.ui.team.formatSavePercentage
import net.montycasa.roux.ui.team.formatShootingPercentage
import net.montycasa.roux.ui.team.formatStatNumber
import net.montycasa.roux.ui.team.formatStatsIceTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

private val HEADSHOT_SIZE = 112.dp
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/**
 * A player's page: the header, then what the feed says about them, each in a card that hides itself when the feed had
 * nothing for it. Only the body scrolls; the back row stays put.
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
                PlayerBody(profile = profile, currentSeason = currentSeason, modifier = Modifier.weight(1f))
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        PlayerHeader(profile = profile, currentSeason = currentSeason)
        VitalsCard(profile = profile)
        SeasonCard(profile = profile, currentSeason = currentSeason)
        CareerCard(career = profile.career)
        LastFiveCard(games = profile.lastFive)
    }
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
private fun VitalsCard(profile: PlayerProfile) {
    val rows = vitals(profile)
    if (rows.isEmpty()) return
    SectionCard(
        title = stringResource(R.string.player_vitals),
        tag = "player.vitals",
        loadable = loaded(rows),
        failureLabel = "",
    ) { current ->
        current.forEach { vital -> VitalRow(vital) }
    }
}

@Composable
private fun vitals(profile: PlayerProfile): List<Vital> =
    buildList {
        profile.heightInches?.let {
            add(
                Vital(
                    "height",
                    stringResource(R.string.player_vital_height),
                    stringResource(R.string.player_height_value, it / 12, it % 12),
                ),
            )
        }
        profile.weightPounds?.let {
            add(Vital("weight", stringResource(R.string.player_vital_weight), stringResource(R.string.player_weight_value, it)))
        }
        profile.shootsCatches?.let {
            add(Vital("shoots", stringResource(R.string.player_vital_shoots), it))
        }
        profile.birthDate?.let {
            add(Vital("born", stringResource(R.string.player_vital_born), formatDate(it)))
        }
        val city = profile.birthCity
        val country = profile.birthCountry
        val place =
            when {
                city != null && country != null -> stringResource(R.string.player_birthplace_value, city, country)
                else -> city ?: country
            }
        if (place != null) {
            add(Vital("birthplace", stringResource(R.string.player_vital_birthplace), place))
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
                    Vital(
                        "draft",
                        stringResource(R.string.player_vital_draft),
                        parts.joinToString(separator = stringResource(R.string.player_separator)),
                    ),
                )
            }
        }
    }

@Composable
private fun VitalRow(vital: Vital) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            vital.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            vital.value,
            modifier = Modifier.testTag("player.vitals.${vital.key}"),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SeasonCard(
    profile: PlayerProfile,
    currentSeason: Int,
) {
    val rows = profile.seasons.newestSeasonRows()
    val season = rows.firstOrNull()?.season ?: return
    val league = rows.singleOrNull()?.leagueAbbrev
    val title =
        when {
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
        current.forEachIndexed { index, line ->
            SeasonLineBlock(line = line, tag = "player.season.$index")
        }
    }
}

@Composable
private fun SeasonLineBlock(
    line: PlayerSeasonLine,
    tag: String,
) {
    val league = line.leagueAbbrev
    val team = line.teamName
    val header =
        when {
            league != null && team != null -> stringResource(R.string.player_league_team, league, team)
            else -> league ?: team
        }
    if (header != null) {
        Text(
            header,
            modifier = Modifier.padding(top = 8.dp).testTag("$tag.header"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    StatGrid(cells = statCells(line.totals), tagPrefix = tag)
}

@Composable
private fun CareerCard(career: PlayerCareer) {
    if (career.regularSeason == null && career.playoffs == null) return
    SectionCard(
        title = stringResource(R.string.player_career),
        tag = "player.career",
        loadable = loaded(career),
        failureLabel = "",
    ) { current ->
        current.regularSeason?.let { totals ->
            StatGrid(cells = statCells(totals), tagPrefix = "player.career")
        }
        current.playoffs?.let { totals ->
            Text(
                stringResource(R.string.player_playoffs),
                modifier = Modifier.padding(top = 8.dp).testTag("player.playoffs.label"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatGrid(cells = statCells(totals), tagPrefix = "player.playoffs")
        }
    }
}

@Composable
private fun LastFiveCard(games: List<PlayerRecentGame>) {
    if (games.isEmpty()) return
    SectionCard(
        title = stringResource(R.string.player_last_five),
        tag = "player.last5",
        loadable = loaded(games),
        failureLabel = "",
    ) { current ->
        current.forEachIndexed { index, game ->
            RecentGameRow(game = game, tag = "player.last5.$index")
        }
    }
}

@Composable
private fun RecentGameRow(
    game: PlayerRecentGame,
    tag: String,
) {
    val date = game.gameDate?.let { formatDate(it) }
    val opponent = opponentLabel(game.opponentAbbrev, game.isHome)
    val decision = (game as? PlayerRecentGame.Goalie)?.decision
    val head = listOfNotNull(date, opponent, decision).joinToString(separator = stringResource(R.string.player_separator))
    val line =
        when (game) {
            is PlayerRecentGame.Skater -> {
                stringResource(
                    R.string.player_recent_skater,
                    statCount(game.goals),
                    statCount(game.assists),
                    statCount(game.points),
                    formatPlusMinus(game.plusMinus),
                    statCount(game.shots),
                    formatStatsIceTime(game.timeOnIceSeconds),
                )
            }

            is PlayerRecentGame.Goalie -> {
                stringResource(
                    R.string.player_recent_goalie,
                    statCount(game.goalsAgainst),
                    statCount(game.shotsAgainst),
                    formatSavePercentage(game.savePercentage),
                    formatStatsIceTime(game.timeOnIceSeconds),
                )
            }
        }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(tag),
    ) {
        Text(
            head,
            modifier = Modifier.testTag("$tag.head"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            line,
            modifier = Modifier.testTag("$tag.stats"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
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

/** One label above a value in a stat grid. [key] names the value's test tag, so the tag never depends on the label text. */
private data class StatCell(
    val key: String,
    val label: String,
    val value: String,
)

private data class Vital(
    val key: String,
    val label: String,
    val value: String,
)

@Composable
private fun StatGrid(
    cells: List<StatCell>,
    tagPrefix: String,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        cells.chunked(STAT_GRID_COLUMNS).forEach { rowCells ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                rowCells.forEach { cell ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            cell.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            cell.value,
                            modifier = Modifier.testTag("$tagPrefix.${cell.key}"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                repeat(STAT_GRID_COLUMNS - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun statCells(totals: PlayerTotals): List<StatCell> =
    when (totals) {
        is PlayerTotals.Skater -> {
            listOf(
                StatCell("gp", stringResource(R.string.player_stat_gp), statCount(totals.gamesPlayed)),
                StatCell("g", stringResource(R.string.player_stat_g), statCount(totals.goals)),
                StatCell("a", stringResource(R.string.player_stat_a), statCount(totals.assists)),
                StatCell("pts", stringResource(R.string.player_stat_pts), statCount(totals.points)),
                StatCell("pm", stringResource(R.string.player_stat_pm), formatPlusMinus(totals.plusMinus)),
                StatCell("pim", stringResource(R.string.player_stat_pim), statCount(totals.penaltyMinutes)),
                StatCell("ppg", stringResource(R.string.player_stat_ppg), statCount(totals.powerPlayGoals)),
                StatCell("shg", stringResource(R.string.player_stat_shg), statCount(totals.shorthandedGoals)),
                StatCell("s", stringResource(R.string.player_stat_s), statCount(totals.shots)),
                StatCell("shpct", stringResource(R.string.player_stat_shpct), formatShootingPercentage(totals.shootingPctg)),
                StatCell("fopct", stringResource(R.string.player_stat_fopct), formatShootingPercentage(totals.faceoffWinningPctg)),
                StatCell("toi", stringResource(R.string.player_stat_toi_per_game), formatStatsIceTime(totals.avgTimeOnIceSeconds)),
            )
        }

        is PlayerTotals.Goalie -> {
            listOf(
                StatCell("gp", stringResource(R.string.player_stat_gp), statCount(totals.gamesPlayed)),
                StatCell("gs", stringResource(R.string.player_stat_gs), statCount(totals.gamesStarted)),
                StatCell("w", stringResource(R.string.player_stat_w), statCount(totals.wins)),
                StatCell("l", stringResource(R.string.player_stat_l), statCount(totals.losses)),
                StatCell("otl", stringResource(R.string.player_stat_otl), statCount(totals.overtimeLosses)),
                StatCell("gaa", stringResource(R.string.player_stat_gaa), formatGoalsAgainstAverage(totals.goalsAgainstAverage)),
                StatCell("sv", stringResource(R.string.player_stat_sv), formatSavePercentage(totals.savePercentage)),
                StatCell("so", stringResource(R.string.player_stat_so), statCount(totals.shutouts)),
                StatCell("ga", stringResource(R.string.player_stat_ga), statCount(totals.goalsAgainst)),
                StatCell("sa", stringResource(R.string.player_stat_sa), statCount(totals.shotsAgainst)),
                StatCell("toi", stringResource(R.string.player_stat_toi), formatStatsIceTime(totals.timeOnIceSeconds)),
                StatCell("pim", stringResource(R.string.player_stat_pim), statCount(totals.penaltyMinutes)),
            )
        }
    }

/** A count in a stat cell. An absent count prints the em dash; the feed's zero prints as zero. */
private fun statCount(value: Int?): String = formatStatNumber(value)

/** A card's data is already in hand, so it is handed over as fresh rather than fetched. */
private fun <T> loaded(value: T): Loadable<T> = Loadable(value, Freshness.Fresh, null)

/** The season as the card and the header name it: "This season" for the current one, otherwise `2026-27`. */
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

private const val STAT_GRID_COLUMNS = 4
