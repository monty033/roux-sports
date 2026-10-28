package net.montycasa.roux.domain.model

import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import kotlin.time.Instant

/**
 * A block of a game page. Declaration order is page order, and no caller may reorder it.
 *
 * The five sections come from all three endpoints, and the mapping is not the obvious one: period scoring and the three
 * stars come from the landing payload, goalies from the boxscore, and shots and power play from the right rail. That is
 * where the league actually puts them - the boxscore carries no scoring at all - and the split is why a page can still
 * show goals when the boxscore request fails.
 */
enum class GameSection {
    PeriodScoring,
    Shots,
    PowerPlay,
    Goalies,
    ThreeStars,
}

/**
 * One game's page: three independently loaded endpoints, each with its own freshness and its own failure.
 *
 * One [Loadable] per endpoint rather than one for the page, because the endpoints fail separately and a fan would rather
 * read the goals than a blank page because the goalie table timed out. Nothing here merges them back into a single
 * verdict: the page's [sections] and [staleSince] answer for the parts that arrived.
 */
data class GameDetail(
    val landing: Loadable<GameLanding>,
    val boxscore: Loadable<GameBoxscore>,
    val rightRail: Loadable<GameRightRail>,
) {
    /**
     * The sections with something to show, in page order.
     *
     * Presence is decided by the payload, not by freshness: a stale boxscore still holds real goals, and hiding them
     * because a refresh failed would be a worse answer than showing them with the banner that says they are old.
     */
    fun sections(): List<GameSection> = GameSection.entries.filter { section -> section.isPresentIn(landing, boxscore, rightRail) }

    /**
     * Presence, per section, read from the endpoint that actually carries it.
     *
     * Filtering the declaration order - rather than appending as each payload is inspected - is what keeps page order
     * independent of source order. Appending per endpoint put the three stars directly after the goals, which is only
     * correct while every section came from one payload.
     */
    private fun GameSection.isPresentIn(
        landing: Loadable<GameLanding>,
        boxscore: Loadable<GameBoxscore>,
        rightRail: Loadable<GameRightRail>,
    ): Boolean =
        when (this) {
            GameSection.PeriodScoring -> landing.data?.periodScoring?.isNotEmpty() == true
            GameSection.ThreeStars -> landing.data?.threeStars?.isNotEmpty() == true
            GameSection.Shots -> rightRail.data?.shots != null
            GameSection.PowerPlay -> rightRail.data?.powerPlay != null
            GameSection.Goalies -> boxscore.data?.goalies?.isNotEmpty() == true
        }

    /**
     * The oldest timestamp among the sections that are stale, or null when none is.
     *
     * The oldest, because a page is only as fresh as its stalest section: reporting the newest would let a refreshed
     * boxscore disguise a landing payload from yesterday.
     */
    fun staleSince(): Instant? =
        listOf(landing, boxscore, rightRail)
            .mapNotNull { (it.freshness as? Freshness.Stale)?.since }
            .minOrNull()
}

/**
 * The landing payload: period scoring and the three stars.
 *
 * Both are empty for a game that has not started, which is the normal shape for most of the day rather than a failure -
 * the payload carries `matchup` instead of `summary`. Presence of these sections is therefore decided by what arrived,
 * never by how fresh the response was.
 */
data class GameLanding(
    val periodScoring: List<PeriodScoring> = emptyList(),
    val threeStars: List<ThreeStar> = emptyList(),
)

/** A star of the game, ranked 1..3 by the league. */
data class ThreeStar(
    val rank: Int,
    val player: PlayerRef,
)

/** A player named by the feed, with the club they played for in this game. */
data class PlayerRef(
    val name: String,
    val teamAbbrev: String = "",
)

/**
 * The boxscore payload: the goalie lines, and nothing else the page draws.
 *
 * It carries no scoring at all, which is worth stating because it is the natural place to look for it.
 */
data class GameBoxscore(
    val goalies: List<GoalieLine> = emptyList(),
)

/** Goals by period, for both clubs. */
data class PeriodScoring(
    val period: Int,
    val awayGoals: Int,
    val homeGoals: Int,
)

/** Shots on goal, both clubs. */
data class ShotTotals(
    val awayShots: Int,
    val homeShots: Int,
)

/** Power-play goals against chances, both clubs. */
data class PowerPlaySummary(
    val awayGoals: Int,
    val awayChances: Int,
    val homeGoals: Int,
    val homeChances: Int,
)

/** One goaltender's line. */
data class GoalieLine(
    val goalie: PlayerRef,
    val saves: Int,
    val shotsAgainst: Int,
)

/** The right rail payload: the per-team totals the page shows beside the boxscore, including shots and power play. */
data class GameRightRail(
    val shots: ShotTotals? = null,
    val powerPlay: PowerPlaySummary? = null,
    val teamStats: List<TeamStat> = emptyList(),
)

/** One labelled team total. */
data class TeamStat(
    val label: String,
    val away: String,
    val home: String,
)
