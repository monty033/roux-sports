package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The decider is pure, so these tests feed it a ledger instead of a store. That makes the interesting
 * property reachable: what the second pass does once the first pass's claims have been written. A test
 * that only ever called `decide` twice with the same input would prove the function is stateless, not
 * that a goal is announced once.
 */
class NotificationDeciderTest {
    private val decider = NotificationDecider()
    private val now = Instant.parse("2025-01-01T00:00:00Z")
    private val gameId = GameId("2025020123")

    private fun game(
        state: GameState = GameState.Live(),
        awayScore: Int? = 0,
        homeScore: Int? = 0,
    ) = Game(
        id = gameId,
        date = "2025-02-01",
        state = state,
        home = TeamRef(TeamKey("MTL"), name = "Montréal Canadiens"),
        away = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs"),
        awayScore = awayScore,
        homeScore = homeScore,
    )

    private fun ledger(
        goalCount: Int = 0,
        startNotified: Boolean = false,
        updatedAt: Instant = now,
    ) = NotificationLedgerEntry(goalCount, startNotified, updatedAt)

    /**
     * Applies the alerts a pass produced the way the store's claims would: a start claims the start,
     * a goal claims its total. Returns the ledger the next pass would read.
     */
    private fun applyClaims(
        alerts: List<Alert>,
        previous: NotificationLedgerEntry?,
    ): NotificationLedgerEntry =
        if (alerts.isEmpty()) {
            previous ?: error("no ledger to carry forward")
        } else {
            ledger(
                goalCount =
                    alerts.filterIsInstance<Alert.Goal>().lastOrNull()?.goalCount
                        ?: previous?.lastNotifiedGoalCount
                        ?: 0,
                startNotified = previous?.startNotified == true || alerts.any { it is Alert.GameStart },
                updatedAt = now,
            )
        }

    @Test
    fun aStartIsAnnouncedOnceAcrossScheduledAndLive() {
        // Nothing is said about a game that has not started.
        assertEquals(
            emptyList<Alert>(),
            decider.decide(game(GameState.Scheduled), ledger(), TeamAlertPreferences(start = true, goals = true), isFavourite = true),
        )

        val first = decider.decide(game(GameState.Live()), ledger(), TeamAlertPreferences(start = true, goals = true), isFavourite = true)
        assertEquals(listOf<Alert>(Alert.GameStart(gameId, goalBaseline = 0)), first)

        // Second pass, reading what the first pass's claim wrote: silence.
        assertEquals(
            emptyList<Alert>(),
            decider.decide(
                game(GameState.Live()),
                applyClaims(first, ledger()),
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
    }

    @Test
    fun aGoalIsAnnouncedOnceAndThenOnlyWhenTheTotalMoves() {
        val started = ledger(goalCount = 0, startNotified = true)

        val first =
            decider.decide(
                game(awayScore = 1, homeScore = 0),
                started,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            )
        assertEquals(listOf<Alert>(Alert.Goal(gameId, goalCount = 1, awayScore = 1, homeScore = 0)), first)

        val claimed = applyClaims(first, started)
        assertEquals(
            "the same score must not be announced twice",
            emptyList<Alert>(),
            decider.decide(
                game(awayScore = 1, homeScore = 0),
                claimed,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )

        assertEquals(
            listOf<Alert>(Alert.Goal(gameId, goalCount = 2, awayScore = 1, homeScore = 1)),
            decider.decide(
                game(awayScore = 1, homeScore = 1),
                claimed,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
    }

    @Test
    fun aCorrectionDownwardIsAnnouncedAsNothingAndDoesNotRewindTheLedger() {
        // 1-2 was announced, then the feed corrects to 1-1. A later 1-2 is the same goal, not a new one.
        val corrected = ledger(goalCount = 3, startNotified = true)
        assertEquals(
            emptyList<Alert>(),
            decider.decide(
                game(awayScore = 1, homeScore = 1),
                corrected,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
        assertEquals(
            "the corrected total is re-reached, which is still the goal already announced",
            emptyList<Alert>(),
            decider.decide(
                game(awayScore = 1, homeScore = 2),
                corrected,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
    }

    @Test
    fun aGameTheAppNeverObservedGetsAStartAndNoGoal() {
        // Cold start mid-game: the score was never seen to change, so calling it a goal would be a claim
        // this app cannot make - and it would fire once per game a sweep picked up.
        assertEquals(
            "the 2-1 on the board becomes the baseline, so it is not announced as a goal",
            listOf<Alert>(Alert.GameStart(gameId, goalBaseline = 3)),
            decider.decide(
                game(awayScore = 2, homeScore = 1),
                ledger = null,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
    }

    @Test
    fun aTeamThatIsNotAFavouriteGetsNothing() {
        assertEquals(
            emptyList<Alert>(),
            decider.decide(
                game(awayScore = 2, homeScore = 1),
                ledger(),
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = false,
            ),
        )
    }

    @Test
    fun eachPreferenceSilencesItsOwnAlert() {
        assertEquals(
            "start off still announces goals",
            listOf<Alert>(Alert.Goal(gameId, goalCount = 1, awayScore = 1, homeScore = 0)),
            decider.decide(
                game(awayScore = 1, homeScore = 0),
                ledger(startNotified = false),
                TeamAlertPreferences(start = false, goals = true),
                isFavourite = true,
            ),
        )
        assertEquals(
            "goals off still announces the start, baselined at what was already announced",
            listOf<Alert>(Alert.GameStart(gameId, goalBaseline = 0)),
            decider.decide(
                game(awayScore = 1, homeScore = 0),
                ledger(startNotified = false),
                TeamAlertPreferences(start = true, goals = false),
                isFavourite = true,
            ),
        )
        assertEquals(
            emptyList<Alert>(),
            decider.decide(
                game(awayScore = 1, homeScore = 0),
                ledger(startNotified = true),
                TeamAlertPreferences(start = false, goals = false),
                isFavourite = true,
            ),
        )
    }

    @Test
    fun aMissingScoreAnnouncesNoGoal() {
        // A score the feed did not supply is unknown, not zero: announcing 1-0 from a missing away score
        // would invent a goal.
        assertEquals(
            emptyList<Alert>(),
            decider.decide(
                game(awayScore = null, homeScore = null),
                ledger(goalCount = 0, startNotified = true),
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
    }

    @Test
    fun aFinishedGameIsSilentAndItsLedgerGoesStaleAfterADay() {
        val final = game(GameState.Final, awayScore = 3, homeScore = 2)
        assertEquals(
            emptyList<Alert>(),
            decider.decide(
                final,
                ledger(goalCount = 5, startNotified = true),
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )

        val entry = ledger(goalCount = 5, startNotified = true, updatedAt = now)
        assertFalse(decider.isLedgerStale(final, entry, now + 23.hours))
        assertTrue(decider.isLedgerStale(final, entry, now + 25.hours))
        // Retention follows the record, not the game: a live game's entry is never swept away from under it.
        assertFalse(decider.isLedgerStale(game(GameState.Live()), entry, now + 48.hours))
    }

    @Test
    fun aStartAndAGoalInTheSamePassDoNotSwallowEachOther() {
        // The start is pending and a goal has happened since the last pass. The start's baseline must be what was
        // already announced (0), not the current total (1) - otherwise the start claim records 1 and the goal claim
        // for 1 is then refused as a duplicate, and the goal is never announced.
        val pendingStart = ledger(goalCount = 0, startNotified = false)
        val alerts =
            decider.decide(
                game(awayScore = 1, homeScore = 0),
                pendingStart,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            )
        assertEquals(
            listOf<Alert>(
                Alert.GameStart(gameId, goalBaseline = 0),
                Alert.Goal(gameId, goalCount = 1, awayScore = 1, homeScore = 0),
            ),
            alerts,
        )
    }

    @Test
    fun aStartClaimedAfterGoalsKeepsTheBaselineOfWhatWasAnnounced() {
        // The start is late: three goals are on the board and one of them was already announced.
        val lateStart = ledger(goalCount = 1, startNotified = false)
        assertEquals(
            listOf<Alert>(
                Alert.GameStart(gameId, goalBaseline = 1),
                Alert.Goal(gameId, goalCount = 3, awayScore = 2, homeScore = 1),
            ),
            decider.decide(
                game(awayScore = 2, homeScore = 1),
                lateStart,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
    }

    @Test
    fun withStartAlertsOffTheFirstSightIsASilentBaselineSoLaterGoalsStillWork() {
        val startOff = TeamAlertPreferences(start = false, goals = true)
        val first = decider.decide(game(awayScore = 2, homeScore = 1), ledger = null, startOff, isFavourite = true)
        assertEquals("nothing is announced, but the total is recorded", listOf<Alert>(Alert.Baseline(gameId, 3)), first)

        // With the baseline written, the next goal is a goal rather than another first sight.
        val baselined = ledger(goalCount = 3, startNotified = false)
        assertEquals(
            emptyList<Alert>(),
            decider.decide(game(awayScore = 2, homeScore = 1), baselined, startOff, isFavourite = true),
        )
        assertEquals(
            listOf<Alert>(Alert.Goal(gameId, goalCount = 5, awayScore = 3, homeScore = 2)),
            decider.decide(game(awayScore = 3, homeScore = 2), baselined, startOff, isFavourite = true),
        )
    }

    @Test
    fun aFirstSightWithoutAScoreRecordsAnUnknownAndStaysSilentWhenTheScoreArrives() {
        // The feed supplies a live game with no score yet. Zero here would be a claim that the game is 0-0, and the
        // first 2-1 that arrives would then be announced as a goal nobody saw.
        val unknown = NotificationLedgerEntry.UNKNOWN_GOAL_COUNT
        assertEquals(
            listOf<Alert>(Alert.GameStart(gameId, goalBaseline = unknown)),
            decider.decide(
                game(awayScore = null, homeScore = null),
                ledger = null,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )

        val recordedUnknown = ledger(goalCount = unknown, startNotified = true)
        assertEquals(
            "the score that arrives becomes the baseline, silently",
            listOf<Alert>(Alert.Baseline(gameId, goalCount = 3)),
            decider.decide(
                game(awayScore = 2, homeScore = 1),
                recordedUnknown,
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )

        // From a real baseline onwards, goals behave normally.
        assertEquals(
            listOf<Alert>(Alert.Goal(gameId, goalCount = 4, awayScore = 2, homeScore = 2)),
            decider.decide(
                game(awayScore = 2, homeScore = 2),
                ledger(goalCount = 3, startNotified = true),
                TeamAlertPreferences(start = true, goals = true),
                isFavourite = true,
            ),
        )
    }
}
