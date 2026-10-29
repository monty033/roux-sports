package net.montycasa.roux.ui.common

import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scoreboard names a club in full before any endpoint answers, so the table is the only source for the city.
 * These pin that it covers the league, and that a club it has never heard of still renders.
 */
class TeamNamesTest {
    private val leagueAbbrevs =
        listOf(
            "ANA",
            "BOS",
            "BUF",
            "CGY",
            "CAR",
            "CHI",
            "COL",
            "CBJ",
            "DAL",
            "DET",
            "EDM",
            "FLA",
            "LAK",
            "MIN",
            "MTL",
            "NSH",
            "NJD",
            "NYI",
            "NYR",
            "OTT",
            "PHI",
            "PIT",
            "SJS",
            "SEA",
            "STL",
            "TBL",
            "TOR",
            "UTA",
            "VAN",
            "VGK",
            "WSH",
            "WPG",
        )

    @Test
    fun everyClubInTheLeagueHasACityAndAName() {
        assertEquals(32, leagueAbbrevs.size)
        leagueAbbrevs.forEach { abbrev ->
            assertTrue("no city for $abbrev", TeamNames.city(abbrev) != null)
            assertTrue("no name for $abbrev", TeamNames.nickname(abbrev) != null)
        }
    }

    @Test
    fun aClubInTheTableIsWrittenCityThenName() {
        val (city, name) = TeamNames.cityAndName(TeamRef(TeamKey("PHI"), name = "Flyers"))

        assertEquals("Philadelphia", city)
        assertEquals("Flyers", name)
    }

    @Test
    fun aClubOutsideTheTableFallsBackToWhatTheFeedSaid() {
        val (city, name) = TeamNames.cityAndName(TeamRef(TeamKey("XYZ"), name = "Metropolitans"))

        assertNull(city)
        assertEquals("Metropolitans", name)
    }

    @Test
    fun aClubWithNothingButAnAbbreviationStillRenders() {
        val (city, name) = TeamNames.cityAndName(TeamRef(TeamKey("XYZ")))

        assertNull(city)
        assertEquals("XYZ", name)
    }
}
