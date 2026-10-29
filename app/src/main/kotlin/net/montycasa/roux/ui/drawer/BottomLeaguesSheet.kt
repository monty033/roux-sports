@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The brand panel: independent of MaterialTheme, exactly as the drawer it replaces was. */
internal val SHEET_PANEL = Color(0xFF08090B)

/** How much of the page the sheet dims at full height. */
private const val SCRIM_ALPHA = 0.55f

/** Height of the strip at the sheet's top edge that moves it, rather than scrolling its contents. */
private val SHEET_HANDLE_HEIGHT = 24.dp

/**
 * The leagues sheet, anchored to the bottom of the window.
 *
 * Three things here are deliberate, and each was a failure in an earlier attempt at this menu:
 *
 * - The sheet covers the bar. It used to rise only as far as the top of the tabs, which left the destinations and the
 *   dial live under an open menu - a second way to change screens while the first one was asking a question.
 * - The scrim is laid out where it is drawn. An offset scrim drew in one place and took taps in another, which left
 *   the page live - and scrolling - underneath an open menu.
 * - The sheet's height is the state. Nothing animates the container on its own, so the edge cannot arrive somewhere
 *   the finger did not put it.
 */
@Composable
fun BottomLeaguesSheet(
    state: LeaguesSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val settle = rememberLeaguesSheetSettler(state)
    val scroll = rememberScrollState()
    val pull =
        remember(state, scroll) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // Downward only, and only once the list has nowhere left to go: scrolling the menu must never
                    // move the menu.
                    if (available.y > 0f && scroll.value == 0 && state.reveal > 0f) {
                        state.drag(-available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (state.reveal <= 0.5f || state.reveal >= state.capPx - 0.5f) return Velocity.Zero
                    // A flick carries the sheet the way it was thrown, which is what makes a short downward swipe
                    // close instead of springing back.
                    settle(-available.y)
                    return Velocity(0f, available.y)
                }
            }
        }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerPx = constraints.maxHeight.toFloat()
        // Measured against the whole window now that the sheet covers the bar: a shorter screen shortens it rather than
        // pushing its edge past the top.
        val capPx = (containerPx * SHEET_MAX_FRACTION).coerceAtLeast(0f)
        SideEffect { state.measure(capPx) }

        val reveal = state.reveal
        val progress = state.progress

        if (progress > 0f) {
            // From the top of the window down to the sheet's edge, so the dimming and the taps cover the same pixels.
            val scrimPx = (containerPx - reveal).coerceAtLeast(0f)
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
                    .fillMaxWidth()
                    .height(with(density) { reveal.toDp() })
                    .background(SHEET_PANEL, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    // Pulling the sheet itself down closes it - the gesture does not need the 24 dp strip at its edge
                    // to start on. It rides on the list's own nested scroll: the list gets the drag first and keeps it
                    // until it is at its top, and everything past that, plus the release speed, moves the sheet.
                    .nestedScroll(pull)
                    .testTag("leagues.sheet"),
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
                        // The sheet runs to the bottom edge of the window, so its rows have to step over the system
                        // gesture area themselves.
                        .windowInsetsPadding(WindowInsets.navigationBars)
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
                    // Downward travel is positive on screen and reveal grows upward, so both the movement and the
                    // release speed are inverted here. They were not, and dragging the strip down pushed the sheet up.
                    onDragEnd = { onEnd(-tracker.calculateVelocity().y) },
                    onDragCancel = { onEnd(0f) },
                ) { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    onDrag(-dragAmount)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No pill here on purpose: the strip below the sheet already carries one, and it is the same handle for both
        // directions. Two grabbers 18 dp apart would read as two different mechanisms.
    }
}
