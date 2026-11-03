package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubGame

/** The two groups a club page shows: the next games, then the most recently finished ones. */
data class ScheduleSections(
    val upcoming: List<ClubGame> = emptyList(),
    val results: List<ClubGame> = emptyList(),
)

/**
 * Splits a whole-season schedule at [now].
 *
 * Upcoming is every game the feed has not finished, ordered by start instant, so a game in progress
 * heads the list instead of vanishing between the two groups; the row says how it stands. Results are
 * the finished games, most recent first. Ordering uses the start instant rather than the feed's
 * `gameDate` string because several games share a date and only the instant can rank them within it.
 * Each group is capped at five, which is enough for a club page without turning it into the scores list.
 */
object ClubScheduleSections {
    const val LIMIT = 5

    fun of(games: List<ClubGame>): ScheduleSections {
        val upcoming =
            games
                .asSequence()
                .filter { !it.isFinished }
                .sortedWith(soonestFirst)
                .take(LIMIT)
                .toList()
        val results =
            games
                .asSequence()
                .filter { it.isFinished }
                .sortedWith(mostRecentFirst)
                .take(LIMIT)
                .toList()
        return ScheduleSections(upcoming = upcoming, results = results)
    }

    private val soonestFirst =
        Comparator<ClubGame> { first, second ->
            when {
                first.startTimeUtc == null && second.startTimeUtc == null -> 0
                first.startTimeUtc == null -> 1
                second.startTimeUtc == null -> -1
                else -> first.startTimeUtc.compareTo(second.startTimeUtc)
            }
        }

    private val mostRecentFirst =
        Comparator<ClubGame> { first, second ->
            when {
                first.startTimeUtc == null && second.startTimeUtc == null -> 0
                first.startTimeUtc == null -> 1
                second.startTimeUtc == null -> -1
                else -> second.startTimeUtc.compareTo(first.startTimeUtc)
            }
        }
}
