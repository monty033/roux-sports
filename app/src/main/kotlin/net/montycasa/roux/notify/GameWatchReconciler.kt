package net.montycasa.roux.notify

import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.notify.GameWatchScheduler
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import java.time.ZoneId
import kotlin.time.Clock

/**
 * Gathers the two things [GameWatchScheduler.reconcile] needs - what the user wants told about each team, and
 * today's slate - and hands them over.
 *
 * It exists because two callers need exactly this: the app start, and the periodic tick that catches a game
 * whose start time the feed publishes late. A second copy of this assembly in the worker would be the copy
 * that goes stale.
 *
 * The slate is loaded through [ScoresRepository.loadDay], which answers from a fresh cache without a
 * request and revalidates when it is not fresh: a periodic tick that always hit the network would spend the
 * user's data to learn nothing on most days. A failed load with nothing cached leaves the existing watches
 * exactly as they are - a reconcile has no news to act on, and cancelling work because the network is down
 * is the opposite of what the user asked for.
 */
class GameWatchReconciler(
    /** The store the app itself writes, so a worker cannot reconcile against a copy nobody else updates. */
    internal val preferences: PreferencesRepository,
    private val scores: ScoresRepository,
    private val scheduler: GameWatchScheduler,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun reconcile() {
        val now = clock.now()
        val today = civilDate(now)
        val day: ScoreDay = scores.loadDay(today, today).data ?: return

        // Read once, up front: the scheduler's question is a plain function, and calling a suspend store from
        // inside it for every team would make the plan's rules depend on how many teams are playing. Every team in
        // the slate is read, not only favourites - an alert on any team is a request.
        val teams = day.games.flatMapTo(linkedSetOf()) { listOf(it.home.key, it.away.key) }
        val alertPreferences: Map<TeamKey, TeamAlertPreferences> =
            teams.associateWith { team -> preferences.getTeamAlertPreferences(team) }

        scheduler.reconcile(now, day) { team ->
            alertPreferences[team] ?: TeamAlertPreferences()
        }
    }

    /** The device's civil day, because "today's games" means the day on the wall in front of the user. */
    private fun civilDate(instant: kotlin.time.Instant): String =
        java.time.Instant
            .ofEpochMilli(instant.toEpochMilliseconds())
            .atZone(zone)
            .toLocalDate()
            .toString()
}
