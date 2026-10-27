package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.StandingRow

enum class StandingsView { League, Conference, Division, Wildcard }

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
    private val conferences = listOf(Conference.Eastern to "Eastern", Conference.Western to "Western")
    private val divisions =
        listOf(
            Division.Atlantic to "Atlantic",
            Division.Metropolitan to "Metropolitan",
            Division.Central to "Central",
            Division.Pacific to "Pacific",
        )
    private val wildcardDivisions = listOf(divisions[0], divisions[1], divisions[3], divisions[2])

    fun group(
        rows: List<StandingRow>,
        view: StandingsView,
        pinned: List<StandingRow> = emptyList(),
    ): StandingsGroups {
        if (rows.isEmpty()) return StandingsGroups(emptyList())
        val sections =
            buildList {
                fun section(
                    title: String,
                    members: List<StandingRow>,
                    cut: Int? = null,
                ) {
                    if (members.isNotEmpty()) add(StandingsSection(title, members, cut))
                }
                section("Your teams", pinned.sortedBy { it.leagueSequence })
                when (view) {
                    StandingsView.League -> {
                        section("NHL", rows.sortedBy { it.leagueSequence })
                    }

                    StandingsView.Conference -> {
                        conferences.forEach { (conference, title) ->
                            section(title, rows.filter { it.conference == conference }.sortedBy { it.conferenceSequence })
                        }
                        section("Other", rows.filter { it.conference is Conference.Unknown }.sortedBy { it.leagueSequence })
                    }

                    StandingsView.Division -> {
                        divisions.forEach { (division, title) ->
                            section(title, rows.filter { it.division == division }.sortedBy { it.divisionSequence })
                        }
                        section("Other", rows.filter { it.division is Division.Unknown }.sortedBy { it.leagueSequence })
                    }

                    StandingsView.Wildcard -> {
                        // An unknown division cannot be placed in a division block. If the feed still ranks it, it
                        // is ranked with the wildcard teams - that is where its sequence says it belongs - and an
                        // unranked one falls through to "Other" below.
                        conferences.forEach { (conference, title) ->
                            val members = rows.filter { it.conference == conference }
                            // Track input positions rather than equality so even duplicate rows are retained.
                            val placed = mutableSetOf<Int>()
                            wildcardDivisions.forEach { (division, name) ->
                                val top =
                                    members.indices
                                        .filter { members[it].division == division }
                                        .sortedBy { members[it].divisionSequence }
                                        .take(3)
                                placed += top
                                section("$title — $name", top.map { members[it] })
                            }
                            val wild =
                                members.indices
                                    .filter { it !in placed && members[it].wildcardRank != null }
                                    .sortedBy { members[it].wildcardSequence }
                            placed += wild
                            section("$title — Wild Card", wild.map { members[it] }, 1.takeIf { wild.size >= 2 })
                            section(
                                "$title — Other",
                                members.indices
                                    .filter { it !in placed }
                                    .map { members[it] }
                                    .sortedBy { it.leagueSequence },
                            )
                        }
                        section("Other", rows.filter { it.conference is Conference.Unknown }.sortedBy { it.leagueSequence })
                    }
                }
            }
        return StandingsGroups(sections)
    }
}
