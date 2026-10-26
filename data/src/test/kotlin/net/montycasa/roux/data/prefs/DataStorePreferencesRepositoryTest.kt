package net.montycasa.roux.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import kotlin.time.Instant

/**
 * Each test reads back through a *new* instance over the same directory, because what matters is
 * that the bytes survive a process restart. Asserting against the instance that wrote them would
 * pass for an in-memory map.
 */
class DataStorePreferencesRepositoryTest {
    private fun newDirectory(): Path = Files.createTempDirectory("roux-prefs").toFile().toOkioPath()

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun fileIn(directory: Path) = directory.resolve("roux.preferences_pb").toFile()

    /**
     * DataStore refuses two live instances over one file, and it releases the path only once the
     * previous store has actually finished closing - which lags `cancel()`. Retrying here keeps a
     * reopen from failing on that timing instead of on the persistence it is meant to test.
     */
    private suspend fun openWhenReleased(
        directory: Path,
        scope: CoroutineScope,
    ): DataStorePreferencesRepository {
        repeat(100) {
            try {
                return DataStorePreferencesRepository(directory, scope)
            } catch (_: IllegalStateException) {
                delay(10)
            }
        }
        return DataStorePreferencesRepository(directory, scope)
    }

    /** Writes ledger fields directly, so a test can produce the half-written states a crash leaves. */
    private suspend fun writeRawLedgerEntry(
        directory: Path,
        gameId: String,
        goalCount: Int? = null,
        startNotified: Boolean? = null,
        updatedAt: Long? = null,
    ) {
        val scope = newScope()
        try {
            PreferenceDataStoreFactory
                .create(scope = scope, produceFile = { fileIn(directory) })
                .edit { preferences ->
                    goalCount?.let { preferences[intPreferencesKey("ledger_goals_$gameId")] = it }
                    startNotified?.let { preferences[booleanPreferencesKey("ledger_start_$gameId")] = it }
                    updatedAt?.let { preferences[longPreferencesKey("ledger_time_$gameId")] = it }
                }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun favouritesPersistAcrossInstances() =
        runTest {
            val directory = newDirectory()
            val first = newScope()
            DataStorePreferencesRepository(directory, first)
                .setFavourites(setOf(TeamKey("TOR"), TeamKey("MTL")))
            first.cancel()

            val second = newScope()
            assertEquals(
                setOf(TeamKey("TOR"), TeamKey("MTL")),
                openWhenReleased(directory, second).getFavourites(),
            )
            second.cancel()
        }

    @Test
    fun favouritesOnlyDefaultsToTrue() =
        runTest {
            val scope = newScope()
            assertEquals(
                true,
                DataStorePreferencesRepository(newDirectory(), scope).getFavouritesOnly(),
            )
            scope.cancel()
        }

    @Test
    fun favouritesOnlyFalsePersistsAcrossInstances() =
        runTest {
            val directory = newDirectory()
            val first = newScope()
            DataStorePreferencesRepository(directory, first).setFavouritesOnly(false)
            first.cancel()

            // A default of true makes this the only assertion that can tell "stored false" from
            // "nothing stored"; a fresh store would answer true either way.
            val second = newScope()
            assertEquals(
                false,
                openWhenReleased(directory, second).getFavouritesOnly(),
            )
            second.cancel()
        }

    @Test
    fun teamAlertPreferencesPersistPerTeam() =
        runTest {
            val directory = newDirectory()
            val first = newScope()
            val writer = DataStorePreferencesRepository(directory, first)
            writer.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(start = false, goals = true))
            writer.setTeamAlertPreferences(TeamKey("MTL"), TeamAlertPreferences(start = true, goals = false))
            first.cancel()

            val second = newScope()
            val reader = openWhenReleased(directory, second)
            assertEquals(
                TeamAlertPreferences(start = false, goals = true),
                reader.getTeamAlertPreferences(TeamKey("TOR")),
            )
            assertEquals(
                TeamAlertPreferences(start = true, goals = false),
                reader.getTeamAlertPreferences(TeamKey("MTL")),
            )
            // One team's controls must not leak into a team that was never configured.
            assertEquals(TeamAlertPreferences(), reader.getTeamAlertPreferences(TeamKey("BOS")))
            second.cancel()
        }

    @Test
    fun ledgerPersistsAcrossInstances() =
        runTest {
            val directory = newDirectory()
            val entry =
                NotificationLedgerEntry(
                    lastNotifiedGoalCount = 3,
                    startNotified = true,
                    updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
                )
            val first = newScope()
            DataStorePreferencesRepository(directory, first).setNotificationLedgerEntry("2024020001", entry)
            first.cancel()

            val second = newScope()
            assertEquals(
                entry,
                openWhenReleased(directory, second).getNotificationLedgerEntry("2024020001"),
            )
            second.cancel()
        }

    @Test
    fun pruneRemovesOnlyEntriesOlderThanTheCutoff() =
        runTest {
            val directory = newDirectory()
            val scope = newScope()
            val repository = DataStorePreferencesRepository(directory, scope)
            val old =
                NotificationLedgerEntry(2, startNotified = true, updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
            val recent =
                NotificationLedgerEntry(4, startNotified = true, updatedAt = Instant.parse("2025-01-05T00:00:00Z"))
            repository.setNotificationLedgerEntry("2024010001", old)
            repository.setNotificationLedgerEntry("2024010002", recent)

            repository.pruneNotificationLedger(Instant.parse("2025-01-03T00:00:00Z"))

            assertNull(repository.getNotificationLedgerEntry("2024010001"))
            // Pruning by age must not sweep the ledger: a live game's dedup state has to survive,
            // or the next goal gets announced twice.
            assertEquals(recent, repository.getNotificationLedgerEntry("2024010002"))
            scope.cancel()
        }

    @Test
    fun corruptFileFallsBackToDefaults() =
        runTest {
            val directory = newDirectory()
            // Bytes that cannot be a Preferences protobuf. Without the corruption handler the first
            // read throws, and the app never recovers on its own.
            Files.write(fileIn(directory).toPath(), byteArrayOf(0x01, 0x02, 0x03, 0x04))

            val scope = newScope()
            val repository = DataStorePreferencesRepository(directory, scope)
            assertEquals(emptySet<TeamKey>(), repository.getFavourites())
            assertEquals(true, repository.getFavouritesOnly())
            assertEquals(TeamAlertPreferences(), repository.getTeamAlertPreferences(TeamKey("TOR")))
            scope.cancel()
        }

    /**
     * A write can die between fields. Each missing field must independently invalidate the entry:
     * reporting "already notified" when the record is incomplete suppresses a real alert, so every
     * field is load-bearing and is tested on its own.
     */
    @Test
    fun entryWithoutGoalCountIsNotReported() =
        runTest {
            val directory = newDirectory()
            writeRawLedgerEntry(directory, "2024020001", startNotified = true, updatedAt = 1_000L)
            val scope = newScope()
            assertNull(openWhenReleased(directory, scope).getNotificationLedgerEntry("2024020001"))
            scope.cancel()
        }

    @Test
    fun entryWithoutStartFlagIsNotReported() =
        runTest {
            val directory = newDirectory()
            writeRawLedgerEntry(directory, "2024020001", goalCount = 7, updatedAt = 1_000L)
            val scope = newScope()
            assertNull(openWhenReleased(directory, scope).getNotificationLedgerEntry("2024020001"))
            scope.cancel()
        }

    @Test
    fun entryWithoutTimestampIsNotReported() =
        runTest {
            val directory = newDirectory()
            writeRawLedgerEntry(directory, "2024020001", goalCount = 7, startNotified = true)
            val scope = newScope()
            assertNull(openWhenReleased(directory, scope).getNotificationLedgerEntry("2024020001"))
            scope.cancel()
        }

    @Test
    fun versionIsStampedByTheFirstWrite() =
        runTest {
            val directory = newDirectory()
            val writerScope = newScope()
            DataStorePreferencesRepository(directory, writerScope).setFavourites(setOf(TeamKey("TOR")))
            // DataStore refuses two live instances over one file, so the writer has to close first.
            writerScope.cancel()

            val readerScope = newScope()
            val stored =
                PreferenceDataStoreFactory
                    .create(scope = readerScope, produceFile = { fileIn(directory) })
                    .data
                    .first()
            assertEquals(1, stored[intPreferencesKey("prefs_version")])
            readerScope.cancel()
        }

    @Test(expected = IllegalArgumentException::class)
    fun gameIdWithUnsafeCharactersIsRejected() =
        runTest {
            val scope = newScope()
            try {
                DataStorePreferencesRepository(newDirectory(), scope)
                    .setNotificationLedgerEntry(
                        "bad id!",
                        NotificationLedgerEntry(1, startNotified = false, updatedAt = Instant.parse("2025-01-01T00:00:00Z")),
                    )
            } finally {
                scope.cancel()
            }
        }
}
