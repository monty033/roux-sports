package net.montycasa.roux.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette's numeric rules, asserted rather than eyeballed.
 *
 * These are the numbers the design was chosen on. A colour picked "by feel" later would break them
 * silently - which happened once already: `#FF4B12` measures 2.99:1 on the light page, a hundredth
 * under the 3:1 floor, so light-theme orange type has to be `#C93A06` instead.
 *
 * The text pairs are one enumerated table rather than a test each. Three review rounds found three
 * separate unasserted pairs (`onFill`, `onPrimary`, `onBackground`), and every one of them was a pair
 * nobody had listed - so the list itself is the thing to make visible.
 */
class ThemeContrastTest {
    /** A foreground painted on a background, and the floor its role requires. */
    private data class Pair(
        val fg: Color,
        val bg: Color,
        val min: Double,
        val what: String,
    )

    private val schemes = listOf(RouxLightColors, RouxDarkColors)
    private val themed = listOf(RouxLightColors to RouxLightAccents, RouxDarkColors to RouxDarkAccents)

    /**
     * Every pair where text sits on a colour.
     *
     * `onSurfaceVariant` appears twice because muted text is painted on the card as well as the page,
     * and in the dark theme the card is the lighter of the two - so it is the harder case. The
     * signature orange appears twice for the same reason.
     */
    private fun textPairs(
        scheme: ColorScheme,
        accents: RouxAccents,
    ): List<Pair> =
        listOf(
            Pair(scheme.onSurface, scheme.surface, 4.5, "onSurface on surface"),
            Pair(scheme.onBackground, scheme.background, 4.5, "onBackground on background"),
            Pair(scheme.onSurfaceVariant, scheme.surface, 4.5, "onSurfaceVariant on the page"),
            Pair(scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5, "onSurfaceVariant on the card"),
            Pair(scheme.onPrimary, scheme.primary, 4.5, "onPrimary on primary"),
            Pair(accents.onFill, accents.fill, 4.5, "onFill on the live fill"),
            Pair(accents.signature, scheme.surface, 4.5, "signature orange on the page"),
            Pair(accents.signature, scheme.surfaceVariant, 4.5, "signature orange on the card"),
        )

    @Test
    fun everyPaintedTextPairMeetsItsFloor() {
        for ((scheme, accents) in themed) {
            for (pair in textPairs(scheme, accents)) {
                val actual = contrastRatio(pair.fg, pair.bg)
                assertTrue(
                    "${pair.what} measured ${"%.2f".format(actual)}:1, needs ${pair.min}",
                    actual >= pair.min,
                )
            }
        }
    }

    /**
     * The live fill as a shape, which owes 3:1 rather than the text floor.
     *
     * Measured against the card it is painted on - the surface it actually sits on - where it clears
     * 3.35:1 in the light theme and 5.63:1 in the dark.
     */
    @Test
    fun theLiveFillClearsThreeToOneWhereItIsPainted() {
        for ((scheme, accents) in themed) {
            assertTrue(
                "live fill on the card measured ${contrastRatio(accents.fill, scheme.surfaceVariant)}:1, needs 3.0",
                contrastRatio(accents.fill, scheme.surfaceVariant) >= 3.0f,
            )
        }
    }

    /**
     * The trap the design warns about, kept as a documented deficit rather than a pass.
     *
     * `#FF4B12` as small type on the light page is legal by no reading of WCAG, and the theme's remedy
     * is to keep it out of the type role entirely - so it is asserted to FAIL, which also catches a
     * future "tidy-up" that makes the fill and the signature the same value.
     */
    @Test
    fun theBrightFillIsNotUsableAsLightThemeType() {
        assertTrue(
            "#FF4B12 should fall short of 3:1 on the page",
            contrastRatio(RouxLightFill, RouxLightPage) < 3.0f,
        )
        assertTrue(
            "the signature orange must be the one that clears the text floor",
            contrastRatio(RouxLightSignature, RouxLightPage) >= 4.5f,
        )
    }

    /** No dynamic colour: the scheme is a constant selected by a flag. */
    @Test
    fun theSchemeIsSelectedByTheDarkFlag() {
        assertSame(RouxDarkColors, rouxColorScheme(dark = true))
        assertSame(RouxLightColors, rouxColorScheme(dark = false))
    }

    /** Background and surface are deliberately the same colour; a mismatch shows as a seam. */
    @Test
    fun pageColoursAgreeAcrossBackgroundAndSurface() {
        for (scheme in schemes) {
            assertEquals(scheme.background, scheme.surface)
        }
    }
}
