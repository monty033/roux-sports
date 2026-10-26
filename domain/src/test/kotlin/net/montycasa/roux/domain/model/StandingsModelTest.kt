package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Verifies standings display, canonical feed names, and retained season and team data. */
class StandingsModelTest {
    @Test
    fun recordDisplaysOvertimeLosses() {
        assertEquals("10-3-2", row(otl = 2).record)
        assertEquals("10-3-0", row(otl = 0).record)
    }

    @Test
    fun recordDisplaysEnDashForMissingOvertimeLosses() {
        val standing = row(otl = null)

        assertNull(standing.otl)
        assertEquals("10-3-\u2013", standing.record)
        assertEquals('\u2013', standing.record[5])
    }

    @Test
    fun wildcardRankIsNullForDivisionPlacementAndPreservesRawZero() {
        val standing = row(otl = 2).copy(wildcardSequence = 0)

        assertNull(standing.wildcardRank)
        assertEquals(0, standing.wildcardSequence)
    }

    @Test
    fun wildcardRankReturnsPositiveConferencePositions() {
        assertEquals(1, row(otl = 2).copy(wildcardSequence = 1).wildcardRank)
        assertEquals(10, row(otl = 2).copy(wildcardSequence = 10).wildcardRank)
    }

    @Test
    fun wildcardRankDistinguishesZeroFromPositiveSequences() {
        val cases = listOf(0 to null, 1 to 1, 2 to 2, 10 to 10)

        cases.forEach { (sequence, expectedRank) ->
            val standing = row(otl = 2).copy(wildcardSequence = sequence)

            assertEquals("wildcard sequence: $sequence", expectedRank, standing.wildcardRank)
        }
    }

    @Test
    fun conferenceMapsKnownNamesWithSurroundingWhitespace() {
        val cases =
            listOf(
                "Eastern" to Conference.Eastern,
                "Western" to Conference.Western,
            )

        cases.forEach { (raw, expected) ->
            listOf(raw, " \t$raw\n").forEach { input ->
                assertEquals("conference input: <$input>", expected, Conference.fromFeed(input))
            }
        }
    }

    @Test
    fun conferencePreservesUnknownAndDifferentlyCasedNames() {
        val cases = listOf("Zzz", "eastern", "western", "EASTERN", " eastern ", "", " \t\n ", " Zzz\t")

        cases.forEach { raw ->
            assertEquals("conference input: <$raw>", Conference.Unknown(raw), Conference.fromFeed(raw))
        }
    }

    @Test
    fun divisionMapsKnownNamesWithSurroundingWhitespace() {
        val cases =
            listOf(
                "Atlantic" to Division.Atlantic,
                "Metropolitan" to Division.Metropolitan,
                "Central" to Division.Central,
                "Pacific" to Division.Pacific,
            )

        cases.forEach { (raw, expected) ->
            listOf(raw, " \t$raw\n").forEach { input ->
                assertEquals("division input: <$input>", expected, Division.fromFeed(input))
            }
        }
    }

    @Test
    fun divisionPreservesUnknownAndDifferentlyCasedNames() {
        val cases =
            listOf("Zzz", "atlantic", "metropolitan", "central", "pacific", "ATLANTIC", " atlantic ", "", " \t\n ", " Zzz\t")

        cases.forEach { raw ->
            assertEquals("division input: <$raw>", Division.Unknown(raw), Division.fromFeed(raw))
        }
    }

    @Test
    fun rowRetainsTeamAssetsAndFeedRankingsIncludingZeroWildcardSequence() {
        val team =
            TeamRef(
                key = TeamKey("CAR"),
                name = "Carolina Hurricanes",
                logoUrl = "https://assets.nhle.com/logos/nhl/svg/CAR_light.svg",
                darkLogoUrl = "https://assets.nhle.com/logos/nhl/svg/CAR_dark.svg",
            )
        val standing = row(otl = 2).copy(team = team)

        assertSame(team, standing.team)
        assertEquals(15, standing.gp)
        assertEquals(22, standing.pts)
        assertEquals(4, standing.leagueSequence)
        assertEquals(2, standing.conferenceSequence)
        assertEquals(1, standing.divisionSequence)
        assertEquals(0, standing.wildcardSequence)
        assertEquals(Conference.Eastern, standing.conference)
        assertEquals(Division.Metropolitan, standing.division)
    }

    @Test
    fun seasonRetainsIsoDatesAndAllowsEitherDateToBeMissing() {
        val cases =
            listOf(
                "2026-10-07" to "2027-04-15",
                null to "2027-04-15",
                "2026-10-07" to null,
                null to null,
            )

        cases.forEach { (start, end) ->
            val season = Season(id = 20262027, start = start, end = end)

            assertEquals(20262027, season.id)
            assertEquals(start, season.start)
            assertEquals(end, season.end)
        }
    }

    private fun row(otl: Int?): StandingRow =
        StandingRow(
            team = TeamRef(TeamKey("CAR")),
            gp = 15,
            w = 10,
            l = 3,
            otl = otl,
            pts = 22,
            leagueSequence = 4,
            conferenceSequence = 2,
            divisionSequence = 1,
            wildcardSequence = 0,
            conference = Conference.Eastern,
            division = Division.Metropolitan,
        )
}
