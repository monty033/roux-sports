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
