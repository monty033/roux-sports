package net.montycasa.roux.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Card scale from final/scores-screen.html. Numerals keep their width as the feed updates. */
private val cardText =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

/** Separate roles keep rail micro-labels from shrinking Material navigation and body text. */
@Immutable
data class GameCardTypography(
    val clubName: TextStyle,
    val score: TextStyle,
    val clock: TextStyle,
    val finalState: TextStyle,
    val shots: TextStyle,
)

val RouxCardTypography =
    GameCardTypography(
        clubName = cardText.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = (-0.005).em),
        score = cardText.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp, letterSpacing = (-0.02).em),
        clock = cardText.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 16.sp),
        finalState =
            cardText.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 12.sp,
                letterSpacing = 0.09.em,
            ),
        shots = cardText.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp),
    )
