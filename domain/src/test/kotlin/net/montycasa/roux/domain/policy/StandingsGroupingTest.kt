package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun theLeaguePageIgnoresTheConferenceMode() {
        // The ranking is a conference question; asking for a division table of the whole league is not a page that
        // exists, so the mode must not change the league's ordering or split it.
        val base = StandingsGrouping.group(rows, StandingsView.League)
        StandingsMode.entries.forEach { mode ->
            assertEquals(base, StandingsGrouping.group(rows, StandingsView.League, mode))
        }
    }

    @Test
    fun aConferencePageRanksItsOwnSixteenTeamsWithNoHeading() {
        // The page is named by the tab above it, so a heading would spend height restating the tab.
        StandingsMode.entries
            .filter { it == StandingsMode.Conference }
            .forEach { mode ->
                listOf(StandingsView.Eastern, StandingsView.Western).forEach { view ->
                    val section = StandingsGrouping.group(rows, view, mode).sections.single()
                    assertEquals("", section.title)
                    assertEquals(16, section.rows.size)
                    assertEquals((1..16).toList(), section.rows.map { row -> row.conferenceSequence })
                    assertNull(section.playoffCutAfter)
                }
            }
    }

    @Test
    fun aConferencePageHasOnlyItsOwnTeams() {
        listOf(StandingsView.Eastern, StandingsView.Western).forEach { view ->
            StandingsMode.entries.forEach { mode ->
                val shown = StandingsGrouping.group(rows, view, mode).sections.flatMap { it.rows }
                assertEquals("${view.name}/${mode.name}", 16, shown.size)
                assertEquals("${view.name}/${mode.name}", shown.distinct().size, shown.size)
                val expected = if (view == StandingsView.Eastern) Conference.Eastern else Conference.Western
                assertTrue("${view.name}/${mode.name} keeps to its conference", shown.all { it.conference == expected })
            }
        }
    }

    @Test
    fun divisionModeDrawsThatConferencesTwoTables() {
        val east = StandingsGrouping.group(rows, StandingsView.Eastern, StandingsMode.Division).sections
        assertEquals(listOf("Atlantic", "Metropolitan"), east.map { it.title })
        assertEquals(listOf(8, 8), east.map { it.rows.size })
        east.forEach { assertEquals((1..8).toList(), it.rows.map { row -> row.divisionSequence }) }

        // Within a conference the divisions keep the league's own order, so Central precedes Pacific in the West.
        val west = StandingsGrouping.group(rows, StandingsView.Western, StandingsMode.Division).sections
        assertEquals(listOf("Central", "Pacific"), west.map { it.title })
        assertEquals(listOf(8, 8), west.map { it.rows.size })
    }

    @Test
    fun wildcardModeIsThePlayoffPictureWithTheCutBelowWcTwo() {
        val sections = StandingsGrouping.group(rows, StandingsView.Eastern, StandingsMode.Wildcard).sections
        assertEquals(listOf("Atlantic", "Metropolitan", "Wild Card"), sections.map { it.title })
        assertEquals(listOf(3, 3, 10), sections.map { it.rows.size })
        assertEquals(listOf(null, null, 1), sections.map { it.playoffCutAfter })
        assertEquals((1..10).toList(), sections.last().rows.map { row -> row.wildcardSequence })
        sections.take(2).forEach { assertEquals(listOf(1, 2, 3), it.rows.map { row -> row.divisionSequence }) }
    }

    @Test
    fun everyConferenceModeShowsTheWholeConferenceExactlyOnce() {
        // No team may disappear because of the ranking that is selected.
        listOf(StandingsView.Eastern, StandingsView.Western).forEach { view ->
            StandingsMode.entries.forEach { mode ->
                val shown = StandingsGrouping.group(rows, view, mode).sections.flatMap { it.rows }
                assertEquals("${view.name}/${mode.name}", 16, shown.size)
                assertEquals(
                    "${view.name}/${mode.name}",
                    shown.toSet(),
                    rows
                        .filter {
                            it.conference ==
                                (if (view == StandingsView.Eastern) Conference.Eastern else Conference.Western)
                        }.toSet(),
                )
            }
        }
    }

    @Test
    fun wildcardDivisionPlacementWinsEvenWhenTopThreeHaveWildcardRanks() {
        val altered = rows.map { it.copy(wildcardSequence = 1) }
        val sections = StandingsGrouping.group(altered, StandingsView.Eastern, StandingsMode.Wildcard).sections
        assertEquals(16, sections.sumOf { it.rows.size })
        assertEquals(listOf(3, 3, 10), sections.map { it.rows.size })
    }

    @Test
    fun anUnknownDivisionGetsItsOwnBlockInDivisionModeAndWildcardRanksInWildcardMode() {
        val unknown = rows[0].copy(division = Division.Unknown("new"), divisionSequence = 1)
        val ranked = rows[1].copy(division = Division.Unknown("new"), wildcardSequence = 1)

        val division = StandingsGrouping.group(listOf(unknown), StandingsView.Eastern, StandingsMode.Division).sections
        assertEquals(listOf("Other"), division.map { it.title })
        assertEquals(listOf(unknown), division.single().rows)

        // A ranked unknown division sorts into the race; an unranked one falls through to "Other".
        val unranked = rows[2].copy(division = Division.Unknown("new"), wildcardSequence = 0)
        val wild = StandingsGrouping.group(listOf(ranked, unranked), StandingsView.Eastern, StandingsMode.Wildcard).sections
        assertEquals(listOf("Wild Card", "Other"), wild.map { it.title })
        assertEquals(listOf(ranked), wild[0].rows)
        assertEquals(listOf(unranked), wild[1].rows)
        wild.forEach { assertNull(it.playoffCutAfter) }
    }

    @Test
    fun aTeamWithNoConferenceIsOnNoConferencePageInAnyMode() {
        // It cannot be on either page without being wrong on one of them; the league page still shows it.
        val unknown = rows.take(2).map { it.copy(conference = Conference.Unknown("new")) }
        listOf(StandingsView.Eastern, StandingsView.Western).forEach { view ->
            StandingsMode.entries.forEach { mode ->
                assertEquals(emptyList<StandingsSection>(), StandingsGrouping.group(unknown, view, mode).sections)
            }
        }
        assertEquals(
            2,
            StandingsGrouping
                .group(unknown, StandingsView.League)
                .sections
                .single()
                .rows.size,
        )
    }

    @Test
    fun wildcardOmitsMissingDivisionsAndEmptyBlocks() {
        val sections = StandingsGrouping.group(rows.takeLast(8), StandingsView.Western, StandingsMode.Wildcard).sections
        assertEquals(listOf("Pacific", "Wild Card"), sections.map { it.title })
    }

    @Test
    fun wildcardCutRequiresAtLeastTwoRows() {
        val top = rows.takeLast(3).mapIndexed { index, row -> row.copy(divisionSequence = index + 1) }
        val wild = rows[24].copy(divisionSequence = 4, wildcardSequence = 1)
        assertNull(
            StandingsGrouping
                .group(top + wild, StandingsView.Western, StandingsMode.Wildcard)
                .sections
                .last()
                .playoffCutAfter,
        )
        val two = top + wild + wild.copy(divisionSequence = 5, wildcardSequence = 2)
        assertEquals(
            1,
            StandingsGrouping
                .group(two, StandingsView.Western, StandingsMode.Wildcard)
                .sections
                .last()
                .playoffCutAfter,
        )
    }

    @Test
    fun emptyInputHasNoSectionsInAnyCombination() {
        StandingsView.entries.forEach { view ->
            StandingsMode.entries.forEach { mode ->
                assertEquals(emptyList<StandingsSection>(), StandingsGrouping.group(emptyList(), view, mode).sections)
            }
        }
    }

    @Test
    fun duplicateSequencesKeepInputOrderInEveryCombination() {
        val ties = rows.take(8).map { it.copy(leagueSequence = 1, conferenceSequence = 1, divisionSequence = 1, wildcardSequence = 1) }
        StandingsView.entries.forEach { view ->
            StandingsMode.entries.forEach { mode ->
                val sections = StandingsGrouping.group(ties, view, mode).sections
                sections.forEach { section ->
                    val positions = section.rows.map { ties.indexOf(it) }
                    assertEquals("${view.name}/${mode.name}/${section.title}", positions.sorted(), positions)
                }
            }
        }
    }
}
