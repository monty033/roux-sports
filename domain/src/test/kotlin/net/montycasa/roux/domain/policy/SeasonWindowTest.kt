package net.montycasa.roux.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The off-season is decided from the feed's neighbouring game days, not from a calendar constant or the gap
 * between them: a long break inside one season must still read as an ordinary empty day.
 */
class SeasonWindowTest {
    @Test
    fun julyTwentyIsDarkAndLandsOnTheLastGameDay() {
        assertEquals(
            SeasonWindow.Dark("2026-06-14"),
            SeasonWindow.of(
                hasGames = false,
                previousGameDate = "2026-06-14",
                nextGameDate = "2026-09-19",
            ),
        )
    }

    @Test
    fun juneTwentyIsDarkBeforeTheNextSeasonBegins() {
        assertEquals(
            SeasonWindow.Dark("2026-06-14"),
            SeasonWindow.of(
                hasGames = false,
                previousGameDate = "2026-06-14",
                nextGameDate = "2026-09-19",
            ),
        )
    }

    @Test
    fun aMidSeasonBreakIsNotDark() {
        // The 2026 Olympic break: both game days belong to the 2025-26 season even though the gap is long.
        assertEquals(
            SeasonWindow.Open,
            SeasonWindow.of(
                hasGames = false,
                previousGameDate = "2026-02-05",
                nextGameDate = "2026-02-22",
            ),
        )
    }

    @Test
    fun aPreseasonDayWithGamesIsNotDark() {
        // The next game date is in a new season, but the selected day itself has games.
        assertEquals(
            SeasonWindow.Open,
            SeasonWindow.of(
                hasGames = true,
                previousGameDate = "2026-06-14",
                nextGameDate = "2026-09-22",
            ),
        )
    }

    @Test
    fun aMissingNextGameDateIsDark() {
        assertEquals(
            SeasonWindow.Dark("2026-06-14"),
            SeasonWindow.of(
                hasGames = false,
                previousGameDate = "2026-06-14",
                nextGameDate = null,
            ),
        )
    }
}
