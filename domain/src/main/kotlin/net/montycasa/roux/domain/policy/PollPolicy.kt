package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Pure polling decisions using caller-supplied time and ISO YYYY-MM-DD date strings.
 * Prefer evidence of activity over the state string, the part of the payload most likely to change.
 */
object PollPolicy {
    val liveDelay: Duration = 25.seconds
    val warmingDelay: Duration = 60.seconds
    val idleDelay: Duration = 5.minutes

    // S2.1's detailed acceptance specifies nine minutes; the plan overview says ten.
    // The detailed step is authoritative here; the discrepancy is raised with the human
    // rather than silently reconciled in code or the approved plan.
    val preStartWindow: Duration = 9.minutes
    val postStartGrace: Duration = 20.minutes

    /**
     * The scheduled time is when the broadcast begins, not when the puck drops: the opening faceoff
     * is five or more minutes later. Polling at the live cadence during that gap buys nothing -
     * nothing has happened yet - so the fast cadence waits until a game can plausibly be underway.
     */
    val puckDropLag: Duration = 5.minutes

    // The twenty-minute grace assumes the state tells us the game is over; an unknown state does not.
    // Allow a plausible game length: about three hours with intermissions, rounded up to four as a ceiling.
    // A puck drop is not the end of a game.
    val plausibleGameDuration: Duration = 4.hours
    val maxBackoff: Duration = 5.minutes

    // A renamed terminal state token would otherwise look like a game still in progress;
    // finalKind survives a stopped clock.
    private fun isTerminal(game: Game): Boolean = game.state is GameState.Final || game.finalKind != null

    /** Evidence that play is under way, as opposed to a state token that merely says so later. */
    private fun hasLiveEvidence(game: Game): Boolean =
        !isTerminal(game) &&
            (game.isLive || (game.state is GameState.Unknown && game.period?.running == true))

    /**
     * Whether the fast cadence may run yet: the scheduled start must be at least [puckDropLag] old.
     *
     * A missing start cannot be measured, so live evidence is trusted on its own rather than
     * holding a game the feed says is in progress at the slow cadence indefinitely.
     */
    private fun fastCadenceAllowed(
        now: Instant,
        game: Game,
    ): Boolean = game.startTimeUtc?.let { start -> now >= start + puckDropLag } ?: true

    /** First match wins: empty, live, warming, today, then no polling. */
    fun nextDelay(
        now: Instant,
        viewingDate: String,
        today: String,
        games: List<Game>,
    ): Duration? {
        if (games.isEmpty()) return null
        // Terminal evidence outranks the state token: a payload that says Live while also
        // carrying a finalKind is contradictory, and believing the terminal half keeps a
        // finished game from polling at the live cadence forever.
        if (games.any { hasLiveEvidence(it) && fastCadenceAllowed(now, it) }) {
            return liveDelay
        }
        // Live evidence inside the puck-drop gap. The broadcast has started and the feed may
        // already say Live, but nothing can have happened on the ice yet, so keep warming rather
        // than paying the live cadence for several minutes of empty updates.
        if (games.any { hasLiveEvidence(it) }) {
            return warmingDelay
        }
        // Without a usable start, Unknown and Scheduled have no evidence to justify warming.
        // Bound the past window: without it, a postponed game polls every minute indefinitely.
        if (games.any {
                !isTerminal(it) &&
                    (
                        it.state == GameState.Pre ||
                            when (it.state) {
                                GameState.Scheduled -> {
                                    it.startTimeUtc?.let { start ->
                                        now - start <= postStartGrace && start - now <= preStartWindow
                                    } == true
                                }

                                is GameState.Unknown -> {
                                    it.startTimeUtc?.let { start ->
                                        now - start <= plausibleGameDuration && start - now <= preStartWindow
                                    } == true
                                }

                                else -> {
                                    false
                                }
                            }
                    )
            }
        ) {
            return warmingDelay
        }
        return if (viewingDate == today) idleDelay else null
    }

    /**
     * Exponential retry delay with ±10% jitter; attempts below one use the first retry.
     * [jitter] must return a value in 0.0..1.0 and is a parameter so the multiplier can be asserted exactly rather than sampled.
     */
    fun backoff(
        attempt: Int,
        jitter: () -> Double = { Random.Default.nextDouble() },
    ): Duration {
        // 25 * 2^4 already exceeds the cap; clamp the exponent to avoid overflow for large attempts.
        val exponent = attempt.coerceIn(1, 5) - 1
        val base = (25.seconds * (1 shl exponent)).coerceAtMost(maxBackoff)
        // Apply the cap again after jitter so the delay never exceeds five minutes.
        return (base * (0.9 + 0.2 * jitter())).coerceAtMost(maxBackoff)
    }
}
