package net.montycasa.roux.ui.common

import net.montycasa.roux.domain.model.TeamRef

/**
 * How a club is written on a page: city first, then the club's own name.
 *
 * The scores feed only carries the nickname (`name.default` is "Flyers"), and the game page has to name a club properly
 * before any endpoint has answered, so the city cannot come from the payload. The league's clubs are stable, so the
 * table is written down once and keyed by the abbreviation the feed does carry.
 *
 * A club missing from this table still renders: [cityAndName] falls back to whatever name the payload supplied, then to
 * the abbreviation, because a misplaced club should look wrong, not blank.
 */
internal object TeamNames {
    private val byAbbrev =
        mapOf(
            "ANA" to ("Anaheim" to "Ducks"),
            "BOS" to ("Boston" to "Bruins"),
            "BUF" to ("Buffalo" to "Sabres"),
            "CGY" to ("Calgary" to "Flames"),
            "CAR" to ("Carolina" to "Hurricanes"),
            "CHI" to ("Chicago" to "Blackhawks"),
            "COL" to ("Colorado" to "Avalanche"),
            "CBJ" to ("Columbus" to "Blue Jackets"),
            "DAL" to ("Dallas" to "Stars"),
            "DET" to ("Detroit" to "Red Wings"),
            "EDM" to ("Edmonton" to "Oilers"),
            "FLA" to ("Florida" to "Panthers"),
            "LAK" to ("Los Angeles" to "Kings"),
            "MIN" to ("Minnesota" to "Wild"),
            "MTL" to ("Montréal" to "Canadiens"),
            "NSH" to ("Nashville" to "Predators"),
            "NJD" to ("New Jersey" to "Devils"),
            "NYI" to ("New York" to "Islanders"),
            "NYR" to ("New York" to "Rangers"),
            "OTT" to ("Ottawa" to "Senators"),
            "PHI" to ("Philadelphia" to "Flyers"),
            "PIT" to ("Pittsburgh" to "Penguins"),
            "SJS" to ("San Jose" to "Sharks"),
            "SEA" to ("Seattle" to "Kraken"),
            "STL" to ("St. Louis" to "Blues"),
            "TBL" to ("Tampa Bay" to "Lightning"),
            "TOR" to ("Toronto" to "Maple Leafs"),
            "UTA" to ("Utah" to "Mammoth"),
            "VAN" to ("Vancouver" to "Canucks"),
            "VGK" to ("Vegas" to "Golden Knights"),
            "WSH" to ("Washington" to "Capitals"),
            "WPG" to ("Winnipeg" to "Jets"),
        )

    /** The city, or null when this club is not one of the league's current 32. */
    fun city(abbrev: String): String? = byAbbrev[abbrev.uppercase()]?.first

    /** The club's own name ("Flyers"), or null for an unrecognised abbreviation. */
    fun nickname(abbrev: String): String? = byAbbrev[abbrev.uppercase()]?.second

    /** The city and club name as the page writes them, falling back to the payload and then the abbreviation. */
    fun cityAndName(team: TeamRef): Pair<String?, String> = city(team.abbrev) to (nickname(team.abbrev) ?: team.name ?: team.abbrev)
}
