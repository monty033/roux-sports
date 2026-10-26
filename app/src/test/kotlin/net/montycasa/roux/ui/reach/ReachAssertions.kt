package net.montycasa.roux.ui.reach

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot

/**
 * The Compose-side half of the reach audit: read where the primary controls landed, then ask [Reach].
 *
 * This file is thin on purpose. Everything that decides pass or fail is in [findReachViolations] and is
 * covered without Compose; the only thing this adds is a tree walk and a px-to-dp conversion. If a
 * reach bug is ever about the *rule*, it is not in here.
 */
fun ComposeTestRule.assertPrimaryControlsReachable() {
    val violations = reachViolations()
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
 * The tags the audit actually found, in tree order.
 *
 * A caller asserts these before judging violations. Without it, a composition that produced no tagged
 * controls at all reads as "no violations" and a broken audit passes silently - which is how the gap
 * test failed once during development, reporting "nothing was thrown" with no hint that it had seen
 * nothing to throw about.
 */
fun ComposeTestRule.primaryControlTags(): List<String> {
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
    return root.primaryControls(density.density).map { it.tag }
}

/** Every way the current composition breaks R1-R5. Empty means reachable. */
fun ComposeTestRule.reachViolations(): List<ReachViolation> {
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val scale = density.density
    return findReachViolations(
        controls = root.primaryControls(scale),
        windowWidthDp = root.size.width / scale,
        windowHeightDp = root.size.height / scale,
    )
}

/**
 * Tagged controls in this subtree, in dp.
 *
 * A tagged node is a leaf as far as this walk is concerned: if a container and something inside it
 * were both tagged, counting both would report a gap violation against itself.
 */
private fun SemanticsNode.primaryControls(scale: Float): List<ControlPlacement> {
    val found = mutableListOf<ControlPlacement>()

    fun walk(node: SemanticsNode) {
        val tag = node.config.getOrNull(PrimaryControlTag)
        if (tag != null) {
            val bounds = node.boundsInRoot
            found +=
                ControlPlacement(
                    tag = tag,
                    left = bounds.left / scale,
                    top = bounds.top / scale,
                    right = bounds.right / scale,
                    bottom = bounds.bottom / scale,
                    frequent = node.config.getOrNull(IsFrequentControl) ?: false,
                )
            return
        }
        node.children.forEach(::walk)
    }
    walk(this)
    return found
}
