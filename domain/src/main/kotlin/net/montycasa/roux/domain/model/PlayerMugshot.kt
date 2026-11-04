package net.montycasa.roux.domain.model

import kotlin.time.Instant

/**
 * Where the league publishes a player's mugshot.
 *
 * The boxscore - the payload the game page's player tables come from - carries no headshot field, while the roster
 * feed does. Rather than fetch a second payload for a picture, the path is built from the three things the boxscore
 * does carry: the player's id, their club, and the season the game belongs to. A build that names a picture the
 * league has not published degrades to initials, which is what every other image on these screens already does.
 */
object PlayerMugshot {
    private const val BASE = "https://assets.nhle.com/mugs/nhl"

    /** Null when any part of the path is missing, so the caller draws initials rather than a broken request. */
    fun url(
        playerId: Long?,
        teamAbbrev: String?,
        seasonStartYear: Int?,
    ): String? {
        val id = playerId?.takeIf { it > 0 } ?: return null
        val club = teamAbbrev?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val season = seasonStartYear ?: return null
        return "$BASE/$season${season + 1}/$club/$id.png"
    }

    /**
     * The season a game belongs to.
     *
     * Two sources, because the two routes that open this page carry different things: a scores row has the feed's
     * date, while the detail route - which rebuilds the game from its arguments so the page can draw before any
     * endpoint answers - carries only the start instant. Reading the instant as well is what stops a page opened
     * from the route from losing every picture to the initials fallback.
     */
    fun seasonStartYear(game: Game): Int? = seasonStartYear(game.date) ?: seasonStartYear(game.startTimeUtc)

    /**
     * The season a game belongs to, from the instant it started.
     *
     * Read in UTC, which is the zone the feed states its start times in: a game that begins 2 August UTC belongs to
     * the season that started that year either way, so the only dates this could ever miss are the ones that fall
     * either side of midnight on 1 August.
     */
    fun seasonStartYear(startTimeUtc: Instant?): Int? {
        // kotlin.time.Instant walks as ISO-8601: yyyy-MM-ddThh:mm:ssZ.
        val text = startTimeUtc?.toString() ?: return null
        val year = text.take(4).toIntOrNull() ?: return null
        val month = text.drop(5).take(2).toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return if (month >= AUGUST) year else year - 1
    }

    /**
     * The season a game belongs to, from its ISO date: the league's seasons turn over in the autumn, so anything
     * from August onward belongs to the season that starts that year.
     */
    fun seasonStartYear(isoDate: String?): Int? {
        val parts = isoDate?.split('-') ?: return null
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        if (year < 1900 || month !in 1..12) return null
        return if (month >= AUGUST) year else year - 1
    }

    /** August, when the league's seasons turn over: preseason belongs to the new season, not the old one. */
    private const val AUGUST = 8
}
