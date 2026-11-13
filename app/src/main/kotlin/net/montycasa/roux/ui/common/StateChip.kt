@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.ui.theme.Roux

@Composable
internal fun StateClock(
    text: String,
    live: Boolean,
    intermission: Boolean = false,
    inline: Boolean = false,
    style: TextStyle = Roux.cardTypography.clock,
    color: Color = if (live) Roux.accents.signature else MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) {
    val breakDescription = if (intermission) stringResource(R.string.game_row_intermission) else null
    Text(
        text,
        // Keep the break available to accessibility without adding a fourth visible rail line.
        // An inline clock needs its own intrinsic width so its paragraph and node agree.
        // Stacked clocks fill the measured rail content width, preserving the existing insets.
        modifier =
            (if (inline) Modifier.width(IntrinsicSize.Max) else Modifier.fillMaxWidth())
                .semantics { if (breakDescription != null) contentDescription = "$text, $breakDescription" },
        style = style,
        color = color,
        textAlign = TextAlign.Start,
        maxLines = maxLines,
    )
}

@Composable
internal fun LiveCapsule(
    @StringRes label: Int,
    testTag: String,
) {
    val liveDescription = stringResource(R.string.game_row_live_description)
    Row(
        Modifier
            .testTag(testTag)
            .width(IntrinsicSize.Max)
            .background(Roux.accents.fill, CircleShape)
            .border(CAPSULE_BORDER, Roux.accents.fill, CircleShape)
            .padding(CAPSULE_PADDING)
            .padding(horizontal = CAPSULE_HORIZONTAL_PADDING, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(LIVE_DOT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LIVE_DOT_SIZE)
                .background(Roux.accents.onFill, CircleShape)
                .semantics { contentDescription = liveDescription },
        )
        Text(
            stringResource(label),
            style = Roux.cardTypography.finalState,
            color = Roux.accents.onFill,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun periodLabel(period: PeriodInfo?): String =
    stringResource(
        // A supplied period type takes precedence over its number (including playoff overtime).
        when (period?.periodType) {
            "OT" -> {
                R.string.game_row_period_overtime
            }

            "SO" -> {
                R.string.game_row_period_shootout
            }

            else -> {
                when (period?.ordinal) {
                    1 -> R.string.game_row_period_first
                    2 -> R.string.game_row_period_second
                    3 -> R.string.game_row_period_third
                    else -> R.string.game_row_period_unavailable
                }
            }
        },
    )
