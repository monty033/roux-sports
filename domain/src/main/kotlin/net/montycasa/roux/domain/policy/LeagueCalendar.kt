package net.montycasa.roux.domain.policy

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The league day is the device's civil day: the slate a person expects when they look at their own phone.
 *
 * The league plays in the east, but a fan in Vancouver at 9:30 PM on the 26th is still on the 26th, and a feed bucketed
 * by Eastern dates would hand them the 27th's games. There is no separate rollover hour - the day turns at local
 * midnight, which is also why [zone] is required rather than read from an ambient default: :domain stays free of device
 * state, and a caller who forgets it gets a compile error instead of a quietly wrong slate.
 */
object LeagueCalendar {
    /**
     * The first calendar year in a season, where July is the boundary.
     *
     * The league's season identifier is the two years run together (`20262027`), and July separates
     * one season from the next: a September 2026 game belongs to 20262027, while a May 2026 game
     * belongs to 20252026. The device's civil day is used, matching the day the scores screen shows.
     */
    fun seasonStartYear(date: LocalDate): Int = if (date.month.ordinal + 1 >= 7) date.year else date.year - 1

    /** The feed's season identifier, such as `20262027`, for [date]. */
    fun seasonId(date: LocalDate): String {
        val start = seasonStartYear(date)
        return "$start${start + 1}"
    }

    /** The season identifier for an ISO `YYYY-MM-DD` day. */
    fun seasonId(day: String): String = seasonId(LocalDate.parse(day))

    /** The season identifier for [instant]'s civil date in [zone]. */
    fun seasonId(
        instant: Instant,
        zone: TimeZone,
    ): String = seasonId(leagueDate(instant, zone))

    /** [instant]'s civil date in [zone], for callers that need calendar arithmetic such as the next midnight. */
    fun leagueDate(
        instant: Instant,
        zone: TimeZone,
    ): LocalDate = instant.toLocalDateTime(zone).date

    /** [instant]'s date in [zone] as ISO YYYY-MM-DD, the same shape the feed uses for its own date keys. */
    fun leagueDay(
        instant: Instant,
        zone: TimeZone,
    ): String = leagueDate(instant, zone).toString()
}
