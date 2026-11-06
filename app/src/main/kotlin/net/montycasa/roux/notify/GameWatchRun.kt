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
import kotlin.time.Duration.Companion.minutes

/** Something that can post an alert. Narrower than [Notifier] so a test can watch what was asked for. */
interface AlertPoster {
    fun post(
        alert: Alert,
        game: Game,
    ): NotificationOutcome

    /**
     * Whether this alert could land right now, asked **before** its claim is taken.
     *
     * A claim is permanent and a refusal is not: the user who has not granted the permission yet, or has silenced
     * the goals channel and may unsilence it, would otherwise lose the start - or the goal - for good. The
     * crash-between-claim-and-post tradeoff is about a crash, not about a recoverable "not now". The check is per
     * alert because the answer is per channel: silencing goals does not silence starts.
     */
    fun canPost(alert: Alert): Boolean = true
}

/**
 * One pass of a watched game: look at the score, claim what is new, post it, and ask to be run again.
 *
 * The order is the whole point. **Every claim is written before its notification is posted**, so a crash
 * between the two loses an alert rather than repeating it: a missed goal is a disappointment, a goal announced
 * twice - or a whole game's goals announced again after a restart - is the app being wrong about the score.
 * The claim is also what makes two workers racing on the same game harmless: only one of them wins it.
 *
 * A game that is absent from the slate or not followed produces nothing, and a finished one produces at most its
 * final-score alert and is never watched again. A slate that could not be
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

        // Today first, then the previous civil day: a game that is still being played after the device's clock
        // rolls over is no longer in today's slate, and its watch is still running. Looking only at today would
        // end the chain at midnight, mid-game.
        var failure: LoadError? = null
        var slateDate: String? = null
        var game: Game? = null
        for (date in listOf(today, previousDay(today))) {
            val load = scores.loadDay(date, today)
            if (load.error != null && failure == null) failure = load.error
            val found = load.data?.games?.firstOrNull { it.id.value == gameId } ?: continue
            slateDate = date
            game = found
            break
        }

        val playing = game ?: return Outcome(posted = 0, failed = failure != null, retryAfter = pauseAsked(failure))

        // Both teams are asked, favourite or not: an alert switched on for either team is a request about this game.
        // The alert is wanted if either team asked for it, because the user asked on a team that is playing.
        val perTeam = listOf(playing.home.key, playing.away.key).map { preferences.getTeamAlertPreferences(it) }
        val forAlert =
            TeamAlertPreferences(
                start = perTeam.any { it.start },
                goals = perTeam.any { it.goals },
                intermission = perTeam.any { it.intermission },
                final = perTeam.any { it.final },
                upcoming = perTeam.any { it.upcoming },
            )
        if (!forAlert.anyOn) return Outcome(posted = 0)

        val alerts = decider.decide(playing, preferences.getNotificationLedgerEntry(gameId), forAlert, now)

        // Each alert is checked against its own channel before its claim is spent, so a silenced channel costs
        // nothing permanent; the game stays watched either way.
        val posted = alerts.count { alert -> poster.canPost(alert) && claim(alert, gameId, now) && post(alert, playing) }

        // Keep following a game that has not finished - not only one the feed calls Live. A watch fires at the
        // scheduled start and the feed often still says Scheduled for the first minutes, so re-chaining only on
        // Live would end the chain exactly when the goals begin. A final ends the chain whatever happened above:
        // the claim, not another pass, is what stops the alert repeating.
        if (playing.state !is GameState.Final) {
            val cadence =
                PollPolicy.nextDelay(now, slateDate ?: today, today, listOf(playing)) ?: DEFAULT_IN_PROGRESS_DELAY
            scheduler.watchAgain(gameId, maxOf(cadence, pauseAsked(failure) ?: Duration.ZERO))
        }

        return Outcome(posted = posted, retryAfter = pauseAsked(failure), failed = failure != null && game == null)
    }

    private suspend fun claim(
        alert: Alert,
        gameId: String,
        now: kotlin.time.Instant,
    ): Boolean =
        when (alert) {
            is Alert.GameStart -> preferences.claimStart(gameId, alert.goalBaseline, now)
            is Alert.Goal -> preferences.claimGoal(gameId, alert.goalCount, now)
            is Alert.Intermission -> preferences.claimIntermission(gameId, alert.period, now)
            is Alert.Final -> preferences.claimFinal(gameId, now)
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

    private fun previousDay(date: String): String =
        java.time.LocalDate
            .parse(date)
            .minusDays(1)
            .toString()

    private fun civilDate(instant: kotlin.time.Instant): String =
        java.time.Instant
            .ofEpochMilli(instant.toEpochMilliseconds())
            .atZone(zone)
            .toLocalDate()
            .toString()

    companion object {
        /** Used when the poll policy declines to name a cadence for a game that has not finished. */
        val DEFAULT_IN_PROGRESS_DELAY: Duration = 15.minutes
    }
}
