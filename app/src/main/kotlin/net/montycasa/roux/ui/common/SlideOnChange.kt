package net.montycasa.roux.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Slides one screenful out and its neighbour in, in the direction of travel.
 *
 * The point is that you can see where you are going while the change happens - the same read the side drawer gave, where
 * the incoming panel is visible rather than appearing all at once. Outgoing and incoming move together, so the pair
 * reads as one gesture rather than two animations.
 *
 * [forward] decides the direction from the two keys: true when the new key sits later in the screen's own order, so a
 * step forward always travels leftwards, the way a page turn does.
 */
@Composable
fun <T> slideOnChange(
    key: T,
    forward: (old: T, new: T) -> Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = 220,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = {
            val onwards = forward(initialState, targetState)
            (
                slideInHorizontally(tween(durationMillis)) { width -> if (onwards) width else -width } +
                    fadeIn(tween(durationMillis))
            ) togetherWith (
                slideOutHorizontally(tween(durationMillis)) { width -> if (onwards) -width else width } +
                    fadeOut(tween(durationMillis))
            )
        },
        label = "screenward",
    ) { state ->
        content(state)
    }
}
