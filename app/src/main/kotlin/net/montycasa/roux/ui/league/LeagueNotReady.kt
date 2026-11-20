// Compose names composables in PascalCase; ktlint's function-naming rule reads that as a misnamed
// function. Suppressed for this file only, rather than changing the rule for the whole repository.
@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.league

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.reachTarget

/**
 * A club or game from a league whose pages are not built yet. The NHL pages call NHL endpoints, so an NFL key must not
 * reach them: that would show another club's data under this one's name. The page says so and offers a way back.
 *
 * The back control follows the app's back-button convention: a 48 dp control in the top bar, marked as a reach target
 * rather than a primary control, because a back control sits outside the bottom reach zone by design.
 */
@Composable
fun LeagueNotReady(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .testTag("league.notReady.back")
                    .reachTarget("league.notReady.back"),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.league_page_not_ready),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
