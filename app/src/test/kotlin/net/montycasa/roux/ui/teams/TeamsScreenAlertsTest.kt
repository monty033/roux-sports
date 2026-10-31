@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.teams

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The alert sheet as the user meets it: opened from a row, showing the two switches, and honest about a
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
    fun theSheetShowsBothSwitchesAndThePromiseTheyMake() {
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row, start = true, goals = false)),
                onToggle = {},
                onRetry = {},
            )
        }

        compose.onNodeWithTag("teams.alerts.sheet").assertExists()
        compose.onNodeWithTag("teams.alerts.start").assertExists()
        compose.onNodeWithTag("teams.alerts.goals").assertExists()
        // How the alerts are produced is part of the offer, so it is stated in the sheet rather than a policy page.
        compose.onNodeWithTag("teams.alerts.disclosure").assertExists()
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
    fun aSwitchReportsWhichAlertWasTurnedOn() {
        var start: Boolean? = null
        var goals: Boolean? = null
        compose.setContent {
            TeamsScreen(
                state = state(TeamAlerts(row)),
                onToggle = {},
                onRetry = {},
                onSetStartAlert = { start = it },
                onSetGoalAlert = { goals = it },
            )
        }

        compose.onNodeWithTag("teams.alerts.start").performClick()
        compose.onNodeWithTag("teams.alerts.goals").performClick()

        assertEquals(true, start)
        assertEquals(true, goals)
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
        compose.onNodeWithTag("teams.alerts.start").assertIsNotEnabled()
        compose.onNodeWithTag("teams.alerts.blocked").assertExists()

        // The way out of the blocked state has to exist: a switch that cannot work and no route to fix it is a
        // dead end.
        compose.onNodeWithTag("teams.alerts.settings").assertExists()
    }

    @Test
    fun noSheetIsShownUntilATeamIsOpened() {
        compose.setContent {
            TeamsScreen(state = state(), onToggle = {}, onRetry = {})
        }

        compose.onNodeWithTag("teams.alerts.sheet").assertDoesNotExist()
    }
}
