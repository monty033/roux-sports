package net.montycasa.roux.data.nfl

import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.NflClubRoster
import net.montycasa.roux.domain.model.NflPlayerStatus
import net.montycasa.roux.domain.model.NflRosterPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The roster a club's payload describes, read from the two captures.
 *
 * Buffalo and Philadelphia are both here because they disagree about the numbers the brief generalised from
 * Buffalo: Philadelphia lists seventy-six players, not seventy-two, and six without a jersey, not two. Those
 * disagreements are asserted rather than smoothed over, so a later reader cannot mistake the brief's sample for a
 * feed rule.
 */
class NflRosterAdapterTest {
    private fun roster(club: String): NflClubRoster =
        when (val result = NflRosterAdapter.parse(fixture(club))) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("Parse failed: ${result.reason}")
        }

    private fun fixture(club: String): String =
        NflRosterAdapterTest::class.java
            .getResourceAsStream("/fx-nfl/$CAPTURE/nfl-roster-$club.json")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Fixture resource not found: nfl-roster-$club.json")

    private fun NflClubRoster.player(name: String): NflRosterPlayer =
        players.singleOrNull { it.name == name } ?: error("No player named $name")

    @Test
    fun aRosterCarriesTheClubAndEveryPlayerTheFeedLists() {
        val buffalo = roster("buf")
        val philadelphia = roster("phi")

        assertEquals("BUF", buffalo.team.key.abbrev)
        assertEquals("Buffalo Bills", buffalo.team.name)
        assertEquals("the captured Buffalo roster", 72, buffalo.players.size)
        // The brief says seventy-two per club. Philadelphia disproves that as a rule.
        assertEquals("the captured Philadelphia roster", 76, philadelphia.players.size)
    }

    @Test
    fun anAbsentJerseyStaysAbsentRatherThanBecomingZero() {
        val buffalo = roster("buf")

        assertNull("Kader Kohou has no jersey in the capture", buffalo.player("Kader Kohou").jersey)
        assertNull("Brock Lampe has no jersey in the capture", buffalo.player("Brock Lampe").jersey)
        assertEquals(
            "Buffalo has exactly two unnumbered players",
            2,
            buffalo.players.count { it.jersey == null },
        )
        assertEquals(
            "Philadelphia has six, not the two the brief generalised from Buffalo",
            6,
            roster("phi").players.count { it.jersey == null },
        )
        // Zero is a real number in the modern NFL, not a stand-in for "absent": Keon Coleman wears it.
        assertEquals("0", buffalo.player("Keon Coleman").jersey)
    }

    @Test
    fun aMissingStatusIsNotActive() {
        val buffalo = roster("buf")

        // Twelve of Buffalo's players state no status at all; none of them may be read as active.
        assertEquals(12, buffalo.players.count { it.status == NflPlayerStatus.NotStated })
        assertEquals(NflPlayerStatus.NotStated, buffalo.player("Nick Broeker").status)
        // Nick Broeker is also active: false in the feed, which is a second statement, not a substitute for a status.
        assertEquals(NflPlayerStatus.NotStated, buffalo.player("Kader Kohou").status)
    }

    @Test
    fun theThreeStatusAnswersAreDistinct() {
        val buffalo = roster("buf")

        assertEquals(NflPlayerStatus.Active("Active"), buffalo.player("Josh Allen").status)
        assertEquals(NflPlayerStatus.Other("Day-To-Day"), buffalo.player("Zane Durant").status)
        assertEquals(NflPlayerStatus.Other("News"), buffalo.player("Sam Franklin Jr.").status)
        assertEquals(NflPlayerStatus.NotStated, buffalo.player("Nick Broeker").status)
        assertEquals(55, buffalo.players.count { it.status is NflPlayerStatus.Active })
        assertEquals(5, buffalo.players.count { it.status is NflPlayerStatus.Other })
    }

    @Test
    fun positionsComeFromEachPlayersOwnPositionObject() {
        val buffalo = roster("buf")
        val abbreviations = buffalo.players.map { it.positionAbbreviation }

        // The team-level groups object has no membership, so these can only come from the athletes themselves.
        assertEquals(16, abbreviations.distinct().size)
        assertEquals("Cornerback", buffalo.player("Kader Kohou").positionName)
        // Every position run is contiguous and runs ascend, which is what "grouped by position" means for this list.
        val runs = abbreviations.distinct()
        assertEquals("each position is one run", runs.size, abbreviations.zipWithNext().count { (a, b) -> a != b } + 1)
        assertEquals(runs.sorted(), runs)
    }

    @Test
    fun theRosterIsOrderedByPositionThenJerseyThenName() {
        val json =
            """
            {"team":{"abbreviation":"BUF","displayName":"Buffalo Bills","athletes":[
              {"id":"1","displayName":"Zed","jersey":"10","position":{"abbreviation":"WR","displayName":"Wide Receiver"}},
              {"id":"2","displayName":"Amy","jersey":"21","position":{"abbreviation":"CB","displayName":"Cornerback"}},
              {"id":"3","displayName":"Bob","jersey":"3","position":{"abbreviation":"CB","displayName":"Cornerback"}},
              {"id":"4","displayName":"Cal","position":{"abbreviation":"CB","displayName":"Cornerback"}},
              {"id":"5","displayName":"Dan","jersey":"3","position":{"abbreviation":"CB","displayName":"Cornerback"}}
            ]}}
            """.trimIndent()

        val players =
            when (val result = NflRosterAdapter.parse(json)) {
                is ParseResult.Success -> result.value.players
                is ParseResult.Failure -> error("Parse failed: ${result.reason}")
            }

        // CB before WR; at CB jersey 3 before 21 before unnumbered; the two 3s by name.
        assertEquals(listOf("Bob", "Dan", "Amy", "Cal", "Zed"), players.map { it.name })
    }

    @Test
    fun aPlayersWordingAndHeadshotAreKeptFromTheFeed() {
        val allen = roster("buf").player("Josh Allen")

        assertEquals("6' 5\"", allen.heightText)
        assertEquals("237 lbs", allen.weightText)
        assertEquals(30, allen.age)
        assertTrue("the headshot is on the authorized artwork host", allen.headshotUrl?.startsWith("https://a.espncdn.com/") == true)
    }

    @Test
    fun anEmptyRosterIsReportedAsEmptyRatherThanGarbled() {
        val result = NflRosterAdapter.parse("""{"team":{"abbreviation":"BUF","athletes":[]}}""")

        assertTrue("an empty roster is an empty payload: $result", result is ParseResult.Failure && result.emptyPayload)
    }

    @Test
    fun aMalformedRosterFails() {
        val result = NflRosterAdapter.parse("""{"team":{"athletes":"not a list"}}""")

        assertTrue("garbage is a failure, not an empty roster: $result", result is ParseResult.Failure)
    }

    private companion object {
        const val CAPTURE = "2026-11-25"
    }
}
