package net.montycasa.roux.ui.reach

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * The one-handed reach rules (A8, R1-R5), as numbers and as a pure check.
 *
 * The rules are fractions of window height rather than absolute offsets, so they hold on a 6.1" phone
 * and on the Pixel 9 alike. Nothing here measures anything: a caller hands in where each target
 * actually landed, and this decides pass or fail. That keeps the policy testable without Compose and
 * keeps the Compose-side reader thin enough to be obvious.
 *
 * R4 is not conditional on tagging. Tagging marks a control as primary, which *additionally* subjects
 * it to R1, R2 and R5; it is not the price of admission to the size and gap rules. An untagged 40 dp
 * icon is still a 40 dp target, so a screen with no primary controls whatsoever can still fail R4.
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
    R5_CONTAINMENT("R5 containment"),
}

/**
 * Where one target ended up, in dp.
 *
 * [path] is the node's position in the semantics tree ("0/2/1"). It exists so an ancestor and the thing
 * inside it are not reported as overlapping: a clickable row with a clickable star inside it is two
 * legitimate targets, not one broken one.
 */
data class ControlPlacement(
    val tag: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val primary: Boolean = false,
    val frequent: Boolean = false,
    val path: String = "",
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
 *
 * Known limit: this only sees what was composed. A row a `LazyColumn` has not scrolled into view is not
 * measured, so an audit of an initial viewport can clear only the part it saw, never the whole list.
 * Callers auditing a list must scroll it and audit again.
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
            if (control.primary && control.top < primaryTopLimit) {
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
            if (control.primary && control.frequent && control.top < frequentTopLimit) {
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
            if (control.primary && control.height < Reach.PRIMARY_MIN_HEIGHT_DP) {
                add(
                    ReachViolation(
                        ReachRule.R4_SIZE,
                        control.tag,
                        "is ${fmt(control.height)} dp tall, under the ${fmt(Reach.PRIMARY_MIN_HEIGHT_DP)} dp " +
                            "primary height",
                    ),
                )
            }
            if (control.primary && control.left < Reach.EDGE_INSET_DP) {
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
            if (control.primary && rightInset < Reach.EDGE_INSET_DP) {
                add(
                    ReachViolation(
                        ReachRule.R5_EDGES,
                        control.tag,
                        "starts ${fmt(rightInset)} dp from the right edge, inside the " +
                            "${fmt(Reach.EDGE_INSET_DP)} dp inset",
                    ),
                )
            }
            // A target running past the window is clipped, so part of it cannot be pressed at all -
            // which no amount of R1, size or horizontal-edge checking would reveal.
            if (control.bottom > windowHeightDp) {
                add(
                    ReachViolation(
                        ReachRule.R5_CONTAINMENT,
                        control.tag,
                        "ends at ${fmt(control.bottom)} dp, " +
                            "${fmt(control.bottom - windowHeightDp)} dp past the bottom of the " +
                            "${fmt(windowHeightDp)} dp window",
                    ),
                )
            }
            if (control.right > windowWidthDp) {
                add(
                    ReachViolation(
                        ReachRule.R5_CONTAINMENT,
                        control.tag,
                        "ends at ${fmt(control.right)} dp, " +
                            "${fmt(control.right - windowWidthDp)} dp past the right edge",
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
 * R4's clearance rule, for two targets close enough on both axes to be neighbours.
 *
 * Distance is measured per axis and a pair is judged only when **both** axes are under the minimum:
 * two targets 4 dp apart vertically but 300 dp apart horizontally are not neighbours, and inventing a
 * failure for them would make the audit unusable. Diagonal neighbours are therefore covered - two
 * targets 4 dp apart on each axis have a 4 dp gap by this measure, not the 5.7 dp corner clearance a
 * straight-line reading would give.
 *
 * An ancestor and its descendant are skipped: their rectangles overlap by construction and both are
 * still reported separately if they break a size rule.
 */
private fun findGapViolation(
    a: ControlPlacement,
    b: ControlPlacement,
): ReachViolation? {
    if (a.path.isNotEmpty() && b.path.isNotEmpty() && (isAncestor(a.path, b.path) || isAncestor(b.path, a.path))) {
        return null
    }

    val gapX = maxOf(b.left - a.right, a.left - b.right)
    val gapY = maxOf(b.top - a.bottom, a.top - b.bottom)
    val worst = maxOf(gapX, gapY)
    if (worst >= Reach.MIN_TARGET_GAP_DP) return null

    // Negative means the rectangles overlap: the lower target cannot be pressed where it is covered.
    val shape =
        if (worst < 0f) {
            "overlap by ${fmt(-worst)} dp"
        } else {
            "are ${fmt(gapX)} dp apart horizontally and ${fmt(gapY)} dp vertically"
        }
    val (nearer, further) = if (a.left <= b.left) a to b else b to a
    return ReachViolation(
        ReachRule.R4_GAP,
        "${nearer.tag} / ${further.tag}",
        "$shape, under the ${fmt(Reach.MIN_TARGET_GAP_DP)} dp gap",
    )
}

private fun isAncestor(
    candidate: String,
    of: String,
): Boolean = of.startsWith("$candidate/")

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
