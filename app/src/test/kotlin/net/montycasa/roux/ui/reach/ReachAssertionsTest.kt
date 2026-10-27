package net.montycasa.roux.ui.reach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reach audit's own acceptance tests (S3.2).
 *
 * Each case plants one violation and checks that the audit names it. The audit is the thing that will
 * police every later screen, so the failure mode that matters is not "does it catch a bad layout" but
 * "does it stay silent on a bad layout" - that is what these assert against, one rule at a time.
 *
 * Window is 411 x 923 dp, so R1's ceiling is 461.5 dp and R2's is 646.1 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReachAssertionsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun failure(vararg expectedPrimary: String): AssertionError =
        assertThrows(AssertionError::class.java) { rule.assertPrimaryControlsReachable(*expectedPrimary) }

    @Test
    fun aControlInTheTopHalfFailsAndTheMessageNamesItsTag() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .padding(start = 16.dp, top = 16.dp)
                        .size(width = 200.dp, height = 56.dp)
                        .primaryControl("scores.title"),
                )
            }
        }

        val message = failure("scores.title").message.orEmpty()
        assertTrue("R1 should be cited, got: $message", message.contains("R1"))
        assertTrue("the failing control should be named, got: $message", message.contains("scores.title"))
    }

    @Test
    fun aControlInTheBottomZonePasses() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(width = 200.dp, height = 56.dp)
                        .primaryControl("scores.refresh"),
                )
            }
        }

        rule.assertPrimaryControlsReachable("scores.refresh")
    }

    @Test
    fun aFrequentControlAtSixtyPercentHeightFailsR2ButNotR1() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 554.dp)
                        .size(width = 200.dp, height = 56.dp)
                        .primaryControl("scores.dateStrip", frequent = true),
                )
            }
        }

        val message = failure("scores.dateStrip").message.orEmpty()
        // 554 dp clears R1's 461.5 dp ceiling and breaks R2's 646.1 dp one, so citing R2 alone is the
        // point: a control can be in the bottom half and still be out of the frequent-control band.
        assertTrue("R2 should be cited, got: $message", message.contains("R2"))
        assertTrue("R1 should not be cited, got: $message", !message.contains("R1"))
    }

    @Test
    fun aTargetNarrowerThanTheMinimumFailsTheSizeRule() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(width = 40.dp, height = 56.dp)
                        .primaryControl("scores.narrowRow"),
                )
            }
        }

        val message = failure("scores.narrowRow").message.orEmpty()
        // 56 dp tall, so only the 48 dp minimum can fail here - the tall-enough primary floor is not
        // what this test is about.
        assertTrue("R4 should be cited, got: $message", message.contains("R4"))
        assertTrue("the failing control should be named, got: $message", message.contains("scores.narrowRow"))
    }

    @Test
    fun aPrimaryTargetShorterThanFiftySixDpFailsEvenAtTheMinimumWidth() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(width = 120.dp, height = 50.dp)
                        .primaryControl("scores.shortRow"),
                )
            }
        }

        val message = failure("scores.shortRow").message.orEmpty()
        // 50 dp clears the 48 dp minimum and breaks the 56 dp primary height, so this is the case that
        // pins the second threshold on its own.
        assertTrue("R4 should be cited, got: $message", message.contains("R4"))
        assertTrue("the primary height should be cited, got: $message", message.contains("56.0 dp primary height"))
    }

    @Test
    fun aControlEightDpFromTheLeftEdgeFailsTheEdgeRule() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 16.dp)
                        .size(width = 120.dp, height = 56.dp)
                        .primaryControl("scores.edgeChip"),
                )
            }
        }

        val message = failure("scores.edgeChip").message.orEmpty()
        assertTrue("R5 should be cited, got: $message", message.contains("R5"))
        assertTrue("the failing control should be named, got: $message", message.contains("scores.edgeChip"))
    }

    @Test
    fun twoTargetsFourDpApartFailTheGapRule() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .primaryControl("strip.first"),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .primaryControl("strip.second"),
                    )
                }
            }
        }

        val message = failure("strip.first", "strip.second").message.orEmpty()
        assertTrue("the gap rule should be cited, got: $message", message.contains("R4 target gap"))
        assertTrue("both neighbours should be named, got: $message", message.contains("strip.first") && message.contains("strip.second"))
    }

    @Test
    fun anUntaggedClickableFortyDpTargetStillFailsTheSizeRule() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .size(width = 40.dp, height = 40.dp)
                        .clickable { },
                )
            }
        }

        // Deliberately no primaryControl(): R4 is about the target, not about the tag. An icon nobody
        // tagged is exactly how a 40 dp tap target ships. minPrimary = 0 because there is nothing
        // primary here to require.
        val message = failure().message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue("the size should be reported, got: $message", message.contains("40.0 x 40.0 dp"))
    }

    @Test
    fun aClickableInsideATaggedParentIsJudgedButNotTreatedAsOverlapping() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(width = 200.dp, height = 56.dp)
                        .primaryControl("scores.card")
                        .clickable { },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(width = 40.dp, height = 40.dp)
                            .clickable { },
                    )
                }
            }
        }

        val message = failure("scores.card").message.orEmpty()
        // The 40 dp child is a real target and fails R4. The parent contains it, so reporting the two as
        // overlapping would be a false failure that makes every card with an icon fail.
        assertTrue("the child should fail R4 size, got: $message", message.contains("R4 target size"))
        assertTrue("nesting must not be reported as overlap, got: $message", !message.contains("R4 target gap"))
    }

    @Test
    fun diagonalNeighboursFourDpApartOnBothAxesFailTheGapRule() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 800.dp)
                        .size(width = 56.dp, height = 56.dp)
                        .primaryControl("grid.topLeft"),
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 76.dp, top = 860.dp)
                        .size(width = 56.dp, height = 56.dp)
                        .primaryControl("grid.bottomRight"),
                )
            }
        }

        // 4 dp of clearance on each axis: not aligned, but a thumb cannot reliably pick between them,
        // and reading only aligned neighbours would call this fine.
        val message = failure("grid.topLeft", "grid.bottomRight").message.orEmpty()
        assertTrue("the gap rule should be cited, got: $message", message.contains("R4 target gap"))
        assertTrue(
            "both neighbours should be named, got: $message",
            message.contains("grid.topLeft") && message.contains("grid.bottomRight"),
        )
    }

    @Test
    fun anAuditThatFoundNothingFailsInsteadOfPassing() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Text("no targets here")
            }
        }

        val message =
            assertThrows(AssertionError::class.java) { rule.assertPrimaryControlsReachable("scores.missing") }
                .message
                .orEmpty()
        assertTrue(
            "an audit that found nothing must say so, got: $message",
            message.contains("primary controls are []") && message.contains("scores.missing"),
        )
    }

    @Test
    fun aControlClippedByTheBottomEdgeFailsContainment() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset(x = 16.dp, y = 860.dp)
                        .size(width = 120.dp, height = 80.dp)
                        .primaryControl("scores.overrun"),
                )
            }
        }

        // 860 + 80 = 940 dp in a 923 dp window: R1, size and the horizontal insets all pass, and 17 dp
        // of the control cannot be touched.
        val message = failure("scores.overrun").message.orEmpty()
        assertTrue("containment should be cited, got: $message", message.contains("R5 containment"))
        // The exact dp depends on pixel rounding at 2.625 density, so assert the shape of the report.
        assertTrue("the overrun should be reported, got: $message", message.contains("dp past the bottom"))
    }

    @Test
    @Config(qualifiers = "w411dp-h400dp-420dpi")
    fun aContentFlowLayoutPassesAtFontScaleOne() {
        rule.setContent { contentFlow(1f) }

        // Window is 400 dp tall, so R1's ceiling is 200 dp. At 1x the text above the button leaves it
        // just under that ceiling.
        val targets = rule.reachTargets()
        assertTrue("the audit should see the button, saw ${targets.map { it.tag }}", targets.any { it.primary })
        assertTrue(
            "the button's top should be near the ceiling, was ${targets.first { it.primary }.rawTop}",
            targets.first { it.primary }.rawTop >= 200f,
        )
        rule.assertPrimaryControlsReachable("scores.retry")
    }

    @Test
    @Config(qualifiers = "w411dp-h400dp-420dpi")
    fun doublingTheTypePushesTheSameLayoutOutOfReachAndTheAuditSaysSo() {
        rule.setContent { contentFlow(2f) }

        // Same layout, twice the type: the text grows upward, taking the button with it, and the button
        // ends up in the top half. This is the case A8's "R1 must still hold at font scale 2.0" is about;
        // a fixture of fixed-size boxes cannot show it because nothing in it moves.
        val message = failure().message.orEmpty()
        assertTrue("R1 should be cited at 2x, got: $message", message.contains("R1"))
    }

    // A lambda property rather than a @Composable function: ktlint requires lowercase function names and
    // Compose lint requires uppercase composable ones, so a named composable cannot satisfy both gates.
    private val contentFlow: @Composable (Float) -> Unit = { fontScale ->
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .padding(start = 16.dp)
                        .size(width = 200.dp, height = 56.dp)
                        .primaryControl("scores.retry"),
                )
                Text(
                    text = (1..8).joinToString("\n") { "line $it of copy that wraps with the type scale" },
                    fontSize = 14.sp,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-420dpi")
    fun aPositionThatFailsOnATallPhonePassesOnAShortOne() {
        // 420 dp down: under 780's 390 dp ceiling, but inside 923's 461.5 dp one. The same absolute
        // position passing on one device and failing on the other is what proves the rule uses the
        // fraction and not a hard-coded number.
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset(x = 16.dp, y = 420.dp)
                        .size(width = 120.dp, height = 56.dp)
                        .primaryControl("scores.midPage"),
                )
            }
        }

        rule.assertPrimaryControlsReachable("scores.midPage")
    }

    @Test
    fun theSamePositionFailsOnATallPhone() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset(x = 16.dp, y = 420.dp)
                        .size(width = 120.dp, height = 56.dp)
                        .primaryControl("scores.midPage"),
                )
            }
        }

        val message = failure().message.orEmpty()
        assertTrue("R1 should be cited on the 923 dp window, got: $message", message.contains("R1"))
    }

    @Test
    fun anUntaggedSliderIsStillATargetForTheSizeRule() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Slider(
                    value = 0.5f,
                    onValueChange = {},
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .width(200.dp)
                            .height(40.dp),
                )
            }
        }

        // A slider carries SetProgress rather than OnClick, so a click-only collector would miss it
        // entirely and R4 would clear a 40 dp control.
        val message = failure().message.orEmpty()
        assertTrue("R4 should be cited for a non-click target, got: $message", message.contains("R4 target size"))
    }

    @Test
    fun aComposedTargetScrolledOutOfViewIsNotJudged() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        Modifier
                            .padding(start = 16.dp, top = 800.dp)
                            .size(width = 200.dp, height = 56.dp)
                            .primaryControl("scroll.visible"),
                    )
                    Spacer(Modifier.height(2000.dp))
                    Box(
                        Modifier
                            .padding(start = 16.dp)
                            .size(width = 200.dp, height = 56.dp)
                            .primaryControl("scroll.offscreen"),
                    )
                }
            }
        }

        // The second target is composed but 2000 dp below the window. Failing the audit for a control
        // nobody can see would make every scrollable screen unauditable.
        rule.assertPrimaryControlsReachable("scroll.visible", "scroll.offscreen")
    }

    @Test
    fun aTargetHangingOffTheLeftEdgeFailsContainment() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset(x = (-20).dp, y = 800.dp)
                        .size(width = 56.dp, height = 56.dp)
                        .primaryControl("scores.halfOff"),
                )
            }
        }

        // Only 36 dp of this 56 dp control is on screen, so it is a 36 dp target however large its layout
        // box is - and no size rule can see that from the raw extent.
        val message = failure("scores.halfOff").message.orEmpty()
        assertTrue("containment should be cited, got: $message", message.contains("R5 containment"))
        assertTrue("the overrun should be reported, got: $message", message.contains("past the left edge"))
        assertTrue(
            "the visible width should fail R4, got: $message",
            message.contains("x 56.0 dp, under the 48.0 dp minimum"),
        )
    }

    @Test
    fun droppingThePrimaryTagsIsCaughtEvenThoughTargetsRemain() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                        .size(width = 200.dp, height = 56.dp)
                        .clickable { },
                )
            }
        }

        // The failure this guards against: someone deletes primaryControl() from a screen, an untagged
        // button keeps the target count healthy, and R1, R2 and the edge rules quietly check nothing.
        // Counted in primaries, the audit says so.
        val message = failure("scores.action").message.orEmpty()
        assertTrue(
            "the guard must count primaries, not targets, got: $message",
            message.contains("primary controls are []") && message.contains("scores.action"),
        )
    }

    @Test
    fun aTargetWithNoVisibleAreaIsNeitherJudgedNorAGapNeighbour() {
        // Pure rectangles, no Compose: an edge-flush fixture at this density rounds to 923.05 dp and
        // trips containment by a hundredth, which would make the test about rounding instead of about
        // the filter. The rule takes placements as data, so it is tested as data.
        val visible =
            ControlPlacement(
                tag = "edge.visible",
                rawLeft = 16f,
                rawTop = 867f,
                rawRight = 216f,
                rawBottom = 923f,
                visibleLeft = 16f,
                visibleTop = 867f,
                visibleRight = 216f,
                visibleBottom = 923f,
                primary = true,
                path = "0",
            )
        // Composed at the window edge: zero visible area, and 56 dp past the bottom if it were judged.
        val belowWindow =
            ControlPlacement(
                tag = "edge.belowWindow",
                rawLeft = 16f,
                rawTop = 923f,
                rawRight = 136f,
                rawBottom = 979f,
                visibleLeft = 16f,
                visibleTop = 923f,
                visibleRight = 136f,
                visibleBottom = 923f,
                primary = true,
                path = "1",
            )

        val violations = findReachViolations(listOf(visible, belowWindow), windowWidthDp = 411f, windowHeightDp = 923f)
        assertTrue(
            "a zero-area target must not be judged or counted as a 0 dp neighbour, got: " +
                violations.map { it.describe() },
            violations.isEmpty(),
        )
    }

    @Test
    fun aTargetClippedByItsOwnAncestorIsJudgedOnWhatShowsThrough() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .padding(start = 16.dp, top = 800.dp)
                        .size(width = 200.dp, height = 30.dp)
                        .clipToBounds(),
                ) {
                    Box(
                        Modifier
                            .requiredSize(width = 200.dp, height = 56.dp)
                            .primaryControl("nested.inViewport"),
                    )
                }
            }
        }

        // The control's own layout box is 56 dp and sits inside the window, so a window-clipped
        // measurement clears it. Its parent only shows 30 dp of it, so a thumb only gets 30 dp.
        val message = failure("nested.inViewport").message.orEmpty()
        assertTrue("R4 should be cited for the clipped target, got: $message", message.contains("R4 target size"))
        assertTrue(
            "the visible height should be reported, got: $message",
            message.contains("x 30") && message.contains("under the 48.0 dp minimum"),
        )
    }

    @Test
    fun aDeclaredCustomGestureTargetIsCheckedForSizeWithoutBecomingPrimary() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                        .size(width = 32.dp, height = 32.dp)
                        .draggable(state = rememberDraggableState { }, orientation = Orientation.Vertical)
                        .reachTarget("drawer.knob"),
                )
            }
        }

        // draggable exposes neither OnClick nor SetProgress, so the audit cannot discover it; the
        // explicit reachTarget() is what makes it measurable. Being at the top of the screen must not
        // make it fail R1, because R1 is about primary controls and a drag knob is not one.
        val message = failure().message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue("the knob should be named, got: $message", message.contains("drawer.knob"))
        assertTrue("a declared target is not primary, got: $message", !message.contains("R1"))
    }

    @Test
    fun losingOnePrimaryTagIsCaughtEvenThoughAnotherRemains() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.refresh", frequent = true),
                    )
                    Spacer(Modifier.width(16.dp))
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .reachTarget("scores.filter"),
                    )
                }
            }
        }

        // Both actions are on screen and both are measured for size, but only one is primary. A count
        // guard is satisfied here; the declared set is not, and the missing action is outside R1/R2.
        val message = failure("scores.refresh", "scores.filter").message.orEmpty()
        assertTrue(
            "the missing primary tag should be named, got: $message",
            message.contains("primary controls are [scores.refresh]") &&
                message.contains("scores.filter"),
        )
    }

    @Test
    fun aTagWithTheWrongNameIsCaughtEvenWhenTheCountAgrees() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.refresh", frequent = true),
                    )
                    Spacer(Modifier.width(16.dp))
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.staleName", frequent = true),
                    )
                }
            }
        }

        // Same number of primaries, different identity: a count comparison is satisfied, so only an
        // exact match notices that the control the screen thinks it has is not the one it tagged.
        val message = failure("scores.refresh", "scores.filter").message.orEmpty()
        assertTrue(
            "the wrong name should be reported, got: $message",
            message.contains("scores.staleName") && message.contains("scores.filter"),
        )
    }

    @Test
    fun aRepeatedDeclarationIsRejected() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                // The upper chip is not tagged; the lower one claims the tag both of them share by name.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                        .size(width = 56.dp, height = 56.dp)
                        .clickable { },
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .size(width = 56.dp, height = 56.dp)
                        .primaryControl("scores.toggle", frequent = true),
                )
            }
        }

        // Declaration side: asking for the same tag twice would otherwise be silently deduplicated.
        val duplicated =
            assertThrows(AssertionError::class.java) {
                rule.assertPrimaryControlsReachable("scores.toggle", "scores.toggle")
            }
        assertTrue(
            "a repeated declaration must be rejected, got: ${duplicated.message}",
            duplicated.message.orEmpty().contains("must be unique"),
        )
    }

    @Test
    fun twoTaggedControlsSharingATagAreRejected() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.toggle", frequent = true),
                    )
                    Spacer(Modifier.width(16.dp))
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.toggle", frequent = true),
                    )
                }
            }
        }

        val duplicated =
            assertThrows(AssertionError::class.java) {
                rule.assertPrimaryControlsReachable("scores.toggle")
            }
        assertTrue(
            "two controls sharing a tag must be rejected, got: ${duplicated.message}",
            duplicated.message.orEmpty().contains("must be unique"),
        )
    }

    @Test
    fun aControlOfferingOnlySetTextIsATarget() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                // SetText without OnClick: what a text field can expose, and what a click-only collector
                // misses. BasicTextField happens to add OnClick on this Compose version, so the action set
                // is exercised directly rather than through a component that would mask the gap.
                Box(
                    Modifier
                        .padding(start = 16.dp, top = 16.dp)
                        .size(width = 40.dp, height = 40.dp)
                        .semantics { setText { true } },
                )
            }
        }

        val message = failure().message.orEmpty()
        assertTrue("R4 should be cited for a SetText control, got: $message", message.contains("R4 target size"))
    }
}
