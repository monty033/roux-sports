package net.montycasa.roux.ui.a11y

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.MainActivity
import net.montycasa.roux.ui.scores.ScoreFixture
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import net.montycasa.roux.ui.scores.waitForFixtureScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a screen reader needs in order to be useful, checked where a test can see it.
 *
 * TalkBack was never run against this app and nobody here can run it, so nothing in this file claims a phrase
 * sounds right or that the reading order is pleasant. What it claims is narrower and checkable: every control
 * Compose exposes with a click action on the scores and teams screens carries some words, and every game row the
 * lazy list has composed exposes one description naming its own clubs, each club's goals and the state.
 *
 * It does not claim a row is read as one utterance. The row's description sits on the card while the rail's own
 * nodes stay separate, which is deliberate - the layout and reach audits measure them - so a reader may still meet
 * those nodes individually.
 *
 * Both are properties of the semantics tree, and both are bounded by what that tree shows. A control with no click
 * action is not inspected. A control that speaks through some other semantics property than text or description
 * would be reported as silent. "Says something" means a non-blank string, not a good one. Only scores and teams are
 * walked; standings, game detail, player detail and settings are covered by their own tests, not by this one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
class SpokenControlsTest {
    private companion object {
        const val ROW_TAG_PREFIX = "scores.game."
    }

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
        assertTrue(
            "$where: ${silent.size} control(s) can be activated but say nothing: " +
                silent.joinToString { it.describe() },
            silent.isEmpty(),
        )
    }

    /** Enough detail to find the control: its test tag when it has one, its role when it has one, and its node id. */
    private fun SemanticsNode.describe(): String {
        val tag = config.getOrNull(SemanticsProperties.TestTag)
        val role = config.getOrNull(SemanticsProperties.Role)
        return listOfNotNull(tag?.let { "tag=$it" }, role?.let { "role=$it" }, "node=$id").joinToString(" ")
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.objectAt(key: String): JsonObject = getValue(key).jsonObject

    @Test
    fun theScoresAndTeamsScreensNameEveryControl() {
        composeRule.waitForFixtureScores()
        assertEveryControlSpeaks("scores")

        composeRule.onNode(hasText("Teams") and isSelectable()).performClick()
        composeRule.waitForIdle()
        assertEveryControlSpeaks("teams")
    }

    /**
     * Each row's sentence is checked against that game's own fixture: the clubs it names, the goals each club scored,
     * and the state it is in. A sentence that loses a club or a goal fails here, and so does a row that exposes
     * several descriptions rather than one, because the point of the sentence is that a listener hears one
     * statement.
     *
     * Only composed rows are inspected: the list is lazy, so games scrolled out of view are not in the tree. The
     * captured slate is larger than one screen, so this checks the rows that are on it.
     */
    @Test
    fun everyComposedGameRowDescribesItsOwnGame() {
        composeRule.waitForFixtureScores()

        val stateWords =
            mapOf(
                "OFF" to "final",
                "LIVE" to "live",
                "FUT" to "scheduled",
                "PRE" to "scheduled",
            )

        // Which rows the list composed, taken from the tree rather than assumed: a row the fixture has but the list
        // has not composed is skipped, and a row composed twice is a defect in its own right.
        val composed =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("a scores row") { node ->
                        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ROW_TAG_PREFIX) == true
                    },
                ).fetchSemanticsNodes()
                .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag)?.removePrefix(ROW_TAG_PREFIX) }
        assertEquals("a row must not be composed twice: $composed", composed.distinct().size, composed.size)
        assertTrue("the list composed no rows", composed.isNotEmpty())

        val byId = ScoreFixture.games.associateBy { it.text("id") }
        for (id in composed) {
            val game = requireNotNull(byId[id]) { "composed a row for a game the fixture does not contain: $id" }
            val node = composeRule.onAllNodesWithTag("$ROW_TAG_PREFIX$id").fetchSemanticsNodes().single()

            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertEquals("row $id must expose exactly one description, found $descriptions", 1, descriptions.size)
            val sentence = descriptions.single()

            val away = game.objectAt("awayTeam")
            val home = game.objectAt("homeTeam")
            for ((side, team) in listOf("away" to away, "home" to home)) {
                val name = team.objectAt("name").text("default")
                val goals = team.text("score")
                // The goals have to sit with the club that scored them, not merely appear in the sentence.
                val bound = Regex("""${Regex.escape(name)}\W{0,4}${Regex.escape(goals)}""", RegexOption.IGNORE_CASE)
                assertTrue("row $id must say $side $name scored $goals: $sentence", bound.containsMatchIn(sentence))
            }

            val state = game.text("gameState")
            val word = stateWords[state] ?: state.lowercase()
            assertTrue(
                "row $id must say its state ($state): $sentence",
                Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(sentence),
            )
        }

        assertEveryControlSpeaks("scores rows")
    }
}
