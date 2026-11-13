package net.montycasa.roux.ui.common

import androidx.compose.ui.unit.dp

/*
 * The geometry of a state capsule.
 *
 * The state rail is drawn at one width, and that width is measured from the widest state a card can
 * show - so the numbers a capsule is measured with have to be the numbers it is drawn with. They live
 * here, beside the capsules, for that reason: a measurement that forgot the border or the padding
 * would set a rail that is too narrow, and the capsule would be clipped instead of the rail being
 * wide enough for it.
 */

/** The border a capsule draws around its label. */
internal val CAPSULE_BORDER = 1.5.dp

/** The padding between the capsule's border and its label. */
internal val CAPSULE_PADDING = 1.5.dp

/** The padding between the capsule's border and its label, along the line. */
internal val CAPSULE_HORIZONTAL_PADDING = 6.dp

/** The dot the live capsule carries before its label. */
internal val LIVE_DOT_SIZE = 6.dp

/** The gap between the live capsule's dot and its label. */
internal val LIVE_DOT_GAP = 4.dp

/** The gap between the live capsule and the clock when the two share a line. */
internal val LIVE_CLOCK_GAP = 4.dp
