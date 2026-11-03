package net.montycasa.roux.domain.repo

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A team nobody has configured must not alert. Both controls are opt-in, so a fresh value starting on would send a
 * notification the user never asked for simply because they had not visited the sheet yet.
 */
class TeamAlertPreferencesTest {
    @Test
    fun aFreshTeamHasBothAlertsOff() {
        val preferences = TeamAlertPreferences()

        assertFalse("game-start alerts must start off", preferences.start)
        assertFalse("goal alerts must start off", preferences.goals)
    }
}
