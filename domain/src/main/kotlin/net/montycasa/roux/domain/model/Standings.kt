package net.montycasa.roux.domain.model

/** A team's conference, including unrecognised names retained from the feed. */
sealed interface Conference {
    /** The Eastern Conference. */
    data object Eastern : Conference

    /** The Western Conference. */
    data object Western : Conference

    /** An unrecognised conference name preserved exactly as received. */
    data class Unknown(
        val raw: String,
    ) : Conference

    companion object {
        /** Matches trimmed feed names case-sensitively; unknown inputs remain unchanged. */
        fun fromFeed(raw: String): Conference =
            when (raw.trim()) {
                "Eastern" -> Eastern
                "Western" -> Western
                else -> Unknown(raw)
            }
    }
}

/** A team's division, including unrecognised names retained from the feed. */
sealed interface Division {
    /** The Atlantic Division. */
    data object Atlantic : Division

    /** The Metropolitan Division. */
    data object Metropolitan : Division

    /** The Central Division. */
    data object Central : Division

    /** The Pacific Division. */
    data object Pacific : Division

    /** An unrecognised division name preserved exactly as received. */
    data class Unknown(
        val raw: String,
    ) : Division

    companion object {
        /** Matches trimmed feed names case-sensitively; unknown inputs remain unchanged. */
        fun fromFeed(raw: String): Division =
            when (raw.trim()) {
                "Atlantic" -> Atlantic
                "Metropolitan" -> Metropolitan
                "Central" -> Central
                "Pacific" -> Pacific
                else -> Unknown(raw)
            }
    }
}

/**
 * A team's standings totals and feed rankings.
 *
 * @property wildcardSequence The feed's raw sequence: a positive value is the team's wildcard rank
 * within its conference; zero means the team is already placed by division position and is not
 * wildcard-ranked. Do not use this value as a sort rank without accounting for zero; use [wildcardRank]
 * to distinguish teams with a wildcard rank from those placed by division.
 */
data class StandingRow(
    val team: TeamRef,
    val gp: Int,
    val w: Int,
    val l: Int,
    val otl: Int?,
    val pts: Int,
    val leagueSequence: Int,
    val conferenceSequence: Int,
    val divisionSequence: Int,
    val wildcardSequence: Int,
    val conference: Conference,
    val division: Division,
) {
    /** Wildcard position within the conference, or null when the team is placed by division. */
    val wildcardRank: Int? get() = wildcardSequence.takeIf { it > 0 }

    /** Compact display form of wins, losses, and overtime losses; a null otl shows an en dash. */
    val record: String get() = "$w-$l-${otl ?: "\u2013"}"
}

/** A season's feed identifier and optional standings dates as ISO YYYY-MM-DD strings. */
data class Season(
    val id: Int,
    val start: String?,
    val end: String?,
)
