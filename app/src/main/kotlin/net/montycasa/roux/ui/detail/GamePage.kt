@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.PlayerMugshot
import net.montycasa.roux.domain.model.SkaterSeason
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.PreGameFormat
import net.montycasa.roux.domain.policy.SeasonSeries
import net.montycasa.roux.domain.policy.SeasonStatRow
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.theme.LocalRouxAccents
import kotlin.time.Instant

@Composable
internal fun GameDestination(
    game: Game,
    detail: GameDetail,
    staleSince: Instant?,
    onRetry: () -> Unit,
    /** True for a game that has not started. Only then do the pre-game cards exist; a started game renders as it always did. */
    preGame: Boolean = false,
    /** The season the game belongs to, which names the players' mugshots. Null leaves every photo to its initials. */
    seasonStartYear: Int? = null,
) {
    SectionCard(
        title = "Scoring by period",
        hideWhenEmpty = { it.periodScoring.isEmpty() },
        tag = "detail.section.scoring",
        loadable = detail.landing,
        failureLabel = "Scoring could not be loaded",
        onRetry = onRetry,
    ) { landing ->
        PeriodScoringTable(game = game, landing = landing)
    }

    SectionCard(
        title = "Shots by period",
        hideWhenEmpty = { it.shotsByPeriod.isEmpty() },
        tag = "detail.section.shots",
        loadable = detail.rightRail,
        failureLabel = "Shots could not be loaded",
        onRetry = onRetry,
    ) { rail ->
        ShotsByPeriodTable(game = game, rail = rail)
    }

    SectionCard(
        title = "Game stats",
        hideWhenEmpty = { it.comparisonRows().isEmpty() },
        tag = "detail.section.stats",
        loadable = detail.rightRail,
        failureLabel = "Game stats could not be loaded",
        onRetry = onRetry,
    ) { rail ->
        GameStatsRows(game = game, rail = rail)
    }

    // The period table's tag is already detail.section.scoring, so the summary takes its own hook rather than
    // making every existing onNodeWithTag("detail.section.scoring") ambiguous.
    SectionCard(
        title = "Scoring summary",
        tag = "detail.section.scoring-summary",
        loadable = detail.landing,
        failureLabel = "Scoring summary could not be loaded",
        onRetry = onRetry,
        hideWhenEmpty = { it.goals.isEmpty() },
    ) { landing ->
        ScoringSummary(game = game, landing = landing)
    }

    SectionCard(
        title = "Penalties",
        tag = "detail.section.penalties-summary",
        loadable = detail.landing,
        failureLabel = "Penalties could not be loaded",
        onRetry = onRetry,
        hideWhenEmpty = { it.penalties.isEmpty() },
    ) { landing ->
        PenaltiesSummary(game = game, landing = landing)
    }

    SectionCard(
        title = "Three stars",
        hideWhenEmpty = { it.threeStars.isEmpty() },
        tag = "detail.section.stars",
        loadable = detail.landing,
        failureLabel = "Three stars could not be loaded",
        onRetry = onRetry,
    ) { landing ->
        ThreeStars(landing)
    }

    if (preGame) {
        PreGameCards(game = game, detail = detail, seasonStartYear = seasonStartYear, onRetry = onRetry)
    }

    SectionCard(
        title = "Game info",
        tag = "detail.section.info",
        loadable = detail.landing,
        failureLabel = "Game info could not be loaded",
        inlineRetry = true,
        onRetry = onRetry,
        hideWhenEmpty = { it.venue == null && it.broadcasts.isEmpty() },
    ) { landing ->
        landing.venue?.let { venue ->
            InfoRow("Arena", listOfNotNull(venue.name, venue.city).joinToString(" · "))
        }
        if (landing.broadcasts.isNotEmpty()) {
            InfoRow(
                "Where to watch",
                landing.broadcasts
                    .groupBy { it.country }
                    .map { (country, broadcasts) ->
                        val networks =
                            broadcasts
                                .sortedBy { if (it.market == "N") 0 else 1 }
                                .map { it.network }
                                .distinct()
                                .take(3)
                                .joinToString(" · ")
                        if (country == null) networks else "$country: $networks"
                    }.joinToString("\n"),
            )
        }
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

@Composable
private fun ThreeStars(landing: GameLanding) {
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

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalRouxAccents.current.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The four cards a game that has not started can show, in page order. Each hides on its own when its source is empty,
 * so a game the feed knows little about shows only what it has.
 */
@Composable
private fun PreGameCards(
    game: Game,
    detail: GameDetail,
    seasonStartYear: Int?,
    onRetry: () -> Unit,
) {
    SectionCard(
        title = "Last Ten Games",
        tag = "detail.section.last10",
        loadable = detail.rightRail,
        failureLabel = "Last 10 could not be loaded",
        onRetry = onRetry,
        hideWhenEmpty = { it.awayForm == null && it.homeForm == null },
    ) { rail ->
        LastTenRows(game = game, rail = rail)
    }

    SectionCard(
        title = "Season stats",
        tag = "detail.section.season-stats",
        loadable = detail.rightRail,
        failureLabel = "Season stats could not be loaded",
        onRetry = onRetry,
        hideWhenEmpty = { PreGameFormat.seasonStatRows(it.awaySeasonStats, it.homeSeasonStats).isEmpty() },
    ) { rail ->
        SeasonStatRows(game = game, rows = PreGameFormat.seasonStatRows(rail.awaySeasonStats, rail.homeSeasonStats))
    }

    SectionCard(
        title = "Season series",
        tag = "detail.section.season-series",
        loadable = detail.rightRail,
        failureLabel = "Season series could not be loaded",
        onRetry = onRetry,
        hideWhenEmpty = { SeasonSeries.meetings(it.seasonSeries, game.away.abbrev, game.home.abbrev).isEmpty() },
    ) { rail ->
        SeasonSeriesRows(game = game, rail = rail)
    }

    SectionCard(
        title = "Top Performers",
        tag = "detail.section.top-performers",
        loadable = detail.landing,
        failureLabel = "Top Performers could not be loaded",
        onRetry = onRetry,
        hideWhenEmpty = {
            PreGameFormat.topPerformers(it.awayTeamId, it.skaters).isEmpty() &&
                PreGameFormat.topPerformers(it.homeTeamId, it.skaters).isEmpty()
        },
    ) { landing ->
        TopPerformerRows(game = game, landing = landing, seasonStartYear = seasonStartYear)
    }

    SectionCard(
        title = "Goaltenders",
        tag = "detail.section.goaltenders",
        loadable = detail.landing,
        failureLabel = "Goaltenders could not be loaded",
        onRetry = onRetry,
        hideWhenEmpty = {
            PreGameFormat.goaliesFor(it.awayTeamId, it.goalies).isEmpty() &&
                PreGameFormat.goaliesFor(it.homeTeamId, it.goalies).isEmpty()
        },
    ) { landing ->
        GoaltenderRows(game = game, landing = landing, seasonStartYear = seasonStartYear)
    }
}

/** The gap between the two clubs' columns, the same in the header and in every row beneath it. */
private val HALF_GAP = 12.dp

/** A club's crest on the header of a two-club card, and the gap between it and the nickname. */
private val HEADER_CREST = 24.dp
private val HEADER_GAP = 6.dp

/** The width a player's row gives up beside its words: the photo and the gap after it. */
private val PHOTO_RESERVE = MUGSHOT_SIZE + 6.dp

/** The share of a season-stats card's width each club's value takes side by side: the two values and the 1.2 label column. */
private val SEASON_SIDE = 1f / 3.2f

/** The gap between the pieces of one side-by-side row, a value and its rank. */
private val ROW_PIECE_GAP = 6.dp

/** One piece of a side-by-side row in a club's column: its text and the style it is drawn in. */
private data class RowPiece(
    val text: String,
    val style: TextStyle,
)

/**
 * How one club's column lays out on a two-club card. Stacked: the club is full width under its own header, not in a
 * half. Wrap: its text may break at spaces. Without wrap, a word is wider than the column, so the text stays on one
 * line and ellipsizes rather than splitting the word.
 */
private data class ClubLayout(
    val stacked: Boolean,
    val wrap: Boolean,
)

private val LocalClubLayout = compositionLocalOf { ClubLayout(stacked = false, wrap = true) }

/** A club's text wraps at its spaces only while every word fits its column. Otherwise it stays on one line. */
@Composable
private fun clubMaxLines(): Int = if (LocalClubLayout.current.wrap) Int.MAX_VALUE else 1

/**
 * True when no space-separated word in [texts] is wider than [widthPx]. A word wider than its column is the only thing
 * that makes a line break inside it, so this is the check that keeps every name whole.
 */
private fun wordsFit(
    measurer: TextMeasurer,
    style: TextStyle,
    texts: List<String>,
    widthPx: Float,
): Boolean = texts.all { text -> text.split(' ').all { word -> measurer.measure(word, style).size.width <= widthPx } }

/** True when every row's pieces, laid side by side with [gapPx] between them, fit in [widthPx]. */
private fun rowsFit(
    measurer: TextMeasurer,
    rows: List<List<RowPiece>>,
    widthPx: Float,
    gapPx: Float,
): Boolean =
    rows.all { row ->
        row.sumOf { measurer.measure(it.text, it.style).size.width } + gapPx * (row.size - 1) <= widthPx
    }

/**
 * The block the two-club cards share. Each club's crest and nickname sit over its own half, the away club hard left and
 * the home club hard right, with the card's rows beneath. A club is named once, here; the rows carry values only.
 *
 * When a single word in either club's text is wider than its half, the halves cannot hold the text without splitting
 * the word, so each club is stacked instead: full width, its header on its own line and its rows beneath, the away club
 * first. The check is measured, so it follows the width and the font scale. [awayText] and [homeText] are every string
 * the club prints in the card; the nickname is measured here. A club's column is its half unless [sideFraction] gives
 * a share of the card, and [rowReserve] is the width a row gives up beside its text, such as a photo.
 */
@Composable
private fun TwoClubBlock(
    game: Game,
    tag: String,
    awayText: List<String>,
    homeText: List<String>,
    awayRows: List<List<RowPiece>> = emptyList(),
    homeRows: List<List<RowPiece>> = emptyList(),
    sideFraction: Float? = null,
    rowReserve: Dp = PHOTO_RESERVE,
    sideBySide: @Composable () -> Unit,
    away: @Composable () -> Unit,
    home: @Composable () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val awayName = TeamNames.cityAndName(game.away).second
    val homeName = TeamNames.cityAndName(game.home).second
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val card = with(density) { maxWidth.toPx() }
        // A half is the card less the gap between the two columns, split evenly: the width TwoClubHalves gives each club.
        val half = (card - with(density) { HALF_GAP.toPx() }) * 0.5f
        val column = sideFraction?.let { card * it } ?: half
        val crest = with(density) { (HEADER_CREST + HEADER_GAP).toPx() }
        val reserve = with(density) { rowReserve.toPx() }
        val gap = with(density) { ROW_PIECE_GAP.toPx() }
        // Side by side, the headers sit in halves, so the names must fit a half less the crest. Every word of the
        // rows must fit the club's column, and each row's pieces must fit beside each other. Stacked, a club needs
        // only the full card width.
        val fitsSideBySide =
            wordsFit(measurer, nameStyle, listOf(awayName, homeName), half - crest) &&
                wordsFit(measurer, nameStyle, awayText + homeText, column - reserve) &&
                rowsFit(measurer, awayRows, column - reserve, gap) &&
                rowsFit(measurer, homeRows, column - reserve, gap)
        val awayWrap =
            wordsFit(measurer, nameStyle, listOf(awayName), card - crest) &&
                wordsFit(measurer, nameStyle, awayText, card - reserve)
        val homeWrap =
            wordsFit(measurer, nameStyle, listOf(homeName), card - crest) &&
                wordsFit(measurer, nameStyle, homeText, card - reserve)
        if (fitsSideBySide) {
            CompositionLocalProvider(LocalClubLayout provides ClubLayout(stacked = false, wrap = true)) {
                Column(Modifier.fillMaxWidth()) {
                    TwoClubHalves(
                        away = { ClubHeader(team = game.away, tag = "$tag.club.away", alignEnd = false) },
                        home = { ClubHeader(team = game.home, tag = "$tag.club.home", alignEnd = true) },
                    )
                    sideBySide()
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalClubLayout provides ClubLayout(stacked = true, wrap = awayWrap)) {
                    Column(Modifier.fillMaxWidth()) {
                        ClubHeader(team = game.away, tag = "$tag.club.away", alignEnd = false)
                        away()
                    }
                }
                Spacer(Modifier.height(HALF_GAP))
                CompositionLocalProvider(LocalClubLayout provides ClubLayout(stacked = true, wrap = homeWrap)) {
                    Column(Modifier.fillMaxWidth()) {
                        ClubHeader(team = game.home, tag = "$tag.club.home", alignEnd = false)
                        home()
                    }
                }
            }
        }
    }
}

/**
 * Two equal columns with the same gap the header uses, so each card's rows sit under the club they belong to. The away
 * column starts at the left edge and the home column ends at the right edge.
 */
@Composable
private fun TwoClubHalves(
    away: @Composable () -> Unit,
    home: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { away() }
        Spacer(Modifier.width(HALF_GAP))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { home() }
    }
}

/**
 * One club's crest and nickname. The nickname wraps at its spaces when every word fits; otherwise it stays on one line
 * and ellipsizes. A word is never split across lines.
 */
@Composable
private fun ClubHeader(
    team: TeamRef,
    tag: String,
    alignEnd: Boolean,
) {
    val crest: @Composable () -> Unit = {
        TeamLogo(team = team, crestSize = HEADER_CREST, imageTag = "$tag.logo")
    }
    val nickname: @Composable () -> Unit = {
        Text(
            TeamNames.cityAndName(team).second,
            modifier = Modifier.testTag("$tag.name"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = clubMaxLines(),
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (alignEnd) {
            nickname()
            Spacer(Modifier.width(HEADER_GAP))
            crest()
        } else {
            crest()
            Spacer(Modifier.width(HEADER_GAP))
            nickname()
        }
    }
}

/**
 * One club's value on a two-club card: the record and streak, printed in the club's own half. A club the feed sent no
 * value for has an empty half.
 */
@Composable
private fun ClubValue(
    text: String?,
    tag: String,
    alignEnd: Boolean,
) {
    if (text == null) return
    Text(
        text,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag(tag),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = if (alignEnd && !LocalClubLayout.current.stacked) TextAlign.End else TextAlign.Start,
        maxLines = clubMaxLines(),
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LastTenRows(
    game: Game,
    rail: GameRightRail,
) {
    val awayText = rail.awayForm?.let { PreGameFormat.lastTenText(it) }
    val homeText = rail.homeForm?.let { PreGameFormat.lastTenText(it) }
    TwoClubBlock(
        game = game,
        tag = "detail.pregame.last10",
        awayText = listOfNotNull(awayText),
        homeText = listOfNotNull(homeText),
        rowReserve = 0.dp,
        sideBySide = {
            TwoClubHalves(
                away = { ClubValue(text = awayText, tag = "detail.pregame.last10.away", alignEnd = false) },
                home = { ClubValue(text = homeText, tag = "detail.pregame.last10.home", alignEnd = true) },
            )
        },
        away = { ClubValue(text = awayText, tag = "detail.pregame.last10.away", alignEnd = false) },
        home = { ClubValue(text = homeText, tag = "detail.pregame.last10.home", alignEnd = true) },
    )
}

/**
 * The season statistics. Side by side, one row each: the away value and its rank on the left, the label between, the
 * home rank and value on the right, then the comparison bar drawn from the values. The bar is the same split the
 * completed game's stats draw. Stacked, each club lists its own values, label and value and rank, with no bar: a bar
 * is a comparison between the two clubs, and the clubs no longer share a row.
 */
@Composable
private fun SeasonStatRows(
    game: Game,
    rows: List<SeasonStatRow>,
) {
    TwoClubBlock(
        game = game,
        tag = "detail.pregame.season",
        awayText = rows.flatMap { listOf(it.label, "${it.awayValue} ${it.awayRank}") },
        homeText = rows.flatMap { listOf(it.label, "${it.homeValue} ${it.homeRank}") },
        awayRows =
            rows.map {
                listOf(
                    RowPiece(it.awayValue, MaterialTheme.typography.bodyMedium),
                    RowPiece(it.awayRank, MaterialTheme.typography.labelMedium),
                )
            },
        homeRows =
            rows.map {
                listOf(
                    RowPiece(it.homeRank, MaterialTheme.typography.labelMedium),
                    RowPiece(it.homeValue, MaterialTheme.typography.bodyMedium),
                )
            },
        sideFraction = SEASON_SIDE,
        rowReserve = 0.dp,
        sideBySide = { SideBySideSeasonRows(rows = rows) },
        away = { StackedSeasonRows(rows = rows, awayClub = true) },
        home = { StackedSeasonRows(rows = rows, awayClub = false) },
    )
}

@Composable
private fun SideBySideSeasonRows(rows: List<SeasonStatRow>) {
    rows.forEach { row ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .testTag("detail.pregame.stat.${row.label}"),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.awayValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(row.awayRank, style = MaterialTheme.typography.labelMedium, color = LocalRouxAccents.current.muted)
                }
                Text(
                    row.label,
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalRouxAccents.current.muted,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(row.homeRank, style = MaterialTheme.typography.labelMedium, color = LocalRouxAccents.current.muted)
                    Spacer(Modifier.width(6.dp))
                    Text(row.homeValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            SplitBar(
                awayValue = row.awayBar,
                homeValue = row.homeBar,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StackedSeasonRows(
    rows: List<SeasonStatRow>,
    awayClub: Boolean,
) {
    val side = if (awayClub) "away" else "home"
    rows.forEach { row ->
        val value = if (awayClub) row.awayValue else row.homeValue
        val rank = if (awayClub) row.awayRank else row.homeRank
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .testTag("detail.pregame.stat.${row.label}.$side"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
                maxLines = clubMaxLines(),
                overflow = TextOverflow.Ellipsis,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            Text(rank, style = MaterialTheme.typography.labelMedium, color = LocalRouxAccents.current.muted)
        }
    }
}

@Composable
private fun SeasonSeriesRows(
    game: Game,
    rail: GameRightRail,
) {
    val meetings = SeasonSeries.meetings(rail.seasonSeries, game.away.abbrev, game.home.abbrev)
    val standing = SeasonSeries.standing(rail.seasonSeries, game.away.abbrev, game.home.abbrev)
    Column {
        meetings.forEach { meeting ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    meeting.date.orEmpty(),
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalRouxAccents.current.muted,
                )
                Text(
                    meeting.scoreline,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            SeasonSeries.standingText(standing),
            modifier = Modifier.padding(top = 4.dp).testTag("detail.pregame.series.standing"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One player's entry on a two-club card: the photo and name on one line, then each figure line beneath it, in order.
 * Every figure is a line of its own, so no figure wraps into the next.
 */
private data class PlayerLine(
    val name: String,
    val photoUrl: String?,
    val photoTag: String,
    val figures: List<String>,
)

/** A club's top skaters as card lines, in the order they are given. Each reads its figures as goals, assists, points. */
private fun skaterLines(
    club: TeamRef,
    skaters: List<SkaterSeason>,
    seasonStartYear: Int?,
    side: String,
): List<PlayerLine> =
    skaters.map { skater ->
        PlayerLine(
            name = skater.name,
            photoUrl = PlayerMugshot.url(skater.playerId, club.abbrev, seasonStartYear),
            photoTag = "detail.pregame.performers.$side.${skater.name}",
            figures = listOf("${skater.goals} G · ${skater.assists} A · ${skater.points} PTS"),
        )
    }

@Composable
private fun TopPerformerRows(
    game: Game,
    landing: GameLanding,
    seasonStartYear: Int?,
) {
    val away = skaterLines(game.away, PreGameFormat.topPerformers(landing.awayTeamId, landing.skaters), seasonStartYear, side = "away")
    val home = skaterLines(game.home, PreGameFormat.topPerformers(landing.homeTeamId, landing.skaters), seasonStartYear, side = "home")
    PlayerCard(game = game, tag = "detail.pregame.performers", away = away, home = home)
}

/**
 * One club's goaltenders, most games first, at most three. A goaltender with no games played is not on the card. Nobody
 * is marked as the starter: the feed names none. A club with no goaltenders has no column at all.
 */
@Composable
private fun GoaltenderRows(
    game: Game,
    landing: GameLanding,
    seasonStartYear: Int?,
) {
    val away =
        PreGameFormat.goaliesFor(landing.awayTeamId, landing.goalies).map { goalie ->
            PlayerLine(
                name = goalie.name,
                photoUrl = PlayerMugshot.url(goalie.playerId, game.away.abbrev, seasonStartYear),
                photoTag = "detail.pregame.goalies.away.${goalie.name}",
                figures = goalieFigures(goalie),
            )
        }
    val home =
        PreGameFormat.goaliesFor(landing.homeTeamId, landing.goalies).map { goalie ->
            PlayerLine(
                name = goalie.name,
                photoUrl = PlayerMugshot.url(goalie.playerId, game.home.abbrev, seasonStartYear),
                photoTag = "detail.pregame.goalies.home.${goalie.name}",
                figures = goalieFigures(goalie),
            )
        }
    PlayerCard(game = game, tag = "detail.pregame.goalies", away = away, home = home)
}

/** A goaltender's two figure lines: `14 GP · 8–4–2`, then `2.71 GAA · 90.6%`. Each is left out when it has no figures. */
private fun goalieFigures(goalie: GoalieSeason): List<String> =
    listOfNotNull(PreGameFormat.goalieGamesLine(goalie), PreGameFormat.goalieRateLine(goalie))

/**
 * A card of player lines, one club per half, or stacked when a name will not fit beside its photo in a half. Names are
 * measured whole, since each sits on one line. The figure lines are not part of that decision: a figure line too long
 * for its half ellipsizes in place.
 */
@Composable
private fun PlayerCard(
    game: Game,
    tag: String,
    away: List<PlayerLine>,
    home: List<PlayerLine>,
) {
    TwoClubBlock(
        game = game,
        tag = tag,
        awayText = emptyList(),
        homeText = emptyList(),
        awayRows = away.map { listOf(RowPiece(it.name, MaterialTheme.typography.bodyMedium)) },
        homeRows = home.map { listOf(RowPiece(it.name, MaterialTheme.typography.bodyMedium)) },
        sideBySide = {
            TwoClubHalves(
                away = { PlayerColumn(tag = "$tag.away", lines = away, alignEnd = false) },
                home = { PlayerColumn(tag = "$tag.home", lines = home, alignEnd = true) },
            )
        },
        away = { PlayerColumn(tag = "$tag.away", lines = away, alignEnd = false) },
        home = { PlayerColumn(tag = "$tag.home", lines = home, alignEnd = true) },
    )
}

/**
 * One club's players in its half of a two-club card. The home column reads its photos and names mirrored, so each
 * player's photo stays on the outer edge, the same side as the club's crest in the header. Stacked, every club starts
 * at the left edge, so the photo stays on the left too.
 */
@Composable
private fun PlayerColumn(
    tag: String,
    lines: List<PlayerLine>,
    alignEnd: Boolean,
) {
    val end = alignEnd && !LocalClubLayout.current.stacked
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalAlignment = if (end) Alignment.End else Alignment.Start,
    ) {
        lines.forEach { line -> PlayerRow(line = line, alignEnd = end) }
    }
}

/**
 * One player: the photo and name on the first line, then each figure on a line of its own beneath the name. The name
 * ellipsizes only when it cannot fit beside the photo; a figure line is never wrapped and ellipsizes in place. The
 * figures are indented past the photo, so they sit under the name and never beside the initials.
 */
@Composable
private fun PlayerRow(
    line: PlayerLine,
    alignEnd: Boolean,
) {
    val indent = Modifier.padding(start = if (alignEnd) 0.dp else PHOTO_RESERVE, end = if (alignEnd) PHOTO_RESERVE else 0.dp)
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (alignEnd) {
                PlayerName(line = line, align = align, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                PlayerMugshotImage(url = line.photoUrl, name = line.name, tag = line.photoTag)
            } else {
                PlayerMugshotImage(url = line.photoUrl, name = line.name, tag = line.photoTag)
                Spacer(Modifier.width(6.dp))
                PlayerName(line = line, align = align, modifier = Modifier.weight(1f))
            }
        }
        line.figures.forEachIndexed { index, figure ->
            Text(
                figure,
                modifier = indent.fillMaxWidth().testTag("${line.photoTag}.figure.$index"),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerName(
    line: PlayerLine,
    align: TextAlign,
    modifier: Modifier,
) {
    Text(
        line.name,
        modifier = modifier.testTag("${line.photoTag}.name"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
