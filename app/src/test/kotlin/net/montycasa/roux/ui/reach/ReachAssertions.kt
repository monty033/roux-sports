package net.montycasa.roux.ui.reach

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot

/**
 * The Compose-side half of the reach audit: read where the targets landed, then ask [Reach].
 *
 * This file is thin on purpose. Everything that decides pass or fail is in [findReachViolations] and is
 * covered without Compose; the only thing this adds is a tree walk and a px-to-dp conversion. If a
 * reach bug is ever about the *rule*, it is not in here.
 */
fun ComposeTestRule.assertPrimaryControlsReachable(minPrimary: Int = 1) {
    val targets = reachTargets()
    // Counted in primaries, not in targets: if the tags were dropped the R1, R2 and R5 checks would run
    // on nothing at all, while an untagged button kept the target count healthy and hid the mistake.
    // A screen with no primary controls says so by passing 0.
    val primaries = targets.count { it.primary }
    if (primaries < minPrimary) {
        throw AssertionError(
            "reach audit found $primaries primary control(s), expected at least $minPrimary - " +
                "R1, R2 and the edge rules were checked against nothing",
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

    fun walk(
        node: SemanticsNode,
        path: String,
    ) {
        val tag = node.config.getOrNull(PrimaryControlTag)
        // A target is anything a finger can act on: a click, or an adjustable control such as a slider,
        // which carries SetProgress instead of OnClick and would otherwise be invisible to R4.
        val actionable =
            node.config.contains(SemanticsActions.OnClick) ||
                node.config.contains(SemanticsActions.SetProgress)
        if (tag != null || actionable) {
            // Position and measured size, not boundsInRoot: boundsInRoot is clipped by parents, so a
            // control running 17 dp past the bottom of the window reports a rectangle that stops exactly
            // at the window edge, and looks like a smaller target that passes every rule. The overrun is
            // only visible in the unclipped rectangle, which is position + size.
            val left = node.positionInRoot.x / scale
            val top = node.positionInRoot.y / scale
            val right = left + node.size.width / scale
            val bottom = top + node.size.height / scale
            found +=
                ControlPlacement(
                    tag = tag ?: describe(node, left, top),
                    rawLeft = left,
                    rawTop = top,
                    rawRight = right,
                    rawBottom = bottom,
                    visibleLeft = left.coerceAtLeast(0f),
                    visibleTop = top.coerceAtLeast(0f),
                    visibleRight = right.coerceAtMost(windowWidthDp),
                    visibleBottom = bottom.coerceAtMost(windowHeightDp),
                    primary = tag != null,
                    frequent = node.config.getOrNull(IsFrequentControl) ?: false,
                    path = path,
                )
        }
        node.children.forEachIndexed { index, child -> walk(child, if (path.isEmpty()) "$index" else "$path/$index") }
    }
    walk(root, "")
    return found
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
