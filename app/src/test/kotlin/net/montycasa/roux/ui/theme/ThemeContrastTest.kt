package net.montycasa.roux.ui.theme

import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette's numeric rules, asserted rather than eyeballed.
 *
 * These are the numbers the design was chosen on. A colour picked "by feel" later would break them
 * silently - which is exactly what happened once already: `#FF4B12` measures 2.99:1 on the light page,
 * a hundredth under the 3:1 floor, so light-theme orange type has to be `#C93A06` instead.
 */
class ThemeContrastTest {
    private fun assertAtLeast(
        minimum: Double,
        a: androidx.compose.ui.graphics.Color,
        b: androidx.compose.ui.graphics.Color,
        what: String,
    ) {
        val actual = contrastRatio(a, b).toDouble()
        assertTrue("$what measured ${"%.2f".format(actual)}:1, needs $minimum", actual >= minimum)
    }

    /** Body text on the page. Both themes clear this comfortably, which is why they can be read. */
    @Test
    fun bodyTextMeetsTheContrastFloor() {
        for (scheme in listOf(RouxLightColors, RouxDarkColors)) {
            assertAtLeast(4.5, scheme.onSurface, scheme.surface, "onSurface on surface")
            // Muted text is painted on the card as well as the page, and in the dark theme the card is
            // the lighter of the two - so it is the harder case and must be asserted in its own right.
            assertAtLeast(4.5, scheme.onSurfaceVariant, scheme.surface, "onSurfaceVariant on surface")
            assertAtLeast(4.5, scheme.onSurfaceVariant, scheme.surfaceVariant, "onSurfaceVariant on the card")
        }
    }

    /**
     * The accent used as a UI shape.
     *
     * The live capsule's fill is measured against the card it is painted on - that is the surface it
     * actually sits on - and it clears 3.35:1 there and 5.94:1 in the dark theme.
     */
    @Test
    fun theLiveFillClearsThreeToOneWhereItIsPainted() {
        for ((scheme, accents) in listOf(RouxLightColors to RouxLightAccents, RouxDarkColors to RouxDarkAccents)) {
            // A fill is a shape, so 3:1 applies - but against the card it is painted on.
            assertAtLeast(3.0, accents.fill, scheme.surfaceVariant, "live fill on the card")
        }
    }

    /** The signature orange is TYPE, not a shape, so it owes the 4.5:1 text floor - on page and card. */
    @Test
    fun theSignatureOrangeMeetsTheTextFloor() {
        for ((scheme, accents) in listOf(RouxLightColors to RouxLightAccents, RouxDarkColors to RouxDarkAccents)) {
            assertAtLeast(4.5, accents.signature, scheme.surface, "signature orange on the page")
            assertAtLeast(4.5, accents.signature, scheme.surfaceVariant, "signature orange on the card")
        }
    }

    /** Text inside the capsule is the near-black on orange; that pair is what a reader looks at. */
    @Test
    fun theLiveFillCarriesItsOwnForeground() {
        for (accents in listOf(RouxLightAccents, RouxDarkAccents)) {
            // Text inside the capsule: 4.5:1, not 3:1. White on this orange is 3.35:1 and would slip
            // through a 3:1 check while being unreadable as a small LIVE label.
            assertAtLeast(4.5, accents.onFill, accents.fill, "onFill on the live fill")
        }
    }

    /**
     * The specific trap the design warns about: `#FF4B12` as small type on the light page.
     *
     * Asserted as a documented deficit rather than a pass, because the design's remedy is "never do
     * this" and the theme enforces it by keeping the fill and the signature orange as separate tokens.
     */
    @Test
    fun theBrightFillIsNotUsableAsLightThemeType() {
        val ratio = contrastRatio(RouxLightFill, RouxLightPage)
        assertTrue("expected #FF4B12 to fall short of 3:1 on the page, measured $ratio", ratio < 3.0f)
        assertTrue(
            "the signature orange must be the one that clears the floor",
            contrastRatio(RouxLightSignature, RouxLightPage) >= 4.5f,
        )
    }

    /**
     * A Material primary button: `onPrimary` on `primary`.
     *
     * Asserted separately from the accents because it is a different pairing. The light primary is the
     * darker `#C93A06`, where white measures 5.13:1 and the capsule's near-black only 3.81:1 - so the
     * two schemes take different inks and one shared constant would fail one of them.
     */
    @Test
    fun thePrimaryPairMeetsTheTextFloor() {
        for (scheme in listOf(RouxLightColors, RouxDarkColors)) {
            assertAtLeast(4.5, scheme.onPrimary, scheme.primary, "onPrimary on primary")
        }
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
        for (scheme: ColorScheme in listOf(RouxLightColors, RouxDarkColors)) {
            assertEquals(scheme.background, scheme.surface)
        }
    }
}
