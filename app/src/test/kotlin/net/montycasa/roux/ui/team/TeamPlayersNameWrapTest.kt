package net.montycasa.roux.ui.team

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.GoalieStat
import net.montycasa.roux.domain.model.RosterPlayer
import net.montycasa.roux.domain.model.SkaterStat
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pinned name column must show the whole name, wrapping if it has to.
 *
 * This lives in a Robolectric harness at real phone width because the plain Compose harness cannot answer the
 * question: there the type scale is small relative to the column, so a long name lays out on one line and reports
 * one line, and an assertion about wrapping can never fail. Here the width and the type are what a phone has, so a
 * long name either wraps — as this asserts — or it is being cut.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TeamPlayersNameWrapTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun aLongNameWrapsInsteadOfBeingCutOff() {
        rule.setContent {
            RouxTheme {
                Surface {
                    TeamDetailScreen(
                        state =
                            TeamDetailUiState(
                                teamKey = TeamKey("TOR"),
                                isLoading = false,
                                schedule = Loadable(emptyList(), Freshness.Fresh, null),
                                roster = Loadable(roster, Freshness.Fresh, null),
                                stats = Loadable(stats, Freshness.Fresh, null),
                            ),
                        onBack = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("team.tab.Players").performClick()
        rule.waitForIdle()

        val long = rule.onNodeWithTag("team.players.player.13.name").fetchSemanticsNode().boundsInRoot
        val short = rule.onNodeWithTag("team.players.player.91.name").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "a name this long must occupy more than one line: ${long.height} against ${short.height}",
            long.height > short.height + 2f,
        )
    }

    private val roster =
        ClubRoster(
            forwards =
                listOf(
                    RosterPlayer(id = 91, name = "Leo Carlsson", sweaterNumber = 91, positionCode = "C"),
                    RosterPlayer(id = 13, name = LONG_NAME, sweaterNumber = 13, positionCode = "L"),
                ),
            defense = listOf(RosterPlayer(id = 14, name = "Drew Helleson", sweaterNumber = 14, positionCode = "D")),
            goalies = listOf(RosterPlayer(id = 31, name = "Laurent Brossoit", sweaterNumber = 31, positionCode = "G")),
        )

    private val stats =
        ClubStats(
            skaters =
                listOf(
                    SkaterStat(playerId = 91, name = "Leo Carlsson", positionCode = "C", gamesPlayed = 14),
                    SkaterStat(playerId = 13, name = LONG_NAME, positionCode = "L", gamesPlayed = 14),
                ),
            goalies = listOf(GoalieStat(playerId = 31, name = "Laurent Brossoit", gamesPlayed = 1)),
        )

    private companion object {
        const val LONG_NAME = "Jean-Sebastien DeLaurentis-Whitmore MacAllister-Fitzgerald"
    }
}
