@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The star that means "your teams".
 *
 * Shared rather than drawn twice: the drawer marks its My Teams row with it and the top bar's chip carries it,
 * and those two must be the same shape or the same idea looks like two.
 */
internal val FavouriteStar: ImageVector =
    ImageVector
        .Builder("Favourite", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M12,3.6 L14.6,8.9 L20.5,9.8 L16.2,13.9 L17.2,19.7 L12,16.9 L6.8,19.7 L7.8,13.9 L3.5,9.8 L9.4,8.9 Z"),
            fill = SolidColor(Color.Black),
        ).build()
