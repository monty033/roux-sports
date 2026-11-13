// Compose names composables in PascalCase; ktlint's function-naming rule reads that as a misnamed
// function. Suppressed for this file only, rather than changing the rule for the whole repository.
@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import net.montycasa.roux.domain.model.AccentColor

/**
 * Fixed light and dark schemes. No dynamic colour, ever: this app has a brand, and a wallpaper-derived
 * palette would break the one thing the design relies on - the accent meaning "live, final, or winning".
 *
 * The accent is the user's choice from Settings; it changes the primary and the accent roles, and nothing else.
 */
fun rouxColorScheme(
    dark: Boolean,
    accent: AccentColor = AccentColor.DEFAULT,
): ColorScheme = rouxScheme(dark, accent)

/** The orange roles Material has no slot for, resolvable from any composable. */
val LocalRouxAccents = staticCompositionLocalOf { RouxLightAccents }

val LocalRouxCardTypography = staticCompositionLocalOf { RouxCardTypography }

/** Read the accents without reaching for `MaterialTheme.colorScheme.primary` and getting lucky. */
object Roux {
    val cardTypography: GameCardTypography
        @Composable @ReadOnlyComposable
        get() = LocalRouxCardTypography.current

    val accents: RouxAccents
        @Composable @ReadOnlyComposable
        get() = LocalRouxAccents.current
}

/**
 * Material's own scale, with the one size we do not accept raised.
 *
 * M3 draws `labelSmall` at 11 sp, which is under the app's 12 sp floor, and it is the style the detail screen's
 * section headers use. Raising it here fixes every call site at once instead of patching each one.
 */
private val RouxTypography =
    Typography().let { base ->
        base.copy(labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
    }

/**
 * Applies the scheme for [dark].
 *
 * The accents travel alongside the [ColorScheme] rather than inside it, so a screen can ask for
 * `Roux.accents.fill` without guessing which orange `primary` happens to be.
 */
@Composable
fun RouxTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.DEFAULT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRouxAccents provides rouxAccents(dark, accent),
        LocalRouxCardTypography provides RouxCardTypography,
    ) {
        MaterialTheme(colorScheme = rouxColorScheme(dark, accent), typography = RouxTypography, content = content)
    }
}

/** Contrast ratio between two colours, as WCAG defines it. */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}
