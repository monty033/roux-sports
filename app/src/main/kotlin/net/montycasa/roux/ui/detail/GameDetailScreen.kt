@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PlayerMugshot
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.PlayoffSeries
import net.montycasa.roux.domain.policy.PreGameFormat
import net.montycasa.roux.ui.common.TeamNames
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.LocalRouxAccents
import java.time.ZoneId

/**
 * One game's page: a hero, then game details or one club's roster, with the destinations in a strip at the bottom.
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
    /** A tap on either club in the hero opens that club's page. */
    onTeamClick: (TeamRef) -> Unit = {},
    /** A tap on a player in either club's player table opens that player's page. */
    onPlayerClick: (Long) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    // 0 is Game; 1 and 2 are the away and home clubs, in that order. The three are pages, so a swipe moves
    // between them the same way the pills do.
    val detailPager = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()
    val detail = state.detail
    val staleSince = detail.staleSince()
    val landing = detail.landing.data
    val heroGame =
        game.copy(
            state = landing?.state ?: game.state,
            period = landing?.period ?: game.period,
            finalKind = landing?.finalKind ?: game.finalKind,
        )
    val series =
        PlayoffSeries.forGame(
            heroGame,
            detail.rightRail.data
                ?.seasonSeries
                .orEmpty(),
        )
    // The pre-game cards and the hero's records exist only before puck drop. A started game keeps its existing page.
    val preGame = heroGame.state == GameState.Scheduled || heroGame.state == GameState.Pre
    val awayRecord = if (preGame) landing?.awayRecord?.let { PreGameFormat.clubRecordText(game.away.abbrev, it) } else null
    val homeRecord = if (preGame) landing?.homeRecord?.let { PreGameFormat.clubRecordText(game.home.abbrev, it) } else null

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailHeader(game = game, onBack = onBack)

        HorizontalPager(
            state = detailPager,
            modifier = Modifier.weight(1f).testTag("detail.pager"),
        ) { page ->
            // Each page keeps its own scroll offset, and carries its own copy of the hero so the top of the page
            // reads the same whichever club you land on.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                GameHero(
                    game = heroGame,
                    zone = zone,
                    onTeamClick = onTeamClick,
                    series = series,
                    awayRecord = awayRecord,
                    homeRecord = homeRecord,
                )

                when (page) {
                    0 -> {
                        GameDestination(
                            game = game,
                            detail = detail,
                            staleSince = staleSince,
                            onRetry = onRetry,
                            preGame = preGame,
                            seasonStartYear = PlayerMugshot.seasonStartYear(game),
                        )
                    }

                    else -> {
                        TeamDestination(
                            detail = detail,
                            team = if (page == 1) game.away else game.home,
                            onPlayerClick = onPlayerClick,
                            seasonStartYear = PlayerMugshot.seasonStartYear(game),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        DetailTabStrip(
            away = game.away,
            home = game.home,
            selected = detailPager.currentPage,
            onSelect = { index -> scope.launch { detailPager.animateScrollToPage(index) } },
        )
    }
}

/**
 * Three destinations as tabs: neutral fill, an orange edge around the selected one, no underline.
 *
 * A rounded rectangle rather than a folder tab, so it cannot be read as part of the app's own navigation bar sitting
 * directly beneath it. Each pill is as wide as its crest and label; when the three do not fit, the strip scrolls
 * sideways, so a club name is never cut short to make room.
 */
@Composable
internal fun DetailTabStrip(
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
            // The top inset is the R4 clearance between a control clipped at the page's bottom edge and the tabs.
            // The bottom inset alone places the tabs, so this shrinks the viewport rather than moving them.
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp)
            .horizontalScroll(rememberScrollState())
            .testTag("detail.tab.strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The club pills carry the real crest and the club's own name: an abbreviation on a tab asks the reader to
        // decode it, which is the same mistake the scoreboard had.
        listOf<TeamRef?>(null, away, home).forEachIndexed { index, team ->
            val label = team?.let { TeamNames.cityAndName(it).second } ?: "Game"
            val tag = team?.abbrev ?: "Game"
            val isSelected = index == selected
            Box(
                Modifier
                    .wrapContentWidth()
                    // 48 dp: this is a reach target, and the audit's R4 floor applies to targets, not paint.
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.5.dp, accents.fill, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        },
                    ).selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .testTag("detail.tab.$tag")
                    .reachTarget("detail.tab.$tag"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (team != null) {
                        Box(Modifier.testTag("detail.tab.$tag.crest")) {
                            TeamLogo(team = team, crestSize = PILL_CREST, imageTag = "detail.logo.tab.$tag")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    // No ellipsis: the pill has no width limit, so the label is always whole.
                    Text(
                        label,
                        modifier = Modifier.testTag("detail.tab.$tag.label"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else accents.muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private val PILL_CREST = 32.dp
