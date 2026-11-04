package net.montycasa.roux.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.ui.drawer.LeaguesPanel
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allSixResolutions() {
        val expected =
            mapOf(
                ThemeMode.System to listOf(false, true),
                ThemeMode.Light to listOf(false, false),
                ThemeMode.Dark to listOf(true, true),
            )
        expected.forEach { (mode, results) ->
            listOf(false, true).forEachIndexed {
                index,
                system,
                ->
                assertEquals("$mode / $system", results[index], resolveDark(mode, system))
            }
        }
    }

    @Test fun storedSelectionRowsAndCallbacks() {
        var reported: ThemeMode? = null
        val selected = androidx.compose.runtime.mutableStateOf(ThemeMode.Dark)
        rule.setContent { RouxTheme { SettingsScreen(selected.value, { reported = it }, {}) } }
        ThemeMode.entries.forEach { stored ->
            rule.runOnIdle { selected.value = stored }
            ThemeMode.entries.forEach { mode ->
                val row = rule.onNodeWithText(mode.name)
                row.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                if (mode == stored) row.assertIsSelected() else row.assertIsNotSelected()
                row.performClick()
                rule.runOnIdle { assertEquals(mode, reported) }
            }
        }
    }

    @Test fun eachSettingKeepsItsOptionsOnTheSameLineAsItsName() {
        // The whole point of the row: the answer sits where the question is, not three rows below it.
        rule.setContent { RouxTheme { SettingsScreen(ThemeMode.System, {}, {}) } }

        val label = rule.onNodeWithText("Theme").fetchSemanticsNode().boundsInRoot
        ThemeMode.entries.forEach { mode ->
            val segment = rule.onNodeWithTag("settings.${mode.name}").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$mode: the segment shares the name's line (label ${label.top}..${label.bottom}, " +
                    "segment ${segment.top}..${segment.bottom})",
                segment.top < label.bottom && segment.bottom > label.top,
            )
            assertTrue("$mode: the segments sit to the right of the name", segment.left >= label.right - 1f)
        }
    }

    @Test fun drawerSettingsIsLabelledAndReachable() {
        var opened = false
        rule.setContent { RouxTheme { LeaguesPanel(DrawerUi(), false, {}, {}, {}, {}, onSettings = { opened = true }) } }
        rule
            .onNodeWithContentDescription("Settings")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        rule.runOnIdle { assertTrue(opened) }
    }
}
