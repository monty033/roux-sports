@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import net.montycasa.roux.ui.theme.LocalRouxDark

/** The tag on a league's crest, so a test can require it and a screenshot can be read. */
internal const val LEAGUE_CREST_TAG = "league.crest"

// The authorised asset host, and the two variants it publishes. The light artwork is drawn on a light
// surface and the dark one on a dark surface: the light crest is a dark disc, which is why it cannot
// simply be tinted.
private const val NHL_CREST_LIGHT = "https://assets.nhle.com/logos/nhl/svg/NHL_light.svg"
private const val NHL_CREST_DARK = "https://assets.nhle.com/logos/nhl/svg/NHL_dark.svg"

/**
 * The crest for a league, or null where there is none to draw.
 *
 * Only a league the app actually carries gets one. A dimmed league in the drawer (the NFL, the MLB)
 * has no crest here and must not borrow the NHL's, or the menu would claim four sports it cannot show.
 */
internal fun leagueCrestUrl(
    leagueId: String,
    dark: Boolean,
): String? =
    when (leagueId) {
        "nhl" -> if (dark) NHL_CREST_DARK else NHL_CREST_LIGHT
        else -> null
    }

/**
 * A league's crest at [size].
 *
 * Decorative: the league's name is always beside it in both places it is drawn, so the artwork says
 * nothing a screen reader would need to hear twice.
 */
@Composable
internal fun LeagueCrest(
    leagueId: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val url = leagueCrestUrl(leagueId, dark = LocalRouxDark.current) ?: return
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.size(size).testTag(LEAGUE_CREST_TAG),
        contentScale = ContentScale.Fit,
    )
}
