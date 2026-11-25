package net.montycasa.roux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.data.nfl.NflTeamsAdapter
import net.montycasa.roux.data.nhl.ParseResult
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Starring a football club must not star the hockey club that shares its abbreviation, and the reverse.
 *
 * The storage is league-qualified, and this stage is where that stops being theory: football clubs become starrable, and
 * ten of its abbreviations are also hockey clubs'. The test does not assume the qualification works. It takes the
 * abbreviations from the two leagues' own captures, finds the ones they share, and for each one stars one league's club
 * and reads the other's back - through a new repository over the same file, so what is asserted survived the disk.
 */
class NflFavoriteCollisionTest {
    /** The football clubs of the 18 November capture, as keys, through the real adapter. */
    private val nflClubs: List<TeamKey> =
        when (val result = NflTeamsAdapter.parse(resource("/fx-nfl/2026-11-18/nfl-teams.json"))) {
            is ParseResult.Success -> result.value.map { it.team.key }
            is ParseResult.Failure -> error("the football capture did not parse: ${result.reason}")
        }

    /** The hockey clubs of the 26 October standings capture. */
    private val nhlClubs: List<TeamKey> =
        Json
            .parseToJsonElement(resource("/fx/2026-10-26/standings.json"))
            .jsonObject
            .getValue("standings")
            .jsonArray
            .map {
                TeamKey(
                    League.NHL,
                    it.jsonObject
                        .getValue("teamAbbrev")
                        .jsonObject
                        .getValue("default")
                        .jsonPrimitive.content,
                )
            }

    /** Football and hockey key pairs with one abbreviation: what a naive store would treat as the same club. */
    private val shared: List<Pair<TeamKey, TeamKey>> =
        nflClubs.mapNotNull { nfl -> nhlClubs.firstOrNull { it.abbrev == nfl.abbrev }?.let { nfl to it } }

    private fun resource(path: String): String =
        requireNotNull(NflFavoriteCollisionTest::class.java.getResourceAsStream(path)) { "missing fixture $path" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun theTwoCapturesReallyShareAbbreviations() {
        // If this ever reads zero, the tests below pass for no reason: they would have nothing to collide.
        assertEquals(
            listOf("BUF", "CAR", "CHI", "DAL", "DET", "MIN", "PHI", "PIT", "SEA", "WSH"),
            shared.map { it.first.abbrev }.sorted(),
        )
        assertTrue(shared.all { (nfl, nhl) -> nfl.league == League.NFL && nhl.league == League.NHL && nfl.abbrev == nhl.abbrev })
    }

    @Test
    fun starringAFootballClubStarsNoHockeyClub() =
        runTest {
            // One football club per file, so a read cannot be satisfied by a neighboring entry.
            shared.forEach { (nfl, nhl) ->
                val fresh = newDirectory()
                withRepository(fresh) { it.setFavourites(setOf(nfl)) }

                withRepository(fresh) { reopened ->
                    val stored = reopened.getFavourites()
                    assertEquals("only ${nfl.league.id}:${nfl.abbrev} was starred", setOf(nfl), stored)
                    assertFalse("${nhl.league.id}:${nhl.abbrev} must not be starred by ${nfl.abbrev}", nhl in stored)
                }
                assertEquals(setOf("nfl:${nfl.abbrev}"), rawFavourites(fresh))
            }
        }

    @Test
    fun starringAHockeyClubStarsNoFootballClub() =
        runTest {
            shared.forEach { (nfl, nhl) ->
                val fresh = newDirectory()
                withRepository(fresh) { it.setFavourites(setOf(nhl)) }

                withRepository(fresh) { reopened ->
                    val stored = reopened.getFavourites()
                    assertEquals("only ${nhl.league.id}:${nhl.abbrev} was starred", setOf(nhl), stored)
                    assertFalse("${nfl.league.id}:${nfl.abbrev} must not be starred by ${nhl.abbrev}", nfl in stored)
                }
                assertEquals(setOf("nhl:${nhl.abbrev}"), rawFavourites(fresh))
            }
        }

    @Test
    fun unstarringOneLeaguesClubLeavesTheOthersStarred() =
        runTest {
            val directory = newDirectory()
            val pairs = shared.map { (nfl, nhl) -> setOf(nfl, nhl) }.flatten().toSet()
            withRepository(directory) { it.setFavourites(pairs) }

            shared.forEach { (nfl, nhl) ->
                withRepository(directory) { repository ->
                    // The same read-modify-write the Teams screen does: its set, minus the one club that was toggled.
                    repository.setFavourites(repository.getFavourites() - nfl)
                    val stored = repository.getFavourites()
                    assertFalse("${nfl.abbrev} (football) was unstarred", nfl in stored)
                    assertTrue("${nhl.abbrev} (hockey) must survive it", nhl in stored)
                }
            }
        }

    @Test
    fun aHockeyOnlyFileFromBeforeFootballDoesNotStarAFootballClub() =
        runTest {
            val directory = newDirectory()
            // What the NHL-only build wrote: bare abbreviations, all of them hockey.
            withRawStore(directory) { store ->
                store.edit { it[FAVOURITES] = shared.map { (_, nhl) -> nhl.abbrev }.toSet() }
            }

            withRepository(directory) { repository ->
                val stored = repository.getFavourites()
                assertEquals(shared.map { it.second }.toSet(), stored)
                shared.forEach { (nfl, _) -> assertFalse("legacy ${nfl.abbrev} is hockey's, not football's", nfl in stored) }
            }
        }

    @Test
    fun alertsFollowTheSameRule() =
        runTest {
            shared.forEach { (nfl, nhl) ->
                val directory = newDirectory()
                withRepository(directory) { it.setTeamAlertPreferences(nfl, TeamAlertPreferences(start = true, final = true)) }

                withRepository(directory) { repository ->
                    assertEquals(TeamAlertPreferences(start = true, final = true), repository.getTeamAlertPreferences(nfl))
                    assertEquals(
                        "football ${nfl.abbrev}'s alerts must not switch on hockey ${nhl.abbrev}'s",
                        TeamAlertPreferences(),
                        repository.getTeamAlertPreferences(nhl),
                    )
                }
            }
        }

    private companion object {
        const val FILE_NAME = "roux.preferences_pb"

        /** Verbatim from datastore-core, which appends the offending file path. */
        const val MULTIPLE_INSTANCES = "There are multiple DataStores active for the same file:"

        val FAVOURITES = stringSetPreferencesKey("favourites_abbreviations")
    }

    private fun newDirectory(): Path = Files.createTempDirectory("roux-prefs").toFile().toOkioPath()

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun fileIn(directory: Path) = directory.resolve(FILE_NAME).toFile()

    /** The set exactly as it sits on disk, for the assertion that the token written is the qualified one. */
    private suspend fun rawFavourites(directory: Path): Set<String>? = withRawStore(directory) { it.data.first()[FAVOURITES] }

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
            return whenReleased {
                // The claim on the file is taken by the first read, so the read is what the retry has to cover.
                block(DataStorePreferencesRepository(directory, scope).also { it.isWritable() })
            }
        } finally {
            closeScope(scope)
        }
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
