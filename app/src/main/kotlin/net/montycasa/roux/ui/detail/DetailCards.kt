@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.theme.LocalRouxAccents

@Composable
internal fun <T> SectionCard(
    title: String,
    tag: String,
    loadable: Loadable<T>,
    failureLabel: String,
    /** Card titles are quiet by default; the players card is a whole table and says so in its own voice. */
    emphasised: Boolean = false,
    /** The players card is identified by its club's crest, so the heading reads as that club's table. */
    headerCrest: TeamRef? = null,
    /** Null when this card has nothing useful to retry - the page retries as a whole, not one endpoint. */
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val data = loadable.data
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
            .testTag(tag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (headerCrest != null) {
                Box(Modifier.testTag("$tag.crest")) {
                    TeamLogo(
                        team = headerCrest,
                        crestSize = PLAYERS_CREST,
                        imageTag = "detail.logo.players.${headerCrest.abbrev}",
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title.uppercase(),
                style = if (emphasised) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                fontWeight = if (emphasised) FontWeight.Bold else null,
                color = if (emphasised) MaterialTheme.colorScheme.onSurface else LocalRouxAccents.current.muted,
            )
        }
        Spacer(Modifier.height(if (emphasised) 10.dp else 8.dp))
        when {
            data != null -> {
                content(data)
            }

            loadable.error != null -> {
                Column {
                    Text(failureLabel, style = MaterialTheme.typography.bodySmall)
                    if (onRetry != null) {
                        RetryLine(onRetry, tag)
                    }
                }
            }

            loadable.freshness is Freshness.Missing -> {
                EmptySectionLine("Loading")
            }

            else -> {
                EmptySectionLine("Not available")
            }
        }
    }
}

@Composable
private fun RetryLine(
    onRetry: () -> Unit,
    tag: String,
) {
    Text(
        "Try again",
        modifier =
            Modifier
                .padding(top = 6.dp)
                .heightIn(min = 48.dp)
                .clickable(onClick = onRetry)
                .testTag("$tag.retry")
                .reachTarget("$tag.retry")
                .padding(vertical = 12.dp),
        style = MaterialTheme.typography.labelMedium,
        color = LocalRouxAccents.current.fill,
    )
}

@Composable
internal fun EmptySectionLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalRouxAccents.current.muted,
    )
}

private val PLAYERS_CREST = 30.dp
