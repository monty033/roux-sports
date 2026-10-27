package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandingsGroupingTest {
    // Deliberately disagreeing points and sequence orders catch accidental point/rank substitutions.
    private val rows =
        (0 until 32).map { index ->
            StandingRow(
                TeamRef(TeamKey("T$index")),
                10,
                5,
                4,
                1,
                index,
                32 - index,
                16 - index % 16,
                8 - index % 8,
                if (index % 8 >= 5) 0 else 10 - (index % 8 + index % 16 / 8 * 5),
                if (index < 16) Conference.Eastern else Conference.Western,
                listOf(Division.Atlantic, Division.Metropolitan, Division.Central, Division.Pacific)[index / 8],
            )
        }

    @Test
    fun leagueHasThirtyTwoRowsByLeagueSequenceNeverPoints() {
        val groups = StandingsGrouping.group(rows, StandingsView.League)
        assertEquals(listOf("NHL"), groups.sections.map { it.title })
        assertEquals(rows.reversed(), groups.sections.single().rows)
    }

    @Test
    fun conferencesHaveSixteenRowsInFixedOrderByConferenceSequence() {
        val sections = StandingsGrouping.group(rows, StandingsView.Conference).sections
        assertEquals(listOf("Eastern", "Western"), sections.map { it.title })
        assertEquals(listOf(16, 16), sections.map { it.rows.size })
        sections.forEach { assertEquals((1..16).toList(), it.rows.map { row -> row.conferenceSequence }) }
    }

    @Test
    fun divisionsHaveEightRowsInFixedOrderByDivisionSequence() {
        val sections = StandingsGrouping.group(rows.reversed(), StandingsView.Division).sections
        assertEquals(listOf("Atlantic", "Metropolitan", "Central", "Pacific"), sections.map { it.title })
        assertEquals(listOf(8, 8, 8, 8), sections.map { it.rows.size })
        sections.forEach { assertEquals((1..8).toList(), it.rows.map { row -> row.divisionSequence }) }
    }

    @Test
    fun wildcardHasThreeThreeAndTenPerConferenceWithCutBelowWcTwo() {
        val sections = StandingsGrouping.group(rows, StandingsView.Wildcard).sections
        assertEquals(
            listOf(
                "Eastern — Atlantic",
                "Eastern — Metropolitan",
                "Eastern — Wild Card",
                "Western — Pacific",
                "Western — Central",
                "Western — Wild Card",
            ),
            sections.map { it.title },
        )
        assertEquals(listOf(3, 3, 10, 3, 3, 10), sections.map { it.rows.size })
        assertEquals(listOf(null, null, 1, null, null, 1), sections.map { it.playoffCutAfter })
        sections.filter { it.title.endsWith("Wild Card") }.forEach {
            assertEquals((1..10).toList(), it.rows.map { row -> row.wildcardSequence })
        }
        assertEquals(rows.toSet(), sections.flatMap { it.rows }.toSet())
        assertEquals(32, sections.sumOf { it.rows.size })
        sections.filter { !it.title.endsWith("Wild Card") }.forEach {
            assertEquals(listOf(1, 2, 3), it.rows.map { row -> row.divisionSequence })
        }
    }

    @Test
    fun wildcardDivisionPlacementWinsEvenWhenTopThreeHaveWildcardRanks() {
        val altered = rows.map { it.copy(wildcardSequence = 1) }
        val sections = StandingsGrouping.group(altered, StandingsView.Wildcard).sections
        assertEquals(32, sections.sumOf { it.rows.size })
        assertEquals(listOf(3, 3, 10, 3, 3, 10), sections.map { it.rows.size })
    }

    @Test
    fun unknownDivisionGoesToOtherAndUnknownConferenceIsNeverLost() {
        val unknown = rows.take(2).map { it.copy(division = Division.Unknown("new"), conference = Conference.Unknown("new")) }
        StandingsView.entries.filter { it != StandingsView.League }.forEach { view ->
            val sections = StandingsGrouping.group(rows + unknown, view).sections
            assertEquals("Other", sections.last().title)
            assertEquals(unknown.sortedBy { it.leagueSequence }, sections.last().rows)
        }
    }

    @Test
    fun wildcardLeftoversHaveNoCutAndUnknownDivisionsCanStillBeWildcardRanked() {
        val other = rows[0].copy(division = Division.Unknown("new"), wildcardSequence = 0)
        val wildcard = rows[1].copy(division = Division.Unknown("new"), wildcardSequence = 1)
        val sections = StandingsGrouping.group(listOf(other, wildcard), StandingsView.Wildcard).sections
        assertEquals(listOf("Eastern — Wild Card", "Eastern — Other"), sections.map { it.title })
        assertEquals(listOf(wildcard), sections[0].rows)
        assertEquals(listOf(other), sections[1].rows)
        sections.forEach { assertNull(it.playoffCutAfter) }
    }

    @Test
    fun wildcardOmitsMissingDivisionsAndEmptyBlocks() {
        val sections = StandingsGrouping.group(rows.takeLast(8), StandingsView.Wildcard).sections
        assertEquals(listOf("Western — Pacific", "Western — Wild Card"), sections.map { it.title })
    }

    @Test
    fun wildcardCutRequiresAtLeastTwoRows() {
        val top = rows.takeLast(3).mapIndexed { index, row -> row.copy(divisionSequence = index + 1) }
        val wild = rows[24].copy(divisionSequence = 4, wildcardSequence = 1)
        assertNull(
            StandingsGrouping
                .group(top + wild, StandingsView.Wildcard)
                .sections
                .last()
                .playoffCutAfter,
        )
        val two = top + wild + wild.copy(divisionSequence = 5, wildcardSequence = 2)
        assertEquals(
            1,
            StandingsGrouping
                .group(two, StandingsView.Wildcard)
                .sections
                .last()
                .playoffCutAfter,
        )
    }

    @Test
    fun pinnedIsFirstSortedAndDeliberatelyDuplicatedInEveryView() {
        val pinned = listOf(rows[0], rows[31])
        StandingsView.entries.forEach { view ->
            val base = StandingsGrouping.group(rows, view).sections
            val withPins = StandingsGrouping.group(rows, view, pinned).sections
            assertEquals(StandingsSection("Your teams", pinned.reversed()), withPins.first())
            assertEquals(base, withPins.drop(1))
        }
    }

    @Test
    fun emptyInputHasNoSectionsEvenWithPins() {
        StandingsView.entries.forEach {
            assertEquals(
                emptyList<StandingsSection>(),
                StandingsGrouping.group(emptyList(), it, rows).sections,
            )
        }
    }

    @Test
    fun duplicateSequencesKeepInputOrderInEverySortIncludingPinsAndWildcard() {
        val ties = rows.take(8).map { it.copy(leagueSequence = 1, conferenceSequence = 1, divisionSequence = 1, wildcardSequence = 1) }
        StandingsView.entries.forEach { view ->
            val sections = StandingsGrouping.group(ties, view, ties).sections
            assertEquals(ties, sections.first().rows)
            assertEquals(ties, sections.drop(1).flatMap { it.rows })
        }
    }
}
