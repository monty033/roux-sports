package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class ScoresOrderingTest {
    @Test
    fun liveThenUpcomingThenFinalThenUnknown() {
        val live = game("live", GameState.Live())
        val pre = game("pre", GameState.Pre)
        val scheduled = game("scheduled", GameState.Scheduled, 21)
        val final = game("final", GameState.Final)
        val unknown = game("unknown", GameState.Unknown("new"))
        assertEquals(
            listOf(live, pre, scheduled, final, unknown),
            ScoresOrdering.order(listOf(unknown, final, scheduled, pre, live)),
        )
    }

    @Test
    fun earliestStartFirstInsideEveryGroup() {
        listOf(GameState.Live(), GameState.Scheduled, GameState.Final, GameState.Unknown("new")).forEach { state ->
            val early = game("z", state, 19)
            val late = game("a", state, 21)
            assertEquals(listOf(early, late), ScoresOrdering.order(listOf(late, early)))
        }
    }

    @Test
    fun missingStartLastInsideEveryGroup() {
        listOf(GameState.Live(), GameState.Pre, GameState.Final, GameState.Unknown("new")).forEach { state ->
            val timed = game("z", state)
            val missing = game("a", state).copy(startTimeUtc = null)
            assertEquals(listOf(timed, missing), ScoresOrdering.order(listOf(missing, timed)))
        }
    }

    @Test
    fun equalStartsUseIdRegardlessOfInputOrder() {
        val a = game("a", GameState.Live())
        val z = game("z", GameState.Live())
        assertEquals(listOf(a, z), ScoresOrdering.order(listOf(z, a)))
        assertEquals(listOf(a, z), ScoresOrdering.order(listOf(a, z)))
    }

    @Test
    fun favoritesLeadOnlyWithinTheirStateGroup() {
        val live = game("live", GameState.Live(), 19)
        val favoriteLive = game("favorite-live", GameState.Live(), 21, "TOR")
        val favoriteFinal = game("favorite-final", GameState.Final, 18, "TOR")
        assertEquals(
            listOf(favoriteLive, live, favoriteFinal),
            ScoresOrdering.order(listOf(favoriteFinal, live, favoriteLive), setOf(TeamKey("TOR"))),
        )
    }

    private fun game(
        id: String,
        state: GameState,
        hour: Int = 20,
        home: String = "MTL",
    ): Game =
        Game(
            id = GameId(id),
            date = "2026-10-26",
            state = state,
            home = TeamRef(TeamKey(home)),
            away = TeamRef(TeamKey("BOS")),
            startTimeUtc = Instant.parse("2026-10-26T$hour:00:00Z"),
        )
}
