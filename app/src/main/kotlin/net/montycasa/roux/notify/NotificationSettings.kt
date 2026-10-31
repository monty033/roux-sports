package net.montycasa.roux.notify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Whether this app may post a notification at all.
 *
 * One function, because the answer is asked in three places - the notifier before it posts, the alert sheet before
 * it lets a switch move, and the activity when it returns from system settings - and three copies of a permission
 * check drift: one of them keeps a `Build.VERSION` branch the others lost, and a switch that looks enabled posts
 * nothing.
 */
fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Whether a notification would actually be delivered: the permission, and notifications enabled for the app.
 *
 * The second half is not a formality. An app can hold the permission and still have its notifications turned off in
 * system settings, which is a state the user creates deliberately and the only place to undo.
 */
fun notificationsAllowed(context: Context): Boolean =
    hasNotificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Opens this app's notification settings.
 *
 * The platform's own screen, because the switch that turns a silenced app back on does not exist in this app and
 * pretending otherwise would leave the user tapping a disabled control. Failure is swallowed: an OEM that ships no
 * such activity should cost the user a dead button, not a crash from a settings row.
 */
fun openNotificationSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
