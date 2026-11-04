package net.montycasa.roux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.policy.LeagueOrder
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import net.montycasa.roux.domain.repo.UpcomingLead
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
 *
 * A file written by a NEWER build is never overwritten. Writes are refused rather than merged, and
 * reads answer with defaults: this build cannot know what those keys mean, and resetting the marker
 * would let a later upgrade mistake an old file for a current one. Losing an unreadable file is bad;
 * silently rewriting it so the newer build's data disappears is worse.
 *
 * One instance per file per process. DataStore enforces this itself and throws if a second live
 * instance is opened over the same path, so wiring must construct one repository and share it.
 */
class DataStorePreferencesRepository(
    directory: Path,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PreferencesRepository {
    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            // The path form, not the File form: it is what a KMP core needs, and it keeps the file exactly where
            // every earlier build put it. The name is unchanged on purpose - moving it would orphan saved state.
            produceFile = { directory.resolve(FILE_NAME) },
        )

    private fun themeMode(preferences: Preferences?): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == preferences?.get(THEME_MODE) } ?: ThemeMode.System

    override suspend fun getThemeMode(): ThemeMode = themeMode(readablePreferences())

    override fun observeThemeMode(): Flow<ThemeMode> = store.data.map { themeMode(if (isInterpretable(it)) it else null) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        requireWritable()
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            preferences[THEME_MODE] = mode.name
        }
    }

    override suspend fun getFavourites(): Set<TeamKey> {
        val preferences = readablePreferences() ?: return emptySet()
        return preferences[FAVOURITES].orEmpty().mapTo(mutableSetOf(), ::TeamKey)
    }

    override suspend fun setFavourites(teams: Set<TeamKey>) {
        requireWritable()
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            preferences[FAVOURITES] = teams.mapTo(mutableSetOf()) { it.abbrev }
        }
    }

    override suspend fun getFavouritesOnly(): Boolean {
        val preferences = readablePreferences() ?: return DEFAULT_FAVOURITES_ONLY
        return preferences[FAVOURITES_ONLY] ?: DEFAULT_FAVOURITES_ONLY
    }

    override suspend fun setFavouritesOnly(enabled: Boolean) {
        requireWritable()
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            preferences[FAVOURITES_ONLY] = enabled
        }
    }

    override suspend fun getLeagueOrder(): List<String> {
        val preferences = readablePreferences() ?: return LeagueOrder.canonical
        return LeagueOrder.reconcile(preferences[LEAGUE_ORDER]?.split(",").orEmpty())
    }

    override suspend fun setLeagueOrder(order: List<String>) {
        requireWritable()
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            // A string preserves sequence; a DataStore string set would discard the user's order.
            preferences[LEAGUE_ORDER] = LeagueOrder.reconcile(order).joinToString(",")
        }
    }

    override suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences {
        val defaults = TeamAlertPreferences()
        val preferences = readablePreferences() ?: return defaults
        return TeamAlertPreferences(
            start = preferences[startKey(team)] ?: defaults.start,
            goals = preferences[goalsKey(team)] ?: defaults.goals,
            intermission = preferences[intermissionKey(team)] ?: defaults.intermission,
            final = preferences[finalKey(team)] ?: defaults.final,
            upcoming = preferences[upcomingKey(team)] ?: defaults.upcoming,
            upcomingLeadMinutes = UpcomingLead.coerce(preferences[upcomingLeadKey(team)]),
        )
    }

    override suspend fun anyTeamAlertsOn(): Boolean {
        val preferences = readablePreferences() ?: return false
        return preferences.asMap().any { (key, value) ->
            value == true && key.name.startsWith("team_") && "_alerts_" in key.name
        }
    }

    override suspend fun setTeamAlertPreferences(
        team: TeamKey,
        preferences: TeamAlertPreferences,
    ) {
        requireWritable()
        store.edit { stored ->
            stored[VERSION] = PREFERENCES_VERSION
            stored[startKey(team)] = preferences.start
            stored[goalsKey(team)] = preferences.goals
            stored[intermissionKey(team)] = preferences.intermission
            stored[finalKey(team)] = preferences.final
            stored[upcomingKey(team)] = preferences.upcoming
            // Coerced on the way in as well as out: a value the alert has no meaning for is never written, so the
            // stored file cannot disagree with what the sheet shows.
            stored[upcomingLeadKey(team)] = UpcomingLead.coerce(preferences.upcomingLeadMinutes)
        }
    }

    override suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry? {
        // An unreadable file reports no notifications, so a stale dedup record cannot suppress an
        // alert that this build has no way to verify was already sent.
        val preferences = readablePreferences() ?: return null
        // Half an entry is not evidence of a notification. Without all three fields there is no
        // record, because reporting "already notified" from a partial write would drop a real alert.
        val goalCount = preferences[goalCountKey(gameId)] ?: return null
        val startNotified = preferences[startNotifiedKey(gameId)] ?: return null
        val updatedAt = preferences[updatedAtKey(gameId)] ?: return null
        return NotificationLedgerEntry(
            lastNotifiedGoalCount = goalCount,
            startNotified = startNotified,
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            // These are optional on read: an entry written before they existed is still a complete entry, and an
            // absent key means "not yet sent". Requiring them would turn every older record into no record.
            finalNotified = preferences[finalNotifiedKey(gameId)] ?: false,
            lastIntermissionNotified = preferences[intermissionNotifiedKey(gameId)] ?: 0,
            upcomingNotified = preferences[upcomingNotifiedKey(gameId)] ?: false,
        )
    }

    override suspend fun setNotificationLedgerEntry(
        gameId: String,
        entry: NotificationLedgerEntry,
    ) {
        requireWritable()
        store.edit { preferences ->
            preferences[VERSION] = PREFERENCES_VERSION
            preferences[goalCountKey(gameId)] = entry.lastNotifiedGoalCount
            preferences[startNotifiedKey(gameId)] = entry.startNotified
            preferences[updatedAtKey(gameId)] = entry.updatedAt.toEpochMilliseconds()
            preferences[finalNotifiedKey(gameId)] = entry.finalNotified
            preferences[intermissionNotifiedKey(gameId)] = entry.lastIntermissionNotified
            preferences[upcomingNotifiedKey(gameId)] = entry.upcomingNotified
        }
    }

    override suspend fun claimGoal(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ): Boolean {
        requireUsableGameId(gameId)
        var claimed = false
        store.edit { preferences ->
            // A newer build's file is never written here. Answering "not claimed" rather than throwing keeps the
            // decision with the caller: the only sane response to "you may not notify" is to stay silent.
            if (!isInterpretable(preferences)) return@edit
            val lastNotified = preferences[goalCountKey(gameId)]
            // An unknown total means no score has ever been observed for this game, so there is nothing for a goal to
            // be above. Recording a baseline comes first; announcing before that would announce a score nobody saw.
            if (lastNotified == NotificationLedgerEntry.UNKNOWN_GOAL_COUNT) return@edit
            // Equal is a duplicate and lower is a correction downward. Neither is an event, and neither may drag
            // the ledger backwards - which is what would happen if a stale observation overwrote a newer one.
            if (lastNotified != null && goalCount <= lastNotified) return@edit
            writeLedger(
                preferences = preferences,
                gameId = gameId,
                goalCount = goalCount,
                startNotified = preferences[startNotifiedKey(gameId)] ?: false,
                at = at,
            )
            claimed = true
        }
        return claimed
    }

    override suspend fun claimStart(
        gameId: String,
        goalBaseline: Int,
        at: Instant,
    ): Boolean {
        requireUsableGameId(gameId)
        var claimed = false
        store.edit { preferences ->
            if (!isInterpretable(preferences)) return@edit
            if (preferences[startNotifiedKey(gameId)] == true) return@edit
            writeLedger(
                preferences = preferences,
                gameId = gameId,
                // The recorded total only ever moves up. A start claim that arrives after a goal was announced must
                // not lower it, or the goal becomes claimable again; and an unknown total is replaced by whatever
                // this claim knows, not silently treated as zero.
                goalCount = mergeGoalCount(preferences[goalCountKey(gameId)], goalBaseline),
                startNotified = true,
                at = at,
            )
            claimed = true
        }
        return claimed
    }

    override suspend fun claimBaseline(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ): Boolean {
        requireUsableGameId(gameId)
        var claimed = false
        store.edit { preferences ->
            if (!isInterpretable(preferences)) return@edit
            // A baseline only fills a gap. If another worker already wrote a complete entry, raising the total here
            // could outrun a goal that is being claimed at the same moment and silence it. An entry whose total is
            // unknown is the gap itself, so it is still fillable.
            val recordedTotal = preferences[goalCountKey(gameId)]
            val alreadyRecorded =
                recordedTotal != null &&
                    recordedTotal != NotificationLedgerEntry.UNKNOWN_GOAL_COUNT &&
                    preferences[startNotifiedKey(gameId)] != null &&
                    preferences[updatedAtKey(gameId)] != null
            if (alreadyRecorded) return@edit
            writeLedger(
                preferences = preferences,
                gameId = gameId,
                goalCount = mergeGoalCount(preferences[goalCountKey(gameId)], goalCount),
                // Nothing was announced, so this must not claim the start either.
                startNotified = preferences[startNotifiedKey(gameId)] ?: false,
                at = at,
            )
            claimed = true
        }
        return claimed
    }

    override suspend fun claimFinal(
        gameId: String,
        at: Instant,
    ): Boolean {
        requireUsableGameId(gameId)
        var claimed = false
        store.edit { preferences ->
            if (!isInterpretable(preferences)) return@edit
            if (preferences[finalNotifiedKey(gameId)] == true) return@edit
            writeCompleteBase(preferences, gameId, at)
            preferences[finalNotifiedKey(gameId)] = true
            claimed = true
        }
        return claimed
    }

    override suspend fun claimIntermission(
        gameId: String,
        period: Int,
        at: Instant,
    ): Boolean {
        requireUsableGameId(gameId)
        var claimed = false
        store.edit { preferences ->
            if (!isInterpretable(preferences)) return@edit
            // Strictly higher only. An equal period is a duplicate, and a lower one is a stale observation that must
            // not pull the record back down and make a period already announced claimable again.
            if (period <= (preferences[intermissionNotifiedKey(gameId)] ?: 0)) return@edit
            writeCompleteBase(preferences, gameId, at)
            preferences[intermissionNotifiedKey(gameId)] = period
            claimed = true
        }
        return claimed
    }

    override suspend fun claimUpcoming(
        gameId: String,
        at: Instant,
    ): Boolean {
        requireUsableGameId(gameId)
        var claimed = false
        store.edit { preferences ->
            if (!isInterpretable(preferences)) return@edit
            if (preferences[upcomingNotifiedKey(gameId)] == true) return@edit
            writeCompleteBase(preferences, gameId, at)
            preferences[upcomingNotifiedKey(gameId)] = true
            claimed = true
        }
        return claimed
    }

    /**
     * Makes the three base fields exist, keeping any that already do, and stamps the entry.
     *
     * A new claim's own key is not a ledger entry: [getNotificationLedgerEntry] reads nothing without all three base
     * fields. Writing the claim alone would store a record the reader reports as absent, and the alert would repeat.
     * Missing fields take the "nothing observed, nothing announced" values, so this never claims a start or a goal.
     */
    private fun writeCompleteBase(
        preferences: MutablePreferences,
        gameId: String,
        at: Instant,
    ) = writeLedger(
        preferences = preferences,
        gameId = gameId,
        goalCount = preferences[goalCountKey(gameId)] ?: NotificationLedgerEntry.UNKNOWN_GOAL_COUNT,
        startNotified = preferences[startNotifiedKey(gameId)] ?: false,
        at = at,
    )

    /**
     * True when this build may interpret and write the file.
     *
     * A file with no marker predates versioning and is read as the current version - not as something to reset.
     */
    private fun isInterpretable(preferences: Preferences): Boolean {
        val stored = preferences[VERSION]
        return stored == null || stored <= PREFERENCES_VERSION
    }

    /**
     * Writes all three ledger fields together.
     *
     * A partial entry reads as no entry at all, so the claim must not leave one behind: the fields are written in one
     * transaction and the stamp is what proves the entry is complete.
     */
    private fun writeLedger(
        preferences: MutablePreferences,
        gameId: String,
        goalCount: Int,
        startNotified: Boolean,
        at: Instant,
    ) {
        preferences[VERSION] = PREFERENCES_VERSION
        preferences[goalCountKey(gameId)] = goalCount
        preferences[startNotifiedKey(gameId)] = startNotified
        preferences[updatedAtKey(gameId)] = at.toEpochMilliseconds()
    }

    /**
     * The next recorded total, given what is stored and what was just observed.
     *
     * [NotificationLedgerEntry.UNKNOWN_GOAL_COUNT] is not a number to compare against - it means nothing has been
     * observed - so it never wins a maximum and is replaced by the first real observation.
     */
    private fun mergeGoalCount(
        stored: Int?,
        observed: Int,
    ): Int =
        when {
            stored == null -> observed
            stored == NotificationLedgerEntry.UNKNOWN_GOAL_COUNT -> observed
            observed == NotificationLedgerEntry.UNKNOWN_GOAL_COUNT -> stored
            else -> maxOf(stored, observed)
        }

    override suspend fun pruneNotificationLedger(
        olderThan: Instant,
        keep: Set<String>,
    ) {
        requireWritable()
        val cutoff = olderThan.toEpochMilliseconds()
        store.edit { preferences ->
            // The update stamp is the key whose name carries the game id, so it drives the sweep and
            // the other keys are removed alongside it. Age alone decides: a game that never reaches final - a
            // postponement whose entry came from an upcoming claim - must not keep its row forever.
            val expired =
                preferences
                    .asMap()
                    .keys
                    .filter { key -> key.name.startsWith(UPDATED_AT_PREFIX) }
                    .mapNotNull { key ->
                        val stamp = preferences[longPreferencesKey(key.name)]
                        val gameId = key.name.removePrefix(UPDATED_AT_PREFIX)
                        // A game still in progress is kept whatever its age: its entry is the only record of what
                        // has already been announced, and dropping it mid-game would repeat those alerts.
                        if (stamp != null && stamp < cutoff && gameId !in keep) gameId else null
                    }
            if (expired.isEmpty()) return@edit
            preferences[VERSION] = PREFERENCES_VERSION
            for (gameId in expired) {
                preferences.remove(updatedAtKey(gameId))
                preferences.remove(goalCountKey(gameId))
                preferences.remove(startNotifiedKey(gameId))
                preferences.remove(finalNotifiedKey(gameId))
                preferences.remove(intermissionNotifiedKey(gameId))
                preferences.remove(upcomingNotifiedKey(gameId))
            }
        }
    }

    override fun observeFavourites(): Flow<Set<TeamKey>> =
        readablePreferencesFlow().map { preferences ->
            // A file from a newer build is not interpreted: it answers defaults, exactly like the one-shot read.
            preferences?.get(FAVOURITES).orEmpty().mapTo(mutableSetOf(), ::TeamKey)
        }

    override fun observeFavouritesOnly(): Flow<Boolean> =
        readablePreferencesFlow().map { preferences ->
            preferences?.get(FAVOURITES_ONLY) ?: DEFAULT_FAVOURITES_ONLY
        }

    override fun observeLeagueOrder(): Flow<List<String>> =
        readablePreferencesFlow().map { preferences ->
            LeagueOrder.reconcile(preferences?.get(LEAGUE_ORDER)?.split(",").orEmpty())
        }

    override suspend fun isWritable(): Boolean = readablePreferences() != null

    /**
     * The stored snapshot as it changes, or null when the file was written by a newer build.
     *
     * One place decides what "readable" means, so the observing reads and the one-shot reads cannot
     * disagree about which files this build is allowed to interpret.
     */
    private fun readablePreferencesFlow(): Flow<Preferences?> =
        store.data.map { preferences ->
            val stored = preferences[VERSION]
            if (stored != null && stored > PREFERENCES_VERSION) null else preferences
        }

    /** Null when the file was written by a newer build and cannot be interpreted by this one. */
    private suspend fun readablePreferences(): Preferences? = readablePreferencesFlow().first()

    private suspend fun requireWritable() {
        val stored = store.data.first()[VERSION]
        if (stored != null && stored > PREFERENCES_VERSION) {
            throw UnsupportedPreferencesVersionException(stored)
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

    private fun intermissionKey(team: TeamKey) = booleanPreferencesKey("team_${team.abbrev}_alerts_intermission")

    private fun finalKey(team: TeamKey) = booleanPreferencesKey("team_${team.abbrev}_alerts_final")

    private fun upcomingKey(team: TeamKey) = booleanPreferencesKey("team_${team.abbrev}_alerts_upcoming")

    private fun upcomingLeadKey(team: TeamKey) = intPreferencesKey("team_${team.abbrev}_alerts_upcoming_lead")

    private fun finalNotifiedKey(gameId: String) = booleanPreferencesKey(FINAL_NOTIFIED_PREFIX + requireUsableGameId(gameId))

    private fun intermissionNotifiedKey(gameId: String) = intPreferencesKey(INTERMISSION_NOTIFIED_PREFIX + requireUsableGameId(gameId))

    private fun upcomingNotifiedKey(gameId: String) = booleanPreferencesKey(UPCOMING_NOTIFIED_PREFIX + requireUsableGameId(gameId))

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
        const val FINAL_NOTIFIED_PREFIX = "ledger_final_"
        const val INTERMISSION_NOTIFIED_PREFIX = "ledger_intermission_"
        const val UPCOMING_NOTIFIED_PREFIX = "ledger_upcoming_"

        val GAME_ID = Regex("[A-Za-z0-9._-]{1,64}")

        val VERSION = intPreferencesKey("prefs_version")
        val FAVOURITES = stringSetPreferencesKey("favourites_abbreviations")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LEAGUE_ORDER = stringPreferencesKey("league_order")
        val FAVOURITES_ONLY = booleanPreferencesKey("favourites_only")
    }
}

/**
 * The preferences file was written by a newer build.
 *
 * Refusing the write keeps the newer data intact for a later upgrade; the alternative - resetting
 * the file to defaults - destroys state this build cannot even describe.
 */
class UnsupportedPreferencesVersionException(
    val storedVersion: Int,
) : IllegalStateException(
        "preferences file version $storedVersion is newer than this build supports",
    )
