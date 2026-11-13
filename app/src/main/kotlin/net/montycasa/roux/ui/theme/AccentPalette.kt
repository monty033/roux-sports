package net.montycasa.roux.ui.theme

import androidx.compose.ui.graphics.Color
import net.montycasa.roux.domain.model.AccentColor

/**
 * One accent's measured colours.
 *
 * [fill] is the live capsule, the dark theme's type and rule, and the drawer's and sheet's accent text. [lightSignature]
 * and [lightStroke] are the light theme's type and rule: darker than [fill], because a bright orange fails 4.5:1 as
 * small type on the light page. Every row was checked against the contrast floors in ContrastAndTypeTest; change a
 * value and that test is what says whether it still holds.
 */
internal data class AccentSwatch(
    val fill: Color,
    val lightSignature: Color,
    val lightStroke: Color,
)

/** The palette table. Orange is the shipped values and is read from the constants in Color.kt, not repeated here. */
internal fun AccentColor.swatch(): AccentSwatch =
    when (this) {
        AccentColor.Orange -> AccentSwatch(RouxLightFill, RouxLightSignature, RouxLightStroke)
        AccentColor.Blue -> AccentSwatch(Color(0xFF3D7EFF), Color(0xFF0F5FFF), Color(0xFF4785FF))
        AccentColor.Teal -> AccentSwatch(Color(0xFF02A791), Color(0xFF2F796F), Color(0xFF009985))
        AccentColor.Green -> AccentSwatch(Color(0xFF00AD00), Color(0xFF317D31), Color(0xFF009E00))
        AccentColor.Purple -> AccentSwatch(Color(0xFFB057FF), Color(0xFF9924FF), Color(0xFFB561FF))
        AccentColor.Red -> AccentSwatch(Color(0xFFFF2B24), Color(0xFFCF302A), Color(0xFFFF3F38))
        AccentColor.Gold -> AccentSwatch(Color(0xFFC28800), Color(0xFF826B35), Color(0xFFB27D00))
        AccentColor.Pink -> AccentSwatch(Color(0xFFFF0080), Color(0xFFBB3E7D), Color(0xFFFF2E96))
        AccentColor.Cyan -> AccentSwatch(Color(0xFF00A1C2), Color(0xFF337784), Color(0xFF0095B2))
    }
