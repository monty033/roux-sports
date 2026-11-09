@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
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

/** A club's crest on the header of a two-club card. */
private val HEADER_CREST = 24.dp

/**
 * The block the four two-club cards share: each club's crest and nickname over its own half, the away club hard left and
 * the home club hard right, then the card's rows beneath. A club is named once, here; the rows carry values only.
 */
@Composable
private fun TwoClubBlock(
    game: Game,
    tag: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TwoClubHalves(
            away = { ClubHeader(team = game.away, tag = "$tag.club.away", alignEnd = false) },
            home = { ClubHeader(team = game.home, tag = "$tag.club.home", alignEnd = true) },
        )
        content()
    }
}

/**
 * Two equal columns with the same gap the header uses, so each card's rows sit under the club they belong to. The away
 * column starts at the left edge and the home column ends at the right edge.
 */
@Composable
private fun TwoClubHalves(
    away: @Composable ColumnScope.() -> Unit,
    home: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, content = away)
        Spacer(Modifier.width(HALF_GAP))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, content = home)
    }
}

/**
 * One club's crest and nickname. The nickname is the same word the tab pills use, on one line: a name that does not fit
 * its half is clipped by the layout rather than wrapped, and the audit measures that it fits.
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
            maxLines = 1,
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
            Spacer(Modifier.width(6.dp))
            crest()
        } else {
            crest()
            Spacer(Modifier.width(6.dp))
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
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
    )
}

@Composable
private fun LastTenRows(
    game: Game,
    rail: GameRightRail,
) {
    TwoClubBlock(game = game, tag = "detail.pregame.last10") {
        TwoClubHalves(
            away = {
                ClubValue(
                    text = rail.awayForm?.let { PreGameFormat.lastTenText(it) },
                    tag = "detail.pregame.last10.away",
                    alignEnd = false,
                )
            },
            home = {
                ClubValue(
                    text = rail.homeForm?.let { PreGameFormat.lastTenText(it) },
                    tag = "detail.pregame.last10.home",
                    alignEnd = true,
                )
            },
        )
    }
}

/**
 * The season statistics, one row each: the away value and its rank on the left, the label between, the home rank and value
 * on the right, then the comparison bar drawn from the values. The bar is the same split the completed game's stats draw.
 */
@Composable
private fun SeasonStatRows(
    game: Game,
    rows: List<SeasonStatRow>,
) {
    TwoClubBlock(game = game, tag = "detail.pregame.season") {
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

/** One player's line on a two-club card: the photo, the name, and the figures printed under the name when there are any. */
private data class PlayerLine(
    val name: String,
    val photoUrl: String?,
    val photoTag: String,
    val figures: String?,
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
            figures = "${skater.goals} G · ${skater.assists} A · ${skater.points} PTS",
        )
    }

@Composable
private fun TopPerformerRows(
    game: Game,
    landing: GameLanding,
    seasonStartYear: Int?,
) {
    val away = PreGameFormat.topPerformers(landing.awayTeamId, landing.skaters)
    val home = PreGameFormat.topPerformers(landing.homeTeamId, landing.skaters)
    TwoClubBlock(game = game, tag = "detail.pregame.performers") {
        TwoClubHalves(
            away = {
                PlayerColumn(
                    tag = "detail.pregame.performers.away",
                    lines = skaterLines(game.away, away, seasonStartYear, side = "away"),
                    alignEnd = false,
                )
            },
            home = {
                PlayerColumn(
                    tag = "detail.pregame.performers.home",
                    lines = skaterLines(game.home, home, seasonStartYear, side = "home"),
                    alignEnd = true,
                )
            },
        )
    }
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
    val away = PreGameFormat.goaliesFor(landing.awayTeamId, landing.goalies)
    val home = PreGameFormat.goaliesFor(landing.homeTeamId, landing.goalies)
    TwoClubBlock(game = game, tag = "detail.pregame.goalies") {
        TwoClubHalves(
            away = {
                PlayerColumn(
                    tag = "detail.pregame.goalies.away",
                    lines =
                        away.map { goalie ->
                            PlayerLine(
                                name = goalie.name,
                                photoUrl = PlayerMugshot.url(goalie.playerId, game.away.abbrev, seasonStartYear),
                                photoTag = "detail.pregame.goalies.away.${goalie.name}",
                                figures = PreGameFormat.goalieStats(goalie),
                            )
                        },
                    alignEnd = false,
                )
            },
            home = {
                PlayerColumn(
                    tag = "detail.pregame.goalies.home",
                    lines =
                        home.map { goalie ->
                            PlayerLine(
                                name = goalie.name,
                                photoUrl = PlayerMugshot.url(goalie.playerId, game.home.abbrev, seasonStartYear),
                                photoTag = "detail.pregame.goalies.home.${goalie.name}",
                                figures = PreGameFormat.goalieStats(goalie),
                            )
                        },
                    alignEnd = true,
                )
            },
        )
    }
}

/**
 * One club's players in its half of a two-club card. The home column reads its photos and names mirrored, so each
 * player's photo stays on the outer edge, the same side as the club's crest in the header.
 */
@Composable
private fun PlayerColumn(
    tag: String,
    lines: List<PlayerLine>,
    alignEnd: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        lines.forEach { line -> PlayerRow(line = line, alignEnd = alignEnd) }
    }
}

@Composable
private fun PlayerRow(
    line: PlayerLine,
    alignEnd: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (alignEnd) {
            PlayerWords(line = line, alignEnd = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            PlayerMugshotImage(url = line.photoUrl, name = line.name, tag = line.photoTag)
        } else {
            PlayerMugshotImage(url = line.photoUrl, name = line.name, tag = line.photoTag)
            Spacer(Modifier.width(6.dp))
            PlayerWords(line = line, alignEnd = false, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerWords(
    line: PlayerLine,
    alignEnd: Boolean,
    modifier: Modifier,
) {
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            line.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align,
        )
        line.figures?.let { figures ->
            Text(
                figures,
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
                textAlign = align,
            )
        }
    }
}
