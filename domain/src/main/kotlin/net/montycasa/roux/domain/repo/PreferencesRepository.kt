package net.montycasa.roux.domain.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.ThemeMode
import kotlin.time.Instant

/**
 * Per-team controls for game alerts.
 *
 * Every switch starts disabled. An alert nobody asked for is worse than one they have to switch on, and the old
 * defaults were on for teams that could not alert at all. An upgrade must not start notifying anyone, which is why
 * the switches added later default off as well.
 */
data class TeamAlertPreferences(
    val start: Boolean = false,
    val goals: Boolean = false,
    val intermission: Boolean = false,
    val final: Boolean = false,
    val upcoming: Boolean = false,
) {
    /** Whether any switch is on: the one question that decides whether a team's games are followed at all. */
    val anyOn: Boolean get() = start || goals || intermission || final || upcoming
}

/**
 * The lead times the "game is about to start" alert may use, in minutes.
 *
 * A closed set rather than a free number: the setting is a choice between a few meaningful warnings, and a stored
 * value outside the set is read as [DEFAULT] instead of being trusted.
 */
object UpcomingLead {
    const val DEFAULT: Int = 30
    val allowed: List<Int> = listOf(15, 30, 60)

    /** [stored] if it is one of the legal lead times, otherwise [DEFAULT]. */
    fun coerce(stored: Int?): Int = if (stored in allowed) checkNotNull(stored) else DEFAULT
}

/**
 * Durable notification state. Persist this BEFORE posting to enforce at-most-once delivery.
 *
 * [lastNotifiedGoalCount] is the high-water score total that has been announced. See
 * [UNKNOWN_GOAL_COUNT] for a game first seen without a score.
 *
 * [lastIntermissionNotified] is the highest period whose intermission has been announced; 0 means none. The other
 * two flags read false for a game whose entry predates them.
 */
data class NotificationLedgerEntry(
    val lastNotifiedGoalCount: Int,
    val startNotified: Boolean,
    val updatedAt: Instant,
    val finalNotified: Boolean = false,
    val lastIntermissionNotified: Int = 0,
    val upcomingNotified: Boolean = false,
) {
    /** True when the game was first seen without a score, so no total has ever been established. */
    val goalCountIsUnknown: Boolean get() = lastNotifiedGoalCount == UNKNOWN_GOAL_COUNT

    companion object {
        /**
         * "No score has ever been observed for this game."
         *
         * A missing score is unknown, not zero: treating it as zero would let the first score the feed
         * later supplies be announced as a goal that was never seen happen. A total recorded as this
         * value must be replaced by a real observation before any goal can be claimed.
         */
        const val UNKNOWN_GOAL_COUNT: Int = -1
    }
}

/** Stores user preferences and notification deduplication state across app launches. */
interface PreferencesRepository {
    /** Saved theme choice; defaults to System when unset. */
    suspend fun getThemeMode(): ThemeMode

    /** Save a theme choice in the shared preferences store. */
    suspend fun setThemeMode(mode: ThemeMode)

    /** Saved theme as it changes, starting with the current value. */
    fun observeThemeMode(): Flow<ThemeMode> = flow { emit(getThemeMode()) }

    /** Minutes before puck drop that the upcoming-game alert fires: 15, 30 or 60, and 30 when unset or illegal. */
    suspend fun getUpcomingLeadMinutes(): Int

    /** Save the lead time. Only the values in [UpcomingLead.allowed] are legal; anything else is rejected. */
    suspend fun setUpcomingLeadMinutes(minutes: Int)

    /** The lead time as it changes, starting with the current value. */
    fun observeUpcomingLeadMinutes(): Flow<Int> = flow { emit(getUpcomingLeadMinutes()) }

    /** Read the saved favourite teams; an empty set means none are selected. */
    suspend fun getFavourites(): Set<TeamKey>

    /** Replace all saved favourite teams. */
    suspend fun setFavourites(teams: Set<TeamKey>)

    /** Whether to show only favourites; defaults to true when unset. */
    suspend fun getFavouritesOnly(): Boolean

    /** Set whether to show only favourite teams. */
    suspend fun setFavouritesOnly(enabled: Boolean)

    /** Read the saved league order, or the canonical order when unset. */
    suspend fun getLeagueOrder(): List<String>

    /** Replace the saved league order. */
    suspend fun setLeagueOrder(order: List<String>)

    /** Read alert controls for a team, returning disabled defaults when unset. */
    suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences

    /**
     * Whether any team has any alert switched on. A local read that lets start-up skip the slate when nothing
     * could be wanted; the default answers true, which only costs a read.
     */
    suspend fun anyTeamAlertsOn(): Boolean = true

    /** Save alert controls for one team. */
    suspend fun setTeamAlertPreferences(
        team: TeamKey,
        preferences: TeamAlertPreferences,
    )

    /**
     * Favourites as they change, starting with what is stored now.
     *
     * The default body reads once and completes, which is honest for a store that cannot notify: a
     * caller that needs live updates gets a stale value rather than a lie about the future. The
     * DataStore implementation overrides these flows to observe the file.
     */
    fun observeFavourites(): Flow<Set<TeamKey>> = flow { emit(getFavourites()) }

    /** Whether to show only favourites, as it changes. See [observeFavourites] for the default body. */
    fun observeFavouritesOnly(): Flow<Boolean> = flow { emit(getFavouritesOnly()) }

    /** The league order as it changes. See [observeFavourites] for the default body. */
    fun observeLeagueOrder(): Flow<List<String>> = flow { emit(getLeagueOrder()) }

    /** Read a game's deduplication entry, or null if no notification state was saved. */
    suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry?

    /** Save deduplication state BEFORE posting a notification (at-most-once delivery). */
    suspend fun setNotificationLedgerEntry(
        gameId: String,
        entry: NotificationLedgerEntry,
    )

    /**
     * Atomically claim the goal alert for [goalCount]; true only for the caller that won it.
     *
     * The comparison and the write share one transaction, so two workers racing over the same goal
     * cannot both read "not yet notified" and both post. A count is claimable only when it is NEWER
     * than the stored one: an equal count is a duplicate, and a lower count is the feed correcting
     * itself downward, so neither is an event and neither may move the ledger backwards.
     *
     * False also covers a file written by a newer build, which this build must not write to, and a game
     * whose total is still [NotificationLedgerEntry.UNKNOWN_GOAL_COUNT]: nothing has been observed, so
     * nothing can be above it. A claim that cannot be taken is answered with silence rather than an
     * exception, because the caller's only reasonable response is to post nothing.
     */
    suspend fun claimGoal(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ): Boolean

    /**
     * Atomically claim the game-start alert; true only for the first caller.
     *
     * [goalBaseline] is written by the same transaction, so claiming the start also settles which
     * goals were already on the board. Without it the first goal alert after a start would fire for
     * goals scored before this app was watching. A baseline lower than what is already recorded never
     * rewrites it downwards: a late start claim must not un-announce a goal.
     */
    suspend fun claimStart(
        gameId: String,
        goalBaseline: Int,
        at: Instant,
    ): Boolean

    /**
     * Record a score baseline without claiming an alert; true when this call wrote it.
     *
     * A game first seen while its start alerts are switched off still needs a recorded total, or every
     * later goal would compare against nothing and be announced as the first one. This leaves
     * `startNotified` alone: nothing was announced, so nothing may say otherwise.
     *
     * It only fills a gap. If a complete entry exists - because another worker claimed first, or because
     * a goal has already been announced - this returns false and writes nothing: a baseline derived from
     * an older snapshot must not raise the total over a goal that is about to be claimed.
     */
    suspend fun claimBaseline(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ): Boolean

    /**
     * Atomically claim the final-score alert; true only for the first caller.
     *
     * A game with no complete entry gets one written alongside the claim, with an unknown goal total and the start
     * unclaimed. Without it the claim would be stored where [getNotificationLedgerEntry] cannot see it, and the alert
     * could repeat. False also covers a file written by a newer build.
     */
    suspend fun claimFinal(
        gameId: String,
        at: Instant,
    ): Boolean

    /**
     * Atomically claim the intermission alert for [period]; true only when it is higher than any announced so far.
     *
     * An equal period is a duplicate and a lower one is a stale observation; neither may move the record backwards.
     * See [claimFinal] for what happens to a game with no entry.
     */
    suspend fun claimIntermission(
        gameId: String,
        period: Int,
        at: Instant,
    ): Boolean

    /** Atomically claim the upcoming-game alert; true only for the first caller. See [claimFinal]. */
    suspend fun claimUpcoming(
        gameId: String,
        at: Instant,
    ): Boolean

    /**
     * False when the stored file was written by a newer build and cannot be interpreted here.
     *
     * Reads still answer with defaults in that case, so a caller that cares about the difference -
     * a settings screen that should not show "no favourites" as if the user had chosen that - asks
     * this first. Writes throw rather than silently lowering the file's version marker.
     */
    suspend fun isWritable(): Boolean

    /**
     * Remove ledger entries timestamped strictly before [olderThan].
     *
     * [keep] names games whose entries must survive the sweep regardless of age. It is required rather
     * than defaulted: a caller that does not say which games it is still watching cannot safely sweep. A game in progress can
     * outlive the retention window - a long overtime, a suspended game resumed the next day - and losing
     * its entry mid-game would let the next pass announce goals that were already announced.
     */
    suspend fun pruneNotificationLedger(
        olderThan: Instant,
        keep: Set<String>,
    )
}
