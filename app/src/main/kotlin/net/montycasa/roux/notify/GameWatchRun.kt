package net.montycasa.roux.notify

import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.policy.Alert
import net.montycasa.roux.domain.policy.NotificationDecider
import net.montycasa.roux.domain.policy.PollPolicy
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Something that can post an alert. Narrower than [Notifier] so a test can watch what was asked for. */
fun interface AlertPoster {
    fun post(
        alert: Alert,
        game: Game,
    ): NotificationOutcome
}

/**
 * One pass of a watched game: look at the score, claim what is new, post it, and ask to be run again.
 *
 * The order is the whole point. **Every claim is written before its notification is posted**, so a crash
 * between the two loses an alert rather than repeating it: a missed goal is a disappointment, a goal announced
 * twice - or a whole game's goals announced again after a restart - is the app being wrong about the score.
 * The claim is also what makes two workers racing on the same game harmless: only one of them wins it.
 *
 * A game that is finished, absent from the slate, or not followed produces nothing. A slate that could not be
 * loaded produces nothing either, and says so through [Outcome.retryAfter] when the feed asked for a pause.
 */
class GameWatchRun(
    private val preferences: PreferencesRepository,
    private val scores: ScoresRepository,
    private val scheduler: GameWatchScheduler,
    private val poster: AlertPoster,
    private val clock: Clock,
    private val decider: NotificationDecider = NotificationDecider(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** What a pass did, so the worker can choose between success and a retry. */
    data class Outcome(
        val posted: Int,
        /** Set when the feed asked for a pause; the follow-up must not be sooner than this. */
        val retryAfter: Duration? = null,
        /** Set when the pass could not read what it needed. */
        val failed: Boolean = false,
    )

    suspend fun run(gameId: String): Outcome {
        val now = clock.now()
        val today = civilDate(now)
        val load = scores.loadDay(today, today)
        // A failure with nothing cached is worth retrying; an empty cache with no error is simply nothing to say.
        val day = load.data ?: return Outcome(posted = 0, failed = load.error != null, retryAfter = pauseAsked(load.error))

        val game = day.games.firstOrNull { it.id.value == gameId } ?: return Outcome(posted = 0)
        if (game.state is GameState.Final) return Outcome(posted = 0)

        val followed = listOf(game.home.key, game.away.key).filter { it in preferences.getFavourites() }
        if (followed.isEmpty()) return Outcome(posted = 0)

        // Two favourites in one game is a real case: the start is announced if either asked for it, because the
        // user asked for it on a team that is playing.
        val perTeam = followed.map { preferences.getTeamAlertPreferences(it) }
        val forAlert = TeamAlertPreferences(start = perTeam.any { it.start }, goals = perTeam.any { it.goals })
        val alerts = decider.decide(game, preferences.getNotificationLedgerEntry(gameId), forAlert, isFavourite = true)

        val posted = alerts.count { alert -> claim(alert, gameId, now) && post(alert, game) }

        // Keep following a game that is still being played. The delay comes from the same policy the scores
        // screen polls with, so a watched game is polled at the cadence that screen would use rather than at a
        // number invented here.
        if (game.state is GameState.Live) {
            val next = PollPolicy.nextDelay(now, today, today, listOf(game)) ?: DEFAULT_LIVE_DELAY
            scheduler.watchAgain(gameId, maxOf(next, pauseAsked(load.error) ?: Duration.ZERO))
        }

        return Outcome(posted = posted, retryAfter = pauseAsked(load.error))
    }

    private suspend fun claim(
        alert: Alert,
        gameId: String,
        now: kotlin.time.Instant,
    ): Boolean =
        when (alert) {
            is Alert.GameStart -> preferences.claimStart(gameId, alert.goalBaseline, now)
            is Alert.Goal -> preferences.claimGoal(gameId, alert.goalCount, now)
            is Alert.Baseline -> preferences.claimBaseline(gameId, alert.goalCount, now)
        }

    /** A baseline is a ledger entry, not news, so it is claimed and never posted. */
    private fun post(
        alert: Alert,
        game: Game,
    ): Boolean {
        if (alert is Alert.Baseline) return false
        return poster.post(alert, game) is NotificationOutcome.Posted
    }

    /** A usable server-requested pause, and only when the server actually asked for one. */
    private fun pauseAsked(error: LoadError?): Duration? = (error as? LoadError.Http)?.retryAfter

    private fun civilDate(instant: kotlin.time.Instant): String =
        java.time.Instant
            .ofEpochMilli(instant.toEpochMilliseconds())
            .atZone(zone)
            .toLocalDate()
            .toString()

    companion object {
        /** Used only if the poll policy declines to name a cadence for a game it says is live. */
        val DEFAULT_LIVE_DELAY: Duration = 60.seconds
    }
}
