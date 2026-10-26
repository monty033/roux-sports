package net.montycasa.roux.ui.reach

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
}
