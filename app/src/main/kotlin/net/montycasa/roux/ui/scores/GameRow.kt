@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.rememberStartTimeLabel
import net.montycasa.roux.ui.theme.Roux

/** Test tag for the logo image, so a test can assert the request was attempted. */
const val LOGO_TAG = "gameRowLogo"
const val CREST_TAG = "gameRowCrest"
const val STATE_CAPSULE_TAG = "gameRowStateCapsule"
const val STATE_RAIL_TAG = "gameRowStateRail"
const val SHOTS_TAG = "gameRowShots"
const val TEAM_LINE_TAG = "gameRowTeamLine"

/** Values from the reference's wide rules and max-width:380px list viewport variant. */
private data class CardMetrics(
    val railWidth: Dp,
    val crestSize: Dp,
    val clubSize: TextUnit,
    val scoreSize: TextUnit,
    val scoreWidth: Dp,
    val columnGap: Dp,
    val startPadding: Dp,
)

private val wideCard = CardMetrics(92.dp, 34.dp, 15.sp, 22.sp, 30.dp, 8.dp, 13.dp)
private val narrowCard = CardMetrics(82.dp, 32.dp, 14.sp, 21.sp, 28.dp, 7.dp, 11.dp)

/**
 * One informational card per game; the host owns the existing navigation callback and tag.
 * Intrinsic height lets the team rows and vertically centred state stack grow together with type size.
 */
@Composable
fun GameRow(
    game: Game,
    narrow: Boolean,
    modifier: Modifier = Modifier,
) {
    val upcoming = game.state == GameState.Scheduled || game.state == GameState.Pre
    val homeScore = game.homeScore
    val awayScore = game.awayScore
    val homeWins = game.state == GameState.Final && homeScore != null && awayScore != null && homeScore > awayScore
    val awayWins = game.state == GameState.Final && homeScore != null && awayScore != null && awayScore > homeScore
    val metrics = if (narrow) narrowCard else wideCard
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, Color.Transparent, RoundedCornerShape(16.dp))
                // Compose's border only draws. Reserve its space to match CSS border-box:
                // 28 + 2 + 28 of rows + 16 of main padding + 2 of border = 76 dp.
                .padding(1.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = metrics.startPadding, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TeamLine(
                game.away,
                game.awayScore.takeUnless { upcoming },
                upcoming,
                awayWins,
                game.state is GameState.Live,
                metrics,
                Modifier.weight(1f),
            )
            TeamLine(
                game.home,
                game.homeScore.takeUnless { upcoming },
                upcoming,
                homeWins,
                game.state is GameState.Live,
                metrics,
                Modifier.weight(1f),
            )
        }
        StateRail(game, upcoming, metrics.railWidth)
    }
}

@Composable
private fun TeamLine(
    team: TeamRef,
    score: Int?,
    upcoming: Boolean,
    winner: Boolean,
    live: Boolean,
    metrics: CardMetrics,
    modifier: Modifier,
) {
    val ink =
        when {
            winner -> Roux.accents.signature
            upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    val scoreInk =
        when {
            winner -> Roux.accents.signature
            upcoming -> Roux.accents.muted
            live -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp).testTag(TEAM_LINE_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
    ) {
        // Like the CSS crest, the artwork extends above/below its 28 dp row.
        Box(Modifier.width(metrics.crestSize).height(28.dp), contentAlignment = Alignment.Center) {
            TeamLogo(team, metrics.crestSize)
        }
        Text(
            text = team.name?.takeIf { it.isNotBlank() } ?: team.abbrev,
            modifier = Modifier.weight(1f),
            style = Roux.cardTypography.clubName.copy(fontSize = metrics.clubSize),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // A reserved slot keeps names aligned, even when no score is known. Grow with large type.
        Box(Modifier.width(metrics.scoreWidth * LocalDensity.current.fontScale.coerceAtLeast(1f)), contentAlignment = Alignment.CenterEnd) {
            if (upcoming || score != null) {
                Text(
                    text = if (upcoming) "–" else score.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    style =
                        Roux.cardTypography.score.copy(
                            fontSize = metrics.scoreSize,
                            lineHeight = metrics.scoreSize,
                            fontWeight =
                                when {
                                    upcoming -> FontWeight.Normal
                                    live -> FontWeight.Bold
                                    else -> Roux.cardTypography.score.fontWeight
                                },
                        ),
                    color = scoreInk,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun TeamLogo(
    team: TeamRef,
    crestSize: Dp,
) {
    var loaded by remember(team.logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.requiredSize(crestSize).clip(CircleShape).testTag(CREST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .alpha(if (loaded) 0f else 1f)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The badge is fixed-size crest artwork, like the logo it replaces. The adjacent
            // club name supplies scalable text; scaling the artwork would crop the abbreviation.
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Text(
                    text = team.abbrev,
                    modifier = if (loaded) Modifier.clearAndSetSemantics {} else Modifier,
                    style = Roux.cardTypography.shots,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        if (team.logoUrl != null) {
            AsyncImage(
                model = team.logoUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag(LOGO_TAG),
                contentScale = ContentScale.Fit,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

@Composable
private fun StateRail(
    game: Game,
    upcoming: Boolean,
    width: Dp,
) {
    val rule = MaterialTheme.colorScheme.outline
    val awayShots = game.awayShots
    val homeShots = game.homeShots
    val unknown = game.state is GameState.Unknown
    // The reference's .rr elements are empty spacers. .rstate centres one stack across the
    // full card height; its lines have no relationship to the two team rows.
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .testTag(STATE_RAIL_TAG)
            .drawBehind {
                drawRect(
                    rule,
                    size =
                        androidx.compose.ui.geometry
                            .Size(1.dp.toPx(), size.height),
                )
            }.padding(start = 12.dp, end = 7.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start,
    ) {
        if (unknown) {
            // Preserve the existing fallback layout for unrecognised feed states.
            Box(Modifier.weight(1f).heightIn(min = 28.dp), contentAlignment = Alignment.CenterStart) {
                RailText(stringResource(R.string.game_row_status_unavailable))
            }
        } else {
            when (game.state) {
                GameState.Scheduled, GameState.Pre -> {
                    StateClock(startTimeLabel(game), live = false)
                }

                is GameState.Live -> {
                    LiveCapsule()
                    StateClock(
                        listOfNotNull(periodLabel(game.period), game.period?.clock?.takeIf { it.isNotBlank() }).joinToString(" "),
                        live = true,
                        intermission = game.period?.inIntermission == true,
                    )
                }

                GameState.Final -> {
                    FinalCapsule(game.finalKind)
                }

                is GameState.Unknown -> {}
            }
        }
        if (unknown) {
            Box(Modifier.weight(1f).heightIn(min = 28.dp), contentAlignment = Alignment.CenterStart) {
                if (awayShots != null && homeShots != null) Shots(awayShots, homeShots)
            }
        } else if (!upcoming && awayShots != null && homeShots != null) {
            Shots(awayShots, homeShots)
        }
    }
}

@Composable
private fun Shots(
    away: Int,
    home: Int,
) {
    Text(
        stringResource(R.string.game_row_shots, away, home),
        Modifier.testTag(SHOTS_TAG),
        style = Roux.cardTypography.shots,
        color = Roux.accents.muted,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun StateClock(
    text: String,
    live: Boolean,
    intermission: Boolean = false,
) {
    val breakDescription = if (intermission) stringResource(R.string.game_row_intermission) else null
    Text(
        text,
        // Keep the break available to accessibility without adding a fourth visible rail line.
        modifier = Modifier.semantics { if (breakDescription != null) contentDescription = "$text, $breakDescription" },
        style = Roux.cardTypography.clock,
        color = if (live) Roux.accents.signature else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun LiveCapsule() {
    val liveDescription = stringResource(R.string.game_row_live_description)
    Row(
        Modifier
            .testTag(STATE_CAPSULE_TAG)
            .width(IntrinsicSize.Max)
            .background(Roux.accents.fill, CircleShape)
            .border(1.5.dp, Roux.accents.fill, CircleShape)
            .padding(1.5.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(Roux.accents.onFill, CircleShape)
                .semantics { contentDescription = liveDescription },
        )
        Text(
            stringResource(R.string.game_row_live_label),
            style = Roux.cardTypography.finalState,
            color = Roux.accents.onFill,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FinalCapsule(kind: FinalKind?) {
    Text(
        stringResource(
            when (kind) {
                FinalKind.OT -> R.string.game_row_final_ot
                FinalKind.SO -> R.string.game_row_final_so
                FinalKind.REG, null -> R.string.game_row_final
            },
        ),
        Modifier
            .testTag(STATE_CAPSULE_TAG)
            .border(1.5.dp, Roux.accents.stroke, CircleShape)
            .padding(1.5.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = Roux.cardTypography.finalState,
        color = Roux.accents.signature,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun RailText(text: String) {
    Text(
        text,
        style = Roux.cardTypography.shots,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun periodLabel(period: PeriodInfo?): String =
    stringResource(
        // A supplied period type takes precedence over its number (including playoff overtime).
        when (period?.periodType) {
            "OT" -> {
                R.string.game_row_period_overtime
            }

            "SO" -> {
                R.string.game_row_period_shootout
            }

            else -> {
                when (period?.ordinal) {
                    1 -> R.string.game_row_period_first
                    2 -> R.string.game_row_period_second
                    3 -> R.string.game_row_period_third
                    else -> R.string.game_row_period_unavailable
                }
            }
        },
    )

@Composable
private fun startTimeLabel(game: Game): String = rememberStartTimeLabel(game)
