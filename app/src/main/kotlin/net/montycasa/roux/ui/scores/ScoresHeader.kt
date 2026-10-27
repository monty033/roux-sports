@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.Roux

@Composable
fun ScoresHeader(
    liveCount: Int,
    onOpenLeagues: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.background)
            .heightIn(min = 64.dp)
            .testTag("scores.header")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOpenLeagues,
            modifier = Modifier.size(48.dp).reachTarget("scores.openLeagues"),
        ) {
            Icon(menuIcon, stringResource(R.string.open_leagues), Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onBackground)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Image(
                painterResource(R.drawable.wordmark_orange),
                contentDescription = stringResource(R.string.app_name),
                // The authority's .brandmark is 1.25rem (20 dp), including transparent pixels.
                modifier = Modifier.height(20.dp).testTag("scores.wordmark"),
                contentScale = ContentScale.Fit,
            )
        }
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
