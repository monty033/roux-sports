package net.montycasa.roux.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import net.montycasa.roux.domain.model.AccentColor
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.drawer.LeaguesPanel
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.teams.TeamAlerts
import net.montycasa.roux.ui.teams.TeamAlertsSheet
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The drawer and the alerts sheet paint the SELECTED accent, proven by reading the pixels they draw.
 *
 * ContrastAndTypeTest checks the palette; it cannot see whether a composable reads that palette, because it builds
 * its pairs from the same table. This renders the real composables with Blue selected and asserts on the render:
 * Blue's fill is present, and the shipped orange is absent. Both expected colours are literals here, so a composable
 * that went back to a hardcoded orange fails this test even though every palette test still passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h923dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccentRenderingTest {
    @get:Rule
    val rule = createComposeRule()

    private val blueFill = Color(0xFF3D7EFF)
    private val shippedOrange = Color(0xFFFF4B12)

    private fun count(
        pixels: PixelMap,
        colour: Color,
    ): Int {
        var n = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y] == colour) n++
            }
        }
        return n
    }

    private fun assertSelectedAccentPainted(
        surface: String,
        dark: Boolean,
        pixels: PixelMap,
    ) {
        val blue = count(pixels, blueFill)
        val orange = count(pixels, shippedOrange)
        assertTrue(
            "$surface (dark=$dark): no pixel of the selected Blue accent was painted",
            blue > 0,
        )
        assertTrue(
            "$surface (dark=$dark): $orange pixel(s) of the shipped orange were painted under Blue",
            orange == 0,
        )
    }

    @Test
    fun drawerPaintsTheSelectedAccent() {
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value, accent = AccentColor.Blue) {
                LeaguesPanel(
                    drawer = DrawerUi(favouriteCount = 6),
                    editing = false,
                    onEditing = {},
                    onLeagueOrder = {},
                    onLeagueSelected = {},
                    onFavouritesOnly = {},
                )
            }
        }
        for (isDark in listOf(false, true)) {
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            assertSelectedAccentPainted("drawer", isDark, rule.onRoot().captureToImage().toPixelMap())
        }
    }

    @Test
    fun alertsSheetPaintsTheSelectedAccent() {
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value, accent = AccentColor.Blue) {
                TeamAlertsSheet(
                    alerts =
                        TeamAlerts(
                            team = TeamRef(TeamKey("ANA"), name = "Anaheim Ducks"),
                            start = true,
                            upcoming = true,
                            leadMinutes = 30,
                        ),
                    canNotify = true,
                    onDismiss = {},
                    onSetAlert = { _, _ -> },
                    onSetReminder = {},
                    onOpenSystemSettings = {},
                )
            }
        }
        for (isDark in listOf(false, true)) {
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            assertSelectedAccentPainted("alerts sheet", isDark, rule.onRoot().captureToImage().toPixelMap())
        }
    }
}
