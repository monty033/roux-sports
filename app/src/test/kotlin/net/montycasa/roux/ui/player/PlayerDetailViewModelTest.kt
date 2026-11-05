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
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.repo.PlayerProfileRepository
import org.junit.After
import org.junit.Assert.assertEquals
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

            assertEquals(listOf(20262027), repository.requestedSeasons)
        }

    /** The 2025-26 games are there, but the player has none yet in 2026-27: the log is empty, not last season's games. */
    @Test
    fun aPlayerWithNoGamesThisSeasonGetsAnEmptyLogAndNeverLastSeasonsGames() =
        runTest {
            val repository =
                FakeRepository(
                    profile(newestSeason = 20252026),
                    gamesBySeason = mapOf(20252026 to listOf(game(gameId = 1L))),
                )

            val viewModel = viewModel(repository)
            runCurrent()

            assertEquals(
                emptyList<PlayerGame>(),
                viewModel.uiState.value.gameLog
                    ?.data,
            )
        }

    /** The season being played has games, and they are the log that is shown. */
    @Test
    fun theCurrentSeasonsGamesAreTheLogThatIsShown() =
        runTest {
            val current = game(gameId = 2L)
            val repository = FakeRepository(profile(newestSeason = 20252026), gamesBySeason = mapOf(20262027 to listOf(current)))

            val viewModel = viewModel(repository)
            runCurrent()

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

    private fun profile(newestSeason: Int) =
        PlayerProfile(
            playerId = PLAYER_ID,
            firstName = "Aleksi",
            lastName = "Heponiemi",
            position = "C",
            career = PlayerCareer(),
            seasons =
                listOf(
                    PlayerSeasonLine(
                        season = newestSeason,
                        gameTypeId = 2,
                        leagueAbbrev = "NHL",
                        teamName = "Carolina Hurricanes",
                        sequence = 1,
                        totals = PlayerTotals.Skater(gamesPlayed = 60),
                    ),
                ),
        )

    private fun game(gameId: Long) =
        PlayerGame.Skater(
            gameId = gameId,
            gameDate = "2026-11-01",
            opponentAbbrev = "OTT",
            isHome = true,
            goals = 1,
        )

    /** Answers a game-log request from [gamesBySeason] and records each season asked for. */
    private class FakeRepository(
        private val profile: PlayerProfile,
        private val gamesBySeason: Map<Int, List<PlayerGame>> = emptyMap(),
    ) : PlayerProfileRepository {
        val requestedSeasons = mutableListOf<Int>()

        override suspend fun profile(playerId: Long): Loadable<PlayerProfile> = Loadable(profile, Freshness.Fresh, null)

        override suspend fun gameLog(
            playerId: Long,
            season: Int,
            isGoalie: Boolean,
        ): Loadable<List<PlayerGame>> {
            requestedSeasons += season
            return Loadable(gamesBySeason[season].orEmpty(), Freshness.Fresh, null)
        }
    }

    private companion object {
        const val PLAYER_ID = 8480037L
    }
}
