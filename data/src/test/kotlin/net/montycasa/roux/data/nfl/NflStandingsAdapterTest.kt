package net.montycasa.roux.data.nfl

import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.NflStandingRow
import net.montycasa.roux.domain.model.NflStandingTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The football table, read from the payloads the capture job stored.
 *
 * Two rules carry most of the weight and each has its own test: a figure the feed states only as a display string
 * (every record) is read from that string, and a figure the feed omits stays missing rather than becoming zero.
 */
class NflStandingsAdapterTest {
    private fun tables(
        name: String,
        capture: String = CAPTURE,
    ): List<NflStandingTable> =
        when (val result = NflStandingsAdapter.parse(fixture(name, capture))) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("Parse failed: ${result.reason}")
        }

    private fun fixture(
        name: String,
        capture: String = CAPTURE,
    ): String =
        NflStandingsAdapterTest::class.java
            .getResourceAsStream("/fx-nfl/$capture/$name")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Fixture resource not found: $name")

    private fun NflStandingTable.row(abbrev: String): NflStandingRow =
        rows.firstOrNull { it.team.abbrev == abbrev } ?: error("$name has no $abbrev")

    @Test
    fun theConferencePayloadGivesTwoTablesOfSixteen() {
        val tables = tables("nfl-standings.json")

        assertEquals(listOf("American Football Conference", "National Football Conference"), tables.map { it.name })
        assertTrue("every conference table holds 16 clubs: ${tables.map { it.rows.size }}", tables.all { it.rows.size == 16 })
    }

    @Test
    fun aRowCarriesTheFiguresTheFeedStates() {
        val row = tables("nfl-standings.json").first().row("BUF")

        assertEquals(8, row.wins)
        assertEquals(1, row.losses)
        assertEquals(0, row.ties)
        assertEquals(".889", row.winPercent)
        assertEquals(287, row.pointsFor)
        assertEquals(199, row.pointsAgainst)
        assertEquals(88, row.differential)
        assertEquals("W5", row.streak)
        assertEquals(1, row.playoffSeed)
    }

    @Test
    fun aRecordStatedOnlyAsADisplayStringIsReadFromThatString() {
        val row = tables("nfl-standings.json").first().row("BUF")

        // The feed writes divisionRecord as `value: 0.0` and `displayValue: "1-1"`. Reading the number would state
        // every division record in the league as zero.
        assertEquals("1-1", row.divisionRecord)
        assertEquals("5-1", row.conferenceRecord)
    }

    @Test
    fun aTiedRecordKeepsItsTie() {
        val row = tables("nfl-standings.json").last().row("NYG")

        assertEquals(1, row.ties)
        assertEquals("5-3-1", row.record)
        // A tied club's division and conference records carry the tie too, and they are the feed's strings.
        assertEquals("2-1-1", row.divisionRecord)
        assertEquals("3-3-1", row.conferenceRecord)
    }

    @Test
    fun theTieIsPrintedEvenWhenItIsZero() {
        // A football record without its tie is ambiguous late in a season, so the zero is printed rather than dropped.
        assertEquals("8-1-0", tables("nfl-standings.json").first().row("BUF").record)
    }

    @Test
    fun theDivisionPayloadGivesFourTablesOfFour() {
        // Captured a week after the conference payload, so a club's record differs between the two: the shape is
        // what this test is for, and no figure is compared across snapshots.
        val afc = tables("nfl-standings-divisions-afc.json", DIVISIONS)
        val nfc = tables("nfl-standings-divisions-nfc.json", DIVISIONS)

        assertEquals(listOf("AFC East", "AFC North", "AFC South", "AFC West"), afc.map { it.name })
        assertEquals(listOf("NFC East", "NFC North", "NFC South", "NFC West"), nfc.map { it.name })
        assertTrue("every division holds four clubs", (afc + nfc).all { it.rows.size == 4 })
        // The division payload states the same figures as the conference one, read the same way.
        val bills = afc.first().row("BUF")
        assertEquals(9, bills.wins)
        assertEquals("9-1-0", bills.record)
    }

    @Test
    fun theFeedOrderIsKeptRatherThanResorted() {
        // Standings arrive ranked. A comparator here would have to invent tie-breaks the feed already applied.
        val seeds = tables("nfl-standings.json").first().rows.map { it.playoffSeed }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), seeds)
    }

    @Test
    fun aFigureTheFeedOmitsStaysMissing() {
        // The synthetic payload drops BUF's points entirely: the row must say nothing, not zero.
        val tables =
            when (
                val result =
                    NflStandingsAdapter.parse(
                        """
                        {"children":[{"name":"AFC East","standings":{"entries":[
                          {"team":{"abbreviation":"BUF","displayName":"Buffalo Bills"},
                           "stats":[{"name":"wins","value":8.0,"displayValue":"8"}]}
                        ]}}]}
                        """.trimIndent(),
                    )
            ) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> error("Parse failed: ${result.reason}")
            }

        val row = tables.single().row("BUF")
        assertEquals(8, row.wins)
        assertNull("points for must stay unstated", row.pointsFor)
        assertNull(row.pointsAgainst)
        assertNull(row.differential)
        assertNull("a record needs all three counts", row.record)
    }

    @Test
    fun aTableWithNoRowsIsNotATable() {
        // The feed answers a level it does not publish with empty groups. A heading with nothing under it is worse
        // than no heading at all.
        val tables =
            when (
                val result =
                    NflStandingsAdapter.parse(
                        """
                        {"children":[
                          {"name":"American Football Conference","standings":{"entries":[]}},
                          {"name":"AFC East","standings":{"entries":[
                            {"team":{"abbreviation":"BUF"},"stats":[{"name":"wins","value":8.0}]}
                          ]}}
                        ]}
                        """.trimIndent(),
                    )
            ) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> error("Parse failed: ${result.reason}")
            }

        assertEquals(listOf("AFC East"), tables.map { it.name })
    }

    @Test
    fun aPayloadWithNoUsableRowsIsAnEmptyPayloadRatherThanAGarbledOne() {
        val result = NflStandingsAdapter.parse("""{"children":[{"name":"AFC","standings":{"entries":[]}}]}""")

        assertTrue("an empty table set is an empty payload: $result", result is ParseResult.Failure && result.emptyPayload)
    }

    @Test
    fun aMalformedPayloadFails() {
        val result = NflStandingsAdapter.parse("""{"children": "not a list"}""")

        assertTrue("garbage is a failure, not an empty table set: $result", result is ParseResult.Failure)
    }

    private companion object {
        const val CAPTURE = "2026-11-18"

        /** The division payloads were captured later; a club had played in between. */
        const val DIVISIONS = "2026-11-24"
    }
}
