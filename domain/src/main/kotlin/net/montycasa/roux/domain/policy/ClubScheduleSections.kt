package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubGame
import kotlin.time.Instant

/** The two groups a club page shows: the next games, then the most recently finished ones. */
data class ScheduleSections(
    val upcoming: List<ClubGame> = emptyList(),
    val results: List<ClubGame> = emptyList(),
)

/**
 * Splits a whole-season schedule at [now].
 *
 * Finished games are results even when they started earlier the same day; unfinished games that
 * have not started yet are upcoming. Ordering uses the start instant rather than the feed's
 * `gameDate` string because several games share a date and only the instant can rank them within it.
 * Each group is capped at five, which is enough for a club page without turning it into the scores list.
 */
object ClubScheduleSections {
    const val LIMIT = 5

    fun of(
        games: List<ClubGame>,
        now: Instant,
    ): ScheduleSections {
        val upcoming =
            games
                .asSequence()
                .filter { !it.isFinished }
                .filter { game -> game.startTimeUtc?.let { it > now } == true }
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

/** Synonym kept short at call sites that already have "sections" in scope. */
fun clubScheduleSections(
    games: List<ClubGame>,
    now: Instant,
): ScheduleSections = ClubScheduleSections.of(games, now)
