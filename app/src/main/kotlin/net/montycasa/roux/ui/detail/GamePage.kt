@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.policy.PreGameFormat
import net.montycasa.roux.domain.policy.SeasonSeries
import net.montycasa.roux.domain.policy.SeasonStatRow
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
        PreGameCards(game = game, detail = detail, onRetry = onRetry)
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
    onRetry: () -> Unit,
) {
    SectionCard(
        title = "Last 10",
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
        GoaltenderRows(game = game, landing = landing)
    }
}

@Composable
private fun LastTenRows(
    game: Game,
    rail: GameRightRail,
) {
    Column {
        rail.awayForm?.let { FormRows(abbrev = game.away.abbrev, form = it, side = "away") }
        rail.homeForm?.let { FormRows(abbrev = game.home.abbrev, form = it, side = "home") }
    }
}

/**
 * One club's last ten: its record and streak on one line, then its results as chips in the order the feed lists them.
 *
 * Wins read in the primary colour and everything else in the muted one. The colour is about the result, not the score,
 * so a loss in a shootout reads the same as any other loss.
 */
@Composable
private fun FormRows(
    abbrev: String,
    form: ClubForm,
    side: String,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("detail.pregame.last10.$side")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                abbrev,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            form.record?.let { record ->
                Spacer(Modifier.width(8.dp))
                Text(
                    PreGameFormat.recordText(record),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            PreGameFormat.streakText(form)?.let { streak ->
                Text(
                    streak,
                    modifier = Modifier.testTag("detail.pregame.last10.$side.streak"),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalRouxAccents.current.muted,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            form.results.forEach { result ->
                Text(
                    result,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (result == "W") MaterialTheme.colorScheme.onSurface else LocalRouxAccents.current.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SeasonStatRows(
    game: Game,
    rows: List<SeasonStatRow>,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                game.away.abbrev,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
            )
            Text(
                game.home.abbrev,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = LocalRouxAccents.current.muted,
                textAlign = TextAlign.End,
            )
        }
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("detail.pregame.stat.${row.label}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

@Composable
private fun GoaltenderRows(
    game: Game,
    landing: GameLanding,
) {
    Column {
        GoalieBlock(
            abbrev = game.away.abbrev,
            side = "away",
            goalies = PreGameFormat.goaliesFor(landing.awayTeamId, landing.goalies),
        )
        GoalieBlock(
            abbrev = game.home.abbrev,
            side = "home",
            goalies = PreGameFormat.goaliesFor(landing.homeTeamId, landing.goalies),
        )
    }
}

/**
 * One club's goaltenders, most games first. The list is never labelled with a starter: the feed names none.
 * A club with no goaltenders has no block at all.
 */
@Composable
private fun GoalieBlock(
    abbrev: String,
    side: String,
    goalies: List<GoalieSeason>,
) {
    if (goalies.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("detail.pregame.goalies.$side")) {
        Text(
            abbrev,
            style = MaterialTheme.typography.labelMedium,
            color = LocalRouxAccents.current.muted,
        )
        goalies.forEach { goalie ->
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    PreGameFormat.goalieHeading(goalie),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PreGameFormat.goalieStats(goalie)?.let { stats ->
                    Text(
                        stats,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalRouxAccents.current.muted,
                    )
                }
            }
        }
    }
}
