package net.montycasa.roux.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.montycasa.roux.MainActivity
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.policy.Alert

/** What happened to an alert, so a caller can record it rather than guess. */
sealed interface NotificationOutcome {
    /** Posted, replacing any notification already shown for this game and kind. */
    data class Posted(
        val id: Int,
    ) : NotificationOutcome

    /** Nothing was posted, and nothing will be until the reason is resolved. */
    data class NotPosted(
        val reason: Reason,
    ) : NotificationOutcome

    /** Nothing was posted because there was nothing to post: a baseline is ledger bookkeeping, not news. */
    data object NothingToPost : NotificationOutcome

    enum class Reason {
        /** The user has not allowed notifications for this app (API 33+ runtime permission). */
        PermissionMissing,

        /** Notifications are allowed for the app but switched off for it - a channel or app-level block. */
        NotificationsOff,
    }
}

/**
 * Posts the alerts the decider asked for.
 *
 * Two channels, because the user's answer to "tell me when it starts" is not always their answer to
 * "tell me about every goal": one can be silenced without silencing the other.
 *
 * The tap target is an **explicit** intent naming [MainActivity] with the game id as an extra. A data
 * URI would also work, but an implicit route can be claimed by any app that registers for the scheme,
 * and the id would arrive through a parser this app does not control. An extra is data this app wrote.
 *
 * An id is derived from the game and the kind of alert, so a second goal replaces the first notification
 * for that game instead of stacking a second one: the score line is the news, not the history of it.
 */
class Notifier(
    private val context: Context,
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /** Creates both channels. Safe to call on every launch; the platform keeps the user's settings. */
    fun ensureChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                GAME_START_CHANNEL,
                context.getString(R.string.notify_channel_start),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_channel_start_description)
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                GOALS_CHANNEL,
                context.getString(R.string.notify_channel_goals),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_channel_goals_description)
            },
        )
    }

    fun post(
        alert: Alert,
        game: Game,
    ): NotificationOutcome {
        // A baseline is bookkeeping, and its answer is the same whatever the notification settings say.
        if (alert is Alert.Baseline) return NotificationOutcome.NothingToPost

        // Created here rather than trusted to a caller: on API 26+ a post to a channel that does not exist is
        // dropped by the platform, so a notifier that relied on an earlier call would quietly do nothing in any
        // process that posted first.
        ensureChannels()
        val channel = channelOf(alert)
        val blocked = blockage(channel)
        if (blocked != null) return NotificationOutcome.NotPosted(blocked)

        val id = idOf(alert)
        val away = game.away.key.abbrev
        val home = game.home.key.abbrev
        val notification =
            when (alert) {
                is Alert.GameStart -> {
                    base(id, channel, alert.gameId.value)
                        .setContentTitle(context.getString(R.string.notify_start_title, away, home))
                        .setContentText(context.getString(R.string.notify_start_text))
                        .build()
                }

                is Alert.Goal -> {
                    base(id, channel, alert.gameId.value)
                        .setContentTitle(
                            context.getString(
                                R.string.notify_goal_title,
                                away,
                                alert.awayScore ?: 0,
                                alert.homeScore ?: 0,
                                home,
                            ),
                        ).setContentText(context.getString(R.string.notify_goal_text))
                        .build()
                }

                // Unreachable - a baseline returned above - but the when has to name every alert to stay total.
                is Alert.Baseline -> {
                    error("a baseline is not news")
                }
            }

        NotificationManagerCompat.from(context).notify(id, notification)
        return NotificationOutcome.Posted(id)
    }

    /** The id shown for a game and kind; also the request code of its tap target. */
    fun idOf(alert: Alert): Int {
        val kind = if (alert is Alert.GameStart) START_KIND else GOAL_KIND
        // Positive because a PendingIntent request code must be; a collision only merges two games' updates.
        return (alert.gameId.value.hashCode() * KINDS + kind) and Int.MAX_VALUE
    }

    private fun base(
        id: Int,
        channel: String,
        gameId: String,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_roux)
            .setContentIntent(tapTarget(id, gameId))
            .setAutoCancel(true)

    private fun tapTarget(
        id: Int,
        gameId: String,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_GAME_ID, gameId)
                // Reuse the task that is already open rather than stacking a second copy of the app.
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun channelOf(alert: Alert): String = if (alert is Alert.GameStart) GAME_START_CHANNEL else GOALS_CHANNEL

    /**
     * Why nothing can be posted, or null when it can.
     *
     * The channel is checked as well as the app, because the two are separate answers: a user who silences
     * goals but not starts has said something specific, and reporting that goal as posted would be reporting a
     * notification the platform dropped.
     */
    private fun blockage(channel: String): NotificationOutcome.Reason? =
        when {
            !hasPermission() -> NotificationOutcome.Reason.PermissionMissing
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> NotificationOutcome.Reason.NotificationsOff
            channelBlocked(channel) -> NotificationOutcome.Reason.NotificationsOff
            else -> null
        }

    private fun channelBlocked(channel: String): Boolean =
        notificationManager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val GAME_START_CHANNEL = "game_start"
        const val GOALS_CHANNEL = "goals"
        const val EXTRA_GAME_ID = "net.montycasa.roux.extra.GAME_ID"

        private const val START_KIND = 0
        private const val GOAL_KIND = 1
        private const val KINDS = 2
    }
}
