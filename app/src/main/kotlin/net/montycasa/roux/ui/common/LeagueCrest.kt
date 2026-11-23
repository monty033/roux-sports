@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import net.montycasa.roux.ui.theme.LocalRouxDark

/**
 * Whether league crests are drawn at all.
 *
 * Every crest is a network URL, so a screenshot that draws one records whatever the machine could reach that minute:
 * a shield where the artwork host answered, an empty slot where it did not. A test that captures images switches
 * artwork off and captures the same empty slot every time instead of chasing a host. The app never sets this.
 */
internal val LocalLeagueCrestArtwork = staticCompositionLocalOf { true }

/** The tag on a league's crest, so a test can require it and a screenshot can be read. */
internal const val LEAGUE_CREST_TAG = "league.crest"

// The authorised asset hosts, and the two variants each publishes. The light artwork is drawn on a light
// surface and the dark one on a dark surface: the light crest is a dark disc, which is why it cannot
// simply be tinted. The NFL's comes from the ESPN artwork host that docs/nfl-sources.md authorises.
private const val NHL_CREST_LIGHT = "https://assets.nhle.com/logos/nhl/svg/NHL_light.svg"
private const val NHL_CREST_DARK = "https://assets.nhle.com/logos/nhl/svg/NHL_dark.svg"
private const val NFL_CREST_LIGHT = "https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png"
private const val NFL_CREST_DARK = "https://a.espncdn.com/i/teamlogos/leagues/500-dark/nfl.png"

/**
 * The crest for a league, or null where there is none to draw.
 *
 * Only a league the app carries gets one, and each gets its own artwork: a league without any here (the MLB,
 * say) draws nothing rather than borrowing another league's crest.
 */
internal fun leagueCrestUrl(
    leagueId: String,
    dark: Boolean,
): String? =
    when (leagueId) {
        "nhl" -> if (dark) NHL_CREST_DARK else NHL_CREST_LIGHT
        "nfl" -> if (dark) NFL_CREST_DARK else NFL_CREST_LIGHT
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
    if (!LocalLeagueCrestArtwork.current) {
        // The slot stays: its width is part of every row's layout, and dropping it would move the league's name.
        Spacer(modifier.size(size).testTag(LEAGUE_CREST_TAG))
        return
    }
    val url = leagueCrestUrl(leagueId, dark = LocalRouxDark.current) ?: return
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.size(size).testTag(LEAGUE_CREST_TAG),
        contentScale = ContentScale.Fit,
    )
}
