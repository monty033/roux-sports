@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.ui.common.LeagueCrest
import net.montycasa.roux.ui.common.leagueCrestUrl
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.LocalRouxDark
import net.montycasa.roux.ui.theme.Roux

@Composable
fun ScoresHeader(
    liveCount: Int,
    modifier: Modifier = Modifier,
    // The league or My Teams the user is in, beside the wordmark. Null leaves the chip out.
    leagueLabel: String? = null,
    // The league whose crest the chip carries. Null, or a league with no crest, draws the label alone.
    leagueId: String? = null,
    // Opens the leagues sheet. The caller decides whether a tap also closes it when the sheet is already open.
    onLeagueClick: () -> Unit = {},
    // Injected so a test can hold the status bar's height still: the phone's own inset is not something a JVM test can
    // see, and this header sitting under the clock is a bug that has now come back twice because nothing asserted it.
    statusInset: WindowInsets = WindowInsets.statusBars,
) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.background)
            // The app draws behind the system bars, so the header has to step down over the status bar itself.
            // Without this the wordmark sat underneath the phone's own clock and battery.
            .windowInsetsPadding(statusInset)
            .heightIn(min = 64.dp)
            .testTag("scores.header")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.wordmark_orange),
            contentDescription = stringResource(R.string.app_name),
            // The authority's .brandmark is 1.25rem (20 dp), including transparent pixels.
            modifier = Modifier.height(20.dp).testTag("scores.wordmark"),
            contentScale = ContentScale.Fit,
        )
        // Equal weights place the chip at the midpoint of the space between the brand and the count, and a row
        // lays its children out in order, so the chip can only ever be squeezed - never drawn over either
        // neighbour. The count keeps its slot even when nothing is live, so the chip does not slide when a game
        // starts.
        Spacer(Modifier.weight(1f))
        if (leagueLabel != null) LeagueChip(leagueLabel, leagueId, onLeagueClick)
        Spacer(Modifier.weight(1f))
        Box(Modifier.widthIn(min = LIVE_PILL_SLOT), contentAlignment = Alignment.CenterEnd) {
            if (liveCount > 0) {
                Row(
                    Modifier
                        .height(28.dp)
                        .widthIn(min = 72.dp)
                        .background(Roux.accents.fill, CircleShape)
                        .testTag("scores.livePill")
                        .padding(horizontal = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(Roux.accents.onFill, CircleShape))
                    Text(
                        stringResource(R.string.live_count, liveCount),
                        color = Roux.accents.onFill,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.12.em,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The room the live count gets whether or not anything is live, so the chip's place does not depend on it. */
private val LIVE_PILL_SLOT = 72.dp

/**
 * The slate the screen is showing, as a pill with the league's crest.
 *
 * The label is what is read; the crest is what is seen first. A league with no crest keeps the plain pill rather
 * than borrowing another league's mark.
 */
@Composable
private fun LeagueChip(
    leagueLabel: String,
    leagueId: String?,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.league_chip_description, leagueLabel)
    val crestId = leagueId?.takeIf { leagueCrestUrl(it, dark = LocalRouxDark.current) != null }
    Row(
        Modifier
            // The touch target is 48 dp, the height the menu button also meets; the pill inside is what is seen.
            .heightIn(min = 48.dp)
            // Capped so a long label cannot take the room the live pill needs, and floored so a short label
            // still gives a 48 dp-wide target, not just a tall one.
            .widthIn(min = 48.dp, max = 140.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .reachTarget("scores.leagueChip")
            .semantics { contentDescription = description }
            .testTag("scores.leagueChip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .padding(
                    start = if (crestId != null) 6.dp else 14.dp,
                    end = 14.dp,
                    top = 4.dp,
                    bottom = 4.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (crestId != null) LeagueCrest(crestId, CHIP_CREST_SIZE)
            Text(
                leagueLabel,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The crest inside the chip: the league's mark, sized to the pill rather than to a badge beside it. */
private val CHIP_CREST_SIZE = 30.dp

private val menuIcon =
    ImageVector
        .Builder("Leagues", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M4,7.5 H20 M4,12 H15 M4,16.5 H20"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        ).build()
