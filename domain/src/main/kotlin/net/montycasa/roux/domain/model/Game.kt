package net.montycasa.roux.domain.model

import kotlin.time.Instant

/** An opaque identifier for a game in the NHL feed. */
@JvmInline
value class GameId(
    val value: String,
)

/** The feed's lifecycle status, including values the application does not yet recognise. */
sealed interface GameState {
    /** A game in progress, optionally flagged as critical by the feed. */
    data class Live(
        val critical: Boolean = false,
    ) : GameState

    /** A future game awaiting pregame coverage. */
    data object Scheduled : GameState

    /** A game in its pregame phase. */
    data object Pre : GameState

    /** A completed game, including one marked officially finished. */
    data object Final : GameState

    /** An unrecognised status preserved exactly as received for adapter diagnostics. */
    data class Unknown(
        val raw: String,
    ) : GameState

    companion object {
        /** Matches trimmed feed codes case-sensitively; unknown inputs remain [Unknown] unchanged. */
        fun fromFeed(raw: String): GameState =
            when (raw.trim()) {
                "LIVE" -> Live()
                "CRIT" -> Live(critical = true)
                "FUT" -> Scheduled
                "PRE" -> Pre
                "FINAL", "OFF" -> Final
                else -> Unknown(raw)
            }
    }
}

/** Whether a game ended in regulation, overtime, or a shootout. */
enum class FinalKind {
    REG,
    OT,
    SO,
    ;

    companion object {
        /** Matches trimmed feed codes case-sensitively; missing or unrecognised values return null. */
        fun from(raw: String?): FinalKind? = entries.firstOrNull { it.name == raw?.trim() }
    }
}

/**
 * The current period and its remaining clock, when supplied by the feed.
 *
 * The adapter maps periodDescriptor.number to [ordinal], periodDescriptor.periodType to [periodType],
 * clock.timeRemaining (mm:ss) to [clock], and clock.inIntermission to [inIntermission]. Absent
 * descriptors/clocks on future games must not require invented values.
 */
data class PeriodInfo(
    val ordinal: Int,
    /** Raw feed token (REG/OT/SO), not display text; the UI must map it for display. */
    val periodType: String?,
    val clock: String?,
    val inIntermission: Boolean = false,
    /**
     * Feed clock.running; null when the clock is absent. This is the only reliable in-progress
     * signal: finished games keep their period and can keep a non-zero remaining time.
     */
    val running: Boolean? = null,
)

/** A day's matchup, with optional scores and progress details from the NHL feed. */
data class Game(
    val id: GameId,
    val date: String, // NHL gameDate, in ISO YYYY-MM-DD format.
    val state: GameState,
    val home: TeamRef,
    val away: TeamRef,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val finalKind: FinalKind? = null,
    val period: PeriodInfo? = null,
    /** Parsed feed startTimeUTC instant; null when the start time is absent or unreadable. */
    val startTimeUtc: Instant? = null,
    /** Null means unavailable; a supplied zero is a real shot count. */
    val homeShots: Int? = null,
    val awayShots: Int? = null,
) {
    // Validation of feed identifiers, dates, and missing or unexpected values belongs
    // to the adapter layer; these value types do not reject messy NHL data.
    val isLive: Boolean get() = state is GameState.Live
}

/** The number of games advertised for an ISO YYYY-MM-DD date in the feed's date index. */
data class DateIndexEntry(
    val date: String,
    val numberOfGames: Int,
)

/** A scoreboard for an ISO YYYY-MM-DD date, with navigation and the adapter's dropped-game count. */
data class ScoreDay(
    val date: String,
    val games: List<Game>,
    val dateIndex: List<DateIndexEntry>,
    val droppedCount: Int,
)
