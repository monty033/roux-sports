package net.montycasa.roux.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P6: one golden per (screen state, theme) for every state in [AuditStates]. The state list is shared with
 * ContrastAndTypeTest, so a screen added to the audit is photographed here without editing this file.
 *
 * Goldens land in app/src/test/screenshots/matrix/<state>-<theme>.png. Changing a golden is a claim that the UI
 * changed; read the diff image before re-recording. See README "Screenshot baselines".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotMatrixTest {
    @get:Rule
    val rule = createComposeRule()

    /** Long enough for the tab selection animation to reach rest before a state is photographed. */
    private val animationSettleMillis = 1_000L

    /** States whose page is longer than the viewport, so the last card needs its own image. */
    private val scrolledStates = listOf("detail.full", "detail.live", "detail.scheduled", "detail.pregame")

    /**
     * States with pre-game cards between the hero and Game info. Season stats and Season series sit between the top and
     * the info image, so neither shows them: each gets its own capture.
     */
    private val preGameStates = listOf("detail.pregame")

    /** States with a shots card: it sits between two cards, so neither the top nor the info image can show it. */
    private val shotsStates = listOf("detail.full", "detail.live")

    /** States with a scoring summary: it too sits between two cards, so it needs its own capture. */
    private val scoringStates = listOf("detail.full", "detail.live")

    /** States with a penalties summary: the same between-cards problem, directly below the scoring summary. */
    private val penaltyStates = listOf("detail.full", "detail.live")

    /**
     * The states the matrix is contracted to photograph. A state list that silently shrinks is the failure mode this
     * audit exists to prevent, so the set and its size are pinned rather than derived from the list itself.
     */
    private val expectedStates =
        setOf(
            "settings",
            "scores.data",
            "scores.empty",
            "scores.stale",
            "scores.offseason",
            "scores.offseason.opener",
            "standings.eastPlayoffs",
            "standings.eastDivision",
            "standings.westPlayoffs",
            "standings.league",
            "teams.list",
            "teams.favourites",
            "teams.alerts",
            "teams.alerts.off",
            "detail.full",
            "detail.live",
            "detail.scheduled",
            "detail.pregame",
            "detail.partial",
            "detail.playoff",
            "player.season",
            "player.gamelog.fallback",
            "team.schedule",
            "team.preseason",
            "drawer.leagues",
        )

    @Test
    fun everyAuditStateInBothThemes() {
        val screens = AuditStates.screens()
        assertEquals("audited state count", expectedStates.size, screens.size)
        assertEquals("audited states", expectedStates, screens.map { it.name }.toSet())
        val index = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                screens[index.intValue].content()
            }
        }
        var captured = 0
        val failures = mutableListOf<AssertionError>()
        screens.forEachIndexed { i, screen ->
            listOf(false, true).forEach { isDark ->
                val theme = if (isDark) "dark" else "light"
                rule.runOnIdle {
                    index.intValue = i
                    dark.value = isDark
                }
                rule.waitForIdle()
                // A state whose content sits behind a tab is photographed with that tab open, as the audit states say.
                screen.tab?.let { tab ->
                    // The click action, not a touch: a touch starts a ripple on the tab, and its frames run on the wall
                    // clock, so the photograph would differ between runs. The page's own scroll still needs its time.
                    rule.onNodeWithTag(tab).performSemanticsAction(SemanticsActions.OnClick)
                    rule.mainClock.advanceTimeBy(animationSettleMillis)
                    rule.waitForIdle()
                }
                val file = screen.name.replace('.', '-') + "-" + theme
                // Always start from the top. The scrolled captures below leave the list where they finished, so without
                // this the dark pass photographed the light pass's scroll position and the "unscrolled" image was not.
                // Anchored on the hero rather than "the first scrollable": on the scores and standings pages that is the
                // date shelf, and scrolling it would silently change those goldens too.
                if (screen.name.startsWith("detail.")) {
                    runCatching {
                        rule.onNodeWithTag("detail.hero").performScrollTo()
                        rule.waitForIdle()
                    }
                }
                // Settings and the alert sheet both scroll inside a fixed surface. Start them from the top so
                // the next state's "unscrolled" image is not the previous state's scroll position.
                if (screen.name.startsWith("settings")) {
                    runCatching {
                        rule.onNodeWithText("Settings").performScrollTo()
                        rule.waitForIdle()
                    }
                }
                if (screen.name.startsWith("teams.alerts")) {
                    runCatching {
                        rule.onNodeWithTag("teams.alerts.upcoming").performScrollTo()
                        rule.waitForIdle()
                    }
                }
                // The card at the top of the page is the one place a state is distinguishable - the hero carries the
                // live capsule, the clock and the score - so it is always captured unscrolled. Scrolling before the
                // capture made detail.full and detail.live produce the same picture, which audited nothing.
                try {
                    rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file.png")
                } catch (failure: AssertionError) {
                    failures += failure
                }
                captured++
                // The sheet scrolls too: the bottom image is where the last two switches and the pinned Done
                // are compared with the top image's first three.
                if (screen.name.startsWith("teams.alerts")) {
                    rule.onNodeWithTag("teams.alerts.final").performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-bottom.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                // A card between two others is in neither image: scrolling to it is the only way the matrix sees it.
                if (screen.name in shotsStates) {
                    rule.onNodeWithTag("detail.section.shots").performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-shots.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                if (screen.name in preGameStates) {
                    for ((suffix, anchor) in listOf("stats" to "detail.section.season-stats", "series" to "detail.section.season-series")) {
                        rule.onNodeWithTag(anchor).performScrollTo()
                        rule.waitForIdle()
                        try {
                            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-$suffix.png")
                        } catch (failure: AssertionError) {
                            failures += failure
                        }
                    }
                }
                if (screen.name in scoringStates) {
                    // The page-level scoring image cannot show the whole card — it is taller than the viewport and
                    // Compose only scrolls an anchor just into view — so it anchors on the card and shows rows. The
                    // complete card, heading included, is recorded by ScoringCardScreenshotTest.
                    val anchor = "detail.section.scoring-summary"
                    rule.onNodeWithTag(anchor).performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-scoring.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                if (screen.name in penaltyStates) {
                    // Like the scoring card, the penalties card is taller than the viewport; the page-level capture
                    // anchors on it and shows rows, while PenaltiesCardScreenshotTest records the whole card.
                    val anchor = "detail.section.penalties-summary"
                    rule.onNodeWithTag(anchor).performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-penalties.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
                // The bottom of a long page needs its own image, or the last card ships unseen.
                if (screen.name in scrolledStates) {
                    rule.onNodeWithTag("detail.section.info").performScrollTo()
                    rule.waitForIdle()
                    try {
                        rule.onRoot().captureRoboImage("src/test/screenshots/matrix/$file-info.png")
                    } catch (failure: AssertionError) {
                        failures += failure
                    }
                }
            }
        }
        assertEquals("one golden per (state, theme)", screens.size * 2, captured)
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} screenshot mismatches", failures.first())
    }
}
