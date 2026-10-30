package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.StandingRow

/**
 * The first level: which part of the league, in the order the strip shows them. The conferences come first because
 * that is what a reader following a team wants; the whole league is the recap you check last.
 */
enum class StandingsView { Eastern, Western, League }

/**
 * The second level: which ranking inside a conference, in strip order - the playoff picture first, then the division
 * tables, then the plain conference ranking. One mode shared by both conferences, so swiping from East to West
 * compares the same table instead of changing two things at once.
 */
enum class StandingsMode { Playoffs, Division, Conference }

data class StandingsSection(
    val title: String,
    val rows: List<StandingRow>,
    /** 0-based index in [rows] after which the playoff cut is drawn, or null for no cut. */
    val playoffCutAfter: Int? = null,
)

data class StandingsGroups(
    val sections: List<StandingsSection>,
)

/** The feed owns ranking and tie-breaking; every sort here is stable and uses its sequence. */
object StandingsGrouping {
    private val divisions =
        listOf(
            Division.Atlantic to "Atlantic",
            Division.Metropolitan to "Metropolitan",
            Division.Central to "Central",
            Division.Pacific to "Pacific",
        )

    /** Eastern divisions first, then Western; Pacific before Central follows the league's own wildcard table. */
    private val divisionOrder = listOf(divisions[0], divisions[1], divisions[3], divisions[2])

    private val conferenceDivisions =
        mapOf(
            Conference.Eastern to listOf(divisions[0], divisions[1]),
            Conference.Western to listOf(divisions[2], divisions[3]),
        )

    fun group(
        rows: List<StandingRow>,
        view: StandingsView,
        mode: StandingsMode = StandingsMode.Playoffs,
    ): StandingsGroups {
        if (rows.isEmpty()) return StandingsGroups(emptyList())
        val sections =
            when (view) {
                StandingsView.League -> listOf(StandingsSection("NHL", rows.sortedBy { it.leagueSequence }))
                StandingsView.Eastern -> conferencePage(rows, Conference.Eastern, mode)
                StandingsView.Western -> conferencePage(rows, Conference.Western, mode)
            }
        return StandingsGroups(sections)
    }

    private fun conferencePage(
        rows: List<StandingRow>,
        conference: Conference,
        mode: StandingsMode,
    ): List<StandingsSection> {
        val members = rows.filter { it.conference == conference }
        if (members.isEmpty()) return emptyList()
        return when (mode) {
            StandingsMode.Playoffs -> wildcardRace(members)

            StandingsMode.Division -> divisionTables(members, conference)

            // The page already says which conference it is, so the ranking needs no heading of its own.
            StandingsMode.Conference -> listOf(StandingsSection("", members.sortedBy { it.conferenceSequence }))
        }
    }

    private fun divisionTables(
        members: List<StandingRow>,
        conference: Conference,
    ): List<StandingsSection> {
        val sections = mutableListOf<StandingsSection>()
        conferenceDivisions[conference].orEmpty().forEach { (division, title) ->
            val block = members.filter { it.division == division }.sortedBy { it.divisionSequence }
            if (block.isNotEmpty()) sections += StandingsSection(title, block)
        }
        val other = members.filter { it.division is Division.Unknown }.sortedBy { it.leagueSequence }
        if (other.isNotEmpty()) sections += StandingsSection("Other", other)
        return sections
    }

    /**
     * The three division places that are in, the wild card race with the cut after the second team, then anything the
     * feed ranked that fits neither block.
     *
     * An unknown division cannot be placed in a division block. If the feed still ranks it, it is ranked with the
     * wildcard teams - that is where its sequence says it belongs - and an unranked one falls through to "Other".
     */
    private fun wildcardRace(members: List<StandingRow>): List<StandingsSection> {
        val sections = mutableListOf<StandingsSection>()
        // Track input positions rather than equality so even duplicate rows are retained.
        val placed = mutableSetOf<Int>()
        divisionOrder.forEach { (division, title) ->
            val top =
                members.indices
                    .filter { members[it].division == division }
                    .sortedBy { members[it].divisionSequence }
                    .take(3)
            placed += top
            if (top.isNotEmpty()) sections += StandingsSection(title, top.map { members[it] })
        }
        val wild =
            members.indices
                .filter { it !in placed && members[it].wildcardRank != null }
                .sortedBy { members[it].wildcardSequence }
        placed += wild
        if (wild.isNotEmpty()) {
            sections += StandingsSection("Wild Card", wild.map { members[it] }, 1.takeIf { wild.size >= 2 })
        }
        val rest =
            members.indices
                .filter { it !in placed }
                .map { members[it] }
                .sortedBy { it.leagueSequence }
        if (rest.isNotEmpty()) sections += StandingsSection("Other", rest)
        return sections
    }
}
