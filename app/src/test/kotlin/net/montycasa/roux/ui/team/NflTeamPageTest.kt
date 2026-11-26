package net.montycasa.roux.ui.team

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflClubSchedule
import net.montycasa.roux.domain.model.NflSeasonEntry
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant

/** Football's club page: what it states about a season, and what it refuses to state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NflTeamPageTest {
    @get:Rule
    val rule = createComposeRule()

    private val bills = TeamKey(League.NFL, "BUF")

    private fun show(
        state: NflTeamPageUiState,
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                NflTeamPageScreen(
                    state = state,
                    onBack = {},
                    onRetry = onRetry,
                    zone = ZoneId.of("America/New_York"),
                    locale = Locale.US,
                )
            }
        }
    }

    @Test
    fun thePageStatesTheClubsRecordAndItsSeason() {
        show(NflTeamPageUiState(teamKey = bills, team = TeamRef(bills, name = "Buffalo Bills"), season = season(), loading = false))

        rule.onNodeWithTag("nflTeam.name").assertIsDisplayed()
        rule.onNodeWithText("Buffalo Bills").assertIsDisplayed()
        // Two wins, one loss, no tie: football writes the zero tie rather than dropping it.
        rule.onNodeWithText("2-1-0 · 2026").assertIsDisplayed()
    }

    @Test
    fun aWinStatesThisClubsScoreFirst() {
        show(NflTeamPageUiState(teamKey = bills, team = TeamRef(bills), season = season(), loading = false))

        rule.onNodeWithText("W 36–31").assertIsDisplayed()
    }

    @Test
    fun aLossStatesThisClubsScoreFirstRatherThanTheWinners() {
        show(NflTeamPageUiState(teamKey = bills, team = TeamRef(bills), season = season(), loading = false))

        // Buffalo is home and scores 17 against Miami's 24: a loss reads from Buffalo's side, not the winner's.
        rule.onNodeWithText("L 17–24").assertIsDisplayed()
    }

    @Test
    fun theByeIsARowOfItsOwn() {
        show(NflTeamPageUiState(teamKey = bills, team = TeamRef(bills), season = season(), loading = false))

        rule.onNodeWithTag("nflTeam.bye").assertIsDisplayed()
        rule.onNodeWithText("Bye").assertIsDisplayed()
    }

    @Test
    fun anUnplayedGameStatesItsKickoffAndNoScore() {
        show(NflTeamPageUiState(teamKey = bills, team = TeamRef(bills), season = season(), loading = false))

        // 17:00Z on 13 September is 1:00 PM in New York, which is where the reader is.
        rule.onNode(hasText("Sun, Sep 13 · 1:00 PM")).assertIsDisplayed()
    }

    @Test
    fun aFailedSeasonOffersARetry() {
        var retried = 0
        show(
            NflTeamPageUiState(teamKey = bills, team = TeamRef(bills), loading = false, error = LoadError.Network),
            onRetry = { retried++ },
        )

        rule.onNodeWithTag("nflTeam.retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun aSeasonThatHasNotLoadedYetSaysSoRatherThanShowingAnEmptyTable() {
        show(NflTeamPageUiState(teamKey = bills, team = TeamRef(bills), loading = true))

        rule.onNodeWithTag("nflTeam.loading").assertIsDisplayed()
    }

    @Test
    fun aKickoffIsPrintedInTheReadersZone() {
        val instant = Instant.parse("2026-09-13T17:00:00Z")

        assertEquals("Sun, Sep 13 · 1:00 PM", kickoffLabel(instant, ZoneId.of("America/New_York"), Locale.US))
        // The same instant, read from the west coast, is still Sunday morning.
        assertEquals("Sun, Sep 13 · 10:00 AM", kickoffLabel(instant, ZoneId.of("America/Los_Angeles"), Locale.US))
    }

    private fun season(): NflClubSchedule =
        NflClubSchedule(
            seasonYear = 2026,
            team = TeamRef(bills, name = "Buffalo Bills"),
            byeWeek = 2,
            statedByeWeek = 2,
            entries =
                listOf(
                    NflSeasonEntry.Game(1, game(home = "HOU", away = "BUF", homeScore = 31, awayScore = 36)),
                    NflSeasonEntry.Bye(2),
                    NflSeasonEntry.Game(3, game(home = "BUF", away = "MIA", homeScore = 17, awayScore = 24)),
                    NflSeasonEntry.Game(4, game(home = "BUF", away = "KC", state = GameState.Scheduled, finished = false)),
                    NflSeasonEntry.Game(5, game(home = "NYJ", away = "BUF", homeScore = 10, awayScore = 21)),
                ),
        )

    private fun game(
        home: String,
        away: String,
        homeScore: Int? = null,
        awayScore: Int? = null,
        state: GameState = GameState.Final,
        finished: Boolean = true,
    ): ClubGame =
        ClubGame(
            id = GameId(League.NFL, "$home-$away"),
            date = "2026-09-13",
            startTimeUtc = Instant.parse("2026-09-13T17:00:00Z"),
            home = TeamRef(TeamKey(League.NFL, home)),
            away = TeamRef(TeamKey(League.NFL, away)),
            state = state,
            finished = finished,
            homeScore = homeScore,
            awayScore = awayScore,
        )
}
