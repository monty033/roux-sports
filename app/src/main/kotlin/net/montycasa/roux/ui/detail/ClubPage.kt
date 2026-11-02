@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.Composable
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames

/**
 * Both lists in one card, goalies first.
 *
 * A reader looking at one club's night wants the whole roster, not two cards that each carry half a heading. The stat
 * columns scroll sideways together with their headers because eight columns plus a name do not fit 360 dp, and shrinking
 * the type to make them fit is the answer that failed the last time it was tried.
 */
@Composable
internal fun TeamDestination(
    detail: GameDetail,
    team: TeamRef,
) {
    val boxscore = detail.boxscore
    val goalies =
        boxscore.data
            ?.goalies
            ?.filter { it.goalie.teamAbbrev == team.abbrev }
            .orEmpty()
    val skaters =
        boxscore.data
            ?.skaters
            ?.filter { it.skater.teamAbbrev == team.abbrev }
            .orEmpty()

    val name = TeamNames.cityAndName(team).second
    SectionCard(
        // Crest, club, then what the table is: the heading names whose table it is at a glance.
        title = "$name Player Stats",
        tag = "detail.section.players",
        emphasised = true,
        headerCrest = team,
        loadable = boxscore,
        failureLabel = "Player stats could not be loaded",
    ) { _ ->
        if (goalies.isEmpty() && skaters.isEmpty()) {
            EmptySectionLine("No player lines for this club")
        } else {
            PlayerTables(goalies = goalies, skaters = skaters)
        }
    }
}
