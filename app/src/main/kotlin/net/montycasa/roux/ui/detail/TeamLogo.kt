@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.LocalRouxCardTypography

/**
 * The club's crest, with its abbreviation showing only until the artwork arrives.
 *
 * The badge is fixed-size artwork, so the fallback text is pinned to font scale 1: scaling it would crop the letters
 * inside a circle that does not grow with them.
 */
@Composable
internal fun TeamLogo(
    team: TeamRef,
    crestSize: Dp,
    imageTag: String = "detail.logo.${team.abbrev}",
) {
    var loaded by remember(team.logoUrl) { mutableStateOf(false) }
    Box(
        Modifier.requiredSize(crestSize).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .alpha(if (loaded) 0f else 1f)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Text(
                    text = team.abbrev,
                    modifier = if (loaded) Modifier.clearAndSetSemantics { } else Modifier,
                    style = LocalRouxCardTypography.current.shots,
                    maxLines = 1,
                )
            }
        }
        if (team.logoUrl != null) {
            AsyncImage(
                model = team.logoUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag(imageTag),
                contentScale = ContentScale.Fit,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}
