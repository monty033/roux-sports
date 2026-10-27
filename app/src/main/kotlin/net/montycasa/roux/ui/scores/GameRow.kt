@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.Roux
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/** Test tag for the logo image, so a test can assert the request was attempted. */
const val LOGO_TAG = "gameRowLogo"

/**
 * Away above home, with aligned scores and a wrapping status footer. Text determines the height,
 * including at double type size. This row is informational and declares no reach controls.
 *
 * Pre retains its pregame label alongside the scheduled time. Missing times read Time TBD; unknown
 * feed states read Status unavailable, without exposing a raw diagnostic code as display text.
 */
@Composable
fun GameRow(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val live = game.state is GameState.Live
    val liveAccent = Roux.accents.fill
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // R9 asks for a 4 dp leading accent bar on a live game, drawn rather than laid out so
                // it spans the row's real height without intrinsics or a second measuring pass.
                .drawBehind {
                    if (live) {
                        drawRect(color = liveAccent, size = Size(width = 4.dp.toPx(), height = size.height))
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TeamLine(game.away, game.awayScore)
        TeamLine(game.home, game.homeScore)
        GameStatus(game)
    }
}

@Composable
private fun TeamLine(
    team: TeamRef,
    score: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TeamLogo(team)
        Text(
            text = team.name ?: team.abbrev,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (score != null) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TeamLogo(team: TeamRef) {
    var loaded by remember(team.logoUrl) { mutableStateOf(false) }
    // The badge always measures the slot, even after success: image loading cannot resize the row.
    // Only the image is constrained in height; badge text can grow with the device's font scale.
    Box(
        modifier = Modifier.width(64.dp).heightIn(min = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = team.abbrev,
            modifier =
                Modifier
                    .alpha(if (loaded) 0f else 1f)
                    .then(if (loaded) Modifier.clearAndSetSemantics {} else Modifier)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .padding(4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (team.logoUrl != null) {
            AsyncImage(
                model = team.logoUrl,
                contentDescription = null, // The adjacent team name already identifies this image.
                // Tagged so a test can prove the request is attempted at all: with the badge as the
                // only visible element, nothing else distinguishes "logo requested" from "never asked".
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
private fun GameStatus(game: Game) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        when (game.state) {
            GameState.Scheduled, GameState.Pre -> {
                if (game.state == GameState.Pre) StatusText(stringResource(R.string.game_row_pregame))
                StatusText(startTimeLabel(game))
            }

            is GameState.Live -> {
                val liveDescription = stringResource(R.string.game_row_live_description)
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Roux.accents.fill, CircleShape)
                        .semantics { contentDescription = liveDescription },
                )
                StatusText(periodLabel(game.period), emphasised = true)
                game.period
                    ?.clock
                    ?.takeIf { it.isNotBlank() }
                    ?.let { StatusText(it, emphasised = true) }
                if (game.period?.inIntermission == true) {
                    StatusText(stringResource(R.string.game_row_intermission))
                }
            }

            GameState.Final -> {
                Text(
                    text =
                        stringResource(
                            when (game.finalKind) {
                                FinalKind.OT -> R.string.game_row_final_ot
                                FinalKind.SO -> R.string.game_row_final_so
                                FinalKind.REG, null -> R.string.game_row_final
                            },
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = Roux.accents.signature,
                )
            }

            is GameState.Unknown -> {
                StatusText(stringResource(R.string.game_row_status_unavailable))
            }
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    emphasised: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasised) FontWeight.Bold else null,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun startTimeLabel(game: Game): String {
    val instant = game.startTimeUtc ?: return stringResource(R.string.game_row_time_tbd)
    val locale = LocalConfiguration.current.locales[0]
    val twentyFourHour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(instant, locale, twentyFourHour) {
        // The device's own 12/24-hour setting decides the skeleton; a hockey app has no business
        // overriding a system preference. Toronto is intentional, independent of the device zone.
        val skeleton = if (twentyFourHour) "Hma" else "hma"
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .apply { timeZone = TimeZone.getTimeZone("America/Toronto") }
            .format(Date(instant.toEpochMilliseconds()))
    }
}
