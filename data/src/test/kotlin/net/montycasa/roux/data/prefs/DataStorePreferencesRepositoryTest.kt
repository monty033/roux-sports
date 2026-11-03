package net.montycasa.roux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.time.Instant

/**
 * Each test reads back through a *new* instance over the same directory, because what matters is
 * that the bytes survive a process restart. Asserting against the instance that wrote them would
 * pass for an in-memory map.
 */
class DataStorePreferencesRepositoryTest {
    @Test fun themeDefaultsToSystem() =
        runTest {
            val scope = newScope()
            try {
                assertEquals(
                    ThemeMode.System,
                    DataStorePreferencesRepository(newDirectory(), scope).getThemeMode(),
                )
            } finally {
                closeScope(scope)
            }
        }

    @Test fun themeModesSurviveReopen() =
        runTest {
            for (mode in ThemeMode.entries) {
                val directory = newDirectory()
                val writerScope = newScope()
                DataStorePreferencesRepository(directory, writerScope).setThemeMode(mode)
                closeScope(writerScope)
                val readerScope = newScope()
                try {
                    assertEquals(mode, openWhenReleased(directory, readerScope).getThemeMode())
                } finally {
                    closeScope(readerScope)
                }
            }
        }

    private companion object {
        /** Verbatim from datastore-core, which appends the offending file path. */
        const val MULTIPLE_INSTANCES = "There are multiple DataStores active for the same file:"
    }

    private fun newDirectory(): Path = Files.createTempDirectory("roux-prefs").toFile().toOkioPath()

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun fileIn(directory: Path) = directory.resolve("roux.preferences_pb").toFile()

    /**
     * Cancels the scope AND waits for it to finish.
     *
     * `cancel()` only requests cancellation: DataStore releases its claim on the file when its
     * coroutines actually complete, which can lag well past the call. Waiting here is what makes the
     * reopen deterministic, and it is the difference between a test that proves persistence and one
     * that fails whenever the machine is busy. This reproduced on a merged main under full-suite
     * load, so the wait is not belt-and-braces - it is the fix.
     */
    private suspend fun closeScope(scope: CoroutineScope) {
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    /**
     * Retries construction if the previous store has not released the file yet, covering the small
     * window between the scope finishing and DataStore dropping its claim.
     */
    private suspend fun openWhenReleased(
        directory: Path,
        scope: CoroutineScope,
    ): DataStorePreferencesRepository {
        var contention: IllegalStateException? = null
        repeat(200) {
            val repository = DataStorePreferencesRepository(directory, scope)
            try {
                // The claim on the file is taken by the first read, not by construction, so the read is what the
                // retry has to cover.
                repository.getFavourites()
                return repository
            } catch (failure: IllegalStateException) {
                // Only a previous instance still holding the file is retryable. Anything else is a real fault and
                // must not be swallowed into a delay loop.
                if (!failure.message.orEmpty().startsWith(MULTIPLE_INSTANCES)) throw failure
                contention = failure
                delay(10)
            }
        }
        // Exhaustion reports the dependency's own failure: replacing it with a generic message would hide that
        // another instance still holds the file, which is the one thing this retry exists to say.
        throw contention ?: IllegalStateException("the store for $directory never became readable")
    }

    /** Raw store for assertions on the file itself, with the same release retry the repository uses. */
    private suspend fun openRawStore(
        directory: Path,
        scope: CoroutineScope,
    ): DataStore<Preferences> {
        repeat(200) {
            try {
                return PreferenceDataStoreFactory.create(scope = scope, produceFile = { fileIn(directory) })
            } catch (_: IllegalStateException) {
                delay(10)
            }
        }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { fileIn(directory) })
    }

    /** Writes ledger fields directly, so a test can produce the half-written states a crash leaves. */
    private suspend fun writeRawLedgerEntry(
        directory: Path,
        gameId: String,
        goalCount: Int? = null,
        startNotified: Boolean? = null,
        updatedAt: Long? = null,
        version: Int? = null,
        favourites: Set<String>? = null,
        favouritesOnly: Boolean? = null,
        leagueOrder: String? = null,
    ) {
        val scope = newScope()
        try {
            openRawStore(directory, scope)
                .edit { preferences ->
                    leagueOrder?.let { preferences[stringPreferencesKey("league_order")] = it }
                    version?.let { preferences[intPreferencesKey("prefs_version")] = it }
                    favourites?.let { preferences[stringSetPreferencesKey("favourites_abbreviations")] = it }
                    favouritesOnly?.let { preferences[booleanPreferencesKey("favourites_only")] = it }
                    goalCount?.let { preferences[intPreferencesKey("ledger_goals_$gameId")] = it }
                    startNotified?.let { preferences[booleanPreferencesKey("ledger_start_$gameId")] = it }
                    updatedAt?.let { preferences[longPreferencesKey("ledger_time_$gameId")] = it }
                }
        } finally {
            closeScope(scope)
        }
    }

    @Test
    fun favouritesPersistAcrossInstances() =
        runTest {
            val directory = newDirectory()
            val first = newScope()
            DataStorePreferencesRepository(directory, first)
                .setFavourites(setOf(TeamKey("TOR"), TeamKey("MTL")))
            closeScope(first)

            val second = newScope()
            assertEquals(
                setOf(TeamKey("TOR"), TeamKey("MTL")),
                openWhenReleased(directory, second).getFavourites(),
            )
            closeScope(second)
        }

    @Test
    fun favouritesOnlyDefaultsToTrue() =
        runTest {
            val scope = newScope()
            assertEquals(
                true,
                DataStorePreferencesRepository(newDirectory(), scope).getFavouritesOnly(),
            )
            closeScope(scope)
        }

    @Test
    fun favouritesOnlyFalsePersistsAcrossInstances() =
        runTest {
            val directory = newDirectory()
            val first = newScope()
            DataStorePreferencesRepository(directory, first).setFavouritesOnly(false)
            closeScope(first)

            // A default of true makes this the only assertion that can tell "stored false" from
            // "nothing stored"; a fresh store would answer true either way.
            val second = newScope()
            assertEquals(
                false,
                openWhenReleased(directory, second).getFavouritesOnly(),
            )
            closeScope(second)
        }

    @Test fun leagueOrderDefaultsToCanonical() =
        runTest {
            val scope = newScope()
            assertEquals(listOf("nhl", "nfl", "mlb"), DataStorePreferencesRepository(newDirectory(), scope).getLeagueOrder())
            closeScope(scope)
        }

    @Test fun leagueOrderPersistsAcrossInstances() =
        runTest {
            val directory = newDirectory()
            val first = newScope()
            val writer = DataStorePreferencesRepository(directory, first)
            writer.setLeagueOrder(listOf("nfl", "mlb", "nhl"))
            writer.setLeagueOrder(listOf("mlb", "nhl", "nfl"))
            closeScope(first)
            val second = newScope()
            assertEquals(listOf("mlb", "nhl", "nfl"), openWhenReleased(directory, second).getLeagueOrder())
            closeScope(second)
        }

    @Test fun rawLeagueOrderIsReconciledWithoutRewritingStorage() =
        runTest {
            val directory = newDirectory()
            writeRawLedgerEntry(directory, "unused", leagueOrder = "nfl,unknown,nfl")
            val scope = newScope()
            assertEquals(listOf("nfl", "nhl", "mlb"), openWhenReleased(directory, scope).getLeagueOrder())
            closeScope(scope)
            val raw = newScope()
            val stored = openRawStore(directory, raw).data.first()
            assertEquals("nfl,unknown,nfl", stored[stringPreferencesKey("league_order")])
            assertNull(stored[intPreferencesKey("prefs_version")])
            closeScope(raw)
        }

    @Test
    fun aFileWrittenByTheLegacyFactorySurvivesTheSwitchAndStillWrites() =
        runTest {
            val directory = newDirectory()
            val legacyScope = newScope()
            try {
                // The factory this app used before createWithPath wrote the very same file, through a File
                // instead of a Path. Opening it here is the upgrade a real user runs on first launch.
                PreferenceDataStoreFactory
                    .create(
                        scope = legacyScope,
                        produceFile = { fileIn(directory) },
                    ).edit { preferences ->
                        preferences[stringSetPreferencesKey("favourites_abbreviations")] = setOf("TOR", "MTL")
                    }
            } finally {
                closeScope(legacyScope)
            }
            assertTrue("the legacy write must land where the new store reads", Files.exists(fileIn(directory).toPath()))

            val scope = newScope()
            try {
                val repository = openWhenReleased(directory, scope)

                assertEquals(
                    "changing the factory must not lose what was already saved",
                    setOf(TeamKey("TOR"), TeamKey("MTL")),
                    repository.getFavourites(),
                )

                repository.setFavouritesOnly(false)

                assertEquals(false, repository.getFavouritesOnly())
                assertEquals(
                    "writing through the new store must not drop the legacy favourites",
                    setOf(TeamKey("TOR"), TeamKey("MTL")),
                    repository.getFavourites(),
                )
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun observeFavouritesEmitsWhatIsStoredAndThenTheChange() =
        runTest {
            val directory = newDirectory()
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)

                repository.observeFavourites().test {
                    assertEquals("a store with nothing saved is empty, not absent", emptySet<TeamKey>(), awaitItem())

                    repository.setFavourites(setOf(TeamKey("TOR")))

                    assertEquals("an observer must see the write without asking again", setOf(TeamKey("TOR")), awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun observeFavouritesOnANewerFileEmitsDefaultsInsteadOfThrowing() =
        runTest {
            val directory = newDirectory()
            // Written by a build that understands more than this one: readable, but not interpretable here.
            writeRawLedgerEntry(directory, gameId = "game-1", version = 2, favourites = setOf("TOR"))

            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)
                repository.observeFavourites().test {
                    assertEquals("a newer file must answer defaults, not its contents", emptySet<TeamKey>(), awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun anUnconfiguredTeamStartsWithBothAlertsOff() =
        runTest {
            val directory = newDirectory()
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)
                val preferences = repository.getTeamAlertPreferences(TeamKey("TOR"))

                assertFalse("game-start alerts must not arrive without an opt-in", preferences.start)
                assertFalse("goal alerts must not arrive without an opt-in", preferences.goals)
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun teamAlertPreferencesPersistPerTeam() =
        runTest {
            val directory = newDirectory()
            val first = newScope()
            val writer = DataStorePreferencesRepository(directory, first)
            writer.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(start = false, goals = true))
            writer.setTeamAlertPreferences(TeamKey("MTL"), TeamAlertPreferences(start = true, goals = false))
            closeScope(first)

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
            closeScope(second)
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
            closeScope(first)

            val second = newScope()
            assertEquals(
                entry,
                openWhenReleased(directory, second).getNotificationLedgerEntry("2024020001"),
            )
            closeScope(second)
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

            repository.pruneNotificationLedger(Instant.parse("2025-01-03T00:00:00Z"), keep = emptySet())

            assertNull(repository.getNotificationLedgerEntry("2024010001"))
            // Pruning by age must not sweep the ledger: a live game's dedup state has to survive,
            // or the next goal gets announced twice.
            assertEquals(recent, repository.getNotificationLedgerEntry("2024010002"))
            closeScope(scope)
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
            assertEquals(listOf("nhl", "nfl", "mlb"), repository.getLeagueOrder())
            assertEquals(TeamAlertPreferences(), repository.getTeamAlertPreferences(TeamKey("TOR")))
            closeScope(scope)
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
            closeScope(scope)
        }

    @Test
    fun entryWithoutStartFlagIsNotReported() =
        runTest {
            val directory = newDirectory()
            writeRawLedgerEntry(directory, "2024020001", goalCount = 7, updatedAt = 1_000L)
            val scope = newScope()
            assertNull(openWhenReleased(directory, scope).getNotificationLedgerEntry("2024020001"))
            closeScope(scope)
        }

    @Test
    fun entryWithoutTimestampIsNotReported() =
        runTest {
            val directory = newDirectory()
            writeRawLedgerEntry(directory, "2024020001", goalCount = 7, startNotified = true)
            val scope = newScope()
            assertNull(openWhenReleased(directory, scope).getNotificationLedgerEntry("2024020001"))
            closeScope(scope)
        }

    /**
     * A file written by a newer build must never be rewritten. Reads answer with defaults because
     * this build cannot interpret those keys, and writes are refused so a downgrade cannot destroy
     * state that the next upgrade would have migrated properly.
     */
    @Test
    fun fileFromANewerBuildReadsAsDefaultsAndRefusesWrites() =
        runTest {
            val directory = newDirectory()
            writeRawLedgerEntry(
                directory,
                "2024020001",
                goalCount = 9,
                startNotified = true,
                updatedAt = 1_000L,
                version = 99,
                favourites = setOf("TOR"),
                favouritesOnly = false,
                leagueOrder = "mlb,nfl,nhl",
            )

            val scope = newScope()
            val repository = openWhenReleased(directory, scope)
            // Every getter must be gated: each of these values IS in the file, so an ungated
            // getter would return it rather than the default.
            assertEquals(emptySet<TeamKey>(), repository.getFavourites())
            assertEquals(true, repository.getFavouritesOnly())
            assertEquals(listOf("nhl", "nfl", "mlb"), repository.getLeagueOrder())
            assertEquals(TeamAlertPreferences(), repository.getTeamAlertPreferences(TeamKey("TOR")))
            assertEquals(null, repository.getNotificationLedgerEntry("2024020001"))
            assertEquals(false, repository.isWritable())
            // Every mutator must refuse, not just the one this test used to check: a write that
            // slipped through would stamp the marker back to 1 and strand the newer file.
            val refusals: List<suspend () -> Unit> =
                listOf(
                    { repository.setFavourites(setOf(TeamKey("MTL"))) },
                    { repository.setFavouritesOnly(false) },
                    { repository.setLeagueOrder(listOf("mlb", "nhl", "nfl")) },
                    { repository.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(start = false, goals = false)) },
                    {
                        repository.setNotificationLedgerEntry(
                            "2024020002",
                            NotificationLedgerEntry(1, startNotified = true, updatedAt = Instant.fromEpochMilliseconds(2_000L)),
                        )
                    },
                    { repository.pruneNotificationLedger(Instant.fromEpochMilliseconds(Long.MAX_VALUE), keep = emptySet()) },
                )
            for (refusal in refusals) {
                assertThrows(UnsupportedPreferencesVersionException::class.java) {
                    runBlocking { refusal() }
                }
            }
            closeScope(scope)

            // The file and its marker must still be intact afterwards.
            val readerScope = newScope()
            val stored =
                openRawStore(directory, readerScope)
                    .data
                    .first()
            assertEquals(99, stored[intPreferencesKey("prefs_version")])
            assertEquals(9, stored[intPreferencesKey("ledger_goals_2024020001")])
            assertEquals(setOf("TOR"), stored[stringSetPreferencesKey("favourites_abbreviations")])
            assertEquals(false, stored[booleanPreferencesKey("favourites_only")])
            assertEquals("mlb,nfl,nhl", stored[stringPreferencesKey("league_order")])
            closeScope(readerScope)
        }

    /** Reading a versionless file must not stamp it; a read is not a migration. */
    @Test
    fun readsDoNotStampTheVersion() =
        runTest {
            val directory = newDirectory()
            // A file with data but no version marker, as an older build would have left it.
            writeRawLedgerEntry(directory, "2024020001", goalCount = 2, startNotified = true, updatedAt = 5_000L)

            val scope = newScope()
            val repository = openWhenReleased(directory, scope)
            assertEquals(
                NotificationLedgerEntry(2, true, Instant.fromEpochMilliseconds(5_000L)),
                repository.getNotificationLedgerEntry("2024020001"),
            )
            repository.getFavourites()
            repository.getFavouritesOnly()
            repository.getTeamAlertPreferences(TeamKey("TOR"))
            closeScope(scope)

            val readerScope = newScope()
            val stored =
                openRawStore(directory, readerScope)
                    .data
                    .first()
            assertEquals(null, stored[intPreferencesKey("prefs_version")])
            closeScope(readerScope)
        }

    @Test
    fun versionIsStampedByTheFirstWrite() =
        runTest {
            val directory = newDirectory()
            val writerScope = newScope()
            DataStorePreferencesRepository(directory, writerScope).setFavourites(setOf(TeamKey("TOR")))
            // DataStore refuses two live instances over one file, so the writer has to close first.
            closeScope(writerScope)

            val readerScope = newScope()
            val stored =
                openRawStore(directory, readerScope)
                    .data
                    .first()
            assertEquals(1, stored[intPreferencesKey("prefs_version")])
            closeScope(readerScope)
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
                closeScope(scope)
            }
        }

    @Test
    fun fiftyConcurrentGoalClaimsForTheSameCountProduceExactlyOneWinner() =
        runBlocking {
            val directory = newDirectory()
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                // A real dispatcher, not virtual time: the race this guards against only exists when the calls
                // genuinely overlap, and 50 sequential claims would pass with the transaction removed.
                val winners =
                    coroutineScope {
                        (1..50)
                            .map { async(Dispatchers.IO) { repository.claimGoal("2025020123", 3, at) } }
                            .awaitAll()
                            .count { it }
                    }
                assertEquals("exactly one caller may post a goal", 1, winners)
                // The ledger has to agree with the winner, or the next pass would claim the same goal again.
                assertEquals(3, repository.getNotificationLedgerEntry("2025020123")?.lastNotifiedGoalCount)
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun aGoalClaimForACountThatIsNotNewerIsRefused() =
        runTest {
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(newDirectory(), scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                assertEquals(true, repository.claimGoal("2025020123", 3, at))
                assertEquals("the same count is a duplicate", false, repository.claimGoal("2025020123", 3, at))
                assertEquals("a lower count is the feed correcting itself, not a goal", false, repository.claimGoal("2025020123", 2, at))
                assertEquals(
                    "a refused claim must not drag the ledger backwards",
                    3,
                    repository.getNotificationLedgerEntry("2025020123")?.lastNotifiedGoalCount,
                )
                assertEquals(true, repository.claimGoal("2025020123", 4, at))
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun aStartClaimIsWonOnceAndRecordsTheGoalBaseline() =
        runTest {
            val directory = newDirectory()
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                // The game is already 2-1 when the start is claimed. Those goals were not seen happen, so the
                // baseline is what stops them being announced as one burst the moment the start fires.
                assertEquals(true, repository.claimStart("2025020123", goalBaseline = 2, at = at))
                assertEquals("a second start claim must lose", false, repository.claimStart("2025020123", goalBaseline = 3, at = at))
                val entry = repository.getNotificationLedgerEntry("2025020123")
                assertEquals(true, entry?.startNotified)
                assertEquals(2, entry?.lastNotifiedGoalCount)
                assertEquals("goals already on the board at the start are not new", false, repository.claimGoal("2025020123", 2, at))
                assertEquals("the next goal still is", true, repository.claimGoal("2025020123", 3, at))
                assertEquals("and the start stays claimed", true, repository.getNotificationLedgerEntry("2025020123")?.startNotified)
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun aStartClaimAfterAGoalDoesNotLoseTheGoalCount() =
        runTest {
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(newDirectory(), scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                assertEquals(true, repository.claimGoal("2025020123", 1, at))
                // The start can arrive after a goal - the app may first see the game mid-period - and it must not
                // reset the count, or the goal at 1 would be announced a second time.
                assertEquals(true, repository.claimStart("2025020123", goalBaseline = 1, at = at))
                val entry = repository.getNotificationLedgerEntry("2025020123")
                assertEquals(true, entry?.startNotified)
                assertEquals(1, entry?.lastNotifiedGoalCount)
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun claimsOnANewerFileAreRefusedWithoutWriting() =
        runTest {
            val directory = newDirectory()
            val at = Instant.parse("2025-01-01T00:00:00Z")
            // A file from a build that understands more than this one: a claim must not touch it.
            writeRawLedgerEntry(
                directory,
                gameId = "game-1",
                goalCount = 1,
                startNotified = false,
                updatedAt = 1L,
                version = 2,
            )

            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)
                assertEquals(false, repository.claimGoal("game-1", 5, at))
                assertEquals(false, repository.claimStart("game-1", 5, at))
            } finally {
                closeScope(scope)
            }

            val readerScope = newScope()
            try {
                val stored = openRawStore(directory, readerScope).data.first()
                assertEquals("the newer build's marker must survive", 2, stored[intPreferencesKey("prefs_version")])
                assertEquals("and its ledger must be untouched", 1, stored[intPreferencesKey("ledger_goals_game-1")])
                assertEquals(false, stored[booleanPreferencesKey("ledger_start_game-1")])
                assertEquals(1L, stored[longPreferencesKey("ledger_time_game-1")])
            } finally {
                closeScope(readerScope)
            }
        }

    @Test
    fun aLateStartClaimDoesNotRewindAnAlreadyAnnouncedTotal() =
        runTest {
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(newDirectory(), scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                assertEquals(true, repository.claimGoal("2025020123", 3, at))
                // A start claim carrying an older baseline arrives after the goal. Lowering the total would make
                // that goal claimable again on the next pass, which is a repeat.
                assertEquals(true, repository.claimStart("2025020123", goalBaseline = 1, at = at))
                assertEquals(3, repository.getNotificationLedgerEntry("2025020123")?.lastNotifiedGoalCount)
                assertEquals(false, repository.claimGoal("2025020123", 3, at))
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun aBaselineRecordsTheTotalWithoutClaimingTheStart() =
        runTest {
            val directory = newDirectory()
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                assertEquals(
                    "a first sight with start alerts off still records the score",
                    true,
                    repository.claimBaseline("2025020123", 2, at),
                )
                val entry = repository.getNotificationLedgerEntry("2025020123")
                assertEquals(2, entry?.lastNotifiedGoalCount)
                assertEquals("nothing was announced, so the start is not claimed", false, entry?.startNotified)
                assertEquals("an equal baseline is not news", false, repository.claimBaseline("2025020123", 2, at))
                assertEquals("and a lower one never rewinds", false, repository.claimBaseline("2025020123", 1, at))
                assertEquals(2, repository.getNotificationLedgerEntry("2025020123")?.lastNotifiedGoalCount)
                // The start can still be claimed later, and it keeps the higher total.
                assertEquals(true, repository.claimStart("2025020123", goalBaseline = 0, at = at))
                assertEquals(2, repository.getNotificationLedgerEntry("2025020123")?.lastNotifiedGoalCount)
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun pruningKeepsTheGamesThatAreStillBeingWatched() =
        runTest {
            val directory = newDirectory()
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(directory, scope)
                val old = Instant.parse("2025-01-01T00:00:00Z")
                val entry = NotificationLedgerEntry(4, startNotified = true, updatedAt = old)
                repository.setNotificationLedgerEntry("finished-game", entry)
                repository.setNotificationLedgerEntry("long-overtime", entry)

                // Both are older than the cutoff. Only the one that is no longer in progress may go: a live game's
                // entry is the only record of what has been announced, whatever its age.
                repository.pruneNotificationLedger(
                    olderThan = Instant.parse("2025-01-02T00:00:00Z"),
                    keep = setOf("long-overtime"),
                )

                assertNull(repository.getNotificationLedgerEntry("finished-game"))
                assertEquals(entry, repository.getNotificationLedgerEntry("long-overtime"))
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun aBaselineDoesNotAdvanceAnEntryThatAlreadyExists() =
        runTest {
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(newDirectory(), scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                assertEquals(true, repository.claimGoal("2025020123", 1, at))
                // A baseline taken from a stale snapshot must not raise the total over a goal that is being claimed
                // at the same moment: doing so would make that goal claim lose and never be announced.
                assertEquals(false, repository.claimBaseline("2025020123", 5, at))
                assertEquals(1, repository.getNotificationLedgerEntry("2025020123")?.lastNotifiedGoalCount)
            } finally {
                closeScope(scope)
            }
        }

    @Test
    fun anUnknownTotalBlocksGoalsUntilAScoreIsObserved() =
        runTest {
            val scope = newScope()
            try {
                val repository = DataStorePreferencesRepository(newDirectory(), scope)
                val at = Instant.parse("2025-01-01T00:00:00Z")
                // The game started while the feed had no score: the start is claimed with an unknown total.
                assertEquals(
                    true,
                    repository.claimStart("2025020123", NotificationLedgerEntry.UNKNOWN_GOAL_COUNT, at),
                )
                assertEquals(
                    "nothing can be above an unknown total",
                    false,
                    repository.claimGoal("2025020123", 0, at),
                )
                val entry = repository.getNotificationLedgerEntry("2025020123")
                assertEquals(NotificationLedgerEntry.UNKNOWN_GOAL_COUNT, entry?.lastNotifiedGoalCount)
                assertEquals(true, entry?.goalCountIsUnknown)

                // The first real score fills the gap rather than announcing anything.
                assertEquals(true, repository.claimBaseline("2025020123", 3, at))
                assertEquals(3, repository.getNotificationLedgerEntry("2025020123")?.lastNotifiedGoalCount)
                assertEquals("and the start stays claimed", true, repository.getNotificationLedgerEntry("2025020123")?.startNotified)
                assertEquals(true, repository.claimGoal("2025020123", 4, at))
            } finally {
                closeScope(scope)
            }
        }
}
