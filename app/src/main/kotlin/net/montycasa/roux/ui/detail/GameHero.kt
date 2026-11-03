@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.LiveCapsule
import net.montycasa.roux.ui.common.StateClock
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.common.periodLabel
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.listRow
import net.montycasa.roux.ui.theme.LocalRouxAccents
import java.time.ZoneId

@Composable
internal fun GameHero(
    game: Game,
    zone: ZoneId,
    onTeamClick: (TeamRef) -> Unit = {},
) {
    Column(
        Modifier
            .testTag("detail.hero")
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (game.state is GameState.Live) {
            val period = game.period
            val extra = period?.periodType == "OT" || period?.periodType == "SO"
            val label =
                when (period?.periodType) {
                    "OT" -> R.string.game_row_period_overtime
                    "SO" -> R.string.game_row_period_shootout
                    else -> R.string.game_row_live_label
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveCapsule(label, testTag = "detail.hero.live.capsule")
                StateClock(
                    if (period?.inIntermission == true) {
                        stringResource(R.string.game_row_intermission)
                    } else if (extra) {
                        period?.clock.orEmpty()
                    } else {
                        listOfNotNull(periodLabel(period), period?.clock?.takeIf { it.isNotBlank() }).joinToString(" ")
                    },
                    live = true,
                    inline = true,
                )
            }
        } else {
            Text(
                heroStatus(game, zone),
                style = MaterialTheme.typography.labelSmall,
                color = LocalRouxAccents.current.muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        TeamScoreLine(team = game.away, score = game.awayScore, side = "away", onTeamClick = onTeamClick)
        Spacer(Modifier.height(6.dp))
        TeamScoreLine(team = game.home, score = game.homeScore, side = "home", onTeamClick = onTeamClick)
    }
}

@Composable
private fun TeamScoreLine(
    team: TeamRef,
    score: Int?,
    side: String,
    onTeamClick: (TeamRef) -> Unit,
) {
    val (city, name) = TeamNames.cityAndName(team)
    val clubName = if (city != null) "$city $name" else name
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Reach.MIN_TARGET_DP.dp)
            .listRow()
            .clickable(role = Role.Button) { onTeamClick(team) }
            .testTag("detail.hero.$side.club")
            .semantics { contentDescription = clubName }
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
                overflow = TextOverflow.Ellipsis,
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
