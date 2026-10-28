package net.montycasa.roux.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import net.montycasa.roux.R
import net.montycasa.roux.domain.model.Game
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Times are shown on the device's clock, in the device's 12/24-hour convention, and never carry a zone label.
 *
 * A 6:30 PM Eastern game reads "3:30 PM" on a Vancouver phone. That is the point of honouring the device clock, and a
 * label on every row would be noise the signed-off design never asked for.
 */
@Composable
fun rememberClockLabel(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(instant, locale, is24Hour) {
        clockLabel(instant, ZoneId.systemDefault(), is24Hour, locale)
    }
}

/**
 * When a time is from an earlier civil day, "yesterday" says more than a bare clock and costs one word.
 *
 * The comparison is between device civil dates, so a banner written at 11:30 PM reads "yesterday" at 12:05 AM even
 * though only thirty-five minutes have passed - which is what a person means by yesterday.
 */
@Composable
fun rememberUpdatedLabel(
    since: Instant,
    now: Instant = Clock.System.now(),
): String {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val yesterday =
        androidx.compose.ui.res
            .stringResource(R.string.common_yesterday)
    return remember(since, now, locale, is24Hour, yesterday) {
        updatedLabel(since, now, ZoneId.systemDefault(), is24Hour, locale, yesterday)
    }
}

internal fun clockLabel(
    instant: Instant,
    zone: ZoneId,
    is24Hour: Boolean,
    locale: Locale,
): String {
    val skeleton = if (is24Hour) "Hma" else "hma"
    return DateFormat
        .getBestDateTimePattern(locale, skeleton)
        .let { pattern -> java.text.SimpleDateFormat(pattern, locale) }
        .apply { timeZone = TimeZone.getTimeZone(zone) }
        .format(Date(instant.toEpochMilliseconds()))
}

/**
 * Today shows a clock, yesterday shows the word, and anything older shows its date.
 *
 * A bare clock is only honest for today: reading "Updated 19:42" two days later would say the cache is fresher than it
 * is, which is the one thing a staleness banner must never do.
 */
internal fun updatedLabel(
    since: Instant,
    now: Instant,
    zone: ZoneId,
    is24Hour: Boolean,
    locale: Locale,
    yesterday: String,
): String {
    val clock = clockLabel(since, zone, is24Hour, locale)
    val sinceDate = civilDate(since, zone)
    val today = civilDate(now, zone)
    return when (sinceDate) {
        today -> clock
        today.minusDays(1) -> "$yesterday $clock"
        else -> "${dateLabel(since, zone, locale)}, $clock"
    }
}

private fun dateLabel(
    instant: Instant,
    zone: ZoneId,
    locale: Locale,
): String =
    java.text
        .SimpleDateFormat("MMM d", locale)
        .apply { timeZone = TimeZone.getTimeZone(zone) }
        .format(Date(instant.toEpochMilliseconds()))

private fun civilDate(
    instant: Instant,
    zone: ZoneId,
): LocalDate =
    java.time.Instant
        .ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(zone)
        .toLocalDate()

/** The kickoff time for a game, or the TBD marker when the feed has not scheduled it yet. */
@Composable
fun rememberStartTimeLabel(game: Game): String {
    val instant =
        game.startTimeUtc ?: return androidx.compose.ui.res
            .stringResource(R.string.game_row_time_tbd)
    return rememberClockLabel(instant)
}
