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
import net.montycasa.roux.domain.repo.UpcomingLead
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

    @Test fun settingsShowsThirtyMinutesSelectedByDefault() {
        rule.setContent { RouxTheme { SettingsScreen(ThemeMode.System, {}, {}) } }

        rule.onNodeWithText("How early the upcoming-game alert arrives.").performScrollTo().assertIsDisplayed()
        UpcomingLead.allowed.forEach { minutes ->
            rule
                .onNodeWithTag("settings.lead.$minutes")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithTag("settings.lead.30").assertIsSelected()
        rule.onNodeWithTag("settings.lead.15").assertIsNotSelected()
        rule.onNodeWithTag("settings.lead.60").assertIsNotSelected()
    }

    @Test fun choosingSixtyMinutesStoresItAndMarksThatRowSelected() {
        var reported: Int? = null
        val lead = androidx.compose.runtime.mutableStateOf(UpcomingLead.DEFAULT)
        rule.setContent {
            RouxTheme {
                SettingsScreen(
                    mode = ThemeMode.System,
                    onMode = {},
                    onBack = {},
                    leadMinutes = lead.value,
                    onLeadMinutes = {
                        reported = it
                        // The app writes and then reads the flow back; this is the same round trip without a store.
                        lead.value = it
                    },
                )
            }
        }

        rule.onNodeWithTag("settings.lead.60").performScrollTo().performClick()

        rule.runOnIdle { assertEquals(60, reported) }
        rule.onNodeWithTag("settings.lead.60").assertIsSelected()
        rule.onNodeWithTag("settings.lead.30").assertIsNotSelected()
    }

    @Test fun choosingAnOutOfSetLeadIsImpossibleThroughTheUi() {
        rule.setContent { RouxTheme { SettingsScreen(ThemeMode.System, {}, {}, leadMinutes = 45) } }

        // There are three choices and no free-number field; the store's coercion to 30 is the only place an
        // illegal value is handled, and the screen cannot produce one.
        rule.onNodeWithTag("settings.lead.15").assertExists()
        rule.onNodeWithTag("settings.lead.30").assertExists()
        rule.onNodeWithTag("settings.lead.60").assertExists()
        rule.onNodeWithTag("settings.lead.45").assertDoesNotExist()
        rule.onNodeWithText("45 minutes").assertDoesNotExist()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
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
