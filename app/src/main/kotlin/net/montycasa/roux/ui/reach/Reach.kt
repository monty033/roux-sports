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
 * Two rectangles, because they answer different questions. The raw rectangle is where the control was
 * laid out; the visible one is the part inside the window. Size and clearance are judged on what a thumb
 * can actually hit, so a 56 dp button hanging 20 dp off the left edge is a 36 dp target however large
 * its layout size is. Position rules (R1, R2, the edge insets) use the raw rectangle, because a control
 * that starts above the reach line does not become reachable by being clipped.
 *
 * [path] is the node's position in the semantics tree ("0/2/1"), so an ancestor and the thing inside it
 * are not reported as overlapping: a clickable row with a clickable star is two legitimate targets.
 */
data class ControlPlacement(
    val tag: String,
    val rawLeft: Float,
    val rawTop: Float,
    val rawRight: Float,
    val rawBottom: Float,
    val visibleLeft: Float,
    val visibleTop: Float,
    val visibleRight: Float,
    val visibleBottom: Float,
    val primary: Boolean = false,
    val frequent: Boolean = false,
    val path: String = "",
    /**
     * True when an ancestor scrolls in the axis this control was cut on, so the rest of it is reachable.
     *
     * A date strip shows six chips and part of a seventh; the seventh is 42 dp of visible target at rest and
     * 56 dp the moment the strip is scrolled sideways. R4 asks how big the target is, and a scroll viewport is
     * not a statement about the target. Judging visible geometry would make every horizontally scrolling
     * primary control illegal at double type, where no window width fits even three chips.
     *
     * Per axis, because the axis is the whole point: a button squeezed to 30 dp wide by a fixed parent inside a
     * vertically scrolling list stays clipped no matter how far the fan scrolls, so a vertical scroller must not
     * excuse a horizontal cut.
     */
    val horizontalScroller: Boolean = false,
    val verticalScroller: Boolean = false,
) {
    /** Whether the axis a control was cut on can be scrolled to reveal the rest of it. */
    val widthRecoverableByScrolling: Boolean get() = horizontalScroller
    val heightRecoverableByScrolling: Boolean get() = verticalScroller

    /** What a thumb can actually press: the part of the target inside the window. */
    val width: Float get() = visibleRight - visibleLeft
    val height: Float get() = visibleBottom - visibleTop

    /**
     * The size R4 judges. Inside a scroll viewport that is the control's own measured size, because the
     * clipping is a viewport fact rather than a property of the control.
     */
    val targetWidth: Float get() = if (widthRecoverableByScrolling) rawRight - rawLeft else width
    val targetHeight: Float get() = if (heightRecoverableByScrolling) rawBottom - rawTop else height

    val isVisible: Boolean get() = width > 0f && height > 0f
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
            // A composed node scrolled entirely out of view is not on screen to be pressed, so it is not
            // a violation - judging it would fail an audit of a screen nobody can see.
            if (!control.isVisible) continue
            if (control.primary && control.rawTop < primaryTopLimit) {
                add(
                    ReachViolation(
                        ReachRule.R1,
                        control.tag,
                        "starts at ${fmt(control.rawTop)} dp, above the ${fmt(primaryTopLimit)} dp limit " +
                            "(0.50 of ${fmt(windowHeightDp)} dp)",
                    ),
                )
            }
            // R2 is an additional ceiling for frequent controls, not a replacement for R1: a frequent
            // control in the top third passes nothing, and one between 0.50 and 0.70 fails only R2.
            if (control.primary && control.frequent && control.rawTop < frequentTopLimit) {
                add(
                    ReachViolation(
                        ReachRule.R2,
                        control.tag,
                        "is frequent but starts at ${fmt(control.rawTop)} dp, above the " +
                            "${fmt(frequentTopLimit)} dp limit (0.70 of ${fmt(windowHeightDp)} dp)",
                    ),
                )
            }
            if (control.targetWidth < Reach.MIN_TARGET_DP || control.targetHeight < Reach.MIN_TARGET_DP) {
                val measured =
                    if (control.targetWidth != control.width || control.targetHeight != control.height) {
                        "${fmt(control.targetWidth)} x ${fmt(control.targetHeight)} dp laid out " +
                            "(${fmt(control.width)} x ${fmt(control.height)} visible; the rest is a scroll away)"
                    } else {
                        "${fmt(control.targetWidth)} x ${fmt(control.targetHeight)} dp"
                    }
                add(
                    ReachViolation(
                        ReachRule.R4_SIZE,
                        control.tag,
                        "is $measured, under the ${fmt(Reach.MIN_TARGET_DP)} dp minimum",
                    ),
                )
            }
            if (control.primary && control.targetHeight < Reach.PRIMARY_MIN_HEIGHT_DP) {
                add(
                    ReachViolation(
                        ReachRule.R4_SIZE,
                        control.tag,
                        "is ${fmt(control.targetHeight)} dp tall, under the ${fmt(Reach.PRIMARY_MIN_HEIGHT_DP)} dp " +
                            "primary height",
                    ),
                )
            }
            if (control.primary && control.rawLeft < Reach.EDGE_INSET_DP) {
                add(
                    ReachViolation(
                        ReachRule.R5_EDGES,
                        control.tag,
                        "starts ${fmt(control.rawLeft)} dp from the left edge, inside the " +
                            "${fmt(Reach.EDGE_INSET_DP)} dp inset",
                    ),
                )
            }
            val rightInset = windowWidthDp - control.rawRight
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
            if (control.rawBottom > windowHeightDp) {
                add(
                    ReachViolation(
                        ReachRule.R5_CONTAINMENT,
                        control.tag,
                        "ends at ${fmt(control.rawBottom)} dp, " +
                            "${fmt(control.rawBottom - windowHeightDp)} dp past the bottom of the " +
                            "${fmt(windowHeightDp)} dp window",
                    ),
                )
            }
            if (control.rawRight > windowWidthDp) {
                add(
                    ReachViolation(
                        ReachRule.R5_CONTAINMENT,
                        control.tag,
                        "ends at ${fmt(control.rawRight)} dp, " +
                            "${fmt(control.rawRight - windowWidthDp)} dp past the right edge",
                    ),
                )
            }
            // The left and top overruns matter for the same reason: the target is cut down to less than
            // it claims to be, and on the left it sits inside the back-gesture strip as well.
            if (control.rawLeft < 0f) {
                add(
                    ReachViolation(
                        ReachRule.R5_CONTAINMENT,
                        control.tag,
                        "starts at ${fmt(control.rawLeft)} dp, ${fmt(-control.rawLeft)} dp past the left edge",
                    ),
                )
            }
            if (control.rawTop < 0f) {
                add(
                    ReachViolation(
                        ReachRule.R5_CONTAINMENT,
                        control.tag,
                        "starts at ${fmt(control.rawTop)} dp, ${fmt(-control.rawTop)} dp above the top edge",
                    ),
                )
            }
        }

        // Only targets with visible area can conflict with each other. Without this filter a control
        // scrolled entirely out of view still reports a 0 dp gap against the one at the window edge,
        // which is the same mistake the per-target checks already avoid.
        val visible = controls.filter { it.isVisible }
        visible.indices.forEach { i ->
            for (j in i + 1 until visible.size) {
                findGapViolation(visible[i], visible[j])?.let { add(it) }
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

    val gapX = maxOf(b.visibleLeft - a.visibleRight, a.visibleLeft - b.visibleRight)
    val gapY = maxOf(b.visibleTop - a.visibleBottom, a.visibleTop - b.visibleBottom)
    val worst = maxOf(gapX, gapY)
    if (worst >= Reach.MIN_TARGET_GAP_DP) return null

    // Negative means the rectangles overlap: the lower target cannot be pressed where it is covered.
    val shape =
        if (worst < 0f) {
            "overlap by ${fmt(-worst)} dp"
        } else {
            "are ${fmt(gapX)} dp apart horizontally and ${fmt(gapY)} dp vertically"
        }
    val (nearer, further) = if (a.visibleLeft <= b.visibleLeft) a to b else b to a
    return ReachViolation(
        ReachRule.R4_GAP,
        "${nearer.tag} / ${further.tag}",
        "$shape, under the ${fmt(Reach.MIN_TARGET_GAP_DP)} dp gap",
    )
}

private fun isAncestor(
    candidate: String,
    of: String,
): Boolean =
    if (candidate.isEmpty()) {
        // Defensive: the fetched semantics root is an ancestor of every node. Current traversal does not
        // collect the root itself, so this branch is unreachable today and is kept only so a future
        // collector that does include it cannot report a root click as overlapping its children.
        of.isNotEmpty()
    } else {
        of.startsWith("$candidate/")
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

/**
 * Declares a target that must meet R4 but is not a primary control.
 *
 * Needed for gesture targets Compose exposes no action for: a `Modifier.draggable` knob or a custom
 * `pointerInput` carries neither OnClick nor SetProgress, so the audit cannot find it, and marking it
 * with [primaryControl] would wrongly subject it to R1, R2 and the edge insets. Anything with a custom
 * gesture must declare itself here or it is invisible to the size rule.
 */
fun Modifier.reachTarget(tag: String): Modifier =
    semantics {
        reachTargetTag = tag
    }

/** The tag of a non-primary target, read back by the audit. */
val ReachTargetTag: SemanticsPropertyKey<String> = SemanticsPropertyKey("ReachTargetTag")
var SemanticsPropertyReceiver.reachTargetTag by ReachTargetTag
