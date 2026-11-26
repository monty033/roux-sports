package net.montycasa.roux.domain.model

/**
 * A football season as a club's schedule states it.
 *
 * Seventeen games in eighteen weeks, so the bye is the week the games leave empty. It is modelled as an entry rather
 * than as a gap the list simply skips, because a skipped number is indistinguishable from a missing row.
 *
 * The feed also states a `byeWeek` beside the games, and **it can be wrong**: Buffalo's captured schedule carries
 * `byeWeek: 5` while Buffalo plays in week 5 and sits out week 7. The games are the evidence, so the bye is read from
 * the gap and the stated number is kept in [statedByeWeek] so the disagreement stays visible instead of being tidied
 * away.
 */
data class NflClubSchedule(
    val seasonYear: Int,
    val team: TeamRef,
    /** The week no game occupies: the bye, read from the games. Null when all eighteen weeks are played. */
    val byeWeek: Int? = null,
    /** The week the feed's own field named, kept as evidence. It may disagree with [byeWeek]; the games win. */
    val statedByeWeek: Int? = null,
    /** Games and the bye, in the feed's own order. */
    val entries: List<NflSeasonEntry> = emptyList(),
)

/** One row of a season: a game, or the bye that occupies a week. */
sealed interface NflSeasonEntry {
    val week: Int

    data class Game(
        override val week: Int,
        val game: ClubGame,
    ) : NflSeasonEntry

    /** The week without a game. [week] is the feed's stated bye week, never a number inferred from a missing one. */
    data class Bye(
        override val week: Int,
    ) : NflSeasonEntry
}

/** How a finished game came out for one club. Football keeps the tie, so it is its own outcome rather than a loss. */
enum class FootballResult {
    Win,
    Loss,
    Tie,

    /** Not played yet, or played with a score the feed did not state. */
    Unplayed,
}

/**
 * This club's result in [game], from this club's side of the score.
 *
 * A game the feed has not completed has no result, whatever numbers sit in its fields: a scheduled fixture whose
 * placeholder score reads 0-0 is not a tie.
 */
fun ClubGame.resultFor(club: TeamKey): FootballResult {
    if (!isFinished) return FootballResult.Unplayed
    val mine =
        if (home.key == club) {
            homeScore
        } else if (away.key == club) {
            awayScore
        } else {
            return FootballResult.Unplayed
        }
    val theirs =
        if (home.key == club) {
            awayScore
        } else if (away.key == club) {
            homeScore
        } else {
            return FootballResult.Unplayed
        }
    val scored = mine ?: return FootballResult.Unplayed
    val conceded = theirs ?: return FootballResult.Unplayed
    return when {
        scored > conceded -> FootballResult.Win
        scored < conceded -> FootballResult.Loss
        else -> FootballResult.Tie
    }
}
