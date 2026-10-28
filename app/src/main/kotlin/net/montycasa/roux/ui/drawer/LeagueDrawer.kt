@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.drawer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import net.montycasa.roux.R
import net.montycasa.roux.domain.policy.LeagueOrder
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
    onLeagueOrder: (List<String>) -> Unit,
    catalog: List<DrawerLeague> = drawerCatalog,
    onLeagueSelected: (String) -> Unit = { onFavouritesOnly(false) },
    contentInsets: WindowInsets = WindowInsets.systemBars,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val open = drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
    // A close (including a scrim tap or swipe) always discards this drawer-local mode.
    var editing by remember(open) { mutableStateOf(false) }
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
                LeagueDrawer(
                    state = state,
                    editing = editing,
                    onEditing = { editing = it },
                    catalog = catalog,
                    onLeagueOrder = onLeagueOrder,
                    onLeagueSelected = { id ->
                        onLeagueSelected(id)
                        scope.launch { drawerState.close() }
                    },
                    onFavouritesOnly = { only ->
                        onFavouritesOnly(only)
                        scope.launch { drawerState.close() }
                    },
                    contentInsets = contentInsets,
                )
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
    state: ScoresUiState,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    onLeagueOrder: (List<String>) -> Unit,
    onLeagueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalog: List<DrawerLeague> = drawerCatalog,
    onFavouritesOnly: (Boolean) -> Unit,
    /**
     * Window insets the drawer must clear. The app draws edge to edge, so the status bar sits on top of this panel
     * and the wordmark was landing underneath it. Injected rather than read from the composition so a test can pin
     * the clearance without a window.
     */
    contentInsets: WindowInsets = WindowInsets.systemBars,
) {
    // Background first, insets after: the panel still paints behind the status bar, only its content moves down.
    Column(
        modifier
            .fillMaxHeight()
            .background(panel)
            .windowInsetsPadding(contentInsets)
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Image(
            painterResource(R.drawable.wordmark_orange),
            stringResource(R.string.app_name),
            Modifier.width(108.dp).alpha(0.95f).testTag("leagues.wordmark"),
        )
        leagueSeason(state.loadedDate)?.let { season ->
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
        DrawerRow(stringResource(R.string.drawer_my_teams), state.favouriteCount.toString(), tag = "leagues.myTeams", team = true) {
            onFavouritesOnly(true)
        }
        DrawerRule()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                // The reference permits a taller heading; 8 dp above/below the 48 dp button
                // preserves the app's required clearance from adjacent navigation targets.
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                .testTag("leagues.heading"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawerLabel(stringResource(R.string.drawer_leagues), Modifier.weight(1f), padded = false)
            Box(
                Modifier
                    .then(if (editing) Modifier.height(48.dp) else Modifier.size(48.dp))
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onEditing(!editing) }
                    .reachTarget("leagues.edit")
                    .testTag("leagues.edit")
                    .then(if (editing) Modifier.padding(horizontal = 12.dp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (editing) {
                    Text(
                        stringResource(R.string.drawer_done),
                        color = orange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.1.em,
                    )
                } else {
                    Icon(pencil, stringResource(R.string.drawer_edit_leagues), Modifier.size(20.dp), tint = orange)
                }
            }
        }
        val order = LeagueOrder.reconcile(state.leagueOrder, catalog.map { it.id })
        // The drawer renders the persisted order directly. Every league in the catalog is arrangeable, including
        // ones with no data yet: greying says "nothing to show", not "not yours to arrange".
        order.forEach { id ->
            key(id) {
                val league = catalog.first { it.id == id }
                ReorderableLeagueRow(
                    league = league,
                    selected = id == state.selectedLeagueId,
                    editing = editing,
                    order = order,
                    onLeagueOrder = onLeagueOrder,
                    onClick = { onLeagueSelected(id) },
                )
            }
        }
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
private fun DrawerLabel(
    label: String,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
) {
    Text(
        label,
        modifier.then(if (padded) Modifier.padding(horizontal = 10.dp, vertical = 6.dp) else Modifier),
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
    modifier: Modifier = Modifier,
    team: Boolean = false,
    selected: Boolean = false,
    dimmed: Boolean = false,
    editing: Boolean = false,
    handle: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
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
                } else if (editing) {
                    Modifier
                } else {
                    Modifier.selectable(selected, role = Role.Tab, onClick = onClick).reachTarget(tag)
                },
            ).testTag(tag)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (team) Icon(star, contentDescription = null, Modifier.size(17.dp).testTag("leagues.myTeams.star"), tint = ink)
        Text(
            label,
            Modifier.weight(1f),
            color =
                when {
                    dimmed -> Color(0xFF6E747E)
                    team -> ink
                    selected -> Color.White
                    else -> Color(0xFFB4B9C1)
                },
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        handle?.invoke()
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

/** Availability is catalog data, so tests and future adapters can offer more than today's NHL. */
data class DrawerLeague(
    val id: String,
    val label: String,
    val sport: Int,
    val available: Boolean,
)

val drawerCatalog: List<DrawerLeague> =
    listOf(
        DrawerLeague("nhl", "NHL", R.string.drawer_hockey, true),
        DrawerLeague("nfl", "NFL", R.string.drawer_football, false),
        DrawerLeague("mlb", "MLB", R.string.drawer_baseball, false),
    )

@Composable
private fun ReorderableLeagueRow(
    league: DrawerLeague,
    selected: Boolean,
    editing: Boolean,
    order: List<String>,
    onLeagueOrder: (List<String>) -> Unit,
    onClick: () -> Unit,
) {
    val latestOrder by rememberUpdatedState(order)
    val writeOrder by rememberUpdatedState(onLeagueOrder)
    val rowHeight = with(LocalDensity.current) { 56.dp.toPx() }
    var offset by remember(editing) { mutableFloatStateOf(0f) }
    var dragging by remember(editing) { mutableStateOf(false) }
    val handleDescription = stringResource(R.string.drawer_reorder_league, league.label)
    val moveUp = stringResource(R.string.drawer_move_up)
    val moveDown = stringResource(R.string.drawer_move_down)
    DrawerRow(
        label = league.label,
        trailing = stringResource(league.sport),
        tag = "leagues.${league.id}",
        selected = selected,
        dimmed = !league.available,
        editing = editing,
        modifier = Modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer { translationY = offset },
        handle =
            if (editing) {
                {
                    Box(
                        Modifier
                            // The glyph stays 10 dp; a transparent 48 dp target makes holding it practical.
                            .size(48.dp)
                            .testTag("leagues.${league.id}.handle")
                            .semantics {
                                contentDescription = handleDescription
                                // TalkBack users get the same durable reorder without the hold-and-drag gesture.
                                customActions =
                                    listOf(-1 to moveUp, 1 to moveDown).mapNotNull { (step, label) ->
                                        val from = latestOrder.indexOf(league.id)
                                        val to = from + step
                                        if (from < 0 || to !in latestOrder.indices) {
                                            null
                                        } else {
                                            CustomAccessibilityAction(label) {
                                                writeOrder(latestOrder.toMutableList().apply { add(to, removeAt(from)) })
                                                true
                                            }
                                        }
                                    }
                            }.pointerInput(league.id) {
                                var gestureOrder = latestOrder
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        gestureOrder = latestOrder
                                        dragging = true
                                    },
                                    onDragEnd = {
                                        dragging = false
                                        offset = 0f
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        offset = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    while (true) {
                                        val from = gestureOrder.indexOf(league.id)
                                        val step =
                                            when {
                                                offset < -rowHeight / 2 -> -1
                                                offset > rowHeight / 2 -> 1
                                                else -> 0
                                            }
                                        val to = from + step
                                        if (from < 0 || step == 0 || to !in gestureOrder.indices) break
                                        gestureOrder = gestureOrder.toMutableList().apply { add(to, removeAt(from)) }
                                        offset -= step * rowHeight
                                        writeOrder(gestureOrder)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(10.dp)) {
                            repeat(3) { bar ->
                                val y = 0.75.dp.toPx() + bar * 4.dp.toPx()
                                drawLine(Color(0xFF6C727B), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                            }
                        }
                    }
                }
            } else {
                null
            },
        onClick = onClick,
    )
}

private val pencil =
    ImageVector
        .Builder("Edit leagues", 20.dp, 20.dp, 24f, 24f)
        .addPath(
            addPathNodes("M16,3 L21,8 L8,21 L3,21 L3,16 Z M14,5 L19,10"),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
