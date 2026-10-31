package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * An alert a game deserves. Every alert is a claim, not a delivery: the caller must take it from the
 * ledger atomically before posting, and post only if it won.
 *
 * [gameId] is carried on the alert so a caller can claim without looking the game up again.
 */
sealed interface Alert {
    val gameId: GameId

    /** The game is being watched and has started. */
    data class GameStart(
        override val gameId: GameId,
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
}

/**
 * Decides what, if anything, a game's current state deserves in the way of alerts.
 *
 * Pure on purpose: it reads the ledger it is given and returns claims to attempt. It never writes,
 * never posts, and holds no state between calls. Deduplication lives in the store's atomic claim, so
 * a wrong answer here can repeat an alert only if the claim also allows it - which it cannot.
 *
 * The rules, in the order they are applied:
 *  - A game that is not a favourite, or is not live, produces nothing.
 *  - The start alert requires the per-team start preference and an unclaimed start.
 *  - A goal alert requires the goal preference, a ledger entry, and a score total above the last one
 *    announced. Equal counts are duplicates; lower counts are the feed correcting itself and are
 *    deliberately not announced, because dragging the ledger back would let the correction be
 *    announced again when the score returns.
 *  - No ledger entry means this app has never observed the game - the phone was asleep through the
 *    start, or alerts were just turned on - so the game gets a start alert and no goal alert. Calling
 *    a score the app never saw change a "goal" is a false claim, and it would fire once for every
 *    game a cold start swept up.
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

        val alerts = mutableListOf<Alert>()
        if (preferences.start && ledger?.startNotified != true) {
            alerts += Alert.GameStart(game.id)
        }

        val away = game.awayScore
        val home = game.homeScore
        if (preferences.goals && ledger != null && away != null && home != null) {
            val goalCount = away + home
            if (goalCount > ledger.lastNotifiedGoalCount) {
                alerts +=
                    Alert.Goal(
                        gameId = game.id,
                        goalCount = goalCount,
                        awayScore = away,
                        homeScore = home,
                    )
            }
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
