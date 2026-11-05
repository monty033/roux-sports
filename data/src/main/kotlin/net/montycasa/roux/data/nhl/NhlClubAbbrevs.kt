package net.montycasa.roux.data.nhl

/**
 * The club abbreviation for a club name as a player's season row carries it: "Carolina Hurricanes" is `CAR`.
 *
 * A season row names its club in full but has no abbreviation, so the career table needs this table to print one. The
 * league's current clubs are here, plus the names a few franchises held in earlier seasons. A name that is not here
 * returns null, and the caller prints the name the feed sent instead of an abbreviation it would have to invent.
 */
internal object NhlClubAbbrevs {
    private val byName =
        mapOf(
            "Anaheim Ducks" to "ANA",
            "Arizona Coyotes" to "ARI",
            "Atlanta Thrashers" to "ATL",
            "Boston Bruins" to "BOS",
            "Buffalo Sabres" to "BUF",
            "Calgary Flames" to "CGY",
            "Carolina Hurricanes" to "CAR",
            "Chicago Blackhawks" to "CHI",
            "Colorado Avalanche" to "COL",
            "Columbus Blue Jackets" to "CBJ",
            "Dallas Stars" to "DAL",
            "Detroit Red Wings" to "DET",
            "Edmonton Oilers" to "EDM",
            "Florida Panthers" to "FLA",
            "Los Angeles Kings" to "LAK",
            "Minnesota Wild" to "MIN",
            "Montréal Canadiens" to "MTL",
            "Nashville Predators" to "NSH",
            "New Jersey Devils" to "NJD",
            "New York Islanders" to "NYI",
            "New York Rangers" to "NYR",
            "Ottawa Senators" to "OTT",
            "Philadelphia Flyers" to "PHI",
            "Phoenix Coyotes" to "PHX",
            "Pittsburgh Penguins" to "PIT",
            "San Jose Sharks" to "SJS",
            "Seattle Kraken" to "SEA",
            "St. Louis Blues" to "STL",
            "Tampa Bay Lightning" to "TBL",
            "Toronto Maple Leafs" to "TOR",
            "Utah Hockey Club" to "UTA",
            "Utah Mammoth" to "UTA",
            "Vancouver Canucks" to "VAN",
            "Vegas Golden Knights" to "VGK",
            "Washington Capitals" to "WSH",
            "Winnipeg Jets" to "WPG",
        )

    /** The abbreviation for [name], or null when the club is not one this table knows. */
    fun forName(name: String): String? = byName[name.trim()]
}
