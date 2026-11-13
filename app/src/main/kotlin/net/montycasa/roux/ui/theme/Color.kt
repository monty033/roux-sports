package net.montycasa.roux.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import net.montycasa.roux.domain.model.AccentColor

/**
 * The palette, taken from the signed-off design rather than invented here.
 *
 * Orange carries three different jobs in that design and they are NOT interchangeable, which is why
 * there are three constants instead of one accent:
 *
 * - [RouxLightFill] paints the live capsule and is only ever seen behind [RouxOnFill], the near-black
 *   the design specifies as its on-signature ink.
 * - [RouxLightSignature] is orange as type and as the winner's name; on the light page it must be the
 *   darker `#C93A06` because `#FF4B12` there measures 2.99:1 - under the 3:1 floor for anything that
 *   is not a large shape. This was measured, not assumed: the design's own note says `#FF4B12` on
 *   white is 3.1:1, and on the `#F2F2EF` page it is a hundredth short of 3.0.
 * - [RouxLightStroke] is the orange rule, one step lighter than the type colour.
 *
 * Collapsing these into a single "accent" is the mistake that produces orange small type on white.
 *
 * These constants are the shipped Orange. The other eight accents are measured in AccentPalette.kt and
 * derive their roles the same way; the schemes and accents below are built from whichever is selected.
 */
internal val RouxLightPage = Color(0xFFF2F2EF)
internal val RouxLightCard = Color(0xFFFFFFFF)
internal val RouxLightInk = Color(0xFF0C0D0F)
internal val RouxLightInkMuted = Color(0xFF54585F)

// --ink3 (muted rail labels) in final/scores-screen.html. Darkened from #6B7078, which measured 4.44:1 on the
// #F2F2EF page (standings section titles sit there); #686D75 measures 4.64:1 on the page and 5.21:1 on the card.
internal val RouxLightMuted = Color(0xFF686D75)
internal val RouxLightRule = Color(0xFFE3E3DD)
internal val RouxLightSignature = Color(0xFFC93A06)
internal val RouxLightFill = Color(0xFFFF4B12)
internal val RouxLightStroke = Color(0xFFE23E0C)

internal val RouxDarkPage = Color(0xFF08090B)
internal val RouxDarkCard = Color(0xFF101115)
internal val RouxDarkInk = Color(0xFFEDEEF1)
internal val RouxDarkInkMuted = Color(0xFFA2A8B1)

// --ink3 (muted rail labels) in final/scores-screen.html.
internal val RouxDarkMuted = Color(0xFF878D96)
internal val RouxDarkRule = Color(0xFF202228)

/**
 * Text that sits on the live fill.
 *
 * This is the design's own `--onsig` value, not white: white on `#FF4B12` measures only 3.35:1, which
 * clears the 3:1 bar for a large shape but fails the 4.5:1 a small LIVE label needs. `#0B0C0E` on the
 * same orange measures 5.84:1 and passes as text.
 */
internal val RouxOnFill = Color(0xFF0B0C0E)

/**
 * The tokens Material's [ColorScheme] has no slot for.
 *
 * The live capsule needs a fill and its own foreground; a "signature" orange used as type is a
 * different colour in the light theme. Keeping them here stops later screens reaching for `primary`
 * and getting the wrong orange.
 */
@Immutable
data class RouxAccents(
    /** The live capsule fill. Always behind [onFill], never used as type. */
    val fill: Color,
    /** Foreground on [fill]. */
    val onFill: Color,
    /** Orange as type: the winner's name and score, while the final capsule uses ink-2. */
    val signature: Color,
    /** Orange as a hairline rule. */
    val stroke: Color,
    /** Muted micro-labels: --ink3 in final/scores-screen.html. */
    val muted: Color,
)

/**
 * Text on the two oranges, which need different inks - this is not one value for both.
 *
 * White on the light `#C93A06` is 5.13:1 while the near-black is 3.81:1; on the bright `#FF4B12` it is
 * the other way round (white 3.35:1, near-black 5.84:1). A single on-primary ink would leave one of the
 * two themes with an unreadable button label, so each scheme states its own.
 */
internal val RouxLightOnPrimary = Color(0xFFFFFFFF)
internal val RouxDarkOnPrimary = Color(0xFF0B0C0E)

private fun lightAccents(accent: AccentColor): RouxAccents {
    val swatch = accent.swatch()
    return RouxAccents(
        fill = swatch.fill,
        onFill = RouxOnFill,
        signature = swatch.lightSignature,
        stroke = swatch.lightStroke,
        muted = RouxLightMuted,
    )
}

/** The dark theme has one accent colour for type, rule and fill: the swatch's fill. */
private fun darkAccents(accent: AccentColor): RouxAccents {
    val fill = accent.swatch().fill
    return RouxAccents(
        fill = fill,
        onFill = RouxOnFill,
        signature = fill,
        stroke = fill,
        muted = RouxDarkMuted,
    )
}

/**
 * Light scheme. [ColorScheme.surfaceVariant] holds the card colour, because a card is the raised
 * surface a list row sits on; the page colour stays in [ColorScheme.surface] and
 * [ColorScheme.background].
 */
private fun lightScheme(accent: AccentColor): ColorScheme =
    lightColorScheme(
        primary = accent.swatch().lightSignature,
        onPrimary = RouxLightOnPrimary,
        background = RouxLightPage,
        onBackground = RouxLightInk,
        surface = RouxLightPage,
        onSurface = RouxLightInk,
        surfaceVariant = RouxLightCard,
        onSurfaceVariant = RouxLightInkMuted,
        outline = RouxLightRule,
        outlineVariant = RouxLightRule,
    )

/** Dark scheme, same structure. `#08090B` is the brand pack's own black. */
private fun darkScheme(accent: AccentColor): ColorScheme =
    darkColorScheme(
        primary = accent.swatch().fill,
        onPrimary = RouxDarkOnPrimary,
        background = RouxDarkPage,
        onBackground = RouxDarkInk,
        surface = RouxDarkPage,
        onSurface = RouxDarkInk,
        surfaceVariant = RouxDarkCard,
        onSurfaceVariant = RouxDarkInkMuted,
        outline = RouxDarkRule,
        outlineVariant = RouxDarkRule,
    )

// Built once per accent and read by identity, so a theme hands out the same instances every time it is applied.
// These sit after the constants above on purpose: top-level properties initialise in source order.
private val lightAccentSets: Map<AccentColor, RouxAccents> = AccentColor.entries.associateWith(::lightAccents)
private val darkAccentSets: Map<AccentColor, RouxAccents> = AccentColor.entries.associateWith(::darkAccents)
private val lightSchemes: Map<AccentColor, ColorScheme> = AccentColor.entries.associateWith(::lightScheme)
private val darkSchemes: Map<AccentColor, ColorScheme> = AccentColor.entries.associateWith(::darkScheme)

/** The accent roles for [dark] and [accent]. */
internal fun rouxAccents(
    dark: Boolean,
    accent: AccentColor,
): RouxAccents = (if (dark) darkAccentSets else lightAccentSets).getValue(accent)

/** The scheme for [dark] and [accent]. */
internal fun rouxScheme(
    dark: Boolean,
    accent: AccentColor,
): ColorScheme = (if (dark) darkSchemes else lightSchemes).getValue(accent)

/**
 * The shipped Orange, for code that has no selected accent to read. A screen that does should read
 * `Roux.accents`, which follows the selection.
 */
val RouxLightAccents: RouxAccents = rouxAccents(dark = false, accent = AccentColor.DEFAULT)
val RouxDarkAccents: RouxAccents = rouxAccents(dark = true, accent = AccentColor.DEFAULT)

/** The shipped Orange scheme; [rouxColorScheme] returns the selected accent's scheme instead. */
val RouxLightColors: ColorScheme = rouxScheme(dark = false, accent = AccentColor.DEFAULT)
val RouxDarkColors: ColorScheme = rouxScheme(dark = true, accent = AccentColor.DEFAULT)
