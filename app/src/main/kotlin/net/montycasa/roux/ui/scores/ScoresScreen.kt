@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.policy.DateStrip
import net.montycasa.roux.domain.policy.FavoritesHint
import net.montycasa.roux.ui.common.RetryButton
import net.montycasa.roux.ui.common.StaleBanner
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.primaryControl
import net.montycasa.roux.ui.reach.reachTarget
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders the selected day's state without owning loading or filtering. Callbacks map to refresh,
 * selectDate and setFavouritesOnly on ScoresViewModel; game navigation belongs to the host.
 * Cached rows survive every refresh failure, with recovery and frequent controls near the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoresScreen(
    state: ScoresUiState,
    onRefresh: () -> Unit,
    onGameClick: (GameId) -> Unit,
    onSelectDate: (String) -> Unit,
    onToggleFavourites: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter =
        remember(locale) {
            DateTimeFormatter.ofPattern("EEE, MMM d", locale).withZone(ZoneId.of("America/Toronto"))
        }
    val dateLabel = remember(state.selectedDate, formatter) { LocalDate.parse(state.selectedDate).format(formatter) }
    val missing = state.freshness == Freshness.Missing && state.rows.isEmpty()
    val loading = missing && state.isRefreshing && state.error == null
    val emptyDate = !missing && state.rows.isEmpty() && state.favoritesHint != FavoritesHint.NoFavouriteGames
    val nextDate = if (emptyDate) DateStrip.nextGameDate(state.dateIndex, state.selectedDate) else null
    // The plan's window is a week either way; the strip scrolls, so a partially visible chip is the
    // affordance, not a defect to be designed away.
    val dates =
        remember(state.dateIndex, state.selectedDate) {
            DateStrip.items(state.dateIndex, state.selectedDate)
        }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp)) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("scores.refresh")
                    .reachTarget("scores.pullToRefresh"),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("scores.list"),
                verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
            ) {
                if (missing) {
                    item {
                        Column(Modifier.padding(Reach.EDGE_INSET_DP.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(if (loading) R.string.scores_loading else R.string.scores_load_error),
                                modifier = Modifier.testTag(if (loading) "scores.loading" else "scores.error"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            state.error?.let { Text(errorMessage(it), style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                } else {
                    state.favoritesHint?.let { hint ->
                        item {
                            Text(
                                stringResource(
                                    when (hint) {
                                        FavoritesHint.PickTeams -> R.string.scores_pick_teams
                                        FavoritesHint.NoFavouriteGames -> R.string.scores_no_favourite_games
                                    },
                                ),
                                modifier = Modifier.padding(Reach.EDGE_INSET_DP.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (emptyDate) {
                        item {
                            Text(
                                stringResource(R.string.scores_empty_date, dateLabel),
                                modifier = Modifier.padding(Reach.EDGE_INSET_DP.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    items(state.rows, key = { it.id.value }) { game ->
                        GameRow(
                            game,
                            Modifier
                                .padding(horizontal = Reach.EDGE_INSET_DP.dp)
                                .testTag("scores.game.${game.id.value}")
                                .clickable { onGameClick(game.id) },
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Reach.EDGE_INSET_DP.dp),
            verticalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
        ) {
            val stale = state.freshness as? Freshness.Stale
            when {
                stale != null -> {
                    StaleBanner(
                        since = stale.since,
                        onRetry = onRefresh,
                        isRefreshing = state.isRefreshing,
                        supportingText = if (state.error != null) stringResource(R.string.scores_refresh_error) else null,
                    )
                }

                missing && !loading -> {
                    RetryButton(onRefresh, state.isRefreshing)
                }

                state.error != null && state.rows.isNotEmpty() -> {
                    Text(stringResource(R.string.scores_refresh_error), style = MaterialTheme.typography.bodyMedium)
                    RetryButton(onRefresh, state.isRefreshing)
                }
            }
            // Recovery takes priority over date navigation so a dead screen has one clear exit.
            if (nextDate != null && stale == null && state.error == null) {
                Button(
                    onClick = { onSelectDate(nextDate) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                            .primaryControl("scores.nextGames"),
                ) {
                    Text(stringResource(R.string.scores_next_games, LocalDate.parse(nextDate).format(formatter)))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(end = Reach.EDGE_INSET_DP.dp, bottom = Reach.EDGE_INSET_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(Reach.MIN_TARGET_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateStripBar(dates, state.selectedDate, onSelectDate, Modifier.weight(1f))
            IconToggleButton(
                checked = state.favouritesOnly,
                onCheckedChange = onToggleFavourites,
                modifier =
                    Modifier
                        .size(Reach.PRIMARY_MIN_HEIGHT_DP.dp)
                        .primaryControl("scores.favourites", frequent = true),
            ) {
                Icon(
                    imageVector = favouriteIcon(state.favouritesOnly),
                    contentDescription = stringResource(R.string.scores_favourites_only),
                )
            }
        }
    }
}

@Composable
private fun errorMessage(error: LoadError): String =
    stringResource(
        when (error) {
            LoadError.Network -> R.string.scores_network_error
            is LoadError.Http -> R.string.scores_http_error
            LoadError.Parse -> R.string.scores_parse_error
            is LoadError.MismatchedDay -> R.string.scores_wrong_day_error
        },
    )

private fun favouriteIcon(selected: Boolean): ImageVector =
    ImageVector
        .Builder(
            name = "Favourites",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes("M12,3 L14.8,8.7 L21,9.6 L16.5,14 L17.6,20.2 L12,17.3 L6.4,20.2 L7.5,14 L3,9.6 L9.2,8.7 Z"),
            fill = if (selected) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
        ).build()
