@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.teams

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
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
                canNotify = false,
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
