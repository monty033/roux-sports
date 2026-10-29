@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The brand panel: independent of MaterialTheme, exactly as the drawer it replaces was. */
internal val SHEET_PANEL = Color(0xFF08090B)

/** How much of the page the sheet dims at full height. */
private const val SCRIM_ALPHA = 0.55f

/** Height of the strip at the sheet's top edge that moves it, rather than scrolling its contents. */
private val SHEET_HANDLE_HEIGHT = 24.dp

/**
 * The leagues sheet, anchored above the bottom bar.
 *
 * Three things here are deliberate, and each was a failure in an earlier attempt at this menu:
 *
 * - The bar is not part of the sheet. It is laid out by the shell, and this surface is offset up by exactly its
 *   measured height, so the bar is never covered, never dimmed, and never leaves a strip of its own background showing
 *   where the sheet should have been.
 * - The scrim is laid out where it is drawn. An offset scrim drew in one place and took taps in another, which left
 *   the page live - and scrolling - underneath an open menu.
 * - The sheet's height is the state. Nothing animates the container on its own, so the edge cannot arrive somewhere
 *   the finger did not put it.
 */
@Composable
fun BottomLeaguesSheet(
    state: LeaguesSheetState,
    barHeightPx: Int,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val settle = rememberLeaguesSheetSettler(state)
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerPx = constraints.maxHeight.toFloat()
        val barPx = barHeightPx.toFloat()
        // Measured against the room above the bar, so a shorter window shortens the sheet instead of pushing the bar
        // off the screen or asking the reader to reach over it.
        val capPx = ((containerPx - barPx) * SHEET_MAX_FRACTION).coerceAtLeast(0f)
        SideEffect { state.measure(capPx) }

        val reveal = state.reveal
        val progress = state.progress

        if (progress > 0f) {
            // From the top of the window down to the sheet's edge: the bar's strip is untouched, so it stays lit and
            // keeps working while the menu is open.
            val scrimPx = (containerPx - barPx - reveal).coerceAtLeast(0f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { scrimPx.toDp() })
                    .background(SHEET_PANEL.copy(alpha = SCRIM_ALPHA * progress))
                    .pointerInput(state) {
                        // Swallows taps and drags: the page must not scroll behind an open menu.
                        detectTapGestures { onDismissRequest() }
                    }.testTag("leagues.scrim"),
            )
        }

        if (reveal > 0.5f) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -barPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { reveal.toDp() })
                    .background(SHEET_PANEL, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .pointerInput(state, scroll) {
                        // Pulling the sheet itself down closes it - the gesture does not need the 24 dp strip at its
                        // edge to start on. Handled in the Initial pass so the list cannot swallow the drag before the
                        // sheet sees it; the list keeps first claim whenever it has somewhere to scroll.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var claimed = false
                            var lastY = down.position.y
                            // The flick is part of the gesture: without the release speed a quick downward swipe that
                            // did not travel half the sheet snapped straight back, which reads as a gesture that
                            // does not work.
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dy = change.position.y - lastY
                                lastY = change.position.y
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!claimed) {
                                    val travelled = change.position.y - down.position.y
                                    if (travelled > viewConfiguration.touchSlop && scroll.value == 0) {
                                        claimed = true
                                    } else if (travelled < -viewConfiguration.touchSlop) {
                                        break
                                    }
                                }
                                if (claimed && dy != 0f) {
                                    state.drag(-dy)
                                    change.consume()
                                }
                                if (!change.pressed) break
                            }
                            // Downward travel is positive on screen, and the settler reads downward as negative.
                            if (claimed) settle(-tracker.calculateVelocity().y)
                        }
                    }.testTag("leagues.sheet"),
            ) {
                SheetHandle(
                    height = SHEET_HANDLE_HEIGHT,
                    onDrag = { state.drag(it) },
                    onEnd = { velocity -> settle(velocity) },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * The sheet's top edge: pulls it down. The grabber the bar shows when the sheet is parked moves up with it, so the
 * affordance never disappears - it belongs to the surface, not to a position on the screen.
 */
@Composable
private fun SheetHandle(
    height: Dp,
    onDrag: (Float) -> Unit,
    onEnd: (Float) -> Unit,
) {
    val tracker = remember { VelocityTracker() }
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = { onEnd(tracker.calculateVelocity().y) },
                    onDragCancel = { onEnd(0f) },
                ) { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    onDrag(dragAmount)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No pill here on purpose: the strip below the sheet already carries one, and it is the same handle for both
        // directions. Two grabbers 18 dp apart would read as two different mechanisms.
    }
}
