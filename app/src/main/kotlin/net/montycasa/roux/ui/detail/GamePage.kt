@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.GameLanding
import net.montycasa.roux.ui.theme.LocalRouxAccents
import kotlin.time.Instant

@Composable
internal fun GameDestination(
    game: Game,
    detail: GameDetail,
    staleSince: Instant?,
    onRetry: () -> Unit,
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
        title = "Game stats",
        hideWhenEmpty = { it.comparisonRows().isEmpty() },
        tag = "detail.section.stats",
        loadable = detail.rightRail,
        failureLabel = "Game stats could not be loaded",
        onRetry = onRetry,
    ) { rail ->
        GameStatsRows(game = game, rail = rail)
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
