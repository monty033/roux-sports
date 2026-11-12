package net.montycasa.roux.ui.a11y

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import net.montycasa.roux.MainActivity
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.scores.waitForFixtureScores
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a screen reader needs in order to be useful, checked where it can be checked without a screen reader.
 *
 * TalkBack was never run against this app: no device pass turned it on, so nothing here claims a phrase sounds
 * right or that the reading order is pleasant. What it does claim is the failure the checklist names — "an icon
 * button that reads as 'button' is a defect" — which is a property of the semantics tree, not of a voice: a control
 * a person can activate either carries words of its own or has descendants that do, and a game row is one unit
 * rather than a pile of fragments.
 *
 * Both halves are ordinary assertions over the merged semantics tree, so a control that loses its description fails
 * here instead of on a phone nobody tests with TalkBack installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
class SpokenControlsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** A control with no words anywhere inside it: no text, no description, nothing for a reader to say. */
    private val silentControl =
        SemanticsMatcher("a control with neither text nor description") { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            val described = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            text.all { it.text.isBlank() } && described.all { it.isBlank() }
        }

    /**
     * Every activatable node in the merged tree is named. Merging is what makes this fair: a row whose children say
     * "Toronto", "4" and "Final" is a row that speaks, and only a control with nothing to say at any depth fails.
     */
    private fun assertEveryControlSpeaks(where: String) {
        val silent = composeRule.onAllNodes(hasClickAction() and silentControl).fetchSemanticsNodes()
        val described = silent.map { it.config.getOrNull(SemanticsProperties.TestTag) ?: it.id.toString() }
        assertTrue(
            "$where: ${silent.size} control(s) can be activated but say nothing: $described",
            silent.isEmpty(),
        )
    }

    @Test
    fun theScoresScreenNamesEveryControl() {
        composeRule.waitForFixtureScores()
        assertEveryControlSpeaks("scores")

        composeRule.onNode(hasText("Teams") and isSelectable()).performClick()
        composeRule.waitForIdle()
        assertEveryControlSpeaks("teams")
    }

    /**
     * The row is the thing a listener meets first: if it reads as five separate fragments the score belongs to no
     * club. Its description is one sentence, so this pins the facts that sentence has to contain rather than its
     * wording, which is the design's business.
     */
    @Test
    fun aGameRowReadsAsOneUnit() {
        composeRule.waitForFixtureScores()

        val rows = composeRule.onAllNodes(hasClickAction() and hasAnyContentDescription()).fetchSemanticsNodes()
        assertTrue("expected at least one spoken game row", rows.isNotEmpty())

        val spoken = rows.mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") }
        assertTrue("no row had a description", spoken.isNotEmpty())

        val first = spoken.first()
        // The row speaks in the design's own words ("Ducks 5, Flyers 4, final"), not in abbreviations, so the club
        // check counts the words that are neither the state nor the numbers: two clubs have to be named.
        val words =
            Regex("[A-Za-z]{3,}")
                .findAll(first)
                .map { it.value.lowercase() }
                .filterNot { it in setOf("final", "live", "scheduled", "pre", "game") }
                .distinct()
                .toList()
        assertTrue("a game row must name both clubs: $first (found $words)", words.size >= 2)
        assertTrue("a game row must carry a score: $first", Regex("""\d""").containsMatchIn(first))
        assertTrue(
            "a game row must say what state the game is in: $first",
            listOf("Final", "Live", "Scheduled", "Pre").any { first.contains(it, ignoreCase = true) },
        )
        assertEveryControlSpeaks("scores rows")
    }

    private fun hasAnyContentDescription(): SemanticsMatcher =
        SemanticsMatcher("has a description") { node ->
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .any { it.isNotBlank() }
        }
}
