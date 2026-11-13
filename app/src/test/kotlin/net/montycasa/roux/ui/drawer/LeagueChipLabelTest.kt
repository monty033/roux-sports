package net.montycasa.roux.ui.drawer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.montycasa.roux.R
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.scores.MY_TEAMS_LEAGUE
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeagueChipLabelTest {
    private val myTeams = ApplicationProvider.getApplicationContext<Context>().getString(R.string.league_chip_my_teams)

    @Test
    fun favouritesOnlyShowsMyTeams() {
        val drawer = DrawerUi(favouritesOnly = true, selectedLeagueId = MY_TEAMS_LEAGUE)
        assertEquals("My Teams", leagueChipLabel(drawer, myTeams))
    }

    @Test
    fun aChosenLeagueShowsItsCatalogLabel() {
        val drawer = DrawerUi(favouritesOnly = false, selectedLeagueId = "nhl")
        assertEquals("NHL", leagueChipLabel(drawer, myTeams))
    }

    @Test
    fun anUnknownLeagueFallsBackToItsId() {
        val drawer = DrawerUi(favouritesOnly = false, selectedLeagueId = "xfl")
        assertEquals("xfl", leagueChipLabel(drawer, myTeams))
    }
}
