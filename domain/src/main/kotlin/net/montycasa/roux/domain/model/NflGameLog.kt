package net.montycasa.roux.domain.model

/**
 * One football player's game log.
 *
 * The columns are the feed's, not the screen's. [groups] are the payload's `categories`, each holding the columns its
 * `count` covers; a quarterback's log therefore arrives with `passing` and `rushing` groups and a kicker's with
 * `fieldgoals` and `pats` ones, with no position-specific list anywhere in the app. [columns] is the same columns
 * flattened in reading order.
 *
 * A value the feed did not state is absent from [NflGameLogEntry.stats], never `"0"` and never `"-"`: the screen prints
 * nothing in that cell, while a real zero remains `"0"`.
 */
data class NflGameLog(
    /** The feed's season heading (`2026 Regular Season`), when it states one. */
    val seasonLabel: String? = null,
    val groups: List<NflGameStatGroup> = emptyList(),
    val games: List<NflGameLogEntry> = emptyList(),
) {
    /** Every stat column in reading order, group by group. */
    val columns: List<NflGameColumn> get() = groups.flatMap { it.columns }
}

/** One run of columns from the feed's `categories`, such as `Passing` with its eleven columns. */
data class NflGameStatGroup(
    /** The feed's category code (`passing`, `fieldgoals`). */
    val name: String,
    /** The feed's heading for the group. It is not always capitalised; the screen prints it as sent. */
    val displayName: String,
    val columns: List<NflGameColumn> = emptyList(),
)

/**
 * One stat column. [name] is the feed's key and is what a row's values are keyed by, so two columns that share a short
 * label do not collide. [displayName] is its full wording; [shortLabel] is the narrow-column label the feed supplies.
 */
data class NflGameColumn(
    val name: String,
    val displayName: String,
    val shortLabel: String,
)

/** One game's line. The label fields are the top-level `events` object's, not the season type's stats row. */
data class NflGameLogEntry(
    val eventId: String,
    val week: Int? = null,
    /** The feed's game date, as sent. Sorting reads it; the screen formats it for display. */
    val gameDate: String? = null,
    /** `vs` for a home game and `@` for a road game, as the feed writes them. */
    val atVs: String? = null,
    val opponentAbbreviation: String? = null,
    val teamAbbreviation: String? = null,
    /** The feed's score wording (`39-35`), kept verbatim. */
    val score: String? = null,
    /** The feed's result code (`W`, `L`, `T`), kept verbatim. */
    val result: String? = null,
    /** Stat values keyed by [NflGameColumn.name]. A key with no value means the feed stated nothing for it. */
    val stats: Map<String, String> = emptyMap(),
)

/**
 * Newest game first, by the feed's date. Undated rows follow the dated ones, and two games on one date order by the
 * later event id, so the order does not depend on the order the payload happened to list its events in.
 */
fun List<NflGameLogEntry>.newestFirst(): List<NflGameLogEntry> =
    sortedWith(
        compareBy<NflGameLogEntry> { it.gameDate == null }
            .thenByDescending { it.gameDate }
            .thenByDescending { it.eventId },
    )
