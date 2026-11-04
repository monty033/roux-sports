package net.montycasa.roux.domain.policy

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Where a calendar date sits relative to the viewer's today.
 *
 * A boolean "is today" is not enough: past and future need different lifetimes, and
 * collapsing them treats a stale past day as if it were still going to be played.
 */
enum class DayRelation { Past, Today, Future }

/**
 * The shape of one day's slate, counted by the caller.
 *
 * These are counts rather than flags because two booleans can contradict each other and because
 * "all games are final" is vacuously true over an empty list. A slate of size zero means no games
 * are published, which is emphatically not the same as every game having finished, and confusing
 * the two caches an empty day forever.
 */
data class DaySlate(
    val size: Int,
    val live: Int = 0,
    val finished: Int = 0,
) {
    init {
        // Counts come from a caller, so the ranges are enforced here rather than trusted. An
        // impossible slate - more live or finished games than games - must not be silently
        // absorbed into a lifetime decision, because an over-counted "finished" is exactly how an
        // unresolved result becomes permanent. Each of these three is independently load-bearing;
        // a separate `size >= 0` check would be dead, since a negative size leaves `0..size` empty
        // and the live range rejects every value.
        require(live in 0..size) { "live=$live is not a count within $size games" }
        require(finished >= 0) { "finished must be non-negative: $finished" }
        // Compare by subtraction, not by `live + finished <= size`: the sum overflows for large
        // counts, wraps negative, and would admit a slate with more games than it has. `live` is
        // already bounded by `size` at this point, so the subtraction cannot overflow.
        require(finished <= size - live) {
            "live=$live plus finished=$finished exceed size=$size"
        }
    }
}

/**
 * Pure cache lifetimes using caller-supplied time so expiry is deterministic.
 * A null lifetime means the data never expires and is refreshed only by an explicit refresh.
 */
object FreshnessPolicy {
    val liveScoreAge: Duration = 25.seconds
    val idleScoreAge: Duration = 5.minutes
    val futureDayAge: Duration = 6.hours
    val liveStandingsAge: Duration = 10.minutes
    val idleStandingsAge: Duration = 1.hours
    val seasonsAge: Duration = 24.hours
    val liveDetailAge: Duration = 30.seconds
    val scheduledDetailAge: Duration = 5.minutes

    /**
     * A final boxscore is not immutable. The league corrects goal and assist credits the day after a
     * game, and those corrections are exactly what the game page shows. The old "a final boxscore
     * never changes" reasoning served the first copy for ever, so a corrected boxscore never
     * appeared; a day is long enough to pick up the overnight corrections.
     */
    val finalDetailAge: Duration = 24.hours
    val nextStartLead: Duration = 1.minutes

    /**
     * A whole-season schedule is not immutable: start times and TV listings move, while final
     * results do not. Six hours keeps those changes visible without requesting 186 KB every time a
     * club page opens.
     */
    val clubScheduleAge: Duration = 6.hours

    /**
     * A roster changes rarely, but a call-up or trade can land mid-day. Six hours mirrors the
     * schedule's window and keeps a tab switch off the network.
     */
    val clubRosterAge: Duration = 6.hours

    /**
     * Season statistics move after every game, but the feed's own numbers are compiled overnight and
     * a tab switch must not generate a request. Six hours matches the roster and the schedule.
     */
    val clubStatsAge: Duration = 6.hours

    /**
     * The league table moves once a day as games finish, so six hours keeps a club page from asking for
     * the whole league on every open. The club's own roster and stats use the same window.
     */
    val leagueStatsAge: Duration = 6.hours

    /**
     * A player's profile changes after a game, not during one, and the feed compiles its totals overnight. Six hours
     * matches the club pages, so opening the same player twice in a session does not ask twice.
     */
    val playerProfileAge: Duration = 6.hours

    /**
     * A finished day does not change; polling it would be pure cost.
     *
     * `startTimeUtc` is the *scheduled* start, which for the NHL precedes the actual puck drop by
     * several minutes. Lateness reasoning must therefore allow for that gap, and the pre-start
     * clip below deliberately lands before the scheduled time rather than at it.
     */
    fun scoreDayMaxAge(
        now: Instant,
        slate: DaySlate,
        day: DayRelation,
        nextStartAt: Instant?,
    ): Duration? {
        if (slate.live > 0) return liveScoreAge
        // Never-expire requires an actually finished past slate: at least one game, and every
        // game finished. The size check keeps an empty past day - a gap in the feed, or a day
        // whose games were never published - from qualifying and pinning itself forever.
        if (day == DayRelation.Past && slate.size > 0 && slate.finished == slate.size) return null
        if (day == DayRelation.Future) return futureDayAge
        // A past day should be final. If it is not, something is unresolved - a postponement,
        // or the feed published a result late - so it is treated as idle traffic rather than as
        // immutable data. That is cheap, and it self-corrects if a result does turn up.
        if (day == DayRelation.Past) return idleScoreAge
        if (nextStartAt == null) return idleScoreAge
        // A five-minute cached copy that expires after puck drop shows a stale scheduled row
        // for up to five minutes exactly when users are watching. Expire before the next start.
        // Floor at zero so an imminent or past start expires immediately rather than going negative.
        return (nextStartAt - now - nextStartLead).coerceIn(Duration.ZERO, idleScoreAge)
    }

    /** Standings move while games are being played; once the night is over an hour is plenty. */
    fun standingsMaxAge(anyGameLive: Boolean): Duration = if (anyGameLive) liveStandingsAge else idleStandingsAge

    /** The season list changes once a season, so a daily check is sufficient. */
    fun seasonsMaxAge(): Duration = seasonsAge

    /** Start times and broadcasts move; final results do not. Six hours is the schedule's starting point. */
    fun clubScheduleMaxAge(): Duration = clubScheduleAge

    /** The roster's six-hour window, matching the club schedule. */
    fun clubRosterMaxAge(): Duration = clubRosterAge

    /** The stats tab's own six-hour window, independent of the roster and the schedule. */
    fun clubStatsMaxAge(): Duration = clubStatsAge

    /** The league table's own six-hour window, independent of any club's stats. */
    fun leagueStatsMaxAge(): Duration = leagueStatsAge

    fun playerProfileMaxAge(): Duration = playerProfileAge

    /**
     * A live detail gets the short lifetime; a scheduled one changes at puck drop; a final one is
     * bounded by a day because scoring corrections land after the game.
     *
     * "Not live" is not the same as "final": a scheduled game is not live, and it changes the
     * moment the game begins - which, because the scheduled time precedes the puck drop, can
     * happen several minutes after the announced time while this screen is already open. A
     * scheduled detail therefore gets a bounded lifetime rather than never expiring.
     */
    fun detailMaxAge(
        isLive: Boolean,
        isFinal: Boolean,
    ): Duration? =
        when {
            isLive -> liveDetailAge
            isFinal -> finalDetailAge
            else -> scheduledDetailAge
        }

    /**
     * Null means never expires. An entry exactly at its limit is still fresh;
     * expiry requires strictly exceeding the lifetime so the boundary is unambiguous.
     */
    fun isStale(
        fetchedAt: Instant,
        now: Instant,
        maxAge: Duration?,
    ): Boolean = maxAge != null && now - fetchedAt > maxAge
}
