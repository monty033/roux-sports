package net.montycasa.roux.ui.reach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    private fun failure(): AssertionError = assertThrows(AssertionError::class.java) { rule.assertPrimaryControlsReachable() }

    /** The audit must have seen the control, or a silent pass means nothing. */
    private fun assertSaw(vararg tags: String) {
        assertEquals(tags.toList(), rule.primaryControlTags())
    }

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

        assertSaw("scores.title")
        val message = failure().message.orEmpty()
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

        assertSaw("scores.refresh")
        rule.assertPrimaryControlsReachable()
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

        assertSaw("scores.dateStrip")
        val message = failure().message.orEmpty()
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

        assertSaw("scores.narrowRow")
        val message = failure().message.orEmpty()
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

        assertSaw("scores.shortRow")
        val message = failure().message.orEmpty()
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

        assertSaw("scores.edgeChip")
        val message = failure().message.orEmpty()
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

        assertSaw("strip.first", "strip.second")
        val message = failure().message.orEmpty()
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
        // tagged is exactly how a 40 dp tap target ships.
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

        val message = failure().message.orEmpty()
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
        val message = failure().message.orEmpty()
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
            assertThrows(AssertionError::class.java) { rule.assertPrimaryControlsReachable() }
                .message
                .orEmpty()
        assertTrue("an empty audit must not read as reachable, got: $message", message.contains("found 0 target"))
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
        val message = failure().message.orEmpty()
        assertTrue("containment should be cited, got: $message", message.contains("R5 containment"))
        // The exact dp depends on pixel rounding at 2.625 density, so assert the shape of the report.
        assertTrue("the overrun should be reported, got: $message", message.contains("dp past the bottom"))
    }

    @Test
    fun aCompliantLayoutPassesAtFontScaleTwo() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        Box(
                            Modifier
                                .size(width = 120.dp, height = 56.dp)
                                .primaryControl("scores.dateStrip", frequent = true),
                        )
                        Spacer(Modifier.width(16.dp))
                        Box(
                            Modifier
                                .size(width = 120.dp, height = 56.dp)
                                .primaryControl("scores.toggle", frequent = true),
                        )
                    }
                }
            }
        }

        // A8 requires R1 and R2 to hold at font scale 2.0. Doubling type is what pushes a compliant
        // layout back up the screen, so the check has to be run at 2.0 rather than assumed.
        assertSaw("scores.dateStrip", "scores.toggle")
        rule.assertPrimaryControlsReachable()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-420dpi")
    fun aCompliantLayoutPassesOnASmallPhone() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(width = 200.dp, height = 56.dp)
                        .primaryControl("scores.refresh", frequent = true),
                )
            }
        }

        // 780 dp tall, so the R1 ceiling moves to 390 dp and R2's to 546 dp - the fractions, not the
        // Pixel 9's numbers, are what the audit uses.
        assertSaw("scores.refresh")
        rule.assertPrimaryControlsReachable()
    }
}
