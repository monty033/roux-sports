package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ScheduleBlock

/**
 * Opening night is the first regular-season game in the schedule blocks a caller has read.
 *
 * Preseason games matter to fans, but they are not the season's opening: the feed's `gameType 1`
 * must never be chosen even when it is the earliest game of the walk. A block with no `gameType 2`
 * game contributes nothing, and no regular-season game anywhere means the answer is not known yet.
 */
object OpeningNight {
    private const val REGULAR_SEASON = 2

    /** The earliest date carrying a regular-season game, or null when no block has one. */
    fun from(blocks: List<ScheduleBlock>): String? =
        blocks
            .asSequence()
            .flatMap { it.days.asSequence() }
            .filter { REGULAR_SEASON in it.gameTypes }
            .map { it.date }
            .minOrNull()
}
