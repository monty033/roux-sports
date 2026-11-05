package net.montycasa.roux.ui.nav

import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PlayerGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A player's log row becomes the game the scores list would open, with the player's club on its own side. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerGameRouteTest {
    @Test
    fun aHomeGameTakesThePlayersClubAsHomeAndTheOpponentAsAway() {
        val game = skater(club = "CAR", opponent = "OTT", isHome = true).toGame()

        assertEquals("CAR", game?.home?.abbrev)
        assertEquals("OTT", game?.away?.abbrev)
    }

    @Test
    fun anAwayGameTakesTheOpponentAsHomeAndThePlayersClubAsAway() {
        val game = skater(club = "CAR", opponent = "OTT", isHome = false).toGame()

        assertEquals("OTT", game?.home?.abbrev)
        assertEquals("CAR", game?.away?.abbrev)
    }

    /** The game carries its id, its date and a final state, and the page loads its own scores. */
    @Test
    fun theGameKeepsItsIdDateAndAFinalStateWithNoScores() {
        val game = skater().toGame()

        assertEquals("2026020188", game?.id?.value)
        assertEquals("2026-11-01", game?.date)
        assertEquals(GameState.Final, game?.state)
        assertNull(game?.homeScore)
        assertNull(game?.awayScore)
    }

    /** A row the page cannot make a game of opens nothing: no club, opponent, home flag or date means no game. */
    @Test
    fun aRowMissingItsClubOpponentHomeFlagOrDateMakesNoGame() {
        assertNull(skater(club = null).toGame())
        assertNull(skater(opponent = null).toGame())
        assertNull(skater(isHome = null).toGame())
        assertNull(skater(date = null).toGame())
    }

    /** The routed game is the same route the scores list builds for that game. */
    @Test
    fun theRouteForAPlayersGameNamesTheGameAndBothClubs() {
        val route = detailRoute(skater(club = "CAR", opponent = "OTT", isHome = true).toGame()!!)

        assertTrue("route is $route", route.startsWith("detail/2026020188?"))
        assertTrue("route is $route", route.contains("home=CAR"))
        assertTrue("route is $route", route.contains("away=OTT"))
    }

    private fun skater(
        club: String? = "CAR",
        opponent: String? = "OTT",
        isHome: Boolean? = true,
        date: String? = "2026-11-01",
    ) = PlayerGame.Skater(
        gameId = 2026020188L,
        gameDate = date,
        opponentAbbrev = opponent,
        isHome = isHome,
        teamAbbrev = club,
    )
}
