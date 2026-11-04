@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.teams

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The alert sheet as the user meets it: opened from a row, showing the five switches, and honest about a
 * notification setting that would stop them working.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TeamsScreenAlertsTest {
    @get:Rule
    val compose = createComposeRule()

    private val row =
        net.montycasa.roux.domain.model.TeamRef(
            net.montycasa.roux.domain.model
                .TeamKey("TOR"),
            name = "Toronto Maple Leafs",
        )

    private val switchTags =
        listOf(
            "teams.alerts.upcoming",
            "teams.alerts.start",
            "teams.alerts.goals",
            "teams.alerts.intermission",
            "teams.alerts.final",
        )

    private fun state(alerts: TeamAlerts? = null) =
        TeamsUiState(
            rows = listOf(row),
            favourites = setOf(row.key),
            isRefreshing = false,
            alerts = alerts,
        )

    @Test
    fun tappingATeamRowAsksForThatTeamsAlerts() {
        var opened: net.montycasa.roux.domain.model.TeamRef? = null
        compose.setContent {
            TeamsScreen(state = state(), onToggle = {}, onRetry = {}, onOpenAlerts = { opened = it })
        }

        compose.onNodeWithTag("teams.alerts.open.TOR").performClick()

        assertEquals(row, opened)
    }

    @Test
    fun tappingASecondRowsBellOpensThatTeamsSheet() {
        val other =
            net.montycasa.roux.domain.model.TeamRef(
                net.montycasa.roux.domain.model
                    .TeamKey("MTL"),
                name = "Montréal Canadiens",
            )
        var opened: net.montycasa.roux.domain.model.TeamRef? = null
        compose.setContent {
            TeamsScreen(
                state = state().copy(rows = listOf(row, other)),
                onToggle = {},
                onRetry = {},
                onOpenAlerts = { opened = it },
            )
        }

        compose.onNodeWithTag("teams.alerts.open.MTL").performClick()

        assertEquals("the bell belongs to its own row", other, opened)
    }

    @Test
    fun allFiveSwitchesRenderWithTheirLabelsInTheirGroups() {
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row, start = true, goals = false)),
                onToggle = {},
                onRetry = {},
            )
        }

        compose.onNodeWithTag("teams.alerts.sheet").assertExists()
        switchTags.forEach { tag ->
            compose.onNodeWithTag(tag).assertExists().assertHeightIsAtLeast(48.dp)
        }
        listOf("Upcoming", "Game start", "Goals", "Intermissions", "Final score").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
        // The headings are labels, not a sixth switch: they carry no toggle action.
        listOf("Before the game", "During the game", "After the game").forEach { heading ->
            compose.onNodeWithText(heading).assertExists()
        }
        compose.onNodeWithTag("teams.alerts.group.before").assertExists()
        compose.onNodeWithTag("teams.alerts.group.during").assertExists()
        compose.onNodeWithTag("teams.alerts.group.after").assertExists()
        // How the alerts are produced is part of the offer, so it is stated in the sheet rather than a policy page.
        compose.onNodeWithTag("teams.alerts.disclosure").assertExists()
        compose.onNodeWithTag("teams.alerts.done").assertExists()
    }

    @Test
    fun aMissingPermissionAsksInsteadOfShowingDeadSwitches() {
        // The bug this fixes: the sheet greyed its switches out whenever the permission was missing, so the one
        // screen that explains the alerts was also the one place the permission could never be asked for.
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row)),
                onToggle = {},
                onRetry = {},
                permissionGranted = false,
                notificationsEnabled = true,
            )
        }

        compose.onNodeWithTag("teams.alerts.prompt.title").assertExists()
        compose.onNodeWithTag("teams.alerts.start").assertDoesNotExist()
    }

    @Test
    fun openingAlertsWithNotificationsOffAsksBeforeShowingDeadSwitches() {
        var enabled = false
        var dismissed = false
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row)),
                onToggle = {},
                onRetry = {},
                permissionGranted = false,
                notificationsEnabled = false,
                onEnableNotifications = { enabled = true },
                onDismissPrompt = { dismissed = true },
            )
        }

        // Asked inside the sheet it belongs to, not in a dialog stacked on top of it.
        compose.onNodeWithTag("teams.alerts.prompt.title").assertExists()
        compose.onNodeWithTag("teams.alerts.prompt.enable").performClick()
        assertTrue("the prompt's action is wired", enabled)

        compose.onNodeWithTag("teams.alerts.prompt.later").performClick()
        assertTrue("and it can be declined", dismissed)
    }

    @Test
    fun thePromptStaysAwayOnceDismissed() {
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row)),
                onToggle = {},
                onRetry = {},
                permissionGranted = false,
                notificationsEnabled = false,
                promptDismissed = true,
            )
        }

        compose.onNodeWithTag("teams.alerts.prompt.enable").assertDoesNotExist()
        // Dismissing the question does not close the sheet: the settings are still there to look at.
        compose.onNodeWithTag("teams.alerts.sheet").assertExists()
    }

    @Test
    fun aTeamRowShowsItsCrestAndClubName() {
        compose.setContent {
            TeamsScreen(state = state(), onToggle = {}, onRetry = {})
        }

        compose.onNodeWithTag("teams.row.TOR").assertExists()
        compose.onNodeWithTag("teams.alerts.open.TOR").assertExists()
    }

    @Test
    fun everySwitchReportsWhichAlertWasTurnedOn() {
        val reported = mutableListOf<Pair<AlertKind, Boolean>>()
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row)),
                onToggle = {},
                onRetry = {},
                onSetAlert = { kind, on -> reported += kind to on },
            )
        }

        switchTags.forEach { tag -> compose.onNodeWithTag(tag).performScrollTo().performClick() }

        assertEquals(
            listOf(
                AlertKind.Upcoming to true,
                AlertKind.Start to true,
                AlertKind.Goals to true,
                AlertKind.Intermission to true,
                AlertKind.Final to true,
            ),
            reported,
        )
    }

    @Test
    fun turningOnANewSwitchWithPermissionMissingStillAsksThroughTheOnePath() {
        // The permission may be granted after the sheet's question has been declined, so the switches stay live:
        // tapping any of them, including the three new ones, must reach the same action that asks for it.
        val reported = mutableListOf<Pair<AlertKind, Boolean>>()
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row)),
                onToggle = {},
                onRetry = {},
                permissionGranted = false,
                notificationsEnabled = true,
                promptDismissed = true,
                onSetAlert = { kind, on -> reported += kind to on },
            )
        }

        compose.onNodeWithTag("teams.alerts.intermission").performScrollTo().performClick()
        compose.onNodeWithTag("teams.alerts.final").performScrollTo().performClick()
        compose.onNodeWithTag("teams.alerts.upcoming").performScrollTo().performClick()

        assertEquals(
            listOf(
                AlertKind.Intermission to true,
                AlertKind.Final to true,
                AlertKind.Upcoming to true,
            ),
            reported,
        )
    }

    @Test
    fun switchesAreDeadAndExplainedWhenNotificationsCannotArrive() {
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row)),
                onToggle = {},
                onRetry = {},
                permissionGranted = true,
                notificationsEnabled = false,
                promptDismissed = true,
                onOpenNotificationSettings = {},
            )
        }

        // A switch that looks alive but stores an alert nothing can deliver is the failure this avoids.
        switchTags.forEach { tag -> compose.onNodeWithTag(tag).assertIsNotEnabled() }
        compose.onNodeWithTag("teams.alerts.blocked").assertExists()

        // The way out of the blocked state has to exist: a switch that cannot work and no route to fix it is a
        // dead end.
        compose.onNodeWithTag("teams.alerts.settings").assertExists()
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w360dp-h780dp")
    fun atDoubleFontOnA360WidePhoneEverySwitchIsReachableAndDoneStaysOnScreen() {
        compose.setContent {
            RouxTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TeamsScreen(
                        state =
                            state(
                                TeamAlerts(
                                    row,
                                    start = true,
                                    goals = true,
                                    intermission = true,
                                    final = true,
                                    upcoming = true,
                                ),
                            ),
                        onToggle = {},
                        onRetry = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("teams.alerts.done").assertIsDisplayed()
        switchTags.forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            // The list moved; the exit did not, so it is still there to press after the last row.
            compose.onNodeWithTag("teams.alerts.done").assertIsDisplayed()
        }
    }

    @Test
    fun aSwitchCanBeToggledFromItsLabelAsWellAsItsSwitch() {
        val reported = mutableListOf<Pair<AlertKind, Boolean>>()
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row, start = true)),
                onToggle = {},
                onRetry = {},
                onSetAlert = { kind, on -> reported += kind to on },
            )
        }

        compose.onNodeWithText("Game start").performScrollTo().performClick()

        assertEquals(listOf(AlertKind.Start to false), reported)
    }

    @Test
    fun noSheetIsShownUntilATeamIsOpened() {
        compose.setContent {
            TeamsScreen(state = state(), onToggle = {}, onRetry = {})
        }

        compose.onNodeWithTag("teams.alerts.sheet").assertDoesNotExist()
    }
}
