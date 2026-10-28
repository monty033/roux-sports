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
