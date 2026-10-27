package net.montycasa.roux.ui.reach

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot

/*
 * The Compose-side half of the reach audit: read where the targets landed, then ask [Reach].
 *
 * This file is thin on purpose. Everything that decides pass or fail is in [findReachViolations] and is
 * covered without Compose; the only thing this adds is a tree walk and a px-to-dp conversion. If a
 * reach bug is ever about the *rule*, it is not in here.
 */

/**
 * Audits every reachable target on screen and insists the tree matches the primaries the screen declares.
 *
 * The declaration is also the audit's limit. It can prove the tree matches what a screen says it intends,
 * not that the intent is right: an action wrongly declared non-primary - a reachTarget instead of a
 * primaryControl - sits outside R1, R2 and the edge rules, and nothing here can tell. Reviewing the
 * declaration is part of reviewing the screen.
 */
fun ComposeTestRule.assertPrimaryControlsReachable(vararg expectedPrimaryTags: String) {
    val targets = reachTargets()
    // The screen states its primary controls, and the audit insists the tree agrees. A count guard only
    // catches losing every tag; a screen with two primary actions would still lose one of them silently,
    // and that action would keep its size check while losing R1, R2 and the edge insets. An empty list is
    // a positive statement that this screen has no primary controls.
    val actual = targets.filter { it.primary }.map { it.tag }
    val expected = expectedPrimaryTags.toList()

    // A tag has to name exactly one control. Compared as sets, two controls sharing a tag would mask one
    // of them losing its tag: the survivor keeps the set equal to the declaration while the other quietly
    // drops out of R1 and R2. Duplicates are rejected outright instead of being collapsed.
    fun duplicates(tags: List<String>): Set<String> =
        tags
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

    val duplicated = duplicates(actual) + duplicates(expected)
    if (duplicated.isNotEmpty()) {
        throw AssertionError(
            "primary tags must be unique, but ${duplicated.sorted()} appear more than once - " +
                "a shared tag hides one control losing it",
        )
    }

    if (actual.sorted() != expected.sorted()) {
        throw AssertionError(
            "primary controls are ${actual.sorted()} but the screen declares ${expected.sorted()} - " +
                "an untagged or reachTarget-declared action is outside R1, R2 and the edge rules",
        )
    }
    val violations = reachViolations(targets)
    if (violations.isNotEmpty()) {
        throw AssertionError(
            buildString {
                append("${violations.size} reach violation(s) at ${density.density} density:\n")
                violations.forEach { append("  - ${it.describe()}\n") }
            }.trimEnd(),
        )
    }
}

/**
 * The tags the audit found for primary controls, in tree order.
 *
 * A caller asserts these before judging violations. Without it, a composition that produced no tagged
 * controls reads as "no violations" and a broken audit passes silently - which is how the gap test
 * failed once during development, reporting "nothing was thrown" with no hint that it had seen nothing.
 */
fun ComposeTestRule.primaryControlTags(): List<String> = reachTargets().filter { it.primary }.map { it.tag }

/** Every way the current composition breaks R1-R5. Empty means reachable. */
fun ComposeTestRule.reachViolations(): List<ReachViolation> = reachViolations(reachTargets())

private fun ComposeTestRule.reachViolations(targets: List<ControlPlacement>): List<ReachViolation> {
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val scale = density.density
    return findReachViolations(
        controls = targets,
        windowWidthDp = root.size.width / scale,
        windowHeightDp = root.size.height / scale,
    )
}

/**
 * Every target in this subtree, in dp: anything clickable, plus anything explicitly tagged primary.
 *
 * Clickable nodes are collected because R4 applies to all targets, not only the tagged ones - an
 * untagged 40 dp icon would otherwise escape the size rule entirely. Tagging adds R1, R2 and R5.
 *
 * The walk descends through tagged nodes rather than stopping at them: a tagged container with a
 * clickable child is two targets, and the child must still meet the size rule. Ancestor/descendant
 * pairs are excluded from the gap rule by [ControlPlacement.path], not by dropping them here.
 */
fun ComposeTestRule.reachTargets(): List<ControlPlacement> {
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val scale = density.density
    val windowWidthDp = root.size.width / scale
    val windowHeightDp = root.size.height / scale
    val found = mutableListOf<ControlPlacement>()

    // Every semantics action that means "a person operates this", decided once rather than one action per
    // review round. Each entry earned its place by being a real control that carries no OnClick: a slider
    // exposes SetProgress, and a text field can expose SetText and nothing else, so a 40 dp field used to
    // vanish from the audit while it passed.
    //
    // Deliberately absent: ScrollBy and its relatives, because a scrollable region is a container rather
    // than a target and including it would flag every list. Custom actions added through
    // semantics { customActions = ... } cannot be read reliably here; such a control declares itself with
    // Modifier.reachTarget(tag).
    val actions =
        listOf(
            SemanticsActions.OnClick,
            SemanticsActions.OnLongClick,
            SemanticsActions.SetProgress,
            SemanticsActions.SetText,
            SemanticsActions.SetSelection,
            SemanticsActions.Expand,
            SemanticsActions.Collapse,
            SemanticsActions.Dismiss,
        )

    fun walk(
        node: SemanticsNode,
        path: String,
        staticLeft: Float = -Float.MAX_VALUE,
        staticRight: Float = Float.MAX_VALUE,
        staticTop: Float = -Float.MAX_VALUE,
        staticBottom: Float = Float.MAX_VALUE,
        horizontalWindows: List<ScrollWindow> = emptyList(),
        verticalWindows: List<ScrollWindow> = emptyList(),
    ) {
        // Two keys, deliberately not merged: primaryControl marks a control that R1, R2 and the edge
        // insets also govern, while reachTarget only says "measure this". Collapsing them would subject
        // a drag knob to the reach zone it is not in.
        val primaryTag = node.config.getOrNull(PrimaryControlTag)
        val declaredTarget = node.config.getOrNull(ReachTargetTag)
        val tag = primaryTag ?: declaredTarget
        if (tag != null || actions.any { node.config.contains(it) }) {
            // Position and measured size, not boundsInRoot: boundsInRoot is clipped by parents, so a
            // control running 17 dp past the bottom of the window reports a rectangle that stops exactly
            // at the window edge, and looks like a smaller target that passes every rule. The overrun is
            // only visible in the unclipped rectangle, which is position + size.
            val left = node.positionInRoot.x / scale
            val top = node.positionInRoot.y / scale
            val right = left + node.size.width / scale
            val bottom = top + node.size.height / scale
            // boundsInRoot is already clipped by every ancestor as well as the window, which is exactly
            // the area a finger can reach. Intersecting position+size with the window instead would miss
            // a control inside a nested scroll viewport that shows only part of it.
            val clipped = node.boundsInRoot
            val visibleLeft = (clipped.left / scale).coerceIn(0f, windowWidthDp)
            val visibleTop = (clipped.top / scale).coerceIn(0f, windowHeightDp)
            val visibleRight = (clipped.right / scale).coerceIn(visibleLeft, windowWidthDp)
            val visibleBottom = (clipped.bottom / scale).coerceIn(visibleTop, windowHeightDp)
            found +=
                ControlPlacement(
                    tag = tag ?: describe(node, left, top),
                    rawLeft = left,
                    rawTop = top,
                    rawRight = right,
                    rawBottom = bottom,
                    visibleLeft = visibleLeft,
                    visibleTop = visibleTop,
                    visibleRight = visibleRight,
                    visibleBottom = visibleBottom,
                    primary = primaryTag != null,
                    frequent = node.config.getOrNull(IsFrequentControl) ?: false,
                    path = path,
                    // How much of this control can ever be on screen at once. Ancestors that cannot scroll
                    // bound it where it sits, so their rectangles have to be intersected with the control's
                    // own - a width alone cannot say that a target is hanging out of the side of its parent.
                    // Scrolling ancestors contribute their extent, and every one of them counts: an inner
                    // 200 dp scroller inside a 30 dp one still only ever shows 30.
                    reachableWidth = reachable(left, right, staticLeft, staticRight, horizontalWindows),
                    reachableHeight = reachable(top, bottom, staticTop, staticBottom, verticalWindows),
                )
        }
        // Two facts per axis, and they are different facts. A parent that does not scroll bounds its child
        // permanently; a parent that scrolls instead publishes a window the child can be dragged through, so it
        // contributes the window's extent rather than its own clipping. Keeping them separate is what tells a
        // chip at the end of a strip apart from a button squeezed inside one.
        // A scrollable modifier with nothing to scroll is not a window: the content cannot move, so whatever is
        // outside the viewport now is outside it forever and the viewport behaves exactly like a fixed parent.
        // maxValue is the scroll range, which is the only way to tell that apart from a real strip.
        val horizontalRange = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        val verticalRange = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        val scrollsHorizontally = horizontalRange != null && horizontalRange.maxValue() > 0f
        val scrollsVertically = verticalRange != null && verticalRange.maxValue() > 0f
        val nodeWidthDp = node.size.width / scale
        val nodeHeightDp = node.size.height / scale
        val nodeLeft = node.positionInRoot.x / scale
        val nodeTop = node.positionInRoot.y / scale
        val nextStaticLeft = if (scrollsHorizontally) staticLeft else maxOf(staticLeft, nodeLeft)
        val nextStaticRight = if (scrollsHorizontally) staticRight else minOf(staticRight, nodeLeft + nodeWidthDp)
        val nextStaticTop = if (scrollsVertically) staticTop else maxOf(staticTop, nodeTop)
        val nextStaticBottom = if (scrollsVertically) staticBottom else minOf(staticBottom, nodeTop + nodeHeightDp)
        val nextHorizontalWindows =
            if (scrollsHorizontally) {
                horizontalWindows + ScrollWindow(nodeLeft, nodeLeft + nodeWidthDp, horizontalRange!!.maxValue() / scale)
            } else {
                horizontalWindows
            }
        val nextVerticalWindows =
            if (scrollsVertically) {
                verticalWindows + ScrollWindow(nodeTop, nodeTop + nodeHeightDp, verticalRange!!.maxValue() / scale)
            } else {
                verticalWindows
            }
        node.children.forEachIndexed { index, child ->
            walk(
                child,
                if (path.isEmpty()) "$index" else "$path/$index",
                nextStaticLeft,
                nextStaticRight,
                nextStaticTop,
                nextStaticBottom,
                nextHorizontalWindows,
                nextVerticalWindows,
            )
        }
    }
    walk(root, "")
    return found
}

/**
 * One scrolling viewport: where it sits, and how far its content can move.
 *
 * The range matters as much as the size. A strip whose content already fits cannot move, and a strip with 20 dp of
 * travel cannot rescue a control that needs 40 - so what a fan can reach is the best overlap any scroll position
 * can produce, not the viewport's width.
 */
private data class ScrollWindow(
    val start: Float,
    val end: Float,
    val scrollRange: Float,
) {
    /**
     * Best overlap between this control and the viewport over every reachable scroll position.
     *
     * Overlap is unimodal in the scroll offset: it grows until the control is as far inside the viewport as it can
     * get and shrinks after that. So the maximum is at whichever reachable offset is closest to the ideal one, and
     * the ideal is "right edges flush" - which for a control narrower than the viewport means fully visible, and
     * for a wider one means the whole viewport covered.
     *
     * The lower bound is load-bearing: without it a control scrolled past the start of its strip gets a negative
     * offset, slides back into view on paper, and is reported reachable - removing it fails
     * [aScrollerThatOnlyScrollsAwayFromATargetRevealsNothing].
     *
     * The upper bound is not, and saying so beats implying otherwise: a control a strip has scrolled entirely out
     * of view is absent from the semantics tree, and a control that is the last thing in a strip has exactly
     * enough travel to bring it flush with the far edge, so removing that bound survives the suite - measured, not
     * assumed. It stays because trailing decoration can break both premises, and the cost is one coerceIn.
     */
    fun bestOverlap(
        controlStart: Float,
        controlEnd: Float,
    ): Float {
        val offset = (controlEnd - end).coerceIn(0f, scrollRange)
        val from = maxOf(controlStart - offset, start)
        val to = minOf(controlEnd - offset, end)
        return (to - from).coerceAtLeast(0f)
    }
}

/**
 * The most of a control that can ever be simultaneously on screen, in dp.
 *
 * [start]/[end] are the control's laid-out extent on one axis. [staticStart]/[staticEnd] are the interval left over
 * by the ancestors that cannot scroll. [windows] are the scroll viewports around it, each with its own travel; the
 * narrowest of them decides, because every one of them clips.
 *
 * Intersecting rectangles rather than comparing widths is what catches a target hanging out of the side of its
 * parent, and asking each window for its best reachable overlap is what catches one that a scroller can never bring
 * fully into view.
 */
private fun reachable(
    start: Float,
    end: Float,
    staticStart: Float,
    staticEnd: Float,
    windows: List<ScrollWindow>,
): Float {
    val clippedStart = maxOf(start, staticStart)
    val clippedEnd = minOf(end, staticEnd)
    var best = (clippedEnd - clippedStart).coerceAtLeast(0f)
    for (window in windows) {
        best = minOf(best, window.bestOverlap(start, end))
    }
    return best
}

/** A name for an untagged target, so a failure can still be found on screen. */
private fun describe(
    node: SemanticsNode,
    leftDp: Float,
    topDp: Float,
): String {
    val testTag = node.config.getOrNull(SemanticsProperties.TestTag)
    if (testTag != null) return testTag
    val label =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: node.config
                .getOrNull(SemanticsProperties.Text)
                ?.firstOrNull()
                ?.text
    return label?.let { "unlabelled '$it'" }
        ?: "unlabelled target at ${leftDp.toInt()},${topDp.toInt()} dp"
}
