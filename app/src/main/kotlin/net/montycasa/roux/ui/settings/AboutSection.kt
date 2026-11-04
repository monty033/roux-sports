@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.montycasa.roux.BuildConfig
import net.montycasa.roux.R

/** The project page the source button opens in the reader's browser; this app never fetches it itself. */
private const val SOURCE_URL = "https://git.montycasa.net/patrick/roux-sports"

/**
 * Licensing, version and provenance, at the bottom of Settings.
 *
 * The version comes from the generated `BuildConfig`, so this screen cannot claim a release the APK
 * is not; the only network involved in the source link is the browser the intent hands the URL to.
 */
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_about_licence),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                // A device with no browser to handle the URL must not crash on this tap; there is nothing
                // else the app can do with the link, so the tap is simply a no-op there.
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
                } catch (_: ActivityNotFoundException) {
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.settings_about_source))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_about_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
