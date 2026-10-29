@file:Suppress("ktlint:standard:function-naming")
/*
 * Roux Sports — the league carousel that sits at the foot of the screen.
 *
 * Logos only, looping, and it doubles as the drawer's handle: the strip the thumb already rests on is the thing that
 * pulls the menu up. Selecting is done by bringing a logo to the middle — the row is a dial, not a list you hunt
 * through — and tapping a logo does the same thing in one step.
 */

package net.montycasa.roux.ui.drawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.montycasa.roux.ui.scores.MY_TEAMS_LEAGUE
import kotlin.math.abs

/** The strip's height. One number: the bar's height and the page's inset both follow it. */
val CAROUSEL_HEIGHT = 70.dp

/** Air between the logos and the bottom of the screen, on top of the system gesture inset. */
private val BOTTOM_BUFFER = 12.dp

private val SLOT = 52.dp
private val LOGO = 40.dp
private const val CENTRED_SCALE = 1.2f

/**
 * The loop is a very long row of repeating items rather than a real circle: neither end can be reached, so the
 * difference does not exist. Starting in the middle leaves room to flick either way for as long as anyone plausibly
 * would.
 */
private const val LOOP_SIZE = Int.MAX_VALUE

/**
 * The strip under the tabs: My Teams and the leagues, as logos, looping. It is the league switcher and the sheet's
 * handle at once, which is why it sits where the thumb already is.
 */
@Composable
fun LeagueCarousel(
    order: List<String>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(order) { carouselEntries(order) }
    val selectedPosition = remember(entries, selectedId) { entries.indexOfFirst { it.id == selectedId }.coerceAtLeast(0) }
    val initial = remember(entries) { loopStart(entries.size, selectedPosition) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    // What the dial last asked for. The parent usually answers within a frame, but the dial must not ask twice while
    // it waits - a tap that brings a logo to the middle would otherwise fire the same choice again as it settles.
    var committed by remember { mutableStateOf(selectedId) }
    LaunchedEffect(selectedId) { committed = selectedId }
    // The bar's own surface, so the dial reads as part of it rather than as a second panel.
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val centred by rememberCentredIndex(state)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(surface)
            .testTag("leagues.carousel")
            // The dial owns the bottom of the window now, so it carries the system inset itself and then a little air:
            // logos pressed against the gesture bar read as a mistake rather than as a footer.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = BOTTOM_BUFFER)
            .height(CAROUSEL_HEIGHT),
    ) {
        val edge = (maxWidth - SLOT) / 2

        // A logo brought to the middle is the choice, and settling is what commits it: a flick that carries three
        // logos past the thumb changes leagues once, not three times on the way.
        LaunchedEffect(state.isScrollInProgress, centred.index) {
            val index = centred.index
            if (state.isScrollInProgress || index < 0) return@LaunchedEffect
            val entry = entries[index % entries.size]
            if (!entry.available) {
                nextAvailable(entries, index)?.let { state.animateScrollToItem(it) }
                return@LaunchedEffect
            }
            if (entry.id != committed) {
                committed = entry.id
                onSelect(entry.id)
            }
        }

        // Arriving with a different league already chosen turns the dial to it.
        LaunchedEffect(selectedPosition) {
            val home = loopStart(entries.size, selectedPosition)
            if (abs(state.firstVisibleItemIndex - home) > entries.size) state.scrollToItem(home)
        }

        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = edge),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(LOOP_SIZE) { index ->
                val entry = entries[index % entries.size]
                LeagueLogo(
                    entry = entry,
                    centred = index == centred.index,
                    // The row repeats itself, so the same league exists several times over. Each league announces
                    // itself once - through the copy nearest the middle - or a screen reader would read it out four
                    // times and a test looking for a logo would find four of them.
                    exposed = isExposed(index, centred.index, entries.size),
                    onClick = {
                        if (entry.available) {
                            if (entry.id != committed) {
                                committed = entry.id
                                onSelect(entry.id)
                            }
                            // The tap and the dial agree: bring the tapped logo to the middle too.
                            scope.launch {
                                state.animateScrollToItem(loopStart(entries.size, index % entries.size))
                            }
                        }
                    },
                )
            }
        }

        // The edges fall away rather than ending, so the row reads as a loop and not as a list with two ends.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to surface,
                        0.16f to Color.Transparent,
                        0.84f to Color.Transparent,
                        1f to surface,
                    ),
                ),
        )

        // The handle belongs to the sheet, not the dial: tap it or pull up on it and the menu arrives.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(CAROUSEL_HANDLE, RoundedCornerShape(2.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenMenu,
                ).testTag("leagues.grabber"),
        )
    }
}

/**
 * One logo. The slot never changes size — only what is drawn inside it — so the row does not churn as items grow and
 * shrink on the way to the middle.
 */
@Composable
private fun LeagueLogo(
    entry: CarouselEntry,
    centred: Boolean,
    exposed: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (centred) CENTRED_SCALE else 1f, label = "logo scale")
    val shape = RoundedCornerShape(13.dp)
    // Ink against paper rather than Material's tonal greys: the app's page is warm, and a lavender tile would be the
    // only cool thing on the screen.
    val paper = MaterialTheme.colorScheme.surfaceVariant
    val inkColour = MaterialTheme.colorScheme.onSurface
    val fill =
        when {
            !entry.available -> inkColour.copy(alpha = 0.05f)
            centred -> inkColour
            else -> inkColour.copy(alpha = 0.07f)
        }
    val ink =
        when {
            !entry.available -> inkColour.copy(alpha = 0.38f)
            centred -> paper
            else -> inkColour.copy(alpha = 0.6f)
        }

    Box(
        Modifier
            .size(SLOT)
            .then(
                if (exposed) {
                    Modifier
                        .semantics(mergeDescendants = true) {
                            this.selected = centred
                            contentDescription = entry.description
                        }.testTag("leagues.carousel.${entry.id}")
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(LOGO)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.background(fill, shape)
                .then(
                    if (centred) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                ).clickable(
                    enabled = entry.available,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.mark,
                color = ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp,
                // The letters are a logo, not a label: the item already names itself, and "NHL" appearing twice in the
                // text tree would also collide with the header that says the same thing.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

internal data class CarouselEntry(
    val id: String,
    val mark: String,
    val description: String,
    val available: Boolean,
)

/** My Teams first, then the leagues in the order the drawer shows them. */
internal fun carouselEntries(order: List<String>): List<CarouselEntry> {
    val byId = drawerCatalog.associateBy { it.id }
    val leagues = order.mapNotNull(byId::get).ifEmpty { drawerCatalog.filter { it.available } }
    return listOf(CarouselEntry(MY_TEAMS_LEAGUE, "★", "My teams", true)) +
        leagues.map { CarouselEntry(it.id, it.label, it.label, it.available) }
}

/** A row that never ends still has to be entered somewhere: the middle, aligned to a whole number of loops. */
internal fun loopStart(
    size: Int,
    position: Int,
): Int {
    if (size <= 0) return 0
    val middle = LOOP_SIZE / 2
    return middle - (middle % size) + position.coerceIn(0, size - 1)
}

/**
 * True for the one copy of a league that speaks for it: the copy nearest the middle, with ties going to the right so
 * that exactly one of them always wins.
 */
internal fun isExposed(
    index: Int,
    centre: Int,
    count: Int,
): Boolean {
    if (count <= 0) return false
    if (centre < 0) return true
    val distance = index - centre
    val wrapped = ((distance % count) + count) % count
    val nearest =
        when {
            wrapped < count / 2 -> wrapped

            wrapped > count / 2 -> wrapped - count

            // An exact half: both copies are equally close, so name the one ahead.
            else -> if (count % 2 == 0) count / 2 else count / 2
        }
    return distance == nearest
}

internal fun nextAvailable(
    entries: List<CarouselEntry>,
    from: Int,
): Int? {
    for (step in 1..entries.size) {
        val index = from + step
        if (entries[index % entries.size].available) return index
    }
    return null
}

/** Which item currently owns the middle of the strip. */
@Composable
private fun rememberCentredIndex(state: LazyListState) =
    remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { item -> abs(item.offset + item.size / 2f - middle) }
                ?.index
                ?.let(::Centred) ?: Centred(-1)
        }
    }

private data class Centred(
    val index: Int,
)

/** The handle at rest. It turns the signal colour while the sheet is moving. */
internal val CAROUSEL_HANDLE = Color(0xFFC4C4BB)
