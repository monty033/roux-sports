package net.montycasa.roux.domain.model

import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import kotlin.math.roundToInt
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
 * The landing payload: the current moment, period scoring and the three stars.
 *
 * Both are empty for a game that has not started, which is the normal shape for most of the day rather than a failure -
 * the payload carries `matchup` instead of `summary`. Presence of these sections is therefore decided by what arrived,
 * never by how fresh the response was.
 */
data class GameLanding(
    val periodScoring: List<PeriodScoring> = emptyList(),
    val threeStars: List<ThreeStar> = emptyList(),
    val period: PeriodInfo? = null,
    val finalKind: FinalKind? = null,
    val state: GameState? = null,
    val venue: Venue? = null,
    val broadcasts: List<Broadcast> = emptyList(),
    /** Every goal the feed named, in the order it arrived. The period table is counted separately. */
    val goals: List<GoalEvent> = emptyList(),
)

data class Venue(
    val name: String,
    val city: String?,
)

data class Broadcast(
    val network: String,
    val country: String?,
    val market: String?,
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
 * The boxscore payload: both clubs' skaters, and the goalie lines.
 *
 * It carries no scoring at all, which is worth stating because it is the natural place to look for it. Skaters arrive
 * in feed order - forwards first, then defencemen, as the league lists them - and that order is kept rather than sorted,
 * so the page reads the same as the source. Goalies who did not play are excluded by the adapter, not here.
 */
data class GameBoxscore(
    val goalies: List<GoalieLine> = emptyList(),
    val skaters: List<SkaterLine> = emptyList(),
)

/** Goals by period, for both clubs. */
data class PeriodScoring(
    val period: Int,
    val awayGoals: Int,
    val homeGoals: Int,
)

/**
 * How a goal was scored, as far as the page can label it.
 *
 * [Unknown] keeps the goal rather than the label: an unfamiliar strength token is a feed change, not evidence that a
 * goal did not happen, so the event is still listed and simply carries no tag.
 */
enum class GoalStrength { EvenStrength, PowerPlay, ShortHanded, Unknown }

/** A goal's modifier, the second tag beside its strength. [Other] is recognised-but-unlabelled, like [GoalStrength.Unknown]. */
enum class GoalModifier { None, EmptyNet, PenaltyShot, Other }

/** An assist as of that goal. [assistsToDate] is the season total the feed published, never a count of this page's rows. */
data class GoalAssist(
    val name: String,
    val assistsToDate: Int?,
)

/**
 * One goal, with everything the feed already sends and the page used to throw away.
 *
 * [timeInPeriod] is kept as the feed writes it (`"10:58"`), because it is a display value and a period's clock is not
 * an instant on a single timeline. [awayScore] and [homeScore] are the running score immediately after this goal, as
 * published - never replayed from the goals before it. [goalsToDate] is likewise the scorer's season total as of this
 * goal, not a count of this game.
 */
data class GoalEvent(
    val period: Int,
    val timeInPeriod: String,
    val isHome: Boolean,
    val scorer: String,
    val goalsToDate: Int?,
    val assists: List<GoalAssist>,
    val strength: GoalStrength,
    val modifier: GoalModifier,
    val awayScore: Int?,
    val homeScore: Int?,
)

/** Shots on goal, both clubs. */
data class ShotTotals(
    val awayShots: Int,
    val homeShots: Int,
)

/**
 * One period's shots, both clubs.
 *
 * Kept beside the [ShotTotals] rather than merged into it because the card reads across periods and the published
 * total is not required to equal their sum. [periodType] is the feed's own token (`REG`, `OT`) and is kept as it
 * arrived; the page decides how to label a column.
 */
data class PeriodShots(
    val period: Int,
    val periodType: String?,
    val away: Int,
    val home: Int,
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
    /** Goals allowed. Needed for a goals-against average, which is not in the payload. */
    val goalsAgainst: Int = 0,
    /** Ice time in seconds; `00:00` for a goalie who dressed but never played. */
    val toiSeconds: Long = 0,
) {
    /**
     * Goals-against average: goals allowed per 60 minutes played.
     *
     * The league does not publish this in the boxscore, so it is derived, and the denominator is the goalie's own ice
     * time rather than the game length - a goalie pulled after two periods has a worse average for the same goals.
     * Null when there is no ice time, because dividing by it would produce an average for someone who did not play.
     */
    fun goalsAgainstAverage(): Double? = if (toiSeconds > 0) goalsAgainst * SECONDS_PER_HOUR.toDouble() / toiSeconds else null

    /** Save percentage over shots faced, or null when no shot was faced - not 0%, which would be a false claim. */
    fun savePercentage(): Double? = if (shotsAgainst > 0) saves.toDouble() / shotsAgainst else null

    private companion object {
        const val SECONDS_PER_HOUR = 3600
    }
}

/**
 * One skater's line for this game.
 *
 * Only the columns the page draws are kept. The payload carries several more - shifts, giveaways, takeaways, blocked
 * shots - and they are deliberately not modelled: a domain field nobody reads is a field that silently rots.
 */
data class SkaterLine(
    val skater: PlayerRef,
    /** The league's position code: C, L, R or D. */
    val position: String,
    val goals: Int,
    val assists: Int,
    val points: Int,
    val plusMinus: Int,
    val shotsOnGoal: Int,
    val penaltyMinutes: Int,
    val toiSeconds: Long,
    /** Faceoff wins as a fraction 0..1, or null for a skater who did not take a draw. */
    val faceoffFraction: Double?,
) {
    /** Faceoff percentage as a whole number, or null when no draw was taken. */
    fun faceoffPercent(): Int? = faceoffFraction?.let { (it * 100).roundToInt() }
}

/** The right rail payload: the per-team totals the page shows beside the boxscore, including shots and power play. */
data class GameRightRail(
    val shots: ShotTotals? = null,
    /**
     * Shots period by period, ordered by period.
     *
     * Empty when the feed omitted the array or every entry was unusable. It is deliberately independent of [shots]:
     * the published total is the authority for the `T` column, and a feed that omits one period must not make the card
     * silently present a smaller total as the game's total.
     */
    val shotsByPeriod: List<PeriodShots> = emptyList(),
    val powerPlay: PowerPlaySummary? = null,
    val teamStats: List<TeamStat> = emptyList(),
)

/** One labelled team total. */
data class TeamStat(
    val label: String,
    val away: String,
    val home: String,
)
