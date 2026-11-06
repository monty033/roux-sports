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
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.policy.Alert
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
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
import kotlin.time.Duration.Companion.hours
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
        finalKind: FinalKind? = null,
        period: PeriodInfo? = null,
    ) = Game(
        id = GameId(gameId),
        date = "2025-01-01",
        state = state,
        home = TeamRef(TeamKey("MTL")),
        away = TeamRef(TeamKey("TOR")),
        startTimeUtc = now - 30.minutes,
        awayScore = away,
        homeScore = home,
        finalKind = finalKind,
        period = period,
    )

    private fun period(
        ordinal: Int,
        inIntermission: Boolean = true,
    ) = PeriodInfo(ordinal = ordinal, periodType = "REG", clock = "00:00", inIntermission = inIntermission)

    private fun intermission(period: Int = 1) =
        Alert.Intermission(
            gameId = GameId(gameId),
            period = period,
            awayAbbrev = "TOR",
            homeAbbrev = "MTL",
            awayScore = 1,
            homeScore = 0,
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

    /**
     * Alerts are opt-in now, so a test that expects one has to ask for it. Until this helper existed these fixtures
     * relied on the old default being on, which made "nothing posted" and "the alert was off" look identical.
     */
    private suspend fun DataStorePreferencesRepository.enableAlerts(team: TeamKey = TeamKey("TOR")) {
        setTeamAlertPreferences(team, TeamAlertPreferences(start = true, goals = true))
    }

    private fun run(
        scores: ScoresRepository,
        scheduler: GameWatchScheduler,
        poster: AlertPoster,
        preferences: PreferencesRepository,
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
            preferences.enableAlerts()
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
            preferences.enableAlerts()
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
    fun aFinishedGameWithTheFinalSwitchOffPostsNothingAndIsNotFollowedFurther() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            preferences.enableAlerts()
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
            assertEquals(emptyList<Alert>(), poster.posted)
            assertEquals("a finished game is not polled again", emptyList<Pair<String, Duration>>(), scheduler.repeated)
        }

    @Test
    fun aRunOnAFinalGamePostsOnceAndSchedulesNoFurtherPass() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(final = true))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            val ended = game(state = GameState.Final, away = 3, home = 2, finalKind = FinalKind.OT)
            val pass = run(FakeScores { Loadable(day(ended), Freshness.Fresh, null) }, scheduler, poster, preferences)

            val first = pass.run(gameId)
            val second = pass.run(gameId)

            assertEquals(1, first.posted)
            assertEquals("the claim, not the schedule, keeps a final from repeating", 0, second.posted)
            assertEquals(
                listOf<Alert>(Alert.Final(GameId(gameId), awayScore = 3, homeScore = 2, finalKind = FinalKind.OT)),
                poster.posted.toList(),
            )
            assertEquals("a final ends the chain, whatever the outcome", emptyList<Pair<String, Duration>>(), scheduler.repeated)
            assertTrue(preferences.getNotificationLedgerEntry(gameId)!!.finalNotified)
        }

    @Test
    fun aFinalThatCouldNotBePostedIsNotClaimedAndStillEndsTheChain() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(final = true))
            val scheduler = RecordingScheduler()
            val ended = game(state = GameState.Final, away = 3, home = 2)

            val blocked =
                run(FakeScores { Loadable(day(ended), Freshness.Fresh, null) }, scheduler, RecordingPoster { false }, preferences)
                    .run(gameId)

            assertEquals(0, blocked.posted)
            assertEquals("canPost is asked before the claim, so nothing was spent", null, preferences.getNotificationLedgerEntry(gameId))
            assertEquals(emptyList<Pair<String, Duration>>(), scheduler.repeated)
        }

    @Test
    fun aFinalWithAMissingScorePostsNothingClaimsNothingAndEndsTheChain() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(final = true))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            val ended = game(state = GameState.Final, away = null, home = 2)

            val outcome =
                run(FakeScores { Loadable(day(ended), Freshness.Fresh, null) }, scheduler, poster, preferences).run(gameId)

            assertEquals(0, outcome.posted)
            assertEquals(emptyList<Alert>(), poster.posted.toList())
            assertEquals(null, preferences.getNotificationLedgerEntry(gameId))
            assertEquals(emptyList<Pair<String, Duration>>(), scheduler.repeated)
        }

    @Test
    fun aFinalOutsideTheWindowIsNotAnnouncedEvenWhenItsLedgerRowWasSwept() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(final = true))
            val poster = RecordingPoster()
            val yesterdays = game(state = GameState.Final, away = 3, home = 2).copy(startTimeUtc = now - 30.hours)

            val outcome =
                run(FakeScores { Loadable(day(yesterdays), Freshness.Fresh, null) }, RecordingScheduler(), poster, preferences)
                    .run(gameId)

            assertEquals(0, outcome.posted)
            assertEquals(emptyList<Alert>(), poster.posted.toList())
            assertEquals(null, preferences.getNotificationLedgerEntry(gameId))
        }

    @Test
    fun aTeamThatIsNotAFavouriteIsFollowedWhenASwitchIsOn() =
        runBlocking {
            val preferences = preferences()
            // No favourites at all: the bell on a team is the whole request.
            preferences.setTeamAlertPreferences(TeamKey("MTL"), TeamAlertPreferences(start = true))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()

            val outcome =
                run(FakeScores { Loadable(day(game()), Freshness.Fresh, null) }, scheduler, poster, preferences).run(gameId)

            assertEquals(1, outcome.posted)
            assertTrue(poster.posted.single() is Alert.GameStart)
            assertEquals("and the live game is still watched", 1, scheduler.repeated.size)
        }

    @Test
    fun aFinalOnlySwitchOnTheOpposingTeamIsEnoughForTheFinal() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("MTL"), TeamAlertPreferences(final = true))
            val poster = RecordingPoster()
            val ended = game(state = GameState.Final, away = 3, home = 2)

            run(FakeScores { Loadable(day(ended), Freshness.Fresh, null) }, RecordingScheduler(), poster, preferences)
                .run(gameId)

            assertEquals(
                listOf<Alert>(Alert.Final(GameId(gameId), awayScore = 3, homeScore = 2, finalKind = null)),
                poster.posted.toList(),
            )
        }

    @Test
    fun aLiveGameAsksToBeCheckedAgainOnThePollPolicyCadence() =
        runBlocking {
            val preferences = preferences()
            preferences.setFavourites(setOf(TeamKey("TOR")))
            preferences.enableAlerts()
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
            preferences.enableAlerts()
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
            preferences.enableAlerts()
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
            preferences.enableAlerts()
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
            preferences.enableAlerts()
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
            preferences.enableAlerts()
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
            preferences.enableAlerts()
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

    @Test
    fun anIntermissionIsPostedOnceForTwoPollsAndClaimedForItsPeriod() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(intermission = true))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            val current = game(period = period(1))
            val pass = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, poster, preferences)

            val first = pass.run(gameId)
            val second = pass.run(gameId)

            assertEquals("the break is announced", 1, first.posted)
            assertEquals("the next poll inside it is silent", 0, second.posted)
            assertEquals(listOf<Alert>(intermission(period = 1)), poster.posted.toList())
            assertEquals(
                "the claim records which period was announced",
                1,
                preferences.getNotificationLedgerEntry(gameId)!!.lastIntermissionNotified,
            )
        }

    @Test
    fun theSecondPeriodsIntermissionIsASeparateAlert() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(intermission = true))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            var current = game(period = period(1))
            val pass = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, poster, preferences)

            assertEquals(1, pass.run(gameId).posted)
            current = game(period = period(2))
            assertEquals("the next break is news again", 1, pass.run(gameId).posted)
            assertEquals("but not twice", 0, pass.run(gameId).posted)

            assertEquals(listOf<Alert>(intermission(period = 1), intermission(period = 2)), poster.posted.toList())
            assertEquals(2, preferences.getNotificationLedgerEntry(gameId)!!.lastIntermissionNotified)
        }

    @Test
    fun aGameAlreadyBackInPlayOnFirstSightGetsNoRetroactiveIntermission() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(intermission = true))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            val current = game(period = period(2, inIntermission = false))
            val pass = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, poster, preferences)

            assertEquals(0, pass.run(gameId).posted)
            assertEquals("only the score baseline is written", emptyList<Alert>(), poster.posted.toList())
            assertEquals(0, preferences.getNotificationLedgerEntry(gameId)?.lastIntermissionNotified ?: 0)
        }

    @Test
    fun theIntermissionSwitchOffPostsNothingEvenWhenGoalsAreOn() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(goals = true))
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            val current = game(period = period(1))
            val pass = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, poster, preferences)

            assertEquals(0, pass.run(gameId).posted)
            assertEquals("goals being on is not permission for an intermission", emptyList<Alert>(), poster.posted.toList())
            assertEquals(0, preferences.getNotificationLedgerEntry(gameId)?.lastIntermissionNotified ?: 0)
        }

    @Test
    fun anIntermissionThatCannotBePostedIsNotClaimed() =
        runBlocking {
            val preferences = preferences()
            preferences.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(intermission = true))
            val scheduler = RecordingScheduler()
            val current = game(period = period(1))
            val blocked = RecordingPoster { false }
            val pass = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, blocked, preferences)

            assertEquals(0, pass.run(gameId).posted)
            assertEquals(
                "a silenced channel does not spend the break",
                0,
                preferences.getNotificationLedgerEntry(gameId)?.lastIntermissionNotified ?: 0,
            )

            val allowed = RecordingPoster()
            val after = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, allowed, preferences).run(gameId)
            assertEquals(1, after.posted)
            assertEquals(listOf<Alert>(intermission(period = 1)), allowed.posted.toList())
        }

    @Test
    fun theClaimStopsTwoPollsThatBothSawTheBreakFromPostingTwice() =
        runBlocking {
            val backing = preferences()
            backing.setTeamAlertPreferences(TeamKey("TOR"), TeamAlertPreferences(intermission = true))
            // Both passes decide from a ledger that has not yet seen the other's claim. The decider cannot tell them
            // apart; only the atomic claim can, which is why the claim is the rule that makes the alert at-most-once.
            val staleLedger =
                object : PreferencesRepository by backing {
                    override suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry? = null
                }
            val poster = RecordingPoster()
            val scheduler = RecordingScheduler()
            val current = game(period = period(1))
            val pass = run(FakeScores { Loadable(day(current), Freshness.Fresh, null) }, scheduler, poster, staleLedger)

            assertEquals(1, pass.run(gameId).posted)
            assertEquals(0, pass.run(gameId).posted)
            assertEquals("the same break is posted once", listOf<Alert>(intermission(period = 1)), poster.posted.toList())
            assertEquals(1, backing.getNotificationLedgerEntry(gameId)!!.lastIntermissionNotified)
        }
}
