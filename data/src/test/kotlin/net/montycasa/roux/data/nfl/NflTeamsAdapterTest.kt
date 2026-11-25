package net.montycasa.roux.data.nfl

import net.montycasa.roux.data.net.EspnHttpClientFactory
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflTeam
import net.montycasa.roux.domain.model.TeamKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The football clubs, read from the payload captured on 18 November. */
class NflTeamsAdapterTest {
    private fun clubs(json: String = fixture()): List<NflTeam> =
        when (val result = NflTeamsAdapter.parse(json)) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("Parse failed: ${result.reason}")
        }

    private fun fixture(): String =
        NflTeamsAdapterTest::class.java
            .getResourceAsStream("/fx-nfl/2026-11-18/nfl-teams.json")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Fixture resource not found: nfl-teams.json")

    /** One club in the feed's wrapper, with only the fields a test names. */
    private fun payload(vararg teams: String) =
        """{"sports":[{"leagues":[{"teams":[${teams.joinToString(",") { """{"team":$it}""" }}]}]}]}"""

    @Test
    fun thePayloadGivesThirtyTwoClubs() {
        assertEquals(32, clubs().size)
    }

    @Test
    fun everyClubIsAFootballClubKeyedByItsAbbreviation() {
        val clubs = clubs()

        assertTrue(clubs.all { it.team.key.league == League.NFL })
        assertEquals("ARI", clubs.first().team.abbrev)
        assertEquals("WSH", clubs.last().team.abbrev)
    }

    @Test
    fun abbreviationsAreUnique() {
        val abbreviations = clubs().map { it.team.abbrev }

        assertEquals(abbreviations.size, abbreviations.toSet().size)
        assertEquals(clubs().map { it.team.key }.toSet().size, clubs().size)
    }

    @Test
    fun aClubCarriesTheFeedsOwnWords() {
        val club = clubs().single { it.team.abbrev == "SF" }

        assertEquals("San Francisco 49ers", club.fullName)
        assertEquals("49ers", club.nickname)
        assertEquals("San Francisco", club.location)
        // The same words on the identity the rest of the app draws from.
        assertEquals("San Francisco 49ers", club.team.name)
        assertEquals("49ers", club.team.shortName)
        assertEquals(TeamKey(League.NFL, "SF"), club.team.key)
    }

    @Test
    fun theFeedsOrderIsKept() {
        val abbreviations = clubs().map { it.team.abbrev }

        // The feed lists by place name, so LV (Las Vegas) sits before LAC and LAR (Los Angeles): an app-side sort by
        // abbreviation would reorder it, which is the thing that must not happen.
        assertTrue(abbreviations.indexOf("LV") < abbreviations.indexOf("LAC"))
        assertEquals(listOf("LV", "LAC", "LAR"), abbreviations.filter { it in setOf("LAC", "LAR", "LV") })
    }

    @Test
    fun everyClubHasALogoOnTheArtworkHostOrNone() {
        clubs().forEach { club ->
            val logo = club.logoUrl
            assertTrue(
                "${club.team.abbrev}: $logo must be on ${EspnHttpClientFactory.LOGO_HOST} or absent",
                logo == null || logo.startsWith("https://${EspnHttpClientFactory.LOGO_HOST}/"),
            )
            assertEquals("the identity carries the same crest", logo, club.team.logoUrl)
        }
        // The capture has a crest for every club; a count of zero would pass the check above for the wrong reason.
        assertEquals(32, clubs().count { it.logoUrl != null })
        assertEquals("https://a.espncdn.com/i/teamlogos/nfl/500/ari.png", clubs().first().logoUrl)
    }

    @Test
    fun aFirstLogoOnAnotherHostIsTakenAsNoneRatherThanSearchedPast() {
        val club =
            clubs(
                payload(
                    """{"abbreviation":"XXX","displayName":"Elsewhere Elsewheres","logos":[
                        {"href":"https://cdn.example.com/xxx.png"},
                        {"href":"https://a.espncdn.com/i/teamlogos/nfl/500/xxx.png"}]}""",
                ),
            ).single()

        assertNull("a later logo on the allowed host must not be reached for", club.logoUrl)
        assertNull(club.team.logoUrl)
        assertEquals("Elsewhere Elsewheres", club.fullName)
    }

    @Test
    fun aLogoOverPlainHttpOrAnUnreadableAddressIsTakenAsNone() {
        val clubs =
            clubs(
                payload(
                    """{"abbreviation":"AAA","logos":[{"href":"http://a.espncdn.com/aaa.png"}]}""",
                    """{"abbreviation":"BBB","logos":[{"href":"not a url at all"}]}""",
                    """{"abbreviation":"CCC","logos":[{"rel":["full"]}]}""",
                    """{"abbreviation":"DDD"}""",
                ),
            )

        assertEquals(listOf("AAA", "BBB", "CCC", "DDD"), clubs.map { it.team.abbrev })
        assertTrue(clubs.all { it.logoUrl == null })
    }

    @Test
    fun aLookAlikeHostIsNotTheArtworkHost() {
        val club =
            clubs(payload("""{"abbreviation":"AAA","logos":[{"href":"https://a.espncdn.com.evil.example/aaa.png"}]}""")).single()

        assertNull(club.logoUrl)
    }

    @Test
    fun aMissingFieldStaysMissingRatherThanBeingComposed() {
        val club = clubs(payload("""{"abbreviation":"AAA","location":"Somewhere"}""")).single()

        assertNull("no full name is built from the place", club.fullName)
        assertNull("no nickname is guessed", club.nickname)
        assertNull(club.team.name)
        assertEquals("Somewhere", club.location)
    }

    @Test
    fun aClubWithoutAnAbbreviationIsLeftOut() {
        val clubs = clubs(payload("""{"displayName":"Nameless"}""", """{"abbreviation":" "}""", """{"abbreviation":"AAA"}"""))

        assertEquals(listOf("AAA"), clubs.map { it.team.abbrev })
    }

    @Test
    fun aRepeatedAbbreviationKeepsTheFirstClub() {
        val clubs =
            clubs(
                payload(
                    """{"abbreviation":"AAA","displayName":"First"}""",
                    """{"abbreviation":"AAA","displayName":"Second"}""",
                ),
            )

        assertEquals(listOf("First"), clubs.map { it.fullName })
    }

    @Test
    fun aPayloadWithNoClubsIsAnEmptyFailure() {
        val result = NflTeamsAdapter.parse("""{"sports":[{"leagues":[{"teams":[]}]}]}""")

        assertTrue("expected a failure, got $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).emptyPayload)
    }

    @Test
    fun aPayloadWithoutTheTeamsFieldIsAFailure() {
        assertNotNull((NflTeamsAdapter.parse("""{"sports":[]}""") as? ParseResult.Failure)?.reason)
        assertTrue(NflTeamsAdapter.parse("""{"sports":"nope"}""") is ParseResult.Failure)
        assertTrue(NflTeamsAdapter.parse("not json") is ParseResult.Failure)
    }
}
