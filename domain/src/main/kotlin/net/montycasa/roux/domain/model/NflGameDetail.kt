package net.montycasa.roux.domain.model

import kotlin.time.Instant

/**
 * One football game's page, from ESPN's single `summary` payload.
 *
 * Unlike hockey's page there is one endpoint, so this is one value rather than a [Loadable] per section. What the page
 * shows is only what the payload states: the hero carries each club's overall record when the feed sends one, the
 * quarter list comes from the scoring plays rather than the status, and every team statistic is the feed's own display
 * string.
 */
data class NflGameDetail(
    val status: NflGameStatus,
    val away: NflGameClub,
    val home: NflGameClub,
    /** The scoring plays, grouped by period and ordered by period, in payload order within each group. */
    val scoring: List<NflScoringQuarter> = emptyList(),
    /** The fixed subset of the boxscore's team statistics, in page order. A missing side is null, never zero. */
    val teamStats: List<NflTeamStatComparison> = emptyList(),
    /** One entry per club the feed named, in feed order. A club with no category keeps its empty entry. */
    val leaders: List<NflTeamLeaders> = emptyList(),
)

/**
 * One side of the scoreboard. [score] is null before the game has started and when the feed states none.
 *
 * [record] is the competitor's overall record exactly as the feed words it (`4-6`, `1-0`). Null when the feed states
 * none, so the hero omits the line rather than inventing `0-0`.
 */
data class NflGameClub(
    val team: TeamRef,
    val score: Int? = null,
    val record: String? = null,
)

/**
 * Where the game stands, with the feed's own wording.
 *
 * [detail] is `status.type.detail` verbatim: `Final` or `Final/OT` on a finished game, and the feed's scheduled wording
 * otherwise. The page prints it as given; the only fallback it supplies is the generic word for the state when the feed
 * omitted the detail. A kickoff is carried as an instant because formatting it belongs to the screen, not to this
 * module.
 */
sealed interface NflGameStatus {
    val detail: String?

    data class Final(
        override val detail: String?,
    ) : NflGameStatus

    data class Live(
        override val detail: String?,
        /** The period number while the game is being played; null when the feed states none. */
        val period: Int? = null,
        val clock: String? = null,
    ) : NflGameStatus

    data class Scheduled(
        override val detail: String?,
        val startTimeUtc: Instant? = null,
    ) : NflGameStatus

    data class Unknown(
        override val detail: String?,
    ) : NflGameStatus
}

/** One quarter's scoring plays. [number] is the payload's `period.number`; 1-4 are regulation and 5+ overtime. */
data class NflScoringQuarter(
    val number: Int,
    val plays: List<NflScoringPlay>,
)

/**
 * One scoring play.
 *
 * [clock] is the feed's `clock.displayValue` (`"3:39"`), kept verbatim. [awayScore] and [homeScore] are the running
 * score immediately after the play, as published, never replayed from the plays before it.
 */
data class NflScoringPlay(
    val clock: String,
    val teamAbbrev: String,
    val isHome: Boolean,
    val text: String,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
)

/**
 * One row of the fixed team-stat comparison.
 *
 * [name] is the feed's statistic name, which is what selects the row. [away] and [home] are the feed's `displayValue`
 * strings exactly as sent - `"8-17"` stays a ratio and `"31:35"` stays a clock - and a side the feed did not state is
 * null so the screen can leave it blank rather than print a zero.
 */
data class NflTeamStatComparison(
    val name: String,
    val away: String? = null,
    val home: String? = null,
)

/** One club's leaders, in the order the feed lists its categories. */
data class NflTeamLeaders(
    val teamAbbrev: String,
    val categories: List<NflLeaderCategory> = emptyList(),
)

/** One leader category. [displayName] is the feed's heading; [leaders] keeps its entries in payload order. */
data class NflLeaderCategory(
    val name: String,
    val displayName: String,
    val leaders: List<NflLeader> = emptyList(),
)

/** One athlete's line in a leader category. [displayValue] is printed verbatim. */
data class NflLeader(
    val athleteName: String,
    val displayValue: String,
)
