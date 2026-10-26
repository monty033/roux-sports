package net.montycasa.roux.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies feed status conversion and whether each lifecycle state represents a live game. */
class GameStateTest {
    @Test
    fun periodRunningDefaultsToNull() {
        assertNull(PeriodInfo(2, "REG", "12:34").running)
    }

    @Test
    fun fromFeedMapsKnownStatesAndPreservesUnknownInput() {
        val cases =
            listOf(
                "LIVE" to GameState.Live(critical = false),
                "CRIT" to GameState.Live(critical = true),
                "FUT" to GameState.Scheduled,
                "PRE" to GameState.Pre,
                "FINAL" to GameState.Final,
                "OFF" to GameState.Final,
                "live" to GameState.Unknown("live"),
                "WAT" to GameState.Unknown("WAT"),
                "" to GameState.Unknown(""),
                " \t\n " to GameState.Unknown(" \t\n "),
                "  WAT\t" to GameState.Unknown("  WAT\t"),
            )

        cases.forEach { (raw, expected) ->
            assertEquals("gameState input: <$raw>", expected, GameState.fromFeed(raw))
        }
    }

    @Test
    fun fromFeedMatchesKnownStatesWithSurroundingWhitespace() {
        val cases =
            listOf(
                " \tLIVE\n" to GameState.Live(critical = false),
                " LIVE " to GameState.Live(critical = false),
                " CRIT " to GameState.Live(critical = true),
                " FUT " to GameState.Scheduled,
                " PRE " to GameState.Pre,
                " FINAL " to GameState.Final,
                " OFF " to GameState.Final,
            )

        cases.forEach { (raw, expected) ->
            assertEquals("gameState input: <$raw>", expected, GameState.fromFeed(raw))
        }
    }

    @Test
    fun finalKindMatchesCaseSensitivelyAndRejectsMissingOrUnknownInput() {
        val cases =
            listOf(
                "OT" to FinalKind.OT,
                "SO" to FinalKind.SO,
                "REG" to FinalKind.REG,
                " \tOT\n" to FinalKind.OT,
                " SO " to FinalKind.SO,
                " REG " to FinalKind.REG,
                "ot" to null,
                "sO" to null,
                "reg" to null,
                " \tOt\n" to null,
                " so " to null,
                " Reg " to null,
                null to null,
                "" to null,
                " \t\n " to null,
                "WAT" to null,
            )

        cases.forEach { (raw, expected) ->
            assertEquals("final kind input: <$raw>", expected, FinalKind.from(raw))
        }
    }

    @Test
    fun isLiveIsTrueOnlyForLiveStates() {
        val cases =
            listOf(
                GameState.Live(critical = false) to true,
                GameState.Live(critical = true) to true,
                GameState.Scheduled to false,
                GameState.Pre to false,
                GameState.Final to false,
                GameState.Unknown("WAT") to false,
                GameState.Unknown("LIVE") to false,
            )
        val games =
            cases.mapIndexed { index, (state, _) ->
                Game(
                    id = GameId(index.toString()),
                    date = "2026-10-26",
                    state = state,
                    home = TeamRef(TeamKey("NYR")),
                    away = TeamRef(TeamKey("LAK")),
                )
            }

        assertEquals(cases.map { it.second }, games.map { it.isLive })
    }
}
