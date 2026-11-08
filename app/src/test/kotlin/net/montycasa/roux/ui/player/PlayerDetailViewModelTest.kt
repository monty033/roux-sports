package net.montycasa.roux.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.repo.PlayerProfileRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    /** Four November 2026 falls in the 2026-27 season, the season being played. */
    private val november =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-04T12:00:00Z")
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The profile's newest season is 2025-26, but the game log asks for 2026-27, the season being played. */
    @Test
    fun theGameLogAsksForTheSeasonBeingPlayedNotTheProfilesNewest() =
        runTest {
            val repository = FakeRepository(profile(newestSeason = 20252026))

            viewModel(repository)
            runCurrent()

            assertEquals(20262027, repository.requestedSeasons.first())
        }

    /**
     * The season being played has no games yet, and the profile has a 2025-26 NHL regular season: the log shows those
     * games instead, names the season, and makes exactly one extra request.
     */
    @Test
    fun anEmptySeasonBeingPlayedFallsBackToTheNewestNhlRegularSeasonOnce() =
        runTest {
            val older = game(gameId = 1L)
            val repository =
                FakeRepository(
                    profile(newestSeason = 20252026),
                    gamesBySeason = mapOf(20252026 to listOf(older)),
                )

            val viewModel = viewModel(repository)
            runCurrent()

            assertEquals(listOf(20262027, 20252026), repository.requestedSeasons)
            assertEquals(20252026, viewModel.uiState.value.gameLogFallbackSeason)
            assertEquals(
                listOf(older),
                viewModel.uiState.value.gameLog
                    ?.data,
            )
        }

    /** A newer AHL season is another league's games, so the NHL season before it is the one the log falls back to. */
    @Test
    fun aNewerAhlSeasonIsIgnoredWhenChoosingTheFallback() =
        runTest {
            val repository =
                FakeRepository(
                    profileWith(listOf(seasonLine(20242025), seasonLine(20252026, league = "AHL"))),
                    gamesBySeason = mapOf(20242025 to listOf(game(gameId = 1L))),
                )

            val viewModel = viewModel(repository)
            runCurrent()

            assertEquals(listOf(20262027, 20242025), repository.requestedSeasons)
            assertEquals(20242025, viewModel.uiState.value.gameLogFallbackSeason)
        }

    /** A player with no NHL regular-season row at all gets the empty log and no second request. */
    @Test
    fun aPlayerWithNoNhlRegularSeasonGetsNoFallbackRequest() =
        runTest {
            val repository = FakeRepository(profileWith(listOf(seasonLine(20252026, league = "AHL"))))

            val viewModel = viewModel(repository)
            runCurrent()

            assertEquals(listOf(20262027), repository.requestedSeasons)
            assertNull(viewModel.uiState.value.gameLogFallbackSeason)
            assertEquals(
                emptyList<PlayerGame>(),
                viewModel.uiState.value.gameLog
                    ?.data,
            )
        }

    /** A failed season being played is a failure on screen, not an empty season to be replaced by an older one. */
    @Test
    fun aFailedCurrentSeasonIsNeverReplacedByAnOlderSeason() =
        runTest {
            val repository =
                FakeRepository(
                    profile(newestSeason = 20252026),
                    gamesBySeason = mapOf(20252026 to listOf(game(gameId = 1L))),
                    failingSeasons = setOf(20262027),
                )

            val viewModel = viewModel(repository)
            runCurrent()

            assertEquals(listOf(20262027), repository.requestedSeasons)
            assertNull(viewModel.uiState.value.gameLogFallbackSeason)
            assertEquals(
                LoadError.Local,
                viewModel.uiState.value.gameLog
                    ?.error,
            )
        }

    /** When the fallback season cannot be read, the empty season being played stays on screen and names no season. */
    @Test
    fun aFailedFallbackKeepsTheEmptySeasonBeingPlayed() =
        runTest {
            val repository =
                FakeRepository(
                    profile(newestSeason = 20252026),
                    failingSeasons = setOf(20252026),
                )

            val viewModel = viewModel(repository)
            runCurrent()

            assertEquals(listOf(20262027, 20252026), repository.requestedSeasons)
            assertNull(viewModel.uiState.value.gameLogFallbackSeason)
            assertEquals(
                emptyList<PlayerGame>(),
                viewModel.uiState.value.gameLog
                    ?.data,
            )
        }

    /** The season being played has games, and they are the log that is shown, from exactly one request. */
    @Test
    fun theCurrentSeasonsGamesAreTheLogThatIsShown() =
        runTest {
            val current = game(gameId = 2L)
            val repository = FakeRepository(profile(newestSeason = 20252026), gamesBySeason = mapOf(20262027 to listOf(current)))

            val viewModel = viewModel(repository)
            runCurrent()

            assertEquals(listOf(20262027), repository.requestedSeasons)
            assertNull(viewModel.uiState.value.gameLogFallbackSeason)
            assertEquals(
                listOf(current),
                viewModel.uiState.value.gameLog
                    ?.data,
            )
        }

    /** The Info card counts ages to the clock's date in UTC: four November 2026 is the day the clock reads. */
    @Test
    fun todayIsTheClocksDateInUtc() =
        runTest {
            val viewModel = viewModel(FakeRepository(profile(newestSeason = 20252026)))

            assertEquals(LocalDate(2026, 11, 4), viewModel.today)
        }

    /** Today is the civil date in the player's zone: at 02:00 UTC on the 5th it is still the 4th in Toronto. */
    @Test
    fun todayIsTheCivilDateInTheZoneTheAgeIsCountedIn() =
        runTest {
            val lateUtc =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-11-05T02:00:00Z")
                }
            val viewModel =
                viewModel(
                    FakeRepository(profile(newestSeason = 20252026)),
                    clock = lateUtc,
                    zone = TimeZone.of("America/Toronto"),
                )

            assertEquals(LocalDate(2026, 11, 4), viewModel.today)
        }

    private fun viewModel(
        repository: PlayerProfileRepository,
        clock: Clock = november,
        zone: TimeZone = TimeZone.UTC,
    ) = PlayerDetailViewModel(
        playerId = PLAYER_ID,
        repository = repository,
        clock = clock,
        zone = zone,
    )

    private fun profile(newestSeason: Int) = profileWith(listOf(seasonLine(newestSeason)))

    private fun profileWith(seasons: List<PlayerSeasonLine>) =
        PlayerProfile(
            playerId = PLAYER_ID,
            firstName = "Aleksi",
            lastName = "Heponiemi",
            position = "C",
            career = PlayerCareer(),
            seasons = seasons,
        )

    private fun seasonLine(
        season: Int,
        league: String = "NHL",
    ) = PlayerSeasonLine(
        season = season,
        gameTypeId = 2,
        leagueAbbrev = league,
        teamName = "Carolina Hurricanes",
        sequence = 1,
        totals = PlayerTotals.Skater(gamesPlayed = 60),
    )

    private fun game(gameId: Long) =
        PlayerGame.Skater(
            gameId = gameId,
            gameDate = "2026-11-01",
            opponentAbbrev = "OTT",
            isHome = true,
            goals = 1,
        )

    /**
     * Answers a game-log request from [gamesBySeason] and records each season asked for. A season in [failingSeasons]
     * answers with a local failure instead.
     */
    private class FakeRepository(
        private val profile: PlayerProfile,
        private val gamesBySeason: Map<Int, List<PlayerGame>> = emptyMap(),
        private val failingSeasons: Set<Int> = emptySet(),
    ) : PlayerProfileRepository {
        val requestedSeasons = mutableListOf<Int>()

        override suspend fun profile(playerId: Long): Loadable<PlayerProfile> = Loadable(profile, Freshness.Fresh, null)

        override suspend fun gameLog(
            playerId: Long,
            season: Int,
            isGoalie: Boolean,
            gameType: Int,
        ): Loadable<List<PlayerGame>> {
            requestedSeasons += season
            if (season in failingSeasons) return Loadable(null, Freshness.Missing, LoadError.Local)
            return Loadable(gamesBySeason[season].orEmpty(), Freshness.Fresh, null)
        }
    }

    private companion object {
        const val PLAYER_ID = 8480037L
    }
}
