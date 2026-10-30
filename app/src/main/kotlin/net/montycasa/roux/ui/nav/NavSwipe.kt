package net.montycasa.roux.ui.nav

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where a horizontal swipe goes.
 *
 * One switch, deliberately: both behaviours are implemented, and changing this single value is the whole revert.
 */
object NavSwipe {
    /**
     * true - a swipe turns the page, moving between Scores, Standings and Teams.
     *
     * false - a swipe stays inside the screen it started on: dates on Scores, grouping on Standings. The tabs are
     * reached only through the bar.
     */
    const val TURNS_PAGES = false
}

/**
 * Steps one item on a horizontal swipe: left for forward, right for back, the directions a page turn uses.
 *
 * Only attach this when [NavSwipe.TURNS_PAGES] is false. While the pager owns horizontal drags it would claim the
 * gesture first anyway, so attaching it alongside would be dead code that reads like intent.
 */
fun Modifier.stepOnHorizontalSwipe(
    onBack: () -> Unit,
    onForward: () -> Unit,
    threshold: Dp = 64.dp,
): Modifier =
    pointerInput(onBack, onForward, threshold) {
        val limit = threshold.toPx()
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                // Negative travel is leftward, which is forward - the same direction a page turn goes.
                when {
                    travelled <= -limit -> onForward()
                    travelled >= limit -> onBack()
                }
            },
        ) { change, dragAmount ->
            travelled += dragAmount
            change.consume()
        }
    }
