package net.montycasa.roux.domain.notify

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Instant

/**
 * One game whose alerts are wanted, how long to wait before watching it, and the moment that wait points at.
 *
 * [firesAt] is carried because a reconcile has to recognise its own earlier decision. The same start time
 * means the work already scheduled is still the right work; a start time that moved means the old work would
 * wake the device for a game that is no longer starting then. Without it a scheduler can only choose between
 * duplicating work and keeping a schedule that has gone stale.
 *
 * It is null for a game already under way: that watch is due as soon as it can be enqueued, and there is no
 * time to compare it against.
 */
data class WatchedGame(
    val gameId: String,
    val delay: Duration,
    val firesAt: Instant?,
)

/**
 * Keeps scheduled background work in step with what the user follows.
 *
 * The interface lives in the domain module and takes plain values, so the scheduling rules can be
 * tested without Android and a different implementation - a UnifiedPush distributor, for instance -
 * can replace the WorkManager one without touching the rules.
 *
 * [reconcile] must be idempotent: it is called on every app start and on a periodic tick, and calling
 * it twice with the same day must leave exactly the same work scheduled, not two copies of it.
 */
interface GameWatchScheduler {
    /**
     * Schedule work for every game in [day] that the user follows and that has not finished.
     *
     * [favourites] and [alertPreferences] are passed in rather than looked up here: a scheduler that
     * read the store would be untestable, and the caller already has both to hand.
     */
    suspend fun reconcile(
        now: Instant,
        day: ScoreDay,
        favourites: Set<TeamKey>,
        alertPreferences: (TeamKey) -> TeamAlertPreferences,
    )

    /** Cancel every scheduled watch, for when alerts are turned off entirely. */
    suspend fun cancelAll()
}

/**
 * Which games deserve a watch, and when.
 *
 * Pure and separate from the scheduler so the rules can be read and tested on their own: the WorkManager
 * implementation's job is only to make the platform agree with this list.
 */
object GameWatchPlan {
    /**
     * The games in [day] to watch, in the feed's order.
     *
     * A game is watched when a team in it is a favourite whose alerts are on - either kind counts, since
     * a watch that only reported goals would have to be started by something else.
     *
     * Finished games are not watched. A game already under way is watched immediately: the app may have
     * been closed through the start, and the goals are what the user opened it for. A scheduled game is
     * watched when it starts. A scheduled game with no readable start time is skipped rather than
     * treated as starting now, which would wake the device for a game there is no time to wait for.
     */
    fun wanted(
        now: Instant,
        day: ScoreDay,
        favourites: Set<TeamKey>,
        alertPreferences: (TeamKey) -> TeamAlertPreferences,
    ): List<WatchedGame> {
        val wanted = mutableListOf<WatchedGame>()
        for (game in day.games) {
            if (game.state is GameState.Final) continue
            if (!isFollowed(game, favourites, alertPreferences)) continue
            wanted += schedule(now, game) ?: continue
        }
        // A feed that lists the same game twice must not produce two watches.
        return wanted.distinctBy { it.gameId }
    }

    private fun isFollowed(
        game: Game,
        favourites: Set<TeamKey>,
        alertPreferences: (TeamKey) -> TeamAlertPreferences,
    ): Boolean =
        listOf(game.home.key, game.away.key).any { team ->
            team in favourites &&
                alertPreferences(team).let { preferences -> preferences.start || preferences.goals }
        }

    /** Null when the game cannot be scheduled: it is neither under way nor has a start time to wait for. */
    private fun schedule(
        now: Instant,
        game: Game,
    ): WatchedGame? =
        when (game.state) {
            is GameState.Live, GameState.Pre -> {
                WatchedGame(game.id.value, ZERO, firesAt = null)
            }

            GameState.Scheduled -> {
                game.startTimeUtc?.let { start ->
                    val delay = (start - now).coerceAtLeast(ZERO)
                    // A start time already in the past is under way as far as this is concerned, so it is due
                    // now rather than at a moment that has gone - and stays due now on every later pass.
                    WatchedGame(game.id.value, delay, firesAt = start.takeIf { delay > ZERO })
                }
            }

            else -> {
                null
            }
        }
}
