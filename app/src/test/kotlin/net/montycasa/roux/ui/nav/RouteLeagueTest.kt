package net.montycasa.roux.ui.nav

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Eagles and the Flyers both read PHI; a route that drops the league would open the wrong club. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouteLeagueTest {
    @Test
    fun anEaglesRowAndAFlyersRowDoNotShareAPage() {
        assertTrue(teamRoute(TeamKey(League.NFL, "PHI")) != teamRoute(TeamKey(League.NHL, "PHI")))
    }

    @Test
    fun aTeamRouteNamesItsLeague() {
        assertTrue(teamRoute(TeamKey(League.NFL, "PHI")).endsWith("?league=nfl"))
        assertTrue(teamRoute(TeamKey(League.NHL, "PHI")).endsWith("?league=nhl"))
    }

    @Test
    fun aGameRouteNamesItsLeague() {
        assertTrue(detailRoute(game(League.NFL)).contains("&league=nfl"))
        assertTrue(detailRoute(game(League.NHL)).contains("&league=nhl"))
    }

    @Test
    fun aRouteWithNoLeagueIsNhlAsItAlwaysWas() {
        assertEquals(League.NHL, leagueFromRouteArg(null))
        assertEquals(League.NHL, leagueFromRouteArg(""))
    }

    @Test
    fun aKnownLeagueResolvesAndAnUnknownOneDoesNot() {
        assertEquals(League.NFL, leagueFromRouteArg("nfl"))
        assertEquals(League.NHL, leagueFromRouteArg("nhl"))
        assertNull(leagueFromRouteArg("mlb"))
    }

    private fun game(league: League) =
        Game(
            id = GameId(league, "401873066"),
            date = "2026-11-19",
            state = GameState.Final,
            home = TeamRef(TeamKey(league, "HOU")),
            away = TeamRef(TeamKey(league, "IND")),
        )
}
