@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PeriodScoring
import net.montycasa.roux.domain.model.SkaterLine
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.LocalRouxAccents
import net.montycasa.roux.ui.theme.LocalRouxCardTypography
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * One game's page: a hero, then a summary or one club's roster, with the destinations in a strip at the bottom.
 *
 * Each card is bound to the endpoint that carries it, never to a combined verdict: a failed boxscore leaves the scoring
 * card intact and says so in the card that actually failed.
 */
@Composable
fun GameDetailScreen(
    game: Game,
    state: DetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    // 0 is Summary; 1 and 2 are the away and home clubs, in that order.
    var destination by rememberSaveable { mutableIntStateOf(0) }
    val detail = state.detail
    val staleSince = detail.staleSince()

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailHeader(game = game, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            GameHero(game = game, zone = zone)

            when (destination) {
                0 -> {
                    SummaryDestination(
                        game = game,
                        detail = detail,
                        staleSince = staleSince,
                        onRetry = onRetry,
                    )
                }

                else -> {
                    TeamDestination(
                        detail = detail,
                        team = if (destination == 1) game.away else game.home,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        DetailTabStrip(
            away = game.away,
            home = game.home,
            selected = destination,
            onSelect = { destination = it },
        )
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Header and hero
 * ------------------------------------------------------------------------------------------------------------ */

@Composable
private fun DetailHeader(
    game: Game,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .testTag("detail.back")
                .reachTarget("detail.back"),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "${game.away.abbrev}  @  ${game.home.abbrev}",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        StatePill(game.state, Modifier.width(64.dp))
    }
}

/** The lifecycle word, which is also the only place a live game says it is live. */
@Composable
private fun StatePill(
    state: GameState,
    modifier: Modifier = Modifier,
) {
    val accents = LocalRouxAccents.current
    val label =
        when (state) {
            GameState.Final -> "Final"
            is GameState.Live -> "Live"
            GameState.Pre, GameState.Scheduled -> "Puck drop"
            is GameState.Unknown -> state.raw
        }
    Text(
        label.uppercase(),
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = accents.muted,
    )
}

@Composable
private fun GameHero(
    game: Game,
    zone: ZoneId,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            heroStatus(game, zone),
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
        )
        Spacer(Modifier.height(10.dp))
        TeamScoreLine(team = game.away, score = game.awayScore, side = "away")
        Spacer(Modifier.height(6.dp))
        TeamScoreLine(team = game.home, score = game.homeScore, side = "home")
    }
}

private fun heroStatus(
    game: Game,
    zone: ZoneId,
): String {
    val state = game.state
    return when (state) {
        GameState.Final -> {
            "Final"
        }

        is GameState.Live -> {
            "Live"
        }

        GameState.Pre -> {
            "Pregame"
        }

        is GameState.Unknown -> {
            state.raw
        }

        GameState.Scheduled -> {
            game.startTimeUtc?.let { start ->
                val local =
                    java.time.Instant
                        .ofEpochSecond(start.epochSeconds, start.nanosecondsOfSecond.toLong())
                        .atZone(zone)
                "Puck drops " + local.format(DateTimeFormatter.ofPattern("h:mma")).lowercase()
            } ?: "Scheduled"
        }
    }
}

@Composable
private fun TeamScoreLine(
    team: TeamRef,
    score: Int?,
    side: String,
) {
    val (city, name) = TeamNames.cityAndName(team)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        if (city != null) {
            // The city hangs above the line, indented past the crest so it sits over the club name rather than
            // floating at the card's edge.
            Text(
                city.uppercase(),
                modifier = Modifier.padding(start = CREST_SIZE + 12.dp).testTag("detail.hero.$side.city"),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.6.sp,
                color = LocalRouxAccents.current.muted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.testTag("detail.hero.$side.crest")) {
                TeamLogo(team = team, crestSize = CREST_SIZE, imageTag = "detail.logo.hero.$side")
            }
            Spacer(Modifier.width(12.dp))
            Text(
                name,
                modifier = Modifier.weight(1f).testTag("detail.hero.$side.name"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (score != null) {
                Text(
                    score.toString(),
                    modifier = Modifier.testTag("detail.hero.$side.score"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The club's crest, with its abbreviation showing only until the artwork arrives.
 *
 * The badge is fixed-size artwork, so the fallback text is pinned to font scale 1: scaling it would crop the letters
 * inside a circle that does not grow with them.
 */
@Composable
private fun TeamLogo(
    team: TeamRef,
    crestSize: Dp,
    imageTag: String = "detail.logo.${team.abbrev}",
) {
    var loaded by remember(team.logoUrl) { mutableStateOf(false) }
    Box(
        Modifier.requiredSize(crestSize).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .alpha(if (loaded) 0f else 1f)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Text(
                    text = team.abbrev,
                    modifier = if (loaded) Modifier.clearAndSetSemantics { } else Modifier,
                    style = LocalRouxCardTypography.current.shots,
                    maxLines = 1,
                )
            }
        }
        if (team.logoUrl != null) {
            AsyncImage(
                model = team.logoUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag(imageTag),
                contentScale = ContentScale.Fit,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Summary destination
 * ------------------------------------------------------------------------------------------------------------ */

@Composable
private fun SummaryDestination(
    game: Game,
    detail: GameDetail,
    staleSince: Instant?,
    onRetry: () -> Unit,
) {
    SectionCard(
        title = "Scoring by period",
        tag = "detail.section.scoring",
        loadable = detail.landing,
        failureLabel = "Scoring could not be loaded",
        onRetry = onRetry,
    ) { landing ->
        PeriodScoringTable(game = game, landing = landing)
    }

    SectionCard(
        title = "Game stats",
        tag = "detail.section.stats",
        loadable = detail.rightRail,
        failureLabel = "Game stats could not be loaded",
        onRetry = onRetry,
    ) { rail ->
        GameStatsRows(rail)
    }

    SectionCard(
        title = "Three stars",
        tag = "detail.section.stars",
        loadable = detail.landing,
        failureLabel = "Three stars could not be loaded",
        onRetry = onRetry,
    ) { landing ->
        ThreeStars(landing)
    }

    if (staleSince != null) {
        Text(
            "Showing the last saved version of this game.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = LocalRouxAccents.current.muted,
        )
    }
}

/**
 * The game's goals as a grid: one column per period, then the total.
 *
 * A completed game is read across - both clubs, every period, the running total - which is why this is a table and not
 * a list of "period: 2-1" lines. Overtime earns a column only when the game had one, so a regulation result is not
 * padded with a column of dashes.
 */
@Composable
private fun PeriodScoringTable(
    game: Game,
    landing: GameLanding,
) {
    val periods = landing.periodScoring.sortedBy { it.period }
    if (periods.isEmpty()) {
        EmptySectionLine("No scoring yet")
        return
    }
    val hasOvertime = periods.any { it.period > 3 }
    val periodCount = if (hasOvertime) 4 else 3

    Column(Modifier.testTag("detail.scoring.table")) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // The crest and its gap, then the same flexible space the club names take, so each heading is centred
            // over its own column.
            Spacer(Modifier.width(SCORING_CREST + 6.dp))
            Spacer(Modifier.weight(1f).padding(end = 8.dp))
            (1..periodCount).forEach { period ->
                val label = if (period == 4) "OT" else period.toString()
                PeriodCell(label, header = true, tag = "detail.period.header.$label")
            }
            PeriodCell("F", header = true, tag = "detail.period.header.F")
        }
        TeamGoalRow(
            team = game.away,
            periods = periods,
            periodCount = periodCount,
            pick = { it.awayGoals },
            total = periods.sumOf { it.awayGoals },
        )
        TeamGoalRow(
            team = game.home,
            periods = periods,
            periodCount = periodCount,
            pick = { it.homeGoals },
            total = periods.sumOf { it.homeGoals },
        )
    }
}

@Composable
private fun PeriodCell(
    text: String,
    header: Boolean = false,
    tag: String? = null,
) {
    Text(
        text,
        modifier = Modifier.width(PERIOD_COLUMN.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        textAlign = TextAlign.Center,
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
        color = if (header) LocalRouxAccents.current.muted else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TeamGoalRow(
    team: TeamRef,
    periods: List<PeriodScoring>,
    periodCount: Int,
    pick: (PeriodScoring) -> Int,
    total: Int,
) {
    val name = TeamNames.cityAndName(team).second
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.testTag("detail.scoring.crest.${team.abbrev}")) {
            TeamLogo(team = team, crestSize = SCORING_CREST, imageTag = "detail.logo.scoring.${team.abbrev}")
        }
        Spacer(Modifier.width(6.dp))
        Text(
            // The club's own name, spelled out: an abbreviation in the scoring grid makes the reader translate it back.
            name,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        (1..periodCount).forEach { period ->
            val entry = periods.firstOrNull { it.period == period }
            PeriodCell(
                text = entry?.let { pick(it).toString() } ?: "—",
                tag = "detail.period.${team.abbrev}.$period",
            )
        }
        PeriodCell(total.toString(), tag = "detail.period.${team.abbrev}.F")
    }
}

@Composable
private fun ThreeStars(landing: GameLanding) {
    if (landing.threeStars.isEmpty()) {
        EmptySectionLine("No stars named yet")
        return
    }
    Column {
        landing.threeStars.forEach { star ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    star.rank.toString(),
                    modifier = Modifier.width(20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalRouxAccents.current.muted,
                )
                Text(
                    star.player.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    star.player.teamAbbrev,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalRouxAccents.current.muted,
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Game stats
 * ------------------------------------------------------------------------------------------------------------ */

/** One comparison row, already formatted for display, with the numbers its bar is drawn from. */
internal data class StatRow(
    val label: String,
    val awayText: String,
    val homeText: String,
    val awayValue: Int,
    val homeValue: Int,
)

/** The league's category codes, in the order the design shows them. */
private val TEAM_STAT_LABELS =
    linkedMapOf(
        "sog" to "Shots",
        "faceoffWinningPctg" to "Faceoffs",
        "powerPlay" to "Power play",
        "pim" to "Penalty minutes",
        "hits" to "Hits",
        "blockedShots" to "Blocked shots",
        "giveaways" to "Giveaways",
        "takeaways" to "Takeaways",
    )

@Composable
private fun GameStatsRows(rail: GameRightRail) {
    val rows = rail.comparisonRows()
    if (rows.isEmpty()) {
        EmptySectionLine("No team totals in this game")
        return
    }
    Column {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.awayText,
                    modifier = Modifier.width(46.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    row.label.uppercase(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalRouxAccents.current.muted,
                )
                Text(
                    row.homeText,
                    modifier = Modifier.width(46.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            SplitBar(
                awayValue = row.awayValue,
                homeValue = row.homeValue,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
            )
        }
    }
}

/**
 * The rail's own rows, ordered, with the duplicate notations folded away.
 *
 * The payload sends ten rows, and two of them are the same fact in another notation: `faceoffWins` repeats
 * `faceoffWinningPctg`, and `powerPlayPctg` repeats `powerPlay`. Drawing both would be two bars for one stat.
 *
 * Values arrive as integers, fractions of one, or `made/chances` strings depending on the row, so each is parsed as the
 * kind of number it is. A blanket integer coercion is what turned 0.56 into 56% of a bar, and turned `28/50` into
 * nothing at all.
 */
internal fun GameRightRail.comparisonRows(): List<StatRow> {
    val byCategory = teamStats.associateBy { it.label }
    return buildList {
        shots?.let {
            add(StatRow("Shots", it.awayShots.toString(), it.homeShots.toString(), it.awayShots, it.homeShots))
        }
        powerPlay?.let {
            add(
                StatRow(
                    label = "Power play",
                    awayText = "${it.awayGoals}/${it.awayChances}",
                    homeText = "${it.homeGoals}/${it.homeChances}",
                    awayValue = it.awayGoals,
                    homeValue = it.homeGoals,
                ),
            )
        }
        TEAM_STAT_LABELS.forEach { (category, label) ->
            // Shots and the power play already came from their typed rows above.
            if (category == "sog" || category == "powerPlay") return@forEach
            val stat = byCategory[category] ?: return@forEach
            val away = parseStatValue(category, stat.away) ?: return@forEach
            val home = parseStatValue(category, stat.home) ?: return@forEach
            add(
                StatRow(
                    label = label,
                    awayText = formatStatText(category, stat.away, away),
                    homeText = formatStatText(category, stat.home, home),
                    awayValue = away,
                    homeValue = home,
                ),
            )
        }
    }
}

/** The number the bar is drawn from: a fraction of one as whole percent, a fraction as its numerator, else the value. */
private fun parseStatValue(
    category: String,
    raw: String,
): Int? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    return when {
        category == "faceoffWinningPctg" -> text.toDoubleOrNull()?.let { (it * 100).roundToInt() }
        text.contains('/') -> text.substringBefore('/').trim().toIntOrNull()
        else -> text.toIntOrNull() ?: text.toDoubleOrNull()?.roundToInt()
    }
}

private fun formatStatText(
    category: String,
    raw: String,
    value: Int,
): String {
    val text = raw.trim()
    return when {
        category == "faceoffWinningPctg" -> "$value%"
        text.contains('/') -> text
        else -> value.toString()
    }
}

/** The narrowest either half of a comparison bar may be, so a zero never reads as a finished full-width bar. */
internal const val MIN_BAR_SIDE_DP = 13f

/**
 * The two halves of a comparison bar, as fractions of the track.
 *
 * Split by share of the total, then floored at [minSideDp] a side - the floor is what stops a 1-0 from drawing as a
 * solid bar. Both zero means nothing to divide: the caller draws the empty track and no fill, rather than inventing
 * 50/50.
 */
internal fun barShares(
    awayValue: Int,
    homeValue: Int,
    trackDp: Float,
    minSideDp: Float = MIN_BAR_SIDE_DP,
): Pair<Float, Float> {
    if (awayValue <= 0 && homeValue <= 0) return 0f to 0f
    if (trackDp <= 0f) return 0f to 0f
    val total = (awayValue.coerceAtLeast(0) + homeValue.coerceAtLeast(0)).toFloat()
    var away = awayValue.coerceAtLeast(0) / total * trackDp
    var home = homeValue.coerceAtLeast(0) / total * trackDp
    val floor = minOf(minSideDp, trackDp / 2f)
    if (away < floor) {
        away = floor
        home = trackDp - away
    }
    if (home < floor) {
        home = floor
        away = trackDp - home
    }
    return (away / trackDp) to (home / trackDp)
}

@Composable
private fun SplitBar(
    awayValue: Int,
    homeValue: Int,
    modifier: Modifier = Modifier,
) {
    val accents = LocalRouxAccents.current
    val track = accents.muted.copy(alpha = 0.28f)
    BoxWithConstraints(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track)
            .clearAndSetSemantics { },
    ) {
        val (awayShare, homeShare) = barShares(awayValue, homeValue, maxWidth.value)
        if (awayShare > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(awayShare)
                    .height(6.dp)
                    .background(accents.fill),
            )
        }
        if (homeShare > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(homeShare)
                    .height(6.dp)
                    .align(Alignment.CenterEnd)
                    .background(accents.muted),
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Club destination: one card, goalies then skaters
 * ------------------------------------------------------------------------------------------------------------ */

/**
 * Both lists in one card, goalies first.
 *
 * A reader looking at one club's night wants the whole roster, not two cards that each carry half a heading. The stat
 * columns scroll sideways together with their headers because eight columns plus a name do not fit 360 dp, and shrinking
 * the type to make them fit is the answer that failed the last time it was tried.
 */
@Composable
private fun TeamDestination(
    detail: GameDetail,
    team: TeamRef,
) {
    val boxscore = detail.boxscore
    val goalies =
        boxscore.data
            ?.goalies
            ?.filter { it.goalie.teamAbbrev == team.abbrev }
            .orEmpty()
    val skaters =
        boxscore.data
            ?.skaters
            ?.filter { it.skater.teamAbbrev == team.abbrev }
            .orEmpty()

    val name = TeamNames.cityAndName(team).second
    SectionCard(
        // Crest, club, then what the table is: the heading names whose table it is at a glance.
        title = "$name Player Stats",
        tag = "detail.section.players",
        emphasised = true,
        headerCrest = team,
        loadable = boxscore,
        failureLabel = "Player stats could not be loaded",
    ) { _ ->
        if (goalies.isEmpty() && skaters.isEmpty()) {
            EmptySectionLine("No player lines for this club")
        } else {
            Column {
                if (skaters.isNotEmpty()) {
                    SubHeading("Skaters")
                    RosterTable(
                        tag = "detail.section.skaters",
                        headers = SKATER_COLUMNS,
                        rows = skaters.map { skaterRow(it) },
                    )
                }
                if (goalies.isNotEmpty()) {
                    Spacer(Modifier.height(if (skaters.isEmpty()) 0.dp else 14.dp))
                    SubHeading("Goalies")
                    RosterTable(
                        tag = "detail.section.goalies",
                        headers = GOALIE_COLUMNS,
                        rows = goalies.map { goalieRow(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = LocalRouxAccents.current.muted,
    )
}

private fun skaterRow(line: SkaterLine): Pair<String, List<String>> =
    // The name is the name; position is its own column so a column of surnames stays readable.
    line.skater.name to
        listOf(
            line.position,
            line.goals.toString(),
            line.assists.toString(),
            line.points.toString(),
            if (line.plusMinus > 0) "+${line.plusMinus}" else line.plusMinus.toString(),
            line.shotsOnGoal.toString(),
            line.penaltyMinutes.toString(),
            formatIceTime(line.toiSeconds),
            line.faceoffPercent()?.let { "$it%" } ?: "—",
        )

private fun goalieRow(line: GoalieLine): Pair<String, List<String>> =
    line.goalie.name to
        listOf(
            line.shotsAgainst.toString(),
            line.saves.toString(),
            line.savePercentage()?.let { formatAverage(it * 100, 1) } ?: "—",
            line.goalsAgainstAverage()?.let { formatAverage(it, 2) } ?: "—",
        )

private val GOALIE_COLUMNS = listOf("SA", "SV", "SV%", "GAA")
private val SKATER_COLUMNS = listOf("POS", "G", "A", "P", "+/-", "SOG", "PIM", "TOI", "FO%")

@Composable
private fun RosterTable(
    tag: String,
    headers: List<String>,
    rows: List<Pair<String, List<String>>>,
) {
    Row(Modifier.fillMaxWidth()) {
        // The names stay put. The reason to scroll is to reach a column, never to lose track of whose row it is.
        Column {
            Spacer(Modifier.height(ROW_HEADER_HEIGHT))
            rows.forEach { (name, _) ->
                Box(Modifier.width(NAME_COLUMN.dp).height(ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .testTag(tag),
        ) {
            Row {
                headers.forEach { StatHeader(it) }
            }
            rows.forEach { (_, values) ->
                Row {
                    values.forEach { StatCell(it) }
                }
            }
        }
    }
}

private val CREST_SIZE = 38.dp
private val SCORING_CREST = 24.dp

/** The pills are 44 dp tall, so their crest can carry real weight inside the tab. */
private val PILL_CREST = 26.dp

/** The players card's crest sits beside its heading and has the room to match the hero's weight. */
private val PLAYERS_CREST = 30.dp
private val ROW_HEADER_HEIGHT = 22.dp
private val ROW_HEIGHT = 30.dp

@Composable
private fun StatCell(text: String) {
    Box(
        Modifier.width(STAT_COLUMN.dp).height(ROW_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatHeader(text: String) {
    Text(
        text,
        modifier = Modifier.width(STAT_COLUMN.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = LocalRouxAccents.current.muted,
        maxLines = 1,
    )
}

private const val NAME_COLUMN = 136
private const val STAT_COLUMN = 42
private const val PERIOD_COLUMN = 40

internal fun formatIceTime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val minutes = seconds / 60
    val rest = seconds % 60
    return "$minutes:${rest.toString().padStart(2, '0')}"
}

internal fun formatAverage(
    value: Double,
    decimals: Int,
): String {
    val scale =
        when (decimals) {
            1 -> 10.0
            2 -> 100.0
            else -> 1.0
        }
    val rounded = kotlin.math.round(value * scale) / scale
    val text = rounded.toString()
    val dot = text.indexOf('.')
    if (dot < 0) return text + "." + "0".repeat(decimals)
    val have = text.length - dot - 1
    return if (have >= decimals) text.substring(0, dot + 1 + decimals) else text + "0".repeat(decimals - have)
}

/* ---------------------------------------------------------------------------------------------------------------
 * Cards, failure lines, and the tab strip
 * ------------------------------------------------------------------------------------------------------------ */

@Composable
private fun <T> SectionCard(
    title: String,
    tag: String,
    loadable: Loadable<T>,
    failureLabel: String,
    /** Card titles are quiet by default; the players card is a whole table and says so in its own voice. */
    emphasised: Boolean = false,
    /** The players card is identified by its club's crest, so the heading reads as that club's table. */
    headerCrest: TeamRef? = null,
    /** Null when this card has nothing useful to retry - the page retries as a whole, not one endpoint. */
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val data = loadable.data
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
            .testTag(tag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (headerCrest != null) {
                Box(Modifier.testTag("$tag.crest")) {
                    TeamLogo(
                        team = headerCrest,
                        crestSize = PLAYERS_CREST,
                        imageTag = "detail.logo.players.${headerCrest.abbrev}",
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title.uppercase(),
                style = if (emphasised) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                fontWeight = if (emphasised) FontWeight.Bold else null,
                color = if (emphasised) MaterialTheme.colorScheme.onSurface else LocalRouxAccents.current.muted,
            )
        }
        Spacer(Modifier.height(if (emphasised) 10.dp else 8.dp))
        when {
            data != null -> {
                content(data)
            }

            loadable.error != null -> {
                Column {
                    Text(failureLabel, style = MaterialTheme.typography.bodySmall)
                    if (onRetry != null) {
                        RetryLine(onRetry, tag)
                    }
                }
            }

            loadable.freshness is Freshness.Missing -> {
                EmptySectionLine("Loading")
            }

            else -> {
                EmptySectionLine("Not available")
            }
        }
    }
}

@Composable
private fun RetryLine(
    onRetry: () -> Unit,
    tag: String,
) {
    Text(
        "Try again",
        modifier =
            Modifier
                .padding(top = 6.dp)
                .heightIn(min = 48.dp)
                .clickable(onClick = onRetry)
                .testTag("$tag.retry")
                .reachTarget("$tag.retry")
                .padding(vertical = 12.dp),
        style = MaterialTheme.typography.labelMedium,
        color = LocalRouxAccents.current.fill,
    )
}

@Composable
private fun EmptySectionLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalRouxAccents.current.muted,
    )
}

/**
 * Three destinations as tabs: neutral fill, an orange edge around the selected one, no underline.
 *
 * A rounded rectangle rather than a folder tab, so it cannot be read as part of the app's own navigation bar sitting
 * directly beneath it.
 */
@Composable
private fun DetailTabStrip(
    away: TeamRef,
    home: TeamRef,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val accents = LocalRouxAccents.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The club pills carry the real crest and the club's own name: an abbreviation on a tab asks the reader to
        // decode it, which is the same mistake the scoreboard had.
        listOf<TeamRef?>(null, away, home).forEachIndexed { index, team ->
            val label = team?.let { TeamNames.cityAndName(it).second } ?: "Summary"
            val tag = team?.abbrev ?: "Summary"
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.5.dp, accents.fill, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        },
                    ).clickable { onSelect(index) }
                    .testTag("detail.tab.$tag")
                    .reachTarget("detail.tab.$tag"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (team != null) {
                        Box(Modifier.testTag("detail.tab.$tag.crest")) {
                            TeamLogo(team = team, crestSize = PILL_CREST, imageTag = "detail.logo.tab.$tag")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label,
                        modifier = Modifier.testTag("detail.tab.$tag.label"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else accents.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
