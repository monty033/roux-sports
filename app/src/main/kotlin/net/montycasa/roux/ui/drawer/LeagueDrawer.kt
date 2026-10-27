@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.montycasa.roux.R
import net.montycasa.roux.ui.reach.reachTarget
import net.montycasa.roux.ui.scores.ScoresUiState
import java.time.LocalDate
import java.time.format.DateTimeParseException

// Brand-panel colours are deliberately independent of MaterialTheme, even on a light page.
private val panel = Color(0xFF08090B)
private val ink = Color(0xFFEDEEF1)
private val muted = Color(0xFF565B64)
private val orange = Color(0xFFFF4B12)
private val rule = Color(0xFF1B1D22)

@Composable
fun LeagueNavigationDrawer(
    drawerState: DrawerState,
    state: ScoresUiState,
    onFavouritesOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier.background(panel),
        scrimColor = panel.copy(alpha = 0.55f),
        drawerContent = {
            ModalDrawerSheet(
                modifier =
                    Modifier.width(240.dp).fillMaxHeight().testTag("leagues.drawer").then(
                        // Keep the sheet measured for gestures without exposing closed navigation to
                        // TalkBack or letting it masquerade as content on another tab.
                        if (drawerState.currentValue == DrawerValue.Closed && drawerState.targetValue == DrawerValue.Closed) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier
                        },
                    ),
                drawerShape = RectangleShape,
                drawerContainerColor = panel,
                drawerContentColor = ink,
                drawerTonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                LeagueDrawer(state.favouriteCount, state.loadedDate) { only ->
                    onFavouritesOnly(only)
                    scope.launch { drawerState.close() }
                }
            }
        },
    ) {
        // The reference reveals the panel by pushing the page, including its bottom navigation.
        Box(
            Modifier.graphicsLayer {
                translationX = drawerState.currentOffset.takeIf { it.isFinite() }?.plus(240.dp.toPx())
                    ?: if (drawerState.currentValue == DrawerValue.Open) 240.dp.toPx() else 0f
                shape = RoundedCornerShape(20.dp)
                clip = translationX > 0f
            },
        ) { content() }
    }
}

@Composable
fun LeagueDrawer(
    favouriteCount: Int,
    loadedDate: String?,
    modifier: Modifier = Modifier,
    onFavouritesOnly: (Boolean) -> Unit,
) {
    Column(modifier.fillMaxHeight().background(panel).padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 24.dp)) {
        Image(
            painterResource(R.drawable.wordmark_orange),
            stringResource(R.string.app_name),
            Modifier.width(108.dp).alpha(0.95f).testTag("leagues.wordmark"),
        )
        leagueSeason(loadedDate)?.let { season ->
            Text(
                season,
                Modifier.padding(top = 8.dp).testTag("leagues.season"),
                color = Color(0xFF6C727B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.24.em,
            )
        }
        Spacer(Modifier.weight(1f).height(8.dp))
        DrawerLabel(stringResource(R.string.drawer_favourites))
        DrawerRow(stringResource(R.string.drawer_my_teams), favouriteCount.toString(), tag = "leagues.myTeams", team = true) {
            onFavouritesOnly(true)
        }
        DrawerRule()
        Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
            DrawerLabel(stringResource(R.string.drawer_leagues))
        }
        DrawerRow("NHL", stringResource(R.string.drawer_hockey), tag = "leagues.nhl", selected = true) { onFavouritesOnly(false) }
        // Drawn, greyed and not offered: the file lists them, the app has no data for them yet.
        DrawerRow("NFL", stringResource(R.string.drawer_football), tag = "leagues.nfl", dimmed = true) {}
        DrawerRow("MLB", stringResource(R.string.drawer_baseball), tag = "leagues.mlb", dimmed = true) {}
        // No separator here: the footer draws its own top border (.dfoot{border-top}), and an extra rule
        // put two lines 17 dp apart whenever Settings/hint are absent.
        Box(Modifier.padding(top = 8.dp)) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
                Text(
                    stringResource(R.string.drawer_footer),
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp),
                    color = muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.em,
                )
            }
        }
    }
}

@Composable
private fun DrawerLabel(label: String) {
    Text(
        label,
        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        color = muted,
        fontSize = 8.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.2.em,
    )
}

@Composable
private fun DrawerRule() {
    Box(
        Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(rule),
    )
}

@Composable
private fun DrawerRow(
    label: String,
    trailing: String,
    tag: String,
    team: Boolean = false,
    selected: Boolean = false,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            // .ditem is transparent by default; only .on (#15171C) and .team (#101216) fill.
            .background(
                when {
                    dimmed -> Color.Transparent
                    team -> Color(0xFF101216)
                    selected -> Color(0xFF15171C)
                    else -> Color.Transparent
                },
            )
            // A dimmed league is drawn but not offered: .ditem.hid in the file. It carries no click
            // action at all - a disabled-but-clickable row still reaches TalkBack as an actionable
            // control, and it would also be audited as a reach target it can never satisfy.
            .then(
                if (dimmed) {
                    Modifier.semantics { disabled() }
                } else {
                    Modifier.selectable(selected, role = Role.Tab, onClick = onClick).reachTarget(tag)
                },
            ).testTag(tag)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (team) Icon(star, contentDescription = null, Modifier.size(18.dp), tint = orange)
        Text(
            label,
            Modifier.weight(1f),
            color =
                when {
                    dimmed -> Color(0xFF6E747E)
                    team -> ink
                    else -> Color.White
                },
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
        Text(
            trailing,
            color =
                when {
                    dimmed -> Color(0xFF5F656E)
                    selected -> orange
                    else -> muted
                },
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.1.em,
        )
    }
}

/** A loaded score date is already a calendar day in the NHL's America/New_York zone. */
internal fun leagueSeason(loadedDate: String?): String? {
    val date =
        try {
            loadedDate?.let(LocalDate::parse) ?: return null
        } catch (_: DateTimeParseException) {
            return null
        }
    val firstYear = if (date.monthValue >= 7) date.year else date.year - 1
    return "%04d–%02d".format(java.util.Locale.ROOT, firstYear, (firstYear + 1) % 100)
}

private val star =
    ImageVector
        .Builder("Favourite", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M12,3.6 L14.6,8.9 L20.5,9.8 L16.2,13.9 L17.2,19.7 L12,16.9 L6.8,19.7 L7.8,13.9 L3.5,9.8 L9.4,8.9 Z"),
            fill = SolidColor(Color.Black),
        ).build()
