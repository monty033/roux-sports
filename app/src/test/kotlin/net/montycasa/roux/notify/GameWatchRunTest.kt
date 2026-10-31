package net.montycasa.roux.notify

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.prefs.DataStorePreferencesRepository
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.policy.Alert
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * One pass of a watched game, end to end: what it claims, what it posts, and what it asks for next.
 *
 * The poster is a recorder, so "posted" means an alert actually reached the notifier rather than a claim
 * having been taken - and the counting tests below fail if the order (claim first, post second) is reversed
 * and a claim is silently dropped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameWatchRunTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = Instant.parse("2025-01-02T02:00:00Z")
    private val zone = ZoneId.of("America/New_York")
    private val gameId = "2025020123"

    private fun game(
        state: GameState = GameState.Live(),
        away: Int? = 1,
        home: Int? = 0,
    ) = Game(
        id = GameId(gameId),
        date = "2025-01-01",
        state = state,
        home = TeamRef(TeamKey("MTL")),
        away = TeamRef(TeamKey("TOR")),
        startTimeUtc = now - 30.minutes,
        awayScore = away,
        homeScore = home,
    )

    private fun day(game: Game) =
        ScoreDay(
            date = "2025-01-01",
            games = listOf(game),
            dateIndex = listOf(DateIndexEntry("2025-01-01", 1)),
            droppedCount = 0,
        )

    private class FakeScores(
        private val answer: () -> Loadable<ScoreDay>,
    ) : ScoresRepository {
        override suspend fun loadDay(
            date: String,
            today: String,
        ): Loadable<ScoreDay> = answer()

        override suspend fun prefetchAdjacent(
            date: String,
            today: String,
        ) = Unit
    }

    private class RecordingScheduler : GameWatchScheduler {
        val repeated = CopyOnWriteArrayList<Pair<String, Duration>>()

        override suspend fun reconcile(
            now: Instant,
            day: ScoreDay,
            favourites: Set<TeamKey>,
            alertPreferences: (TeamKey) -> TeamAlertPreferences,
        ) = Unit

        override suspend fun watchAgain(
            gameId: String,
            delay: Duration,
        ) {
            repeated += gameId to delay
        }

        override suspend fun cancelAll() = Unit
    }

    private class RecordingPoster(
        private val ableFor: (Alert) -> Boolean = { true },
    ) : AlertPoster {
        val posted = CopyOnWriteArrayList<Alert>()

        override fun canPost(alert: Alert): Boolean = ableFor(alert)

        override fun post(
            alert: Alert,
            game: Game,
        ): NotificationOutcome {
            posted += alert
            return NotificationOutcome.Posted(alert.gameId.value.hashCode())
        }
    }

    private fun preferences(): DataStorePreferencesRepository =
        DataStorePreferencesRepository(
            directory = temporaryFolder.newFolder("datastore").absolutePath.toPath(),
            scope = CoroutineScope(Dispatchers.IO),
        )

    private fun run(
        scores: ScoresRepository,
        scheduler: GameWatchScheduler,
        poster: AlertPoster,
        preferences: DataStorePreferencesRepository,
    ) = GameWatchRun(
        preferences = preferences,
        scores = scores,
        scheduler = scheduler,
        poster = poster,
        clock =
            object : Clock {
                override fun now(): Instant = now
            },
        zone = zone,
    )

    @Test
    fun aLiveFavouriteIsClaimedAndAnnouncedOnce() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            val pass = run(FakeScores { Loadable(day(game()), Freshness.Fresh, null) }, scheduler, poster, preferences)

            val first = pass.run(gameId)
            val second = pass.run(gameId)

            assertEquals("one start alert, however many passes see the same score", 1, first.posted)
            assertEquals("and the second pass announces nothing", 0, second.posted)
            assertTrue(poster.posted.single() is Alert.GameStart)
            // The ledger is written before the post, so a crash between them loses an alert rather than repeating it.
            assertTrue(preferences.getNotificationLedgerEntry(gameId)!!.startNotified)
        }

    @Test
    fun aNewGoalIsAnnouncedAndTheNextOneIsNot() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            var current = game(away = 1, home = 0)
            val pass =
                run(
                    FakeScores { Loadable(day(current), Freshness.Fresh, null) },
                    scheduler,
                    poster,
                    preferences,
                )
            pass.run(gameId)
            poster.posted.clear()

            current = game(away = 2, home = 0)
            val goal = pass.run(gameId)
            val repeat = pass.run(gameId)

            assertEquals("the new total is announced", 1, goal.posted)
            assertEquals("the same total again is not", 0, repeat.posted)
            assertEquals(Alert.Goal(GameId(gameId), goalCount = 2, awayScore = 2, homeScore = 0), poster.posted.single())
        }

    @Test
    fun aGameNobodyFollowsIsIgnored() =
        runBlocking {
            val preferences = preferences()
            val poster = RecordingPoster()
            val outcome =
                run(FakeScores { Loadable(day(game()), Freshness.Fresh, null) }, RecordingScheduler(), poster, preferences)
                    .run(gameId)

            assertEquals(0, outcome.posted)
            assertEquals(emptyList<Alert>(), poster.posted)
        }

    @Test
    fun aFinishedGameIsNotPostedAndIsNotFollowedFurther() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()

            val outcome =
                run(
                    FakeScores { Loadable(day(game(state = GameState.Final)), Freshness.Fresh, null) },
                    scheduler,
                    poster,
                    preferences,
                ).run(gameId)

            assertEquals(0, outcome.posted)
            assertEquals("a finished game is not polled again", emptyList<Pair<String, Duration>>(), scheduler.repeated)
        }

    @Test
    fun aLiveGameAsksToBeCheckedAgainOnThePollPolicyCadence() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val scheduler = RecordingScheduler()

            run(FakeScores { Loadable(day(game()), Freshness.Fresh, null) }, scheduler, RecordingPoster(), preferences)
                .run(gameId)

            val (repeatedId, delay) = scheduler.repeated.single()
            assertEquals(gameId, repeatedId)
            // The scores screen's live cadence, not a number invented in the worker.
            assertEquals(25.seconds, delay)
        }

    @Test
    fun aServerPauseIsHonouredByTheFollowUp() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val scheduler = RecordingScheduler()
            val pause =
                Loadable<ScoreDay>(
                    data = day(game()),
                    freshness = Freshness.Stale(now - 1.minutes),
                    error = LoadError.Http(status = 429, retryAfter = 90.seconds),
                )

            val outcome = run(FakeScores { pause }, scheduler, RecordingPoster(), preferences).run(gameId)

            assertEquals(90.seconds, outcome.retryAfter)
            assertTrue(
                "the follow-up must not be sooner than the pause the server asked for: ${scheduler.repeated}",
                scheduler.repeated.single().second >= 90.seconds,
            )
        }

    @Test
    fun aSlateThatCouldNotBeLoadedAsksForARetry() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val failed = Loadable<ScoreDay>(null, Freshness.Missing, LoadError.Network)

            val outcome =
                run(FakeScores { failed }, RecordingScheduler(), RecordingPoster(), preferences).run(gameId)

            assertTrue("nothing was read, so nothing can be said about the game", outcome.failed)
            assertFalse("and no score was announced", outcome.posted > 0)
        }

    @Test
    fun aGameTheFeedStillCallsScheduledIsWatchedFurther() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            // A watch fires at the scheduled start; the feed lags and still says Scheduled. Re-chaining only on
            // Live would end the chain exactly when the goals start arriving.
            val scheduled = game(state = GameState.Scheduled, away = null, home = null)

            run(FakeScores { Loadable(day(scheduled), Freshness.Fresh, null) }, scheduler, poster, preferences)
                .run(gameId)

            assertEquals("a game that has not finished is watched again", 1, scheduler.repeated.size)
            assertTrue("and not sooner than the poll policy allows", scheduler.repeated.single().second >= 25.seconds)
        }

    @Test
    fun aGameStillPlayingAfterMidnightIsFoundInThePreviousDay() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val poster = RecordingPoster()
            // 02:00Z on the 2nd is still the evening of the 1st in New York; the slate it belongs to is the 1st.
            // The watch fires at 00:30 local on the 2nd, when "today" is already the 2nd.
            val pass =
                GameWatchRun(
                    preferences = preferences,
                    scores =
                        object : ScoresRepository {
                            override suspend fun loadDay(
                                date: String,
                                today: String,
                            ): Loadable<ScoreDay> =
                                if (date == "2025-01-01") {
                                    Loadable(day(game()), Freshness.Stale(now - 1.minutes), null)
                                } else {
                                    Loadable(null, Freshness.Missing, null)
                                }

                            override suspend fun prefetchAdjacent(
                                date: String,
                                today: String,
                            ) = Unit
                        },
                    scheduler = RecordingScheduler(),
                    poster = poster,
                    clock =
                        object : Clock {
                            override fun now(): Instant = Instant.parse("2025-01-02T05:30:00Z")
                        },
                    zone = zone,
                )

            val outcome = pass.run(gameId)

            assertEquals("the game is found where it was played, not where the clock now is", 1, outcome.posted)
            assertTrue(poster.posted.single() is Alert.GameStart)
        }

    @Test
    fun aBlockedPosterDoesNotSpendTheClaim() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val blocked = RecordingPoster { false }
            val scheduler = RecordingScheduler()
            val pass = run(FakeScores { Loadable(day(game()), Freshness.Fresh, null) }, scheduler, blocked, preferences)

            val blockedOutcome = pass.run(gameId)

            assertEquals(0, blockedOutcome.posted)
            assertEquals(
                "nothing was claimed while the user could not receive notifications",
                null,
                preferences.getNotificationLedgerEntry(gameId),
            )

            // Granting the permission afterwards still gets the start: the claim was never spent.
            val allowed = RecordingPoster()
            val after = run(FakeScores { Loadable(day(game()), Freshness.Fresh, null) }, scheduler, allowed, preferences).run(gameId)
            assertEquals(1, after.posted)
            assertTrue(allowed.posted.single() is Alert.GameStart)
        }

    @Test
    fun aSilencedGoalsChannelDoesNotSpendTheGoal() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            val scheduler = RecordingScheduler()
            var current = game(away = 1, home = 0)
            // Starts are allowed, goals are silenced: the two channels are separate promises.
            val goalsMuted = RecordingPoster { alert -> alert !is Alert.Goal }
            val pass = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, goalsMuted, preferences)
            pass.run(gameId)

            current = game(away = 2, home = 0)
            assertEquals("a silenced goal is not claimed", 0, pass.run(gameId).posted)

            // Unsilencing the channel afterwards still gets the goal that was missed.
            val allAllowed = RecordingPoster()
            val after = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, allAllowed, preferences).run(gameId)
            assertEquals(1, after.posted)
            assertEquals(Alert.Goal(GameId(gameId), goalCount = 2, awayScore = 2, homeScore = 0), allAllowed.posted.single())
        }
}
