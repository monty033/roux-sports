@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.Broadcast
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.common.CAPSULE_BORDER
import net.montycasa.roux.ui.common.CAPSULE_HORIZONTAL_PADDING
import net.montycasa.roux.ui.common.CAPSULE_PADDING
import net.montycasa.roux.ui.common.LIVE_CLOCK_GAP
import net.montycasa.roux.ui.common.LiveCapsule
import net.montycasa.roux.ui.common.StateClock
import net.montycasa.roux.ui.common.periodLabel
import net.montycasa.roux.ui.common.rememberStartTimeLabel
import net.montycasa.roux.ui.theme.Roux

/** Test tag for the logo image, so a test can assert the request was attempted. */
const val LOGO_TAG = "gameRowLogo"
const val CREST_TAG = "gameRowCrest"
const val STATE_CAPSULE_TAG = "gameRowStateCapsule"
const val STATE_RAIL_TAG = "gameRowStateRail"
const val WATCH_TAG = "gameRowWatch"
const val TEAM_LINE_TAG = "gameRowTeamLine"

/** The club's name, or its abbreviation where the name would have to be cut. */
const val CLUB_NAME_TAG = "gameRowClubName"

/**
 * The accent stripe that marks a favourite matchup.
 *
 * Four dp: the width Patrick approved, a shade thicker than the mock's three. It is painted over the
 * card's left edge rather than laid out beside it, so it costs the card no width - the club names
 * and the rail keep the measurements the design fixed.
 */
internal val FAVOURITE_ACCENT_WIDTH = 6.dp

/** Values from the reference's wide rules and max-width:380px list viewport variant. */
private data class CardMetrics(
    val railWidth: Dp,
    val crestSize: Dp,
    val clubSize: TextUnit,
    val scoreSize: TextUnit,
    val scoreWidth: Dp,
    val columnGap: Dp,
    val startPadding: Dp,
)

/**
 * The width the club column keeps for the club's name: two and a half times the club's own type
 * size, which is about six characters.
 *
 * Six characters is what the name is for - enough to tell Boston from Buffalo - and the club's type
 * size is what it is measured in, so the room grows with the type and the guarantee holds at double
 * size as well.
 */
private const val CLUB_NAME_EM = 2.5f

/** The club column's padding at the end, against the rail. */
private val CLUB_COLUMN_END_PADDING = 4.dp

/**
 * The width the rail may take: the card's width less what the club column needs to stay readable.
 *
 * The rail is the width the widest state needs, but a state that needs more than the card can spare
 * cannot have it: the club name would be squeezed down to a bare ellipsis, and a card that no longer
 * says which team is playing is worse than one whose clock has wrapped. So the rail stops at the
 * card's width less the club column's share - the crest, the gaps, the score slot and room for the
 * name - and whatever the rail cannot have, it wraps inside itself.
 */
@Composable
private fun CardMetrics.railCeiling(cardWidth: Dp): Dp {
    val fontScale = LocalDensity.current.fontScale
    val clubColumn =
        startPadding +
            CLUB_COLUMN_END_PADDING +
            crestSize +
            columnGap * 2 +
            scoreWidth * fontScale +
            (clubSize.value * fontScale * CLUB_NAME_EM).dp
    return (cardWidth - clubColumn).coerceAtLeast(0.dp)
}

/**
 * The width the card would be drawn at when the host did not say: the window's own width, in dp.
 *
 * The card fills the list width, and the list fills the window on every phone the app draws for, so the
 * window is the honest stand-in rather than a number written down here.
 */
@Composable
private fun windowWidth(): Dp {
    val density = LocalDensity.current
    return with(density) {
        LocalWindowInfo.current.containerSize.width
            .toDp()
    }
}

/** The rail pads 12 dp at the start and 7 at the end; a child given the inner width cannot widen the rail. */
private val RAIL_START_INSET = 12.dp
private val RAIL_END_INSET = 7.dp

/** The rail's horizontal padding, added once to the width the widest state needs. */
internal val RAIL_INSETS = RAIL_START_INSET + RAIL_END_INSET

private val wideCard = CardMetrics(92.dp, 34.dp, 15.sp, 22.sp, 30.dp, 8.dp, 13.dp)
private val narrowCard = CardMetrics(128.dp, 32.dp, 14.sp, 21.sp, 28.dp, 7.dp, 11.dp)

/**
 * One informational card per game; the host owns the existing navigation callback and tag.
 * Intrinsic height lets the team rows and vertically centred state stack grow together with type size.
 *
 * [cardWidth] is the width the card is drawn at, which the host knows and the card cannot: the rail's
 * share of the card is measured against it. Left out, the screen's width is used, which is the card's
 * width on every phone the app draws.
 */
@Composable
fun GameRow(
    game: Game,
    narrow: Boolean,
    modifier: Modifier = Modifier,
    favourite: Boolean = false,
    cardWidth: Dp? = null,
) {
    val upcoming = game.state == GameState.Scheduled || game.state == GameState.Pre
    val homeScore = game.homeScore
    val awayScore = game.awayScore
    val homeWins = game.state == GameState.Final && homeScore != null && awayScore != null && homeScore > awayScore
    val awayWins = game.state == GameState.Final && homeScore != null && awayScore != null && awayScore > homeScore
    val metrics = if (narrow) narrowCard else wideCard
    // One width for every card. The rail is the width the widest state needs, measured once and not
    // from this card's own state, so the right-hand edge of a list is the same edge on every card.
    val measuredRailWidth = rememberStateRailWidth(metrics.railWidth)
    // ...and never wider than the card can spare: the club column keeps enough room to name the club
    // and the rail takes what is left. The ceiling wins even over the floor, so a card too narrow for
    // both gives the space to the club column.
    //
    // Known limit, stated rather than papered over: once the ceiling drops the rail below the width a
    // state needs, that state has nowhere to go - the start time, the capsule and the live clock are
    // all single-line - so it clips rather than wrapping. That needs a card narrower than about 315 dp
    // at double type, which is narrower than the smallest phone this app draws for (a 360 dp screen
    // leaves the card about 336 dp, where the ceiling is 149 dp and never bites). A compact state
    // layout is the fix if such a width ever matters.
    val railWidth =
        min(
            max(metrics.railWidth, measuredRailWidth),
            metrics.railCeiling(cardWidth ?: windowWidth()),
        )
    val description = gameRowDescription(game, favourite)
    // Read outside the draw scope: the accent is the brand orange, the same one the live capsule fills with.
    val accent = Roux.accents.fill
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, Color.Transparent, RoundedCornerShape(16.dp))
                // The accent rides the card's full left edge and is clipped by the rounded corner
                // above, so it follows the card's shape instead of standing proud of it. Painted
                // over the card rather than laid out beside it: a stripe with a width of its own
                // would move the club names and the rail.
                .drawBehind {
                    if (favourite) {
                        drawRect(accent, size = Size(FAVOURITE_ACCENT_WIDTH.toPx(), size.height))
                    }
                }
                // One sentence for the whole card. The rows and the rail are the same information drawn
                // twice - a score per team, and a state the sentence already states - so a screen reader
                // gets the card as one statement instead of seven unconnected fragments. It is set without
                // merging descendants, because the rail's own nodes are what the layout and reach audits
                // measure; TalkBack reads the sentence as the card's description.
                .semantics { contentDescription = description }
                // Compose's border only draws. Reserve its space to match CSS border-box:
                // 28 + 2 + 28 of rows + 16 of main padding + 2 of border = 76 dp.
                .padding(1.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(
                    start = metrics.startPadding,
                    end = CLUB_COLUMN_END_PADDING,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TeamLine(
                game.away,
                game.awayScore.takeUnless { upcoming },
                upcoming,
                awayWins,
                game.state is GameState.Live,
                metrics,
                Modifier.weight(1f),
            )
            TeamLine(
                game.home,
                game.homeScore.takeUnless { upcoming },
                upcoming,
                homeWins,
                game.state is GameState.Live,
                metrics,
                Modifier.weight(1f),
            )
        }
        StateRail(game, upcoming, railWidth)
    }
}

@Composable
private fun TeamLine(
    team: TeamRef,
    score: Int?,
    upcoming: Boolean,
    winner: Boolean,
    live: Boolean,
    metrics: CardMetrics,
    modifier: Modifier,
) {
    val ink =
        when {
            winner -> Roux.accents.signature
            upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    val scoreInk =
        when {
            winner -> Roux.accents.signature
            upcoming -> Roux.accents.muted
            live -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp).testTag(TEAM_LINE_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
    ) {
        // Like the CSS crest, the artwork extends above/below its 28 dp row.
        Box(Modifier.width(metrics.crestSize).height(28.dp), contentAlignment = Alignment.Center) {
            TeamLogo(team, metrics.crestSize)
        }
        val clubName = team.name?.takeIf { it.isNotBlank() }
        // A name cut to "Bos…" does not tell Boston from Boston College, and the rail can leave exactly
        // that much room at large type. The abbreviation is short, true and always whole, so it stands in
        // whenever the name would have to be cut.
        //
        // Whether it has to be cut is decided from the room the column actually got - the constraint the
        // text was measured under - and not from the window's width: a card can be resized inside a window
        // that never changes (split screen, a tablet pane), and a latch on the window would keep the
        // abbreviation long after the room came back.
        val clubStyle = Roux.cardTypography.clubName.copy(fontSize = metrics.clubSize)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        var clubLabel by remember(clubName) { mutableStateOf(clubName ?: team.abbrev) }
        Text(
            text = clubLabel,
            modifier = Modifier.weight(1f).testTag(CLUB_NAME_TAG),
            style = clubStyle,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (clubName != null) {
                    val room = layout.layoutInput.constraints.maxWidth
                    // The name is drawn on one line or not at all, so its unwrapped width is the test.
                    val needed =
                        measurer
                            .measure(
                                text = clubName,
                                style = clubStyle,
                                maxLines = 1,
                                softWrap = false,
                                density = density,
                            ).size.width
                    val wanted = if (needed <= room) clubName else team.abbrev
                    // Converges: the choice is a function of the room, which does not depend on the label.
                    if (wanted != clubLabel) clubLabel = wanted
                }
            },
        )
        // A reserved slot keeps names aligned, even when no score is known. Grow with large type.
        Box(Modifier.width(metrics.scoreWidth * LocalDensity.current.fontScale.coerceAtLeast(1f)), contentAlignment = Alignment.CenterEnd) {
            if (upcoming || score != null) {
                Text(
                    text = if (upcoming) "–" else score.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    style =
                        Roux.cardTypography.score.copy(
                            fontSize = metrics.scoreSize,
                            lineHeight = metrics.scoreSize,
                            fontWeight =
                                when {
                                    upcoming -> FontWeight.Normal
                                    live -> FontWeight.Bold
                                    else -> Roux.cardTypography.score.fontWeight
                                },
                        ),
                    color = scoreInk,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
internal fun TeamLogo(
    team: TeamRef,
    crestSize: Dp,
) {
    var loaded by remember(team.logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.requiredSize(crestSize).clip(CircleShape).testTag(CREST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .alpha(if (loaded) 0f else 1f)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The badge is fixed-size crest artwork, like the logo it replaces. The adjacent
            // club name supplies scalable text; scaling the artwork would crop the abbreviation.
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Text(
                    text = team.abbrev,
                    modifier = if (loaded) Modifier.clearAndSetSemantics {} else Modifier,
                    style = Roux.cardTypography.shots,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        if (team.logoUrl != null) {
            AsyncImage(
                model = team.logoUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag(LOGO_TAG),
                contentScale = ContentScale.Fit,
                onLoading = { loaded = false },
                onError = { loaded = false },
                onSuccess = { loaded = true },
            )
        }
    }
}

@Composable
private fun StateRail(
    game: Game,
    upcoming: Boolean,
    width: Dp,
) {
    val rule = MaterialTheme.colorScheme.outline
    val unknown = game.state is GameState.Unknown
    // The reference's .rr elements are empty spacers. .rstate centres one stack across the
    // full card height; its lines have no relationship to the two team rows.
    Column(
        // One width for every card: the rail is drawn at the width the widest state needs, so the
        // right-hand edge of the list is the same edge on every card. The club column is the one that
        // yields, because a truncated club name still reads and a two-line capsule does not.
        Modifier
            .width(width)
            .testTag(STATE_RAIL_TAG)
            .fillMaxHeight()
            .drawBehind {
                drawRect(
                    rule,
                    size =
                        androidx.compose.ui.geometry
                            .Size(1.dp.toPx(), size.height),
                )
            }.padding(start = RAIL_START_INSET, end = RAIL_END_INSET, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start,
    ) {
        if (unknown) {
            // Preserve the existing fallback layout for unrecognised feed states.
            Box(Modifier.weight(1f).heightIn(min = 28.dp), contentAlignment = Alignment.CenterStart) {
                RailText(stringResource(R.string.game_row_status_unavailable))
            }
        } else {
            when (game.state) {
                GameState.Scheduled, GameState.Pre -> {
                    StateClock(
                        startTimeLabel(game),
                        live = false,
                        style = Roux.cardTypography.startTime,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    // A game that has not started has no shots to report, so the second line is the one thing
                    // a viewer wants before it does: where it will be shown.
                    // The line is given the rail's inner width so it can wrap down the card rather than push the
                    // rail wider: the right edge of every card is the same edge.
                    watchLine(game.broadcasts)?.let { WatchChannels(it, width - RAIL_INSETS) }
                }

                is GameState.Live -> {
                    // Extra periods put the period label beside the clock in one row.
                    val periodType = game.period?.periodType
                    // Only an extra period moves up into the capsule: a regulation game keeps "2nd 12:34" on
                    // one line because it fits, and "OT"/"SO" are the labels that do not.
                    val extra = periodType == "OT" || periodType == "SO"
                    val label =
                        when {
                            periodType == "OT" -> R.string.game_row_period_overtime
                            periodType == "SO" -> R.string.game_row_period_shootout
                            else -> R.string.game_row_live_label
                        }
                    val intermission = game.period?.inIntermission == true
                    if (extra) {
                        // One line: capsule and clock beside each other. The period used to sit above a bare
                        // clock, which read as two lines for a game that had only two facts to report.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LIVE_CLOCK_GAP),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LiveCapsule(label = label, testTag = STATE_CAPSULE_TAG)
                            StateClock(game.period?.clock.orEmpty(), live = true, intermission = intermission, inline = true)
                        }
                    } else {
                        LiveCapsule(label = label, testTag = STATE_CAPSULE_TAG)
                        StateClock(
                            listOfNotNull(periodLabel(game.period), game.period?.clock?.takeIf { it.isNotBlank() })
                                .joinToString(" "),
                            live = true,
                            intermission = intermission,
                        )
                    }
                }

                GameState.Final -> {
                    FinalCapsule(game.finalKind)
                }

                is GameState.Unknown -> {}
            }
        }
    }
}

/**
 * The networks a game will be on, national listings first.
 *
 * The rail is 92 dp wide, so a full listing - five networks is ordinary - would wrap into noise. Two names
 * and a count keep it one glance; the game page carries the complete list.
 */
internal fun watchLine(broadcasts: List<Broadcast>): String? {
    if (broadcasts.isEmpty()) return null
    val names =
        broadcasts
            .sortedBy { if (it.market == "N") 0 else 1 }
            .map { it.network }
            .filter { it.isNotBlank() }
            .distinct()
    if (names.isEmpty()) return null
    // Two names used to be the ceiling because the rail refused to wrap; it wraps now, so three fit when the
    // names are short, and the count still says how many are left.
    val shown = names.take(3).joinToString(" · ")
    val remaining = names.size - 3
    return if (remaining > 0) "$shown +$remaining" else shown
}

@Composable
private fun WatchChannels(
    line: String,
    maxWidth: Dp,
) {
    Text(
        line,
        Modifier.width(maxWidth).testTag(WATCH_TAG),
        style = Roux.cardTypography.networks,
        color = Roux.accents.muted,
        textAlign = TextAlign.Start,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FinalCapsule(kind: FinalKind?) {
    Text(
        stringResource(
            when (kind) {
                FinalKind.OT -> R.string.game_row_final_ot
                FinalKind.SO -> R.string.game_row_final_so
                FinalKind.REG, null -> R.string.game_row_final
            },
        ),
        Modifier
            .testTag(STATE_CAPSULE_TAG)
            .border(CAPSULE_BORDER, Roux.accents.stroke, CircleShape)
            .padding(CAPSULE_PADDING)
            .padding(horizontal = CAPSULE_HORIZONTAL_PADDING, vertical = 2.dp),
        style = Roux.cardTypography.finalState,
        color = Roux.accents.signature,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun RailText(text: String) {
    Text(
        text,
        style = Roux.cardTypography.shots,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

/**
 * What a screen reader says for one card, as a single sentence.
 *
 * The card is drawn as two team rows and a rail, and read in that order a live game arrives as seven
 * fragments - "Boston Bruins", "3", "New York Rangers", "2", "Live", "LIVE", "2nd 7:42" - which is the same
 * score twice with the state split across two labels. The sentence is the fixed order a person would say it
 * in: who played, the score, then the state. The state appears once, which is why the live word is not
 * repeated from the rail's capsule.
 *
 * A favourite card says so. The accent is a cue a sighted viewer reads at a glance, and a card that looks
 * different has to sound different; the cue is spoken, the visible text is not changed.
 */
@Composable
internal fun gameRowDescription(
    game: Game,
    favourite: Boolean = false,
): String {
    val away = game.away.name?.takeIf { it.isNotBlank() } ?: game.away.abbrev
    val home = game.home.name?.takeIf { it.isNotBlank() } ?: game.home.abbrev
    val awayScore = game.awayScore
    val homeScore = game.homeScore
    val scoreLine =
        if (awayScore != null && homeScore != null) {
            stringResource(R.string.game_row_description_score, away, awayScore, home, homeScore)
        } else {
            stringResource(R.string.game_row_description_upcoming, away, home)
        }
    val state =
        when (game.state) {
            is GameState.Live -> {
                gameRowLiveState(game)
            }

            GameState.Final -> {
                stringResource(
                    when (game.finalKind) {
                        FinalKind.OT -> R.string.game_row_description_final_ot
                        FinalKind.SO -> R.string.game_row_description_final_so
                        FinalKind.REG, null -> R.string.game_row_description_final
                    },
                )
            }

            GameState.Scheduled, GameState.Pre -> {
                startTimeLabel(game)
            }

            is GameState.Unknown -> {
                stringResource(R.string.game_row_description_unknown)
            }
        }
    val sentence = stringResource(R.string.game_row_description, scoreLine, state)
    // An ordinary card keeps its sentence byte for byte: only the favourite one is prefixed.
    return if (favourite) stringResource(R.string.game_row_description_favourite, sentence) else sentence
}

/** The live part of the sentence: the word, then the period and the clock, each spoken once. */
@Composable
private fun gameRowLiveState(game: Game): String {
    val period = game.period
    val pieces = mutableListOf(stringResource(R.string.game_row_live_description).lowercase())
    if (period != null) {
        // "2nd period" - the rail's bare "2nd" is a label beside a clock, which a sentence cannot lean on.
        pieces += stringResource(R.string.game_row_description_period, periodLabel(period))
        if (period.inIntermission) pieces += stringResource(R.string.game_row_intermission).lowercase()
    }
    period?.clock?.takeIf { it.isNotBlank() }?.let { pieces += it }
    return pieces.joinToString(", ")
}

@Composable
private fun startTimeLabel(game: Game): String = rememberStartTimeLabel(game)
