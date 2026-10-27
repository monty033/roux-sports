package net.montycasa.roux.ui.reach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
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

    /**
     * A strip of six 56 dp chips in a 200 dp viewport leaves the last one half visible. It is 42 dp of target
     * at rest and 56 dp after one flick, so the size rule measures the chip, not the viewport - otherwise every
     * horizontally scrolling primary control is illegal and the strip has to be shrunk until the artefact
     * disappears.
     */
    @Test
    fun aChipClippedByItsStripIsMeasuredAtItsOwnSize() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                    Row(
                        Modifier
                            .width(200.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(6) { index ->
                            Box(
                                Modifier
                                    .size(width = 56.dp, height = 56.dp)
                                    .primaryControl("scores.chip.$index", frequent = true),
                            )
                        }
                    }
                }
            }
        }

        rule.assertPrimaryControlsReachable(
            "scores.chip.0",
            "scores.chip.1",
            "scores.chip.2",
            "scores.chip.3",
            "scores.chip.4",
            "scores.chip.5",
        )
    }

    /**
     * The exception above is scoped to scrolling ancestors. A control cut short by a parent that cannot scroll
     * is genuinely smaller than it looks in code, and that is the case the visible-geometry rule exists for.
     */
    @Test
    fun aTargetClippedByAFixedParentIsJudgedByWhatIsActuallyVisible() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .width(30.dp)
                        .clipToBounds(),
                ) {
                    Box(
                        Modifier
                            .requiredSize(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.truncated"),
                    )
                }
            }
        }

        val message = failure("scores.truncated").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue("the visible width should be reported, got: $message", message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")))
        assertTrue("a fixed parent must not be excused as a scroller, got: $message", !message.contains("laid out"))
    }

    /**
     * The scroller exception is per axis, and the axis is the point. A vertical list can never reveal a control
     * that is too narrow, so a fixed-width parent inside a scrolling list must still fail. A coarse "inside some
     * scroller" flag would have excused exactly this case.
     */
    @Test
    fun aTargetTooNarrowInsideAVerticalListIsStillAViolation() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(Modifier.width(30.dp).clipToBounds()) {
                        Box(
                            Modifier
                                .requiredSize(width = 56.dp, height = 56.dp)
                                .primaryControl("scores.squeezed"),
                        )
                    }
                }
            }
        }

        val message = failure("scores.squeezed").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue(
            "the visible width must be judged, got: $message",
            message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")),
        )
        assertTrue(
            "a vertical scroller must not excuse a horizontal cut, got: $message",
            message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")),
        )
    }

    /**
     * A horizontal strip excuses a chip it cuts at its own edge. It must not excuse a chip that a fixed parent
     * inside the strip squeezed: scrolling the strip moves that parent, and the chip stays 30 dp wide the whole
     * way. The scroller has to be the thing whose edge bounded the control, not merely an ancestor of it.
     */
    @Test
    fun aTargetSqueezedInsideAHorizontalStripIsStillAViolation() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .width(200.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    // Deliberately away from the strip's edges: flush against one, "the viewport cut it" would
                    // be true by accident and this test would pass whichever rule was in force.
                    Spacer(Modifier.width(40.dp))
                    Box(Modifier.width(30.dp).clipToBounds()) {
                        Box(
                            Modifier
                                .requiredSize(width = 56.dp, height = 56.dp)
                                .primaryControl("scores.squeezedInStrip"),
                        )
                    }
                    Spacer(Modifier.width(100.dp))
                }
            }
        }

        val message = failure("scores.squeezedInStrip").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue(
            "the visible width must be judged, got: $message",
            message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")),
        )
        assertTrue(
            "the strip must not excuse a squeeze it cannot scroll away, got: $message",
            message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")),
        )
    }

    /**
     * A viewport can be narrower than the minimum itself. However far it scrolls, 48 dp is never on screen at
     * once, so the target is unreachable by R4's definition no matter that it was laid out at 56.
     */
    @Test
    fun aTargetInAViewportNarrowerThanTheMinimumStillFails() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .width(30.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Box(
                        Modifier
                            .requiredSize(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.narrowViewport"),
                    )
                }
            }
        }

        val message = failure("scores.narrowViewport").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue("30 dp is all that can ever show, got: $message", message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")))
    }

    /**
     * A strip wide enough to show the whole chip still cannot undo a squeeze by a fixed parent inside it. This is
     * the case that a rule keyed on "is there a scroller above me" got wrong twice.
     */
    @Test
    fun aTargetSqueezedInsideAWideStripIsStillAViolation() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .width(300.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.width(20.dp))
                    Box(Modifier.width(30.dp).clipToBounds()) {
                        Box(
                            Modifier
                                .requiredSize(width = 56.dp, height = 56.dp)
                                .primaryControl("scores.squeezedInWideStrip"),
                        )
                    }
                    Spacer(Modifier.width(200.dp))
                }
            }
        }

        val message = failure("scores.squeezedInWideStrip").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue(
            "the 30 dp parent bounds it permanently, got: $message",
            message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")),
        )
    }

    /**
     * A wide parent that the target hangs out of the side of: the parent is 200 dp, the target is 56 dp, and only
     * 16 dp of it is inside. Comparing widths would call this healthy; intersecting the rectangles does not.
     */
    @Test
    fun aTargetHangingOutOfItsParentsSideIsJudgedOnWhatIsInside() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 100.dp, bottom = 16.dp)
                        .width(200.dp)
                        .clipToBounds(),
                ) {
                    Box(
                        Modifier
                            .offset(x = (-40).dp)
                            .requiredSize(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.hangingOut"),
                    )
                }
            }
        }

        val message = failure("scores.hangingOut").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
    }

    /**
     * Nested viewports: an inner 200 dp strip inside an outer 30 dp one. The fan can only ever see 30 dp of the
     * target, so the narrowest window is the one that decides.
     */
    @Test
    fun theNarrowestViewportInANestDecides() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .width(30.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Row(Modifier.width(200.dp).horizontalScroll(rememberScrollState())) {
                        Box(
                            Modifier
                                .requiredSize(width = 56.dp, height = 56.dp)
                                .primaryControl("scores.nestedViewports"),
                        )
                    }
                }
            }
        }

        val message = failure("scores.nestedViewports").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue("the outer 30 dp window is the limit, got: $message", message.matches(Regex("(?s).*is 30\\.\\d x 56\\.0 dp.*")))
    }

    /**
     * A scrollable modifier with nothing to scroll cannot reveal anything. An offset target inside it is cut by a
     * window that will never move, so it is judged on what shows through - the scroll range is what distinguishes
     * this from a strip with more content than fits.
     */
    @Test
    fun aScrollerWithNoScrollRangeCannotRevealAnOffsetTarget() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .width(100.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Box(
                        Modifier
                            .offset(x = 60.dp)
                            .requiredSize(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.offsetNoRange"),
                    )
                }
            }
        }

        val message = failure("scores.offsetNoRange").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue(
            "100 - 60 = 40 dp is what can show, got: $message",
            message.matches(Regex("(?s).*is 40\\.\\d x 56\\.0 dp.*")),
        )
    }

    /**
     * A scroller with real travel is still not a rescue if the travel goes the wrong way. This strip can scroll 356
     * dp, but its first child sits 40 dp to the left of the viewport, so every scroll position shows less of it, not
     * more: 16 dp is the best a fan can ever get. The viewport's width says nothing about that - its range and the
     * child's offset together do.
     */
    @Test
    fun aScrollerThatOnlyScrollsAwayFromATargetRevealsNothing() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 100.dp, bottom = 16.dp)
                        .width(100.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Box(
                        Modifier
                            .offset(x = (-40).dp)
                            .requiredSize(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.scrollsAway"),
                    )
                    Spacer(Modifier.width(400.dp))
                }
            }
        }

        val message = failure("scores.scrollsAway").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue(
            "only 16 dp can ever be inside the viewport, got: $message",
            message.matches(Regex("(?s).*is 16\\.0 x 56\\.0 dp.*")),
        )
    }

    /**
     * The other half of the same fact: a strip is allowed to hide a control it can bring back. This chip sits
     * outside its viewport and inside the window, and one flick puts all 56 dp on screen, so R4 must stay silent.
     * Without this, the rule could pass every "detects a bad layout" test while failing the layouts it exists to
     * permit.
     */
    @Test
    fun aChipAStripCanScrollIntoViewIsReachable() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 16.dp)
                        .width(100.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.width(150.dp))
                    Box(
                        Modifier
                            .requiredSize(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.offViewportChip"),
                    )
                    Spacer(Modifier.width(150.dp))
                }
            }
        }

        rule.assertPrimaryControlsReachable("scores.offViewportChip")
    }

    /**
     * Two constraints cutting in different places cannot be judged one at a time. This 200 dp control has 50 dp
     * inside its parent and 100 dp inside the viewport, so asking each separately says "50 dp, fine". Together, with
     * no travel to slide them against each other, only the 10 dp where the parent's window meets the viewport's edge
     * is ever on screen. The fix is order: intersect the non-scrolling ancestors first, then let scrolling move the
     * result.
     */
    @Test
    fun aStaticClipAndAViewportThatCutInDifferentPlacesAreCombined() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 16.dp)
                        .width(100.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    // The parent is the only child, so the strip has no travel at all: nothing can slide.
                    Box(Modifier.offset(x = 90.dp).width(50.dp).clipToBounds()) {
                        Box(
                            Modifier
                                .offset(x = (-100).dp)
                                .requiredSize(width = 200.dp, height = 56.dp)
                                .primaryControl("scores.twoCuts"),
                        )
                    }
                }
            }
        }

        val message = failure("scores.twoCuts").message.orEmpty()
        assertTrue("R4 should be cited, got: $message", message.contains("R4 target size"))
        assertTrue(
            "the two cuts leave only the 10 dp where they meet, got: $message",
            message.matches(Regex("(?s).*is 10\\.\\d x 56\\.0 dp.*")),
        )
    }

    /**
     * Semantics are read at whatever position the strip is currently in, so a strip that is already scrolled can be
     * dragged back. This chip shows 36 dp at its current position and all 56 dp twenty dp earlier, which the fan
     * reaches by dragging the other way - a legal offset of -20. Judging only non-negative offsets would call a
     * perfectly good chip a 36 dp target.
     */
    @Test
    fun aStripAlreadyScrolledCanBeDraggedBackToRevealAControl() {
        rule.setContent {
            val density = LocalDensity.current
            val scrolled = rememberScrollState(initial = with(density) { 60.dp.roundToPx() })
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 100.dp, bottom = 16.dp)
                        .width(100.dp)
                        .horizontalScroll(scrolled),
                ) {
                    Spacer(Modifier.width(40.dp))
                    Box(
                        Modifier
                            .requiredSize(width = 56.dp, height = 56.dp)
                            .primaryControl("scores.dragBack"),
                    )
                    Spacer(Modifier.width(300.dp))
                }
            }
        }

        rule.assertPrimaryControlsReachable("scores.dragBack")
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

        // The window is an ancestor that does not scroll, so intersecting its rectangle leaves 36 dp of this
        // 56 dp control reachable: R4 reports the target as too small and R5 reports where it was put. Both are
        // true statements about the same mistake, and the pair is more useful than either alone.
        val message = failure("scores.halfOff").message.orEmpty()
        assertTrue("containment should be cited, got: $message", message.contains("R5 containment"))
        assertTrue("the overrun should be reported, got: $message", message.contains("past the left edge"))
        assertTrue(
            "the reachable width should fail R4, got: $message",
            message.matches(Regex("(?s).*is 36\\.\\d x 56\\.0 dp.*")),
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
