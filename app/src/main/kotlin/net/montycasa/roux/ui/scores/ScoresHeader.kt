@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.Roux

@Composable
fun ScoresHeader(
    liveCount: Int,
    modifier: Modifier = Modifier,
    // The league or My Teams the user is in, beside the wordmark. Null leaves the chip out.
    leagueLabel: String? = null,
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            Image(
                painterResource(R.drawable.wordmark_orange),
                contentDescription = stringResource(R.string.app_name),
                // The authority's .brandmark is 1.25rem (20 dp), including transparent pixels.
                modifier = Modifier.height(20.dp).testTag("scores.wordmark"),
                contentScale = ContentScale.Fit,
            )
        }
        if (leagueLabel != null) {
            val description = stringResource(R.string.league_chip_description, leagueLabel)
            Row(
                Modifier
                    // The touch target is 48 dp, the height the menu button also meets; the pill inside is what is seen.
                    .heightIn(min = 48.dp)
                    // Capped so a long label cannot take the room the live pill needs on a 360 dp screen,
                    // and floored so a short label still gives a 48 dp-wide target, not just a tall one.
                    .widthIn(min = 48.dp, max = 140.dp)
                    .clickable(role = Role.Button, onClick = onLeagueClick)
                    .reachTarget("scores.leagueChip")
                    .semantics { contentDescription = description }
                    .testTag("scores.leagueChip"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
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
        // The chip sits beside the wordmark, not out at the right edge: the leftover space belongs to the
        // spacer, so the live count keeps the right edge it has always had.
        Spacer(Modifier.weight(1f))
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

private val menuIcon =
    ImageVector
        .Builder("Leagues", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M4,7.5 H20 M4,12 H15 M4,16.5 H20"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        ).build()
