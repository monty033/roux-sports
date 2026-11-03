package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class ClubScheduleSectionsTest {
    private val now = Instant.parse("2026-11-03T12:00:00Z")

    @Test
    fun aGameLaterTodayIsUpcomingAndAFinishedGameEarlierTodayIsAResult() {
        val later = game("later", "2026-11-03T20:00:00Z", GameState.Scheduled)
        val finished = game("finished", "2026-11-03T15:00:00Z", GameState.Final, finished = true)

        val sections = ClubScheduleSections.of(listOf(finished, later), now)

        assertEquals(listOf(later), sections.upcoming)
        assertEquals(listOf(finished), sections.results)
        assertTrue("a game cannot appear in both groups", sections.upcoming.intersect(sections.results).isEmpty())
    }

    @Test
    fun sameDayGamesAreOrderedByStartInstantNotByTheDateString() {
        // Both upcoming games share `gameDate`, so a sort by that string would keep this input order
        // (late first) instead of the actual schedule.
        val late = game("late", "2026-11-03T22:00:00Z", GameState.Scheduled)
        val early = game("early", "2026-11-03T18:00:00Z", GameState.Scheduled)
        // The finished pair has the same trap: the earlier result must come second.
        val earlierResult = game("earlier-result", "2026-11-03T13:00:00Z", GameState.Final, finished = true)
        val laterResult = game("later-result", "2026-11-03T16:00:00Z", GameState.Final, finished = true)

        val sections = ClubScheduleSections.of(listOf(late, early, earlierResult, laterResult), now)

        assertEquals(listOf(early, late), sections.upcoming)
        assertEquals(listOf(laterResult, earlierResult), sections.results)
    }

    @Test
    fun eachGroupKeepsOnlyTheFiveNearestOrMostRecentGames() {
        val upcoming =
            (1..7).map { game("up-$it", "2026-11-04T${9 + it}:00:00Z", GameState.Scheduled) }
        val results =
            (1..7).map { game("result-$it", "2026-10-${20 + it}T12:00:00Z", GameState.Final, finished = true) }

        val sections = ClubScheduleSections.of(upcoming + results, now)

        assertEquals(5, sections.upcoming.size)
        assertEquals(listOf("up-1", "up-2", "up-3", "up-4", "up-5"), sections.upcoming.map { it.id.value })
        assertEquals(5, sections.results.size)
        assertEquals(
            listOf("result-7", "result-6", "result-5", "result-4", "result-3"),
            sections.results.map { it.id.value },
        )
    }

    @Test
    fun aGameWithNoStartInstantIsNotPresentedAsUpcoming() {
        val noStart = game("no-start", null, GameState.Scheduled)
        val later = game("later", "2026-11-03T20:00:00Z", GameState.Scheduled)
        val sections = ClubScheduleSections.of(listOf(noStart, later), now)

        assertEquals(listOf(later), sections.upcoming)
        assertFalse(sections.upcoming.any { it.id.value == "no-start" })
    }

    private fun game(
        id: String,
        start: String?,
        state: GameState,
        finished: Boolean = false,
    ): ClubGame =
        ClubGame(
            id = GameId(id),
            date = "2026-11-03",
            startTimeUtc = start?.let(Instant::parse),
            home = TeamRef(TeamKey("ANA")),
            away = TeamRef(TeamKey("TOR")),
            state = state,
            finished = finished,
        )
}
