package net.montycasa.roux.domain.model

import net.montycasa.roux.domain.policy.LeagueOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamKeyTest {
    @Test fun theSameAbbreviationInTwoLeaguesIsTwoTeams() {
        assertNotEquals(TeamKey(League.NHL, "PHI"), TeamKey(League.NFL, "PHI"))
    }

    @Test fun theSameLeagueAndAbbreviationIsTheSameTeam() {
        assertEquals(TeamKey(League.NHL, "PHI"), TeamKey(League.NHL, "PHI"))
    }

    @Test fun theSameValueInTwoLeaguesIsTwoGames() {
        assertNotEquals(GameId(League.NHL, "2026020001"), GameId(League.NFL, "2026020001"))
    }

    @Test fun theSameLeagueAndValueIsTheSameGame() {
        assertEquals(GameId(League.NHL, "2026020001"), GameId(League.NHL, "2026020001"))
    }

    @Test fun leagueIdsAreTheStableStrings() {
        assertEquals("nhl", League.NHL.id)
        assertEquals("nfl", League.NFL.id)
    }

    @Test fun bothLeagueIdsAreCanonicalLeagueOrderIds() {
        assertTrue(League.NHL.id in LeagueOrder.canonical)
        assertTrue(League.NFL.id in LeagueOrder.canonical)
    }
}
