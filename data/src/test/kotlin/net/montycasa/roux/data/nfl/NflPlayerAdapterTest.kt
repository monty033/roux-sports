package net.montycasa.roux.data.nfl

import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.NflGameLog
import net.montycasa.roux.domain.model.NflPlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The football player payloads, read from the 26 November 2026 captures.
 *
 * The six profiles and six logs cover a quarterback, running back, receiver, kicker, punter and cornerback. Only the
 * quarterback and the kicker are asserted here in full: they disagree about both the summary and the game-log column
 * set, which is what proves the table is built from the feed rather than from a position table in the app.
 */
class NflPlayerAdapterTest {
    @Test
    fun aQbProfileCarriesTheIdentityTheFeedStates() {
        val profile = profile("qb-goff")

        assertEquals("3046779", profile.playerId.value)
        assertEquals("Jared Goff", profile.name)
        assertEquals("QB", profile.positionAbbreviation)
        assertEquals("#16", profile.jersey)
        assertEquals("https://a.espncdn.com/i/headshots/nfl/players/full/3046779.png", profile.headshotUrl)
        assertEquals("6' 4\"", profile.heightText)
        assertEquals("217 lbs", profile.weightText)
        assertEquals(32, profile.age)
        assertEquals("California", profile.college)
        assertEquals("11th Season", profile.experienceText)
        assertEquals("DET", profile.team?.key?.abbrev)
        assertEquals(2026, profile.season?.year)
    }

    /** The summary is the feed's formatted values, in the feed's order, with no re-formatting by the app. */
    @Test
    fun aQbSeasonSummaryIsTheFeedsOwnFormattedStatistics() {
        val profile = profile("qb-goff")

        assertEquals("2026 regular season stats", profile.seasonSummaryTitle)
        assertEquals(
            listOf(
                "Passing Yards" to "2,716",
                "Passing Touchdowns" to "20",
                "Interceptions" to "4",
                "Adjusted QBR" to "63.9",
            ),
            profile.seasonStats.map { it.displayName to it.displayValue },
        )
    }

    /**
     * A feed that states no jersey, headshot or experience line leaves those fields absent. The screen must render
     * nothing for them; in particular a missing jersey is not number zero, which is a real NFL number.
     */
    @Test
    fun aMissingProfileFieldStaysAbsentRatherThanBecomingZeroOrAPlaceholder() {
        val json =
            """
            {"athlete":{"id":"9001","displayName":"No Number","position":{"abbreviation":"QB"},
              "statsSummary":{"statistics":[]}}}
            """.trimIndent()

        val profile =
            when (val result = NflPlayerAdapter.parseProfile(json)) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> error("Parse failed: ${result.reason}")
            }

        assertNull("a missing jersey is not 0", profile.jersey)
        assertNull(profile.headshotUrl)
        assertNull(profile.heightText)
        assertNull(profile.weightText)
        assertNull(profile.age)
        assertNull(profile.college)
        assertNull(profile.experienceText)
    }

    @Test
    fun theQuarterbackLogUsesTheFeedsPassingAndRushingColumns() {
        val log = log("qb-goff")

        assertEquals("2026 Regular Season", log.seasonLabel)
        assertEquals(listOf("Passing", "Rushing"), log.groups.map { it.displayName })
        assertEquals(listOf(11, 5), log.groups.map { it.columns.size })
        assertEquals(
            listOf(
                "Completions",
                "Passing Attempts",
                "Passing Yards",
                "Completion Percentage",
                "Yards Per Pass Attempt",
                "Passing Touchdowns",
                "Interceptions",
                "Longest Pass",
                "Total Sacks",
                "Passer Rating",
                "Adjusted QBR",
                "Rushing Attempts",
                "Rushing Yards",
                "Yards Per Rush Attempt",
                "Rushing Touchdowns",
                "Long Rushing",
            ),
            log.columns.map { it.displayName },
        )
        // The narrow headings are the feed's labels, not the long display names.
        assertEquals(listOf("CMP", "ATT", "YDS", "CMP%"), log.columns.take(4).map { it.shortLabel })
    }

    /**
     * A kicker's log has eleven columns in two groups, and they are the field-goal and extra-point columns. The first
     * list is asserted in full so a hard-coded quarterback column list cannot pass this case.
     */
    @Test
    fun theKickerLogUsesTheFeedsFieldGoalAndExtraPointColumns() {
        val log = log("k-stbates")

        // The feed states these two groups as bare keys; the table is headed with spelled names instead.
        assertEquals(listOf("Field Goals", "Extra Points"), log.groups.map { it.displayName })
        assertEquals(listOf(9, 2), log.groups.map { it.columns.size })
        assertEquals(
            listOf(
                "FGM 1-19 yards",
                "FGM 20-29 yards",
                "FGM 30-39 yards",
                "FGM 40-49 yards",
                "FGM 50+ yards",
                "Long Field Goal Made",
                "Field Goal Percentage",
                "Field goals made",
                "Average field goal length",
                "Extra Points Made",
                "Total Kicking Points",
            ),
            log.columns.map { it.displayName },
        )
        // The feed's label for the first field-goal band carries a trailing tab; the column prints the trimmed label.
        assertEquals("1-19", log.columns.first().shortLabel)
    }

    /**
     * The cornerback's capture states `-` for fumbles in every game. That is absence, so the row holds no value for
     * those columns; a real zero in the adjacent sack column is kept as the string "0".
     */
    @Test
    fun aDashStatIsAbsentWhileARealZeroIsKept() {
        val log = log("cb-yasin")
        val row = log.games.first()

        assertFalse("the feed's dash must not become a value: ${row.stats}", "fumbles" in row.stats)
        assertNull(row.stats["fumbles"])
        assertNull(row.stats["fumblesLost"])
        assertEquals("0", row.stats["sacks"])
    }

    @Test
    fun aMalformedProfileFailsRatherThanInventingAPlayer() {
        val result = NflPlayerAdapter.parseProfile("""{"athlete":{"id":"1"}}""")

        assertEquals(ParseResult.Failure("NFL player profile mapping failed: no player name"), result)
    }

    private fun profile(name: String): NflPlayerProfile =
        when (val result = NflPlayerAdapter.parseProfile(fixture("nfl-player-$name.json"))) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("Parse failed: ${result.reason}")
        }

    private fun log(name: String): NflGameLog =
        when (val result = NflPlayerAdapter.parseGameLog(fixture("nfl-gamelog-$name.json"))) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> error("Parse failed: ${result.reason}")
        }

    private fun fixture(file: String): String =
        NflPlayerAdapterTest::class.java
            .getResourceAsStream("/fx-nfl/$CAPTURE/$file")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Fixture resource not found: $file")

    private companion object {
        const val CAPTURE = "2026-11-26"
    }
}
