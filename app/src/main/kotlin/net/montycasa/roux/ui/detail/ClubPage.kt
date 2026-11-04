@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.runtime.Composable
import net.montycasa.roux.domain.model.GameDetail
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.TeamNames

/**
 * All three player groups in one card, forwards first.
 *
 * A reader looking at one club's night wants the whole roster, not three cards that each carry half a heading. The
 * groups come from the boxscore's own lists, so a defenceman is grouped by the list the feed put him in rather than by
 * his position code. The stat columns scroll sideways together with their headers because eight columns plus a name do
 * not fit 360 dp, and shrinking the type to make them fit is the answer that failed the last time it was tried.
 */
@Composable
internal fun TeamDestination(
    detail: GameDetail,
    team: TeamRef,
    onPlayerClick: (Long) -> Unit = {},
    /** The season the game belongs to, for the mugshot path. */
    seasonStartYear: Int? = null,
) {
    val boxscore = detail.boxscore
    val goalies =
        boxscore.data
            ?.goalies
            ?.filter { it.goalie.teamAbbrev == team.abbrev }
            .orEmpty()
    val forwards =
        boxscore.data
            ?.forwards
            ?.filter { it.skater.teamAbbrev == team.abbrev }
            .orEmpty()
    val defense =
        boxscore.data
            ?.defense
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
        hideWhenEmpty = { goalies.isEmpty() && forwards.isEmpty() && defense.isEmpty() },
        failureLabel = "Player stats could not be loaded",
    ) { _ ->
        PlayerTables(
            goalies = goalies,
            forwards = forwards,
            defense = defense,
            onPlayerClick = onPlayerClick,
            seasonStartYear = seasonStartYear,
        )
    }
}
