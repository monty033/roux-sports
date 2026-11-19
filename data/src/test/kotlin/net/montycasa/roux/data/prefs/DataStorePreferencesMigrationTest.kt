package net.montycasa.roux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Proves an upgrade rather than a fresh start: every case begins with a file an older build wrote.
 *
 * Reads go through a new repository over the same directory, so what is asserted is what survived on disk.
 */
class DataStorePreferencesMigrationTest {
    @Test
    fun aVersionOneFileReadsItsFavouritesAndAlertsAsNhlAfterTheUpgrade() =
        runTest {
            val directory = newDirectory()
            writeVersionOneFile(directory)

            withRepository(directory) { repository ->
                assertEquals(
                    setOf(TeamKey(League.NHL, "PHI"), TeamKey(League.NHL, "CAR")),
                    repository.getFavourites(),
                )
                assertEquals(
                    TeamAlertPreferences(start = true, goals = true, upcomingLeadMinutes = 60),
                    repository.getTeamAlertPreferences(TeamKey(League.NHL, "PHI")),
                )
                assertEquals(
                    "the NHL team's old switches must not reach an NFL team with the same abbreviation",
                    TeamAlertPreferences(),
                    repository.getTeamAlertPreferences(TeamKey(League.NFL, "PHI")),
                )
                assertTrue(repository.anyTeamAlertsOn())
            }
        }

    @Test
    fun savingAfterTheUpgradeWritesQualifiedFavouritesAndKeepsTheLegacyKeys() =
        runTest {
            val directory = newDirectory()
            writeVersionOneFile(directory)

            withRepository(directory) { repository ->
                repository.setFavourites(setOf(TeamKey(League.NHL, "PHI"), TeamKey(League.NFL, "PHI")))
            }

            val stored = withRawStore(directory) { it.data.first() }
            assertEquals(setOf("nhl:PHI", "nfl:PHI"), stored[FAVOURITES])
            // Legacy keys are left in place: they cost a few bytes and keep a rollback harmless.
            assertEquals(true, stored[booleanPreferencesKey("team_PHI_alerts_start")])

            withRepository(directory) { repository ->
                assertEquals(
                    "both teams must come back as distinct teams",
                    setOf(TeamKey(League.NHL, "PHI"), TeamKey(League.NFL, "PHI")),
                    repository.getFavourites(),
                )
            }
        }

    @Test
    fun anUnversionedFileReadsItsLegacyEntriesAsNhl() =
        runTest {
            val directory = newDirectory()
            withRawStore(directory) { store ->
                store.edit { stored ->
                    stored[FAVOURITES] = setOf("PHI")
                    stored[booleanPreferencesKey("team_PHI_alerts_goals")] = true
                }
            }

            withRepository(directory) { repository ->
                assertEquals(setOf(TeamKey(League.NHL, "PHI")), repository.getFavourites())
                assertEquals(
                    TeamAlertPreferences(goals = true),
                    repository.getTeamAlertPreferences(TeamKey(League.NHL, "PHI")),
                )
            }
        }

    @Test
    fun aQualifiedAlertSwitchIsFoundByAnyTeamAlertsOn() =
        runTest {
            val directory = newDirectory()
            withRepository(directory) { repository ->
                repository.setTeamAlertPreferences(TeamKey(League.NFL, "PHI"), TeamAlertPreferences(final = true))
            }

            // Nothing legacy is in this file, so the scan can only be answering from the qualified name. Every team key is
            // listed, not only the enabled ones: a legacy name written with `false` would otherwise go unnoticed.
            val stored = withRawStore(directory) { it.data.first() }
            assertEquals(
                "every switch is written under a qualified name, and none under a bare one",
                setOf(
                    "team_nfl:PHI_alerts_start",
                    "team_nfl:PHI_alerts_goals",
                    "team_nfl:PHI_alerts_intermission",
                    "team_nfl:PHI_alerts_final",
                    "team_nfl:PHI_alerts_upcoming",
                    "team_nfl:PHI_alerts_upcoming_lead",
                ),
                stored
                    .asMap()
                    .keys
                    .map { it.name }
                    .filter { it.contains("_alerts_") }
                    .toSet(),
            )
            withRepository(directory) { repository ->
                assertTrue(repository.anyTeamAlertsOn())
            }
        }

    @Test
    fun turningASwitchOffAfterTheUpgradeStopsCountingItAsOn() =
        runTest {
            val directory = newDirectory()
            writeVersionOneFile(directory)

            // The v1 file has start and goals on for PHI. Turning both off must leave nothing on: the legacy keys are
            // still in the file, and counting them would keep the app's "nobody wants alerts" fast path dead forever.
            withRepository(directory) { repository ->
                assertTrue(repository.anyTeamAlertsOn())
                repository.setTeamAlertPreferences(TeamKey(League.NHL, "PHI"), TeamAlertPreferences())
                assertEquals(
                    "a switch turned off must stop counting, even though the legacy key still says true",
                    false,
                    repository.anyTeamAlertsOn(),
                )
            }

            val stored = withRawStore(directory) { it.data.first() }
            assertEquals("the legacy keys are kept", true, stored[booleanPreferencesKey("team_PHI_alerts_start")])
            assertEquals(false, stored[booleanPreferencesKey("team_nhl:PHI_alerts_start")])
        }

    @Test
    fun aFavouriteThisBuildCannotNameIsHiddenButNotDeleted() =
        runTest {
            val directory = newDirectory()
            withRawStore(directory) { store ->
                store.edit { stored ->
                    stored[FAVOURITES] = setOf("PHI", "mlb:NYY")
                }
            }

            withRepository(directory) { repository ->
                assertEquals(
                    "a league this build does not know is hidden, not guessed at",
                    setOf(TeamKey(League.NHL, "PHI")),
                    repository.getFavourites(),
                )
                // Toggling any team rewrites the whole set; the token this build cannot name must survive that.
                repository.setFavourites(setOf(TeamKey(League.NHL, "PHI"), TeamKey(League.NFL, "PHI")))
            }

            val stored = withRawStore(directory) { it.data.first() }
            assertEquals(
                "a later build that knows the league must still find the choice",
                setOf("nhl:PHI", "nfl:PHI", "mlb:NYY"),
                stored[FAVOURITES],
            )
        }

    private companion object {
        const val FILE_NAME = "roux.preferences_pb"

        /** Verbatim from datastore-core, which appends the offending file path. */
        const val MULTIPLE_INSTANCES = "There are multiple DataStores active for the same file:"

        val FAVOURITES = stringSetPreferencesKey("favourites_abbreviations")
    }

    /**
     * The file a version-1 build left behind: two favourites, and one team with two switches on and a 60-minute lead.
     * The raw handle is closed before this returns, so the repository can open the directory.
     */
    private suspend fun writeVersionOneFile(directory: Path) {
        withRawStore(directory) { store ->
            store.edit { stored ->
                stored[intPreferencesKey("prefs_version")] = 1
                stored[FAVOURITES] = setOf("PHI", "CAR")
                stored[booleanPreferencesKey("team_PHI_alerts_start")] = true
                stored[booleanPreferencesKey("team_PHI_alerts_goals")] = true
                stored[intPreferencesKey("team_PHI_alerts_upcoming_lead")] = 60
            }
        }
    }

    private fun newDirectory(): Path = Files.createTempDirectory("roux-prefs").toFile().toOkioPath()

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun fileIn(directory: Path) = directory.resolve(FILE_NAME).toFile()

    /** Cancels the scope and waits for it, so DataStore has begun releasing the file before anything reopens it. */
    private suspend fun closeScope(scope: CoroutineScope) {
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    /** Runs [block] on a repository over [directory], then closes it so the next opener can claim the file. */
    private suspend fun <T> withRepository(
        directory: Path,
        block: suspend (DataStorePreferencesRepository) -> T,
    ): T {
        val scope = newScope()
        try {
            return block(openRepository(directory, scope))
        } finally {
            closeScope(scope)
        }
    }

    private suspend fun openRepository(
        directory: Path,
        scope: CoroutineScope,
    ): DataStorePreferencesRepository =
        whenReleased {
            // The claim on the file is taken by the first read, so the read is what the retry has to cover.
            DataStorePreferencesRepository(directory, scope).also { it.isWritable() }
        }

    /** Raw store over the same file, for assertions on what is actually stored. */
    private suspend fun <T> withRawStore(
        directory: Path,
        block: suspend (DataStore<Preferences>) -> T,
    ): T {
        val scope = newScope()
        try {
            return whenReleased {
                block(PreferenceDataStoreFactory.create(scope = scope, produceFile = { fileIn(directory) }))
            }
        } finally {
            closeScope(scope)
        }
    }

    /**
     * Retries while a previous store still holds the file, because the release lags cancellation. Any other failure is
     * a real fault and is rethrown rather than swallowed into a delay loop.
     */
    private suspend fun <T> whenReleased(attempt: suspend () -> T): T {
        var contention: IllegalStateException? = null
        repeat(200) {
            try {
                return attempt()
            } catch (failure: IllegalStateException) {
                if (!failure.message.orEmpty().startsWith(MULTIPLE_INSTANCES)) throw failure
                contention = failure
                delay(10)
            }
        }
        throw contention ?: IllegalStateException("the store for the directory never became readable")
    }
}
