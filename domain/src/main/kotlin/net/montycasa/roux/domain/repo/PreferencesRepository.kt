package net.montycasa.roux.domain.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.montycasa.roux.domain.model.TeamKey
import kotlin.time.Instant

/** Per-team controls for game-start and goal alerts. Both are enabled by default. */
data class TeamAlertPreferences(
    val start: Boolean = true,
    val goals: Boolean = true,
)

/** Durable notification state. Persist this BEFORE posting to enforce at-most-once delivery. */
data class NotificationLedgerEntry(
    val lastNotifiedGoalCount: Int,
    val startNotified: Boolean,
    val updatedAt: Instant,
)

/** Stores user preferences and notification deduplication state across app launches. */
interface PreferencesRepository {
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

    /** Read alert controls for a team, returning enabled defaults when unset. */
    suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences

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
     * DataStore implementation overrides all three to observe the file.
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
     * False also covers a file written by a newer build, which this build must not write to. A claim
     * that cannot be taken is answered with silence rather than an exception, because the caller's
     * only reasonable response is to post nothing.
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
     */
    suspend fun claimBaseline(
        gameId: String,
        goalCount: Int,
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
     * [keep] names games whose entries must survive the sweep regardless of age. A game in progress can
     * outlive the retention window - a long overtime, a suspended game resumed the next day - and losing
     * its entry mid-game would let the next pass announce goals that were already announced.
     */
    suspend fun pruneNotificationLedger(
        olderThan: Instant,
        keep: Set<String> = emptySet(),
    )
}
