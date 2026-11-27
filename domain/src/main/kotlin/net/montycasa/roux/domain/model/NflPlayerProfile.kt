package net.montycasa.roux.domain.model

/**
 * One football player's identity.
 *
 * The value is league-qualified by its type: an ESPN NFL athlete id is a string, while a hockey player id is a number,
 * and a page or cache entry keyed by this type can never answer for an NHL player of the same digits. The id is kept
 * as the feed writes it rather than parsed into a number, because the feed's ids are opaque tokens.
 */
data class NflPlayerId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "A football player id must not be blank" }
    }
}

/**
 * A football player's page: who the player is and the season summary the feed publishes.
 *
 * Every fact the feed can omit is nullable. Absence means the feed stated nothing, and the screen renders nothing in
 * its place; no adapter may turn a missing jersey, headshot or experience line into a placeholder.
 */
data class NflPlayerProfile(
    val playerId: NflPlayerId,
    val name: String,
    /** The feed's position code (`QB`, `K`, `CB`), from the athlete's own position object. */
    val positionAbbreviation: String? = null,
    /** The position's full wording (`Quarterback`), from the same object. */
    val positionName: String? = null,
    /** The feed's display jersey (`#16`). Null when the feed states none, which is not the same as number zero. */
    val jersey: String? = null,
    /** A headshot on the authorized artwork host, or null when the feed sent none or a different host. */
    val headshotUrl: String? = null,
    /** The feed's own height wording (`6' 4"`). */
    val heightText: String? = null,
    /** The feed's own weight wording (`217 lbs`). */
    val weightText: String? = null,
    val age: Int? = null,
    val college: String? = null,
    /** The feed's own experience wording (`11th Season`). */
    val experienceText: String? = null,
    val team: TeamRef? = null,
    /** The season the page describes, as the feed names it. */
    val season: NflPlayerSeason? = null,
    /** The feed's heading for the summary (`2026 regular season stats`). */
    val seasonSummaryTitle: String? = null,
    /** The season summary in feed order. Empty when the feed published no statistics. */
    val seasonStats: List<NflSeasonStat> = emptyList(),
)

/** The season block on a player's payload. Both fields are absent when the feed does not state them. */
data class NflPlayerSeason(
    val year: Int? = null,
    /** The feed's own wording for the season (`2026`). */
    val displayName: String? = null,
)

/**
 * One season-summary statistic. [displayValue] is the feed's own formatted value (`2,716`, `89.5`), kept verbatim so the
 * screen never re-formats a number the feed already decided how to write.
 */
data class NflSeasonStat(
    val name: String,
    val displayName: String,
    val displayValue: String,
)
