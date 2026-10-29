package net.montycasa.roux.ui.drawer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far the sheet rises on a short pull: every league plus the top of the management block. */
val SHEET_PEEK = 240.dp

/**
 * The tallest the sheet may be, as a fraction of the space above the bar.
 *
 * It deliberately has no full-screen anchor: the bar is the only way in and out of the menu, and a sheet that covers
 * the page asks the reader to aim at a scrim to get back. The cap also keeps the whole sheet inside the reach of a
 * thumb held at the bottom of the device.
 */
const val SHEET_MAX_FRACTION = 0.56f

/** Past this speed a flick carries to the next anchor rather than settling to the nearest one. */
private const val FLING_PX_PER_SECOND = 700f

/**
 * How far the leagues sheet is out of the bottom of the screen, in pixels.
 *
 * The sheet used to be a boolean: the panel animated itself and the drawer only had to say open or shut. A sheet that
 * follows a finger needs the position itself, so the position is the state and the anchors are the only places it may
 * come to rest: parked, the peek, and the cap.
 */
class LeaguesSheetState(
    /** The short-pull anchor, in pixels, supplied once the density is known. */
    peekPx: Float = 0f,
) {
    var reveal by mutableFloatStateOf(0f)
        private set

    /** The cap in pixels, measured from the window above the bar. Zero until the host has been laid out. */
    var capPx by mutableFloatStateOf(0f)
        private set

    var peekPx by mutableFloatStateOf(peekPx)
        private set

    val isOpen: Boolean get() = reveal > 0.5f

    /** 0 when parked, 1 at the cap: what the scrim and the attached surfaces fade against. */
    val progress: Float get() = if (capPx <= 0f) 0f else (reveal / capPx).coerceIn(0f, 1f)

    /** Records the window's real size. The sheet is clamped rather than allowed to outgrow a shorter window. */
    fun measure(
        cap: Float,
        peek: Float,
    ) {
        capPx = cap.coerceAtLeast(0f)
        peekPx = peek.coerceIn(0f, capPx)
        if (reveal > capPx) reveal = capPx
    }

    /** Finger travel, unresisted: the sheet is under the finger, not chasing it through a curve. */
    fun drag(by: Float) {
        reveal = (reveal + by).coerceIn(0f, capPx)
    }

    fun set(value: Float) {
        reveal = value.coerceIn(0f, capPx)
    }

    /** Parked, the peek, and the cap. Duplicates collapse on a window too short to separate them. */
    fun anchors(): List<Float> = listOf(0f, peekPx, capPx).distinct().sorted()

    /**
     * Where a release should land. A flick goes the way it was thrown; anything slower settles to the nearest anchor,
     * so a half-hearted pull does not jump the sheet past what the finger asked for.
     */
    fun target(velocity: Float): Float {
        val anchors = anchors()
        val current = reveal
        if (abs(velocity) > FLING_PX_PER_SECOND) {
            val ahead = if (velocity > 0f) anchors.filter { it > current + 1f } else anchors.filter { it < current - 1f }
            if (ahead.isNotEmpty()) return if (velocity > 0f) ahead.min() else ahead.max()
        }
        return anchors.minBy { abs(it - current) }
    }
}

/**
 * Settles the sheet after a gesture.
 *
 * Returned rather than held inside the host because the bar - which lives in the shell, not in the sheet - is also a
 * place a pull can start, and both entry points have to land on the same anchors.
 */
@Composable
fun rememberLeaguesSheetSettler(state: LeaguesSheetState): (Float) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        { velocity ->
            scope.launch {
                val from = state.reveal
                val to = state.target(velocity)
                if (abs(to - from) < 0.5f) {
                    state.set(to)
                } else {
                    animate(from, to, animationSpec = tween(durationMillis = 220)) { value, _ -> state.set(value) }
                }
            }
        }
    }
}

/** Opens to the peek: the short pull a tap stands in for. */
fun LeaguesSheetState.openToPeek() {
    set(if (peekPx > 0f) peekPx else capPx)
}

fun LeaguesSheetState.close() {
    set(0f)
}

/** Kept for call sites that think in dp. */
internal fun Dp.toPxOrZero(): Float = if (value.isFinite()) value else 0f
