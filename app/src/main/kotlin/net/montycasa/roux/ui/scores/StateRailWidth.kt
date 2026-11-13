package net.montycasa.roux.ui.scores

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.ui.common.CAPSULE_BORDER
import net.montycasa.roux.ui.common.CAPSULE_HORIZONTAL_PADDING
import net.montycasa.roux.ui.common.CAPSULE_PADDING
import net.montycasa.roux.ui.common.LIVE_CLOCK_GAP
import net.montycasa.roux.ui.common.LIVE_DOT_GAP
import net.montycasa.roux.ui.common.LIVE_DOT_SIZE
import net.montycasa.roux.ui.theme.GameCardTypography
import net.montycasa.roux.ui.theme.Roux

/**
 * The width of the state rail, and only one of them.
 *
 * The rail used to measure its own content, so its width followed the state of the card it was on:
 * "FINAL" was narrower than "FINAL - OT", and a live overtime row - a capsule and a clock side by
 * side - was wider again. The right-hand edge of a list of cards therefore stepped in and out as the
 * games scrolled past, which is what was reported from the phone.
 *
 * So the width is decided from the widest state a card can draw, and every card draws its rail at
 * that width. It is measured from the text, under the styles and the density and the font scale the
 * card is drawn at, because a number written down in pixels would be wrong on the next phone and
 * wrong again at the next font scale. The card's own metric is the floor under it: the narrow card
 * keeps its 128 dp whether the widest state needs that much or not.
 *
 * Every card measures the same strings and gets the same number, so the cards in a list line up
 * without any of them having to know what the others are drawing.
 */
@Composable
internal fun rememberStateRailWidth(floor: Dp): Dp {
    val density = LocalDensity.current
    val labels = railLabels()
    val typography = Roux.cardTypography
    val measurer = rememberTextMeasurer()
    return remember(density, floor, labels, typography, measurer) {
        maxOf(floor, measureWidestState(measurer, density, typography, labels) + RAIL_INSETS)
    }
}

/**
 * The labels the rail can draw, read once so the width can be measured from the strings the app
 * actually ships rather than from copies of them written down in a test.
 */
private data class RailLabels(
    val final: String,
    val finalOvertime: String,
    val finalShootout: String,
    val live: String,
    val overtime: String,
    val shootout: String,
    val firstPeriod: String,
    val secondPeriod: String,
    val thirdPeriod: String,
    val unknown: String,
    val timeTbd: String,
)

@Composable
private fun railLabels(): RailLabels =
    RailLabels(
        final = stringResource(R.string.game_row_final),
        finalOvertime = stringResource(R.string.game_row_final_ot),
        finalShootout = stringResource(R.string.game_row_final_so),
        live = stringResource(R.string.game_row_live_label),
        overtime = stringResource(R.string.game_row_period_overtime),
        shootout = stringResource(R.string.game_row_period_shootout),
        firstPeriod = stringResource(R.string.game_row_period_first),
        secondPeriod = stringResource(R.string.game_row_period_second),
        thirdPeriod = stringResource(R.string.game_row_period_third),
        unknown = stringResource(R.string.game_row_status_unavailable),
        timeTbd = stringResource(R.string.game_row_time_tbd),
    )

/**
 * The width of the widest state a card can draw, in the content area inside the rail's padding.
 *
 * Every state the rail knows how to draw is measured here, under the style it is drawn in, and the
 * widest of them wins. The rail's own insets are added once, by the caller.
 */
private fun measureWidestState(
    measurer: TextMeasurer,
    density: Density,
    type: GameCardTypography,
    labels: RailLabels,
): Dp {
    var widest = 0f

    fun textWidth(
        text: String,
        style: TextStyle,
    ): Float =
        measurer
            .measure(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                density = density,
            ).size.width
            .toFloat()

    fun px(value: Dp): Float = with(density) { value.toPx() }

    // The chrome around a capsule's label: the padding inside its border, and the border itself.
    // The border reserves no room in the layout but is painted across the edge of it, so it is
    // counted here to keep the painted capsule inside the rail.
    val capsuleChrome = px(CAPSULE_PADDING * 2 + CAPSULE_HORIZONTAL_PADDING * 2 + CAPSULE_BORDER * 2)

    // The live capsule adds a dot and a gap before its label.
    val liveChrome = capsuleChrome + px(LIVE_DOT_SIZE + LIVE_DOT_GAP)

    // The final capsule: the label, its border and its padding.
    listOf(labels.final, labels.finalOvertime, labels.finalShootout).forEach {
        widest = maxOf(widest, textWidth(it, type.finalState) + capsuleChrome)
    }

    // The live capsule on its own, which is what a regulation card draws above its clock.
    listOf(labels.live, labels.overtime, labels.shootout).forEach {
        widest = maxOf(widest, textWidth(it, type.finalState) + liveChrome)
    }

    // The extra period: the capsule and the clock share one line, with a gap between them.
    val clockWidth = textWidth(WIDEST_CLOCK, type.clock)
    listOf(labels.overtime, labels.shootout).forEach {
        widest = maxOf(widest, textWidth(it, type.finalState) + liveChrome + px(LIVE_CLOCK_GAP) + clockWidth)
    }

    // The regulation stack: the clock line, which is wider than the capsule above it.
    listOf(labels.firstPeriod, labels.secondPeriod, labels.thirdPeriod).forEach {
        widest = maxOf(widest, textWidth("$it $WIDEST_CLOCK", type.clock))
    }

    // The scheduled start time, and the line a game with no time at all draws instead.
    widest = maxOf(widest, textWidth(WIDEST_START_TIME, type.startTime))
    widest = maxOf(widest, textWidth(labels.timeTbd, type.startTime))

    // The unknown state: the fallback the rail draws for a state it does not recognise.
    widest = maxOf(widest, textWidth(labels.unknown, type.shots))

    return with(density) { widest.toDp() }
}

/** The clock a live game can show: the widest time a feed reports. */
private const val WIDEST_CLOCK = "88:88"

/** The start time a scheduled card can show, in the app's twelve-hour form. */
private const val WIDEST_START_TIME = "12:00 PM"
