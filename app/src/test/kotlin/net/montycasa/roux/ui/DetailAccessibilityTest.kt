package net.montycasa.roux.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import net.montycasa.roux.ui.player.PlayerDetailScreen
import net.montycasa.roux.ui.reach.ReachTargetTag
import net.montycasa.roux.ui.team.TeamDetailScreen
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the two newest pages say to TalkBack.
 *
 * The bottom sub-navigation is four independent choices: each cell owns its own name and selected state, and the row
 * that groups them is not one long announcement. A game-log line is one node that carries both the words and the tap,
 * so a screen reader lands on the sentence it can also activate rather than on silent text beside a hidden button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everyPlayerTabAnnouncesItsOwnNameAndSelectedState() {
        showPlayer()

        rule.onNodeWithTag("player.tab.Season").assertIsSelected().assertTextEquals("Season")
        rule.onNodeWithTag("player.tab.Career").assertIsNotSelected().assertTextEquals("Career")
        rule.onNodeWithTag("player.tab.GameLog").assertIsNotSelected().assertTextEquals("Game log")
        rule.onNodeWithTag("player.tab.Info").assertIsNotSelected().assertTextEquals("Info")
    }

    @Test
    fun thePlayerTabRowItselfDoesNotReadTheFourNamesAsOneBlob() {
        showPlayer()

        val group = rule.onNode(SemanticsMatcher.expectValue(ReachTargetTag, "player.tab")).fetchSemanticsNode()
        assertTrue(
            "the tab row announces ${group.config.getOrNull(SemanticsProperties.Text)}",
            group.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty(),
        )
        assertTrue(
            "the tab row carries a content description",
            group.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty(),
        )
        listOf(
            "player.tab.Season" to "Season",
            "player.tab.Career" to "Career",
            "player.tab.GameLog" to "Game log",
            "player.tab.Info" to "Info",
        ).forEach { (tag, name) -> rule.onNodeWithTag(tag).assertTextEquals(name) }
    }

    @Test
    fun everyClubTabAnnouncesItsOwnNameAndSelectedState() {
        showClub()

        rule.onNodeWithTag("team.tab.Schedule").assertIsSelected().assertTextEquals("Schedule")
        rule.onNodeWithTag("team.tab.Players").assertIsNotSelected().assertTextEquals("Players")
        rule.onNodeWithTag("team.tab.TeamStats").assertIsNotSelected().assertTextEquals("Team Stats")
    }

    @Test
    fun theClubTabRowItselfDoesNotReadTheThreeNamesAsOneBlob() {
        showClub()

        val group = rule.onNode(SemanticsMatcher.expectValue(ReachTargetTag, "team.tab")).fetchSemanticsNode()
        assertTrue(
            "the tab row announces the names: ${group.config.getOrNull(SemanticsProperties.Text)}",
            group.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty(),
        )
        assertTrue(
            "the tab row carries a content description",
            group.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty(),
        )
    }

    /**
     * A game-log line has no scoreline in the feed, so the result it speaks is the player's stat line. The date, the
     * opponent and that result are all on the one node that a reader can also tap.
     */
    @Test
    fun aGameLogRowIsOneNodeThatSpeaksTheDateOpponentAndStatResult() {
        showPlayer()
        selectGameLog()

        rule.onAllNodesWithTag(GAME_ROW, useUnmergedTree = true).assertCountEquals(1)
        val row = rule.onNodeWithTag(GAME_ROW).assertHasClickAction().assertIsDisplayed()
        val sentence =
            row
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString(", ") { it.text }
        assertEquals("Nov 1 · vs OTT, 1, 2, 3, +1, 4, 12:34", sentence)
    }

    @Test
    fun clickingTheGameLogRowOpensTheGameItNames() {
        var opened: Long? = null
        showPlayer(onGameClick = { opened = it })
        selectGameLog()

        rule.onNodeWithTag(GAME_ROW).performClick()

        assertEquals(DetailPageFixtures.NEWEST_GAME_ID, opened)
    }

    private fun showPlayer(onGameClick: (Long) -> Unit = {}) {
        rule.setContent {
            RouxTheme {
                PlayerDetailScreen(
                    state = DetailPageFixtures.playerState,
                    currentSeason = DetailPageFixtures.CURRENT_SEASON,
                    today = DetailPageFixtures.TODAY,
                    onBack = {},
                    onRetry = {},
                    onGameClick = onGameClick,
                )
            }
        }
    }

    private fun showClub() {
        rule.setContent {
            RouxTheme {
                TeamDetailScreen(
                    state = DetailPageFixtures.teamState,
                    onBack = {},
                    now = DetailPageFixtures.NOW,
                )
            }
        }
    }

    private fun selectGameLog() {
        rule.onNodeWithTag("player.tab.GameLog").performClick()
        rule.waitForIdle()
    }

    private companion object {
        const val GAME_ROW = "player.gamelog.row.0"
    }
}
