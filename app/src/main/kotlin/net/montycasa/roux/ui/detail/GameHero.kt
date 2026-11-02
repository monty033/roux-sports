@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.theme.LocalRouxAccents
import java.time.ZoneId

@Composable
internal fun GameHero(
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

private val CREST_SIZE = 38.dp
