package net.montycasa.roux.domain.policy

/**
 * The league's season window around a selected score day.
 *
 * The score feed's previous and next game dates are the only season information it carries. Comparing their
 * seasons separates the off-season, where the next games are months away, from a break inside one season
 * (All-Star, the Olympics), where today's "No games / Next games" behaviour is still correct.
 */
sealed interface SeasonWindow {
    /**
     * A day with no games between two seasons, or with no next game date.
     *
     * [lastGameDate] is the feed's previous game day, the selection a dark day should land on. It is null
     * only when the feed supplied no previous date either.
     */
    data class Dark(
        val lastGameDate: String?,
    ) : SeasonWindow

    /** A day with games, or an empty day whose neighbouring game days belong to the same season. */
    data object Open : SeasonWindow

    companion object {
        /**
         * Decide the window without reading a clock or the device.
         *
         * A day with games is never dark, including a preseason day. An empty day is dark when the feed
         * supplies no next game date, or when its previous and next game dates fall in different seasons.
         * A missing previous date leaves nothing to compare, so it is not dark.
         */
        fun of(
            hasGames: Boolean,
            previousGameDate: String?,
            nextGameDate: String?,
        ): SeasonWindow {
            if (hasGames) return Open
            val previous = previousGameDate?.takeIf { it.isNotBlank() }
            val next = nextGameDate?.takeIf { it.isNotBlank() }
            if (next == null) return Dark(previous)
            if (previous == null) return Open
            val crossesSeasons =
                LeagueCalendar.seasonId(previous) != LeagueCalendar.seasonId(next)
            return if (crossesSeasons) Dark(previous) else Open
        }
    }
}
