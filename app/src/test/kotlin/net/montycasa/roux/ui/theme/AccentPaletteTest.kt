package net.montycasa.roux.ui.theme

import androidx.compose.ui.graphics.Color
import net.montycasa.roux.domain.model.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The measured table from the accent brief. These values are the contract; a change here is a design change. */
class AccentPaletteTest {
    private data class Row(
        val accent: AccentColor,
        val fill: Long,
        val lightSignature: Long,
        val lightStroke: Long,
    )

    private val table =
        listOf(
            Row(AccentColor.Orange, 0xFFFF4B12, 0xFFC93A06, 0xFFE23E0C),
            Row(AccentColor.Blue, 0xFF3D7EFF, 0xFF0F5FFF, 0xFF4785FF),
            Row(AccentColor.Teal, 0xFF02A791, 0xFF2F796F, 0xFF009985),
            Row(AccentColor.Green, 0xFF00AD00, 0xFF317D31, 0xFF009E00),
            Row(AccentColor.Purple, 0xFFB057FF, 0xFF9924FF, 0xFFB561FF),
            Row(AccentColor.Red, 0xFFFF2B24, 0xFFCF302A, 0xFFFF3F38),
            Row(AccentColor.Gold, 0xFFC28800, 0xFF826B35, 0xFFB27D00),
            Row(AccentColor.Pink, 0xFFFF0080, 0xFFBB3E7D, 0xFFFF2E96),
            Row(AccentColor.Cyan, 0xFF00A1C2, 0xFF337784, 0xFF0095B2),
        )

    @Test
    fun everyAccentHasARowInTheTable() {
        assertEquals(AccentColor.entries.toList(), table.map { it.accent })
    }

    @Test
    fun swatchesMatchTheTable() {
        for (row in table) {
            assertEquals(
                row.accent.toString(),
                AccentSwatch(Color(row.fill), Color(row.lightSignature), Color(row.lightStroke)),
                row.accent.swatch(),
            )
        }
    }

    @Test
    fun lightAccentRolesFollowTheSelectedAccent() {
        for (row in table) {
            val light = rouxAccents(dark = false, accent = row.accent)
            assertEquals(row.accent.toString(), Color(row.fill), light.fill)
            assertEquals(row.accent.toString(), Color(row.lightSignature), light.signature)
            assertEquals(row.accent.toString(), Color(row.lightStroke), light.stroke)
            assertEquals(row.accent.toString(), RouxOnFill, light.onFill)
            assertEquals(row.accent.toString(), RouxLightMuted, light.muted)
        }
    }

    @Test
    fun darkAccentRolesUseTheFillForTypeAndRule() {
        for (row in table) {
            val dark = rouxAccents(dark = true, accent = row.accent)
            assertEquals(row.accent.toString(), Color(row.fill), dark.fill)
            assertEquals(row.accent.toString(), Color(row.fill), dark.signature)
            assertEquals(row.accent.toString(), Color(row.fill), dark.stroke)
            assertEquals(row.accent.toString(), RouxOnFill, dark.onFill)
            assertEquals(row.accent.toString(), RouxDarkMuted, dark.muted)
        }
    }

    @Test
    fun primaryFollowsTheSelectedAccentInEachTheme() {
        for (row in table) {
            assertEquals(
                row.accent.toString(),
                Color(row.lightSignature),
                rouxColorScheme(dark = false, accent = row.accent).primary,
            )
            assertEquals(row.accent.toString(), Color(row.fill), rouxColorScheme(dark = true, accent = row.accent).primary)
        }
    }

    @Test
    fun orangeIsTheShippedOrange() {
        assertEquals(Color(0xFFFF4B12), RouxLightFill)
        assertEquals(Color(0xFFC93A06), RouxLightSignature)
        assertEquals(Color(0xFFE23E0C), RouxLightStroke)
        assertSame(RouxLightAccents, rouxAccents(dark = false, accent = AccentColor.Orange))
        assertSame(RouxDarkAccents, rouxAccents(dark = true, accent = AccentColor.Orange))
        assertEquals(Color(0xFFFF4B12), RouxDarkAccents.fill)
    }
}
