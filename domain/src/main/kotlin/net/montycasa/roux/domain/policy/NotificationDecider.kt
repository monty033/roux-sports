package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.FinalKind
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
     * The game has reached an intermission and the score can be stated.
     *
     * [period] is the period that just ended, and it is also what makes the alert claimable once: the store records
     * the highest period announced, so the same break cannot be announced twice and the next period's break is new.
     * Both scores are non-null for the same reason as a final: a break whose score is unknown is not announced.
     */
    data class Intermission(
        override val gameId: GameId,
        val period: Int,
        val awayAbbrev: String,
        val homeAbbrev: String,
        val awayScore: Int,
        val homeScore: Int,
    ) : Alert

    /**
     * The game has ended and the final score can be stated.
     *
     * Both scores are non-null on purpose: an alert that cannot state a score is worse than no alert, so a final
     * with a missing score never becomes one. [finalKind] is null when the feed did not say how it ended, and the
     * notifier words that as a plain "Final" rather than guessing.
     */
    data class Final(
        override val gameId: GameId,
        val awayScore: Int,
        val homeScore: Int,
        val finalKind: FinalKind?,
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
 *  - Preferences with no switch on produce nothing. Favourites play no part: a bell on any team is a request.
 *  - A game that is neither live nor final produces nothing.
 *  - A final needs the final preference, an unclaimed final, both scores, and to be inside the recency window
 *    ([FINAL_WINDOW] after the start, or - with no start time to measure from - an existing ledger entry). A
 *    missing score is no alert at all, and nothing is claimed for it. Without the window, a pass that can reach
 *    finished games would re-announce a final whose ledger row had been swept.
 *  - For a live game: no ledger entry means this app has never observed the game - the phone was asleep through the
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
 *  - An intermission alert needs the intermission preference, `clock.inIntermission` true on a game that is
 *    not final, both scores, and a period above the last one announced. It is independent of the start and
 *    goal rules: a first sight of a game already at a break still announces the break once, and a game already
 *    back in play announces nothing retroactively.
 *  - A missing score is unknown, not zero, so it announces no goal and no intermission.
 */
class NotificationDecider {
    fun decide(
        game: Game,
        ledger: NotificationLedgerEntry?,
        preferences: TeamAlertPreferences,
        now: Instant,
    ): List<Alert> {
        if (!preferences.anyOn) return emptyList()
        if (game.state is GameState.Final) return decideFinal(game, ledger, preferences, now)
        if (game.state !is GameState.Live) return emptyList()

        val away = game.awayScore
        val home = game.homeScore
        val goalCount = if (away != null && home != null) away + home else null

        val intermission = decideIntermission(game, ledger, preferences)

        if (ledger == null) {
            val alerts = mutableListOf<Alert>()
            when {
                // With no score there is no safe baseline: treating it as zero would let the first score the feed
                // supplies later be announced as a goal nobody saw. Record it as unknown instead.
                preferences.start -> alerts += Alert.GameStart(game.id, goalBaseline = goalCount ?: UNKNOWN_GOAL_COUNT)

                goalCount != null -> alerts += Alert.Baseline(game.id, goalCount)
            }
            // A game first observed at a break has still reached one, so the break is new even without a record.
            intermission?.let { alerts += it }
            return alerts
        }

        val alerts = mutableListOf<Alert>()
        if (preferences.start && !ledger.startNotified) {
            alerts += Alert.GameStart(game.id, goalBaseline = ledger.lastNotifiedGoalCount)
        }
        // A recorded unknown means the game was first seen without a score. The first score that arrives becomes the
        // baseline silently; announcing it would claim a goal that was never seen happen.
        if (ledger.goalCountIsUnknown && goalCount != null) {
            alerts += Alert.Baseline(game.id, goalCount)
            intermission?.let { alerts += it }
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
        intermission?.let { alerts += it }
        return alerts
    }

    /**
     * The break the game is at right now, or null.
     *
     * The store's claim is what makes this at-most-once; the check here is what keeps the decider from asking for a
     * period whose claim is already known to be refused. A game that is back in play, or that this app first sees
     * after the break, has `inIntermission` false and therefore produces nothing retroactively.
     */
    private fun decideIntermission(
        game: Game,
        ledger: NotificationLedgerEntry?,
        preferences: TeamAlertPreferences,
    ): Alert.Intermission? {
        if (!preferences.intermission) return null
        val period = game.period ?: return null
        if (!period.inIntermission) return null
        if (ledger != null && period.ordinal <= ledger.lastIntermissionNotified) return null
        val away = game.awayScore ?: return null
        val home = game.homeScore ?: return null
        return Alert.Intermission(
            gameId = game.id,
            period = period.ordinal,
            awayAbbrev = game.away.key.abbrev,
            homeAbbrev = game.home.key.abbrev,
            awayScore = away,
            homeScore = home,
        )
    }

    private fun decideFinal(
        game: Game,
        ledger: NotificationLedgerEntry?,
        preferences: TeamAlertPreferences,
        now: Instant,
    ): List<Alert> {
        if (!preferences.final) return emptyList()
        if (ledger?.finalNotified == true) return emptyList()
        // With no start time there is nothing to measure the window from, so only a game this app has already
        // recorded - one it was watching - qualifies.
        val recent = if (game.startTimeUtc != null) isWithinFinalWindow(game.startTimeUtc, now) else ledger != null
        if (!recent) return emptyList()

        // A missing score is unknown, not zero: "Final: 0, 0" would be a statement the feed never made.
        val away = game.awayScore ?: return emptyList()
        val home = game.homeScore ?: return emptyList()
        return listOf(Alert.Final(game.id, awayScore = away, homeScore = home, finalKind = game.finalKind))
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

        /**
         * How long after its start a finished game can still be announced; also how long the plan keeps it wanted.
         * Measured from the start because that is what the feed gives, and long enough for the longest game plus the
         * pass that follows the horn.
         */
        val FINAL_WINDOW: Duration = 6.hours

        /** Whether a game that began at [start] is still inside [FINAL_WINDOW] at [now]. False when there is no start. */
        fun isWithinFinalWindow(
            start: Instant?,
            now: Instant,
        ): Boolean = start != null && now <= start + FINAL_WINDOW
    }
}
