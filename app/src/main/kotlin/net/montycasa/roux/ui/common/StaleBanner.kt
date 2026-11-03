@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Describes cached scores in words and offers a full-size recovery action. The host places this
 * banner in the bottom reach zone and supplies the horizontal edge insets.
 */
@Composable
fun StaleBanner(
    since: Instant,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    supportingText: String? = null,
    /** The retry's tag, so a screen can name its own control without removing it from the reach audit. */
    retryTag: String = "scores.retry",
    /** The clock the day comparison is made against, injected so a test can stand on a fixed day. */
    now: Instant = Clock.System.now(),
) {
    val updated = rememberUpdatedLabel(since, now)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp)) {
        Text(stringResource(R.string.common_updated, updated), style = MaterialTheme.typography.bodyMedium)
        Text(
            supportingText ?: stringResource(R.string.common_stale_scores),
            style = MaterialTheme.typography.bodyMedium,
        )
        RetryButton(onRetry, isRefreshing, retryTag)
    }
}

@Composable
internal fun RetryButton(
    onRetry: () -> Unit,
    isRefreshing: Boolean,
) = RetryButton(onRetry, isRefreshing, "scores.retry")

@Composable
internal fun RetryButton(
    onRetry: () -> Unit,
    isRefreshing: Boolean,
    retryTag: String,
) {
    Button(
        onClick = onRetry,
        enabled = !isRefreshing,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                .primaryControl(retryTag)
                .testTag(retryTag),
    ) {
        Text(stringResource(if (isRefreshing) R.string.common_refreshing else R.string.common_retry))
    }
}
