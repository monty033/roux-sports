package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.NotificationLedgerEntry.Companion.UNKNOWN_GOAL_COUNT
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Something the caller should claim. The two notifying shapes are posted; a baseline is claimed and
 * said nothing about.
 *
 * [gameId] is carried on every claim so a caller can act without looking the game up again.
 */
sealed interface Alert {
    val gameId: GameId

    /**
     * The game is being watched and has started.
     *
     * [goalBaseline] is the total the claim must record alongside the start: the goals already announced
     * when there is a record, and the whole current score when there is none. It is stated here rather
     * than left for the caller to invent, because the wrong baseline either announces goals scored
     * before this app was watching or swallows a real one.
     */
    data class GameStart(
        override val gameId: GameId,
        val goalBaseline: Int,
    ) : Alert

    /**
     * The score has moved past what was last announced.
     *
     * [goalCount] is the score total, which is also the value the claim is made against. [awayScore]
     * and [homeScore] are kept so the notifier can word the alert without a second lookup; they are
     * null only when the feed omitted a score, in which case no goal alert is produced at all.
     */
    data class Goal(
        override val gameId: GameId,
        val goalCount: Int,
        val awayScore: Int?,
        val homeScore: Int?,
    ) : Alert

    /**
     * Record this total without announcing anything.
     *
     * Needed when the app first sees a live game whose start alerts are switched off: without a record,
     * every later goal has nothing to compare against and would be announced as though it were the first.
     */
    data class Baseline(
        override val gameId: GameId,
        val goalCount: Int,
    ) : Alert
}

/**
 * Decides what, if anything, a game's current state deserves in the way of claims.
 *
 * Pure on purpose: it reads the ledger it is given and returns claims to attempt. It never writes,
 * never posts, and holds no state between calls. Deduplication lives in the store's atomic claim, so
 * a wrong answer here can repeat an alert only if the claim also allows it - which it cannot.
 *
 * The rules, in the order they are applied:
 *  - A game that is not a favourite, or is not live, produces nothing.
 *  - No ledger entry means this app has never observed the game - the phone was asleep through the
 *    start, or alerts were just turned on. With start alerts on, that is a start alert whose baseline is
 *    the current score, so goals scored before now are not announced. With them off, it is a silent
 *    baseline, so a goal from here on still has something to compare against.
 *  - Once a record exists, the start alert needs the per-team start preference and an unclaimed start,
 *    and its baseline is the total already announced - never the current score, which would swallow a
 *    goal that happened since the last pass.
 *  - A goal alert needs the goal preference and a total above the last one announced. Equal totals are
 *    duplicates. A lower total is the feed correcting itself and is deliberately not announced, because
 *    dragging the ledger back would let the correction be announced again when the score returns; the
 *    recorded total therefore keeps its high-water mark.
 *  - A missing score is unknown, not zero, so it announces no goal.
 */
class NotificationDecider {
    fun decide(
        game: Game,
        ledger: NotificationLedgerEntry?,
        preferences: TeamAlertPreferences,
        isFavourite: Boolean,
    ): List<Alert> {
        if (!isFavourite) return emptyList()
        if (game.state !is GameState.Live) return emptyList()

        val away = game.awayScore
        val home = game.homeScore
        val goalCount = if (away != null && home != null) away + home else null

        if (ledger == null) {
            return when {
                // With no score there is no safe baseline: treating it as zero would let the first score the feed
                // supplies later be announced as a goal nobody saw. Record it as unknown instead.
                preferences.start -> listOf(Alert.GameStart(game.id, goalBaseline = goalCount ?: UNKNOWN_GOAL_COUNT))

                goalCount != null -> listOf(Alert.Baseline(game.id, goalCount))

                else -> emptyList()
            }
        }

        val alerts = mutableListOf<Alert>()
        if (preferences.start && !ledger.startNotified) {
            alerts += Alert.GameStart(game.id, goalBaseline = ledger.lastNotifiedGoalCount)
        }
        // A recorded unknown means the game was first seen without a score. The first score that arrives becomes the
        // baseline silently; announcing it would claim a goal that was never seen happen.
        if (ledger.goalCountIsUnknown && goalCount != null) {
            alerts += Alert.Baseline(game.id, goalCount)
            return alerts
        }
        if (preferences.goals && goalCount != null && goalCount > ledger.lastNotifiedGoalCount) {
            alerts +=
                Alert.Goal(
                    gameId = game.id,
                    goalCount = goalCount,
                    awayScore = away,
                    homeScore = home,
                )
        }
        return alerts
    }

    /**
     * Whether a finished game's ledger entry is old enough to sweep.
     *
     * A day is measured from the entry's last write, not the game's start: a final that was written
     * to an hour ago is still the only record of what was announced, and re-announcing is the failure
     * that matters more than keeping a row around.
     */
    fun isLedgerStale(
        game: Game,
        ledger: NotificationLedgerEntry,
        now: Instant,
    ): Boolean = game.state is GameState.Final && ledger.updatedAt + LEDGER_RETENTION <= now

    companion object {
        /** How long a finished game's deduplication record is kept. */
        val LEDGER_RETENTION: Duration = 24.hours
    }
}
