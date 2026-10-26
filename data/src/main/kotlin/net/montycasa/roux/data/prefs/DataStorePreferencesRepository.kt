package net.montycasa.roux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path
import kotlin.time.Instant

/**
 * Preferences and notification state in a single `roux.preferences_pb` file.
 *
 * The directory is supplied by the caller rather than derived from an Android context, so this stays
 * a plain JVM type that a desktop or test target can use without an Android dependency.
 *
 * This is durable user state, so corruption is handled rather than thrown: a damaged file falls back
 * to defaults instead of making every later launch fail.
 *
 * Reads never write. A version is stamped by the first write that stores anything, so a missing
 * version means the file predates versioning and is read as v1 - not that it should be rewritten.
 */
class DataStorePreferencesRepository(
    directory: Path,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PreferencesRepository {
    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { directory.resolve(FILE_NAME).toFile() },
        )

    override suspend fun getFavourites(): Set<TeamKey> =
        store.data
            .first()[FAVOURITES]
            .orEmpty()
            .mapTo(mutableSetOf(), ::TeamKey)

    override suspend fun setFavourites(teams: Set<TeamKey>) {
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            preferences[FAVOURITES] = teams.mapTo(mutableSetOf()) { it.abbrev }
        }
    }

    override suspend fun getFavouritesOnly(): Boolean = store.data.first()[FAVOURITES_ONLY] ?: DEFAULT_FAVOURITES_ONLY

    override suspend fun setFavouritesOnly(enabled: Boolean) {
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            preferences[FAVOURITES_ONLY] = enabled
        }
    }

    override suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences {
        val preferences = store.data.first()
        return TeamAlertPreferences(
            start = preferences[startKey(team)] ?: true,
            goals = preferences[goalsKey(team)] ?: true,
        )
    }

    override suspend fun setTeamAlertPreferences(
        team: TeamKey,
        preferences: TeamAlertPreferences,
    ) {
        store.edit { stored ->
            stored[VERSION] = PREFERENCES_VERSION
            stored[startKey(team)] = preferences.start
            stored[goalsKey(team)] = preferences.goals
        }
    }

    override suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry? {
        val preferences = store.data.first()
        // Half an entry is not evidence of a notification. Without all three fields there is no
        // record, because reporting "already notified" from a partial write would drop a real alert.
        val goalCount = preferences[goalCountKey(gameId)] ?: return null
        val startNotified = preferences[startNotifiedKey(gameId)] ?: return null
        val updatedAt = preferences[updatedAtKey(gameId)] ?: return null
        return NotificationLedgerEntry(
            lastNotifiedGoalCount = goalCount,
            startNotified = startNotified,
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        )
    }

    override suspend fun setNotificationLedgerEntry(
        gameId: String,
        entry: NotificationLedgerEntry,
    ) {
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            preferences[goalCountKey(gameId)] = entry.lastNotifiedGoalCount
            preferences[startNotifiedKey(gameId)] = entry.startNotified
            preferences[updatedAtKey(gameId)] = entry.updatedAt.toEpochMilliseconds()
        }
    }

    override suspend fun pruneNotificationLedger(olderThan: Instant) {
        val cutoff = olderThan.toEpochMilliseconds()
        store.edit { preferences ->
            // The update stamp is the key whose name carries the game id, so it drives the sweep and
            // the other two keys are removed alongside it.
            val expired =
                preferences
                    .asMap()
                    .keys
                    .filter { key -> key.name.startsWith(UPDATED_AT_PREFIX) }
                    .mapNotNull { key ->
                        val stamp = preferences[longPreferencesKey(key.name)]
                        val gameId = key.name.removePrefix(UPDATED_AT_PREFIX)
                        if (stamp != null && stamp < cutoff) gameId else null
                    }
            if (expired.isEmpty()) return@edit
            preferences[VERSION] = PREFERENCES_VERSION
            for (gameId in expired) {
                preferences.remove(updatedAtKey(gameId))
                preferences.remove(goalCountKey(gameId))
                preferences.remove(startNotifiedKey(gameId))
            }
        }
    }

    /**
     * Game ids are feed identifiers embedded in key names so the ledger can be swept by age.
     * Rejecting anything outside a safe alphabet stops one id from forging keys that belong to
     * another game, which would corrupt dedup state rather than merely fail to store.
     */
    private fun requireUsableGameId(gameId: String): String {
        require(GAME_ID.matches(gameId)) {
            "game id must be 1..64 characters of [A-Za-z0-9._-]"
        }
        return gameId
    }

    private fun startKey(team: TeamKey) = booleanPreferencesKey("team_${team.abbrev}_alerts_start")

    private fun goalsKey(team: TeamKey) = booleanPreferencesKey("team_${team.abbrev}_alerts_goals")

    private fun goalCountKey(gameId: String) = intPreferencesKey(GOAL_COUNT_PREFIX + requireUsableGameId(gameId))

    private fun startNotifiedKey(gameId: String) = booleanPreferencesKey(START_NOTIFIED_PREFIX + requireUsableGameId(gameId))

    private fun updatedAtKey(gameId: String) = longPreferencesKey(UPDATED_AT_PREFIX + requireUsableGameId(gameId))

    private companion object {
        const val FILE_NAME = "roux.preferences_pb"
        const val PREFERENCES_VERSION = 1
        const val DEFAULT_FAVOURITES_ONLY = true

        const val GOAL_COUNT_PREFIX = "ledger_goals_"
        const val START_NOTIFIED_PREFIX = "ledger_start_"
        const val UPDATED_AT_PREFIX = "ledger_time_"

        val GAME_ID = Regex("[A-Za-z0-9._-]{1,64}")

        val VERSION = intPreferencesKey("prefs_version")
        val FAVOURITES = stringSetPreferencesKey("favourites_abbreviations")
        val FAVOURITES_ONLY = booleanPreferencesKey("favourites_only")
    }
}
