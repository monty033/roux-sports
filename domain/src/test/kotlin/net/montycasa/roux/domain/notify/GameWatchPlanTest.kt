package net.montycasa.roux.domain.notify

import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.NotificationDecider
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The scheduling rules, without a scheduler: what the platform is told to do is only as good as the list
 * it is given, and these are the cases where that list is wrong in ways no device test would notice.
 */
class GameWatchPlanTest {
    private val now = Instant.parse("2025-01-01T18:00:00Z")

    private fun game(
        id: String = "2025020123",
        state: GameState = GameState.Scheduled,
        start: Instant? = now + 2.hours,
        home: String = "MTL",
        away: String = "TOR",
    ) = Game(
        id = GameId(id),
        date = "2025-02-01",
        state = state,
        home = TeamRef(TeamKey(home)),
        away = TeamRef(TeamKey(away)),
        startTimeUtc = start,
    )

    private fun day(vararg games: Game) = ScoreDay("2025-02-01", games.toList(), listOf(DateIndexEntry("2025-02-01", games.size)), 0)

    private fun on() = { _: TeamKey -> TeamAlertPreferences(start = true, goals = true) }

    private fun off() = { _: TeamKey -> TeamAlertPreferences(start = false, goals = false) }

    @Test
    fun aFollowedGameIsWatchedForTheTimeUntilItStarts() {
        val wanted = GameWatchPlan.wanted(now, day(game(start = now + 2.hours)), on())

        assertEquals(listOf(WatchedGame("2025020123", 2.hours, firesAt = now + 2.hours)), wanted)
    }

    @Test
    fun thePlanNamesTheStartTimeItIsWaitingForSoAMovedGameIsRecognisable() {
        val scheduled = GameWatchPlan.wanted(now, day(game(start = now + 2.hours)), on())
        val moved = GameWatchPlan.wanted(now, day(game(start = now + 3.hours)), on())
        val underWay = GameWatchPlan.wanted(now, day(game(state = GameState.Live())), on())

        assertEquals(now + 2.hours, scheduled.single().firesAt)
        // Same game, same subscription, different start: a reconcile compares these to notice the move.
        assertEquals(now + 3.hours, moved.single().firesAt)
        // Nothing to compare for a game already playing; it is due whenever it is enqueued.
        assertNull(underWay.single().firesAt)
    }

    @Test
    fun aGameAlreadyUnderWayIsWatchedWithNoDelay() {
        val live = GameWatchPlan.wanted(now, day(game(state = GameState.Live())), on())
        val pre = GameWatchPlan.wanted(now, day(game(state = GameState.Pre)), on())
        val started = GameWatchPlan.wanted(now, day(game(start = now - 1.hours)), on())

        assertEquals(listOf(WatchedGame("2025020123", 0.minutes, firesAt = null)), live)
        assertEquals(listOf(WatchedGame("2025020123", 0.minutes, firesAt = null)), pre)
        // A scheduled game whose start has already passed is under way as far as anyone here can tell.
        assertEquals(listOf(WatchedGame("2025020123", 0.minutes, firesAt = null)), started)
    }

    @Test
    fun aGameWithNoStartTimeIsSkippedRatherThanWatchedImmediately() {
        // Waking the device now for a game with no published time would be a guess dressed as a schedule.
        assertNull(
            GameWatchPlan
                .wanted(now, day(game(start = null)), on())
                .firstOrNull(),
        )
    }

    @Test
    fun longFinishedAndUnrecognisedGamesAreNotWatched() {
        assertEquals(
            emptyList<WatchedGame>(),
            GameWatchPlan.wanted(now, day(game(state = GameState.Final, start = now - 10.hours)), on()),
        )
        // An unknown state is not evidence of a future game; the feed will say so on a later pass.
        assertEquals(
            emptyList<WatchedGame>(),
            GameWatchPlan.wanted(now, day(game(state = GameState.Unknown("TBD"))), on()),
        )
    }

    @Test
    fun aTeamWithEveryAlertOffIsNotWatched() {
        assertEquals(
            emptyList<WatchedGame>(),
            GameWatchPlan.wanted(now, day(game()), off()),
        )
        // Either alert alone is still a reason to watch.
        assertEquals(
            listOf("2025020123"),
            GameWatchPlan
                .wanted(now, day(game())) { TeamAlertPreferences(start = false, goals = true) }
                .map { it.gameId },
        )
    }

    @Test
    fun aTeamWithOnlyTheFinalSwitchOnGetsAChain() {
        val onlyFinal = { _: TeamKey -> TeamAlertPreferences(final = true) }

        assertEquals(
            listOf(WatchedGame("2025020123", 2.hours, firesAt = now + 2.hours)),
            GameWatchPlan.wanted(now, day(game(start = now + 2.hours)), onlyFinal),
        )
        assertEquals(
            listOf(WatchedGame("2025020123", 0.minutes, firesAt = null)),
            GameWatchPlan.wanted(now, day(game(state = GameState.Live())), onlyFinal),
        )
    }

    @Test
    fun everyOneOfTheFiveSwitchesAloneIsEnoughToFollowATeam() {
        val alone =
            listOf(
                TeamAlertPreferences(start = true),
                TeamAlertPreferences(goals = true),
                TeamAlertPreferences(intermission = true),
                TeamAlertPreferences(final = true),
                TeamAlertPreferences(upcoming = true),
            )

        for (preferences in alone) {
            assertEquals(
                "$preferences",
                listOf("2025020123"),
                GameWatchPlan.wanted(now, day(game())) { preferences }.map { it.gameId },
            )
        }
    }

    @Test
    fun aTeamIsFollowedByItsSwitchesAloneWhetherOrNotItIsAFavourite() {
        // The plan is never told who the favourites are: only the opposing team has a switch on, and that is enough.
        val onlyBoston = { team: TeamKey -> if (team == TeamKey("BOS")) TeamAlertPreferences(goals = true) else TeamAlertPreferences() }

        assertEquals(
            listOf("2025020123"),
            GameWatchPlan.wanted(now, day(game(home = "MTL", away = "BOS")), onlyBoston).map { it.gameId },
        )
        assertEquals(
            emptyList<WatchedGame>(),
            GameWatchPlan.wanted(now, day(game(home = "MTL", away = "TOR")), onlyBoston),
        )
    }

    @Test
    fun aReconcileRightAfterTheHornKeepsThatGamesPassAlive() {
        val justEnded = game(state = GameState.Final, start = now - 3.hours)

        assertEquals(
            listOf(WatchedGame("2025020123", 0.minutes, firesAt = null)),
            GameWatchPlan.wanted(now, day(justEnded), on()),
        )
    }

    @Test
    fun aFinishedGameIsWantedUpToTheEndOfTheWindowAndNotAMomentLonger() {
        val window = NotificationDecider.FINAL_WINDOW
        assertEquals(6.hours, window)

        assertEquals(
            "exactly at the end of the window is still inside it",
            listOf("2025020123"),
            GameWatchPlan.wanted(now, day(game(state = GameState.Final, start = now - window)), on()).map { it.gameId },
        )
        assertEquals(
            emptyList<WatchedGame>(),
            GameWatchPlan.wanted(now, day(game(state = GameState.Final, start = now - window - 1.minutes)), on()),
        )
    }

    @Test
    fun aReconcileAfterTheWindowDropsTheFinishedGame() {
        val longOver = game(state = GameState.Final, start = now - 7.hours)

        assertEquals(emptyList<WatchedGame>(), GameWatchPlan.wanted(now, day(longOver), on()))
    }

    @Test
    fun aFinishedGameWithNoStartTimeIsNotWanted() {
        // Nothing to measure the window from, and nothing to wait for: the plan cannot tell a fresh final from a stale one.
        assertEquals(
            emptyList<WatchedGame>(),
            GameWatchPlan.wanted(now, day(game(state = GameState.Final, start = null)), on()),
        )
    }

    @Test
    fun aFinishedGameOfATeamWithNoSwitchOnIsNotWanted() {
        assertEquals(
            emptyList<WatchedGame>(),
            GameWatchPlan.wanted(now, day(game(state = GameState.Final, start = now - 3.hours)), off()),
        )
    }

    @Test
    fun aGameListedTwiceProducesOneWatch() {
        val wanted =
            GameWatchPlan.wanted(
                now,
                day(game(id = "2025020123"), game(id = "2025020123")),
                on(),
            )

        assertEquals(listOf("2025020123"), wanted.map { it.gameId })
    }
}
