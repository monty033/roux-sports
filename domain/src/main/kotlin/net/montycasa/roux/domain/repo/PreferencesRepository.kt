package net.montycasa.roux.domain.repo

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

    /** Read alert controls for a team, returning enabled defaults when unset. */
    suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences

    /** Save alert controls for one team. */
    suspend fun setTeamAlertPreferences(
        team: TeamKey,
        preferences: TeamAlertPreferences,
    )

    /** Read a game's deduplication entry, or null if no notification state was saved. */
    suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry?

    /** Save deduplication state BEFORE posting a notification (at-most-once delivery). */
    suspend fun setNotificationLedgerEntry(
        gameId: String,
        entry: NotificationLedgerEntry,
    )

    /**
     * False when the stored file was written by a newer build and cannot be interpreted here.
     *
     * Reads still answer with defaults in that case, so a caller that cares about the difference -
     * a settings screen that should not show "no favourites" as if the user had chosen that - asks
     * this first. Writes throw rather than silently lowering the file's version marker.
     */
    suspend fun isWritable(): Boolean

    /** Remove ledger entries timestamped strictly before [olderThan]. */
    suspend fun pruneNotificationLedger(olderThan: Instant)
}
