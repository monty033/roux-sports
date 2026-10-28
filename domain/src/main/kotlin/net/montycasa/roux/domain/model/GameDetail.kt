package net.montycasa.roux.domain.model

import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import kotlin.time.Instant

/**
 * A block of a game page. Declaration order is page order, and no caller may reorder it.
 *
 * The five sections come from two endpoints: the first four from the boxscore, three stars from the landing payload. That
 * split is why a page can show a scoreless-but-complete game when one of the two requests fails.
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
    fun sections(): List<GameSection> =
        buildList {
            boxscore.data?.let { box ->
                if (box.periodScoring.isNotEmpty()) add(GameSection.PeriodScoring)
                if (box.shots != null) add(GameSection.Shots)
                if (box.powerPlay != null) add(GameSection.PowerPlay)
                if (box.goalies.isNotEmpty()) add(GameSection.Goalies)
            }
            landing.data?.let { landing -> if (landing.threeStars.isNotEmpty()) add(GameSection.ThreeStars) }
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
 * The landing payload: the three stars, which is the only section of it the page renders as a block.
 *
 * Field shapes here are provisional - G1 captures the real endpoints, and this model carries only what the page needs to
 * decide whether a section exists and what to label it.
 */
data class GameLanding(
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

/** The boxscore payload: the four sections the page draws between the header and the three stars. */
data class GameBoxscore(
    val periodScoring: List<PeriodScoring> = emptyList(),
    val shots: ShotTotals? = null,
    val powerPlay: PowerPlaySummary? = null,
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

/** The right rail payload: per-team totals the page shows beside the boxscore. */
data class GameRightRail(
    val teamStats: List<TeamStat> = emptyList(),
)

/** One labelled team total. */
data class TeamStat(
    val label: String,
    val away: String,
    val home: String,
)
