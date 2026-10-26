package net.montycasa.roux.ui.reach

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * The one-handed reach rules (A8, R1-R5), as numbers and as a pure check.
 *
 * The rules are fractions of window height rather than absolute offsets, so they hold on a 6.1" phone
 * and on the Pixel 9 alike. Nothing here measures anything: a caller hands in where each primary
 * control actually landed, and this decides pass or fail. That keeps the policy testable without
 * Compose and keeps the Compose-side reader thin enough to be obvious.
 */
object Reach {
    /** R1: a primary control's top edge must be at or below this fraction of window height. */
    const val PRIMARY_TOP_MAX_FRACTION = 0.50f

    /** R2: a frequent control's top edge must be at or below this fraction of window height. */
    const val FREQUENT_TOP_MAX_FRACTION = 0.70f

    /** R4: no target smaller than this in either dimension. */
    const val MIN_TARGET_DP = 48f

    /** R4: a primary control is bigger than the minimum - it is the thing a thumb must hit. */
    const val PRIMARY_MIN_HEIGHT_DP = 56f

    /** R4: edge-to-edge clearance between adjacent targets. */
    const val MIN_TARGET_GAP_DP = 8f

    /** R5: no primary control this close to the left or right edge (thumb arc, back gesture). */
    const val EDGE_INSET_DP = 16f
}

/** Which rule a violation belongs to, so a failure can be read without decoding prose. */
enum class ReachRule(
    val id: String,
) {
    R1("R1 reach zone"),
    R2("R2 frequent controls"),
    R4_SIZE("R4 target size"),
    R4_GAP("R4 target gap"),
    R5_EDGES("R5 edges"),
}

/** Where one primary control ended up, in dp, with the tag its author gave it. */
data class ControlPlacement(
    val tag: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val frequent: Boolean = false,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A single failed rule, with the measurement that failed it. */
data class ReachViolation(
    val rule: ReachRule,
    val tag: String,
    val detail: String,
) {
    fun describe(): String = "${rule.id}: '$tag' $detail"
}

/**
 * Every way [controls] breaks R1-R5 in a window of this size.
 *
 * An empty list means the layout is reachable. Violations are returned rather than thrown so a caller
 * can report all of them at once - one failing control usually means several, and fixing them one
 * test-failure at a time is how a reach audit gets abandoned halfway.
 */
fun findReachViolations(
    controls: List<ControlPlacement>,
    windowWidthDp: Float,
    windowHeightDp: Float,
): List<ReachViolation> =
    buildList {
        val primaryTopLimit = windowHeightDp * Reach.PRIMARY_TOP_MAX_FRACTION
        val frequentTopLimit = windowHeightDp * Reach.FREQUENT_TOP_MAX_FRACTION

        for (control in controls) {
            if (control.top < primaryTopLimit) {
                add(
                    ReachViolation(
                        ReachRule.R1,
                        control.tag,
                        "starts at ${fmt(control.top)} dp, above the ${fmt(primaryTopLimit)} dp limit " +
                            "(0.50 of ${fmt(windowHeightDp)} dp)",
                    ),
                )
            }
            // R2 is an additional ceiling for frequent controls, not a replacement for R1: a frequent
            // control in the top third passes nothing, and one between 0.50 and 0.70 fails only R2.
            if (control.frequent && control.top < frequentTopLimit) {
                add(
                    ReachViolation(
                        ReachRule.R2,
                        control.tag,
                        "is frequent but starts at ${fmt(control.top)} dp, above the " +
                            "${fmt(frequentTopLimit)} dp limit (0.70 of ${fmt(windowHeightDp)} dp)",
                    ),
                )
            }
            if (control.width < Reach.MIN_TARGET_DP || control.height < Reach.MIN_TARGET_DP) {
                add(
                    ReachViolation(
                        ReachRule.R4_SIZE,
                        control.tag,
                        "is ${fmt(control.width)} x ${fmt(control.height)} dp, under the " +
                            "${fmt(Reach.MIN_TARGET_DP)} dp minimum",
                    ),
                )
            }
            if (control.height < Reach.PRIMARY_MIN_HEIGHT_DP) {
                add(
                    ReachViolation(
                        ReachRule.R4_SIZE,
                        control.tag,
                        "is ${fmt(control.height)} dp tall, under the ${fmt(Reach.PRIMARY_MIN_HEIGHT_DP)} dp " +
                            "primary height",
                    ),
                )
            }
            if (control.left < Reach.EDGE_INSET_DP) {
                add(
                    ReachViolation(
                        ReachRule.R5_EDGES,
                        control.tag,
                        "starts ${fmt(control.left)} dp from the left edge, inside the " +
                            "${fmt(Reach.EDGE_INSET_DP)} dp inset",
                    ),
                )
            }
            val rightInset = windowWidthDp - control.right
            if (rightInset < Reach.EDGE_INSET_DP) {
                add(
                    ReachViolation(
                        ReachRule.R5_EDGES,
                        control.tag,
                        "starts ${fmt(rightInset)} dp from the right edge, inside the " +
                            "${fmt(Reach.EDGE_INSET_DP)} dp inset",
                    ),
                )
            }
        }

        controls.indices.forEach { i ->
            for (j in i + 1 until controls.size) {
                findGapViolation(controls[i], controls[j])?.let { add(it) }
            }
        }
    }

/**
 * R4's clearance rule, for two targets that sit side by side or stacked.
 *
 * Only pairs that actually neighbour each other are judged: two controls on different rows of a list
 * are separated by the row height, not by a gap their author chose, so checking every pair would
 * invent failures. A pair counts as horizontal neighbours when their vertical spans overlap, and as
 * vertical neighbours when their horizontal spans do.
 */
private fun findGapViolation(
    a: ControlPlacement,
    b: ControlPlacement,
): ReachViolation? {
    val verticalOverlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    val horizontalOverlap = minOf(a.right, b.right) - maxOf(a.left, b.left)

    val gap: Float
    val axis: String
    when {
        verticalOverlap > 0f -> {
            gap = maxOf(b.left - a.right, a.left - b.right)
            axis = "horizontally"
        }

        horizontalOverlap > 0f -> {
            gap = maxOf(b.top - a.bottom, a.top - b.bottom)
            axis = "vertically"
        }

        else -> {
            return null
        }
    }

    // A negative gap means the targets overlap each other, which is worse than a small gap: the
    // lower one cannot be tapped in the covered region at all.
    if (gap < Reach.MIN_TARGET_GAP_DP) {
        val (nearer, further) = if (a.left <= b.left) a to b else b to a
        return ReachViolation(
            ReachRule.R4_GAP,
            "${nearer.tag} / ${further.tag}",
            "are ${fmt(gap)} dp apart $axis, under the ${fmt(Reach.MIN_TARGET_GAP_DP)} dp gap",
        )
    }
    return null
}

private fun fmt(value: Float): String = "%.1f".format(value)

/**
 * Marks a control as primary for the reach audit, or as frequent for R2's tighter ceiling.
 *
 * The tag is what a failure names, so it should say where the control lives ("scores.dateChip") rather
 * than what it looks like ("orangePill") - the point of the failure is to find the control again.
 */
fun Modifier.primaryControl(
    tag: String,
    frequent: Boolean = false,
): Modifier =
    semantics {
        primaryControlTag = tag
        isFrequentControl = frequent
    }

/** The tag a control was marked with, read back by the audit. */
val PrimaryControlTag: SemanticsPropertyKey<String> = SemanticsPropertyKey("PrimaryControlTag")
var SemanticsPropertyReceiver.primaryControlTag by PrimaryControlTag

/** Whether R2's tighter ceiling applies to this control. */
val IsFrequentControl: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("IsFrequentControl")
var SemanticsPropertyReceiver.isFrequentControl by IsFrequentControl
