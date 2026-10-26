// Compose names composables in PascalCase; ktlint's function-naming rule reads that as a misnamed
// function. Suppressed for this file only, rather than changing the rule for the whole repository.
@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Fixed light and dark schemes. No dynamic colour, ever: this app has a brand, and a wallpaper-derived
 * palette would break the one thing the design relies on - orange meaning "live, final, or winning".
 */
fun rouxColorScheme(dark: Boolean): ColorScheme = if (dark) RouxDarkColors else RouxLightColors

/** The orange roles Material has no slot for, resolvable from any composable. */
val LocalRouxAccents = staticCompositionLocalOf { RouxLightAccents }

/** Read the accents without reaching for `MaterialTheme.colorScheme.primary` and getting lucky. */
object Roux {
    val accents: RouxAccents
        @Composable @ReadOnlyComposable
        get() = LocalRouxAccents.current
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
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRouxAccents provides if (dark) RouxDarkAccents else RouxLightAccents) {
        MaterialTheme(colorScheme = rouxColorScheme(dark), content = content)
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
