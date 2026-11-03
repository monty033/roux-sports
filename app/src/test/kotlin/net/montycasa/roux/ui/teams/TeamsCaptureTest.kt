package net.montycasa.roux.ui.teams

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.Season
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.StandingsRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** The test tag on a composed team row; the picker renders all thirty-two once the list has arrived. */
internal val teamRowMatcher =
    SemanticsMatcher("team row") {
        val tag = it.config.getOrNull(SemanticsProperties.TestTag)
        tag?.startsWith("teams.row.") == true && tag.startsWith("teams.row.body.").not()
    }

/**
 * Blocks until the Teams tab has rendered rows, not merely until Compose is idle.
 *
 * The rows arrive from a repository flow outside the composition. `waitForIdle()` settles the
 * composition but cannot see that flow, which is how a capture could land on "Loading teams…".
 * The timeout fails loudly instead of capturing whatever happens to be on screen.
 */
internal fun ComposeTestRule.waitForTeamsRows(timeoutMillis: Long = 10_000) {
    waitUntil(timeoutMillis) {
        onAllNodes(teamRowMatcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * The race made visible: the source does not answer until the test releases it.
 *
 * Without the wait in the capture path, the post-release assertion below runs on the same loading
 * frame this source deliberately holds, so the test fails rather than passing on timing luck.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TeamsCaptureTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theLoadingFrameIsHeldUntilTheSourceDeliversRows() {
        val source = DelayedTeamsSource()
        rule.setContent {
            RouxTheme {
                val teams = remember { TeamsViewModel(source, StubPreferences()) }
                val state by teams.uiState.collectAsState()
                TeamsScreen(state, onToggle = {}, onRetry = {})
            }
        }
        rule.waitUntil(10_000) { source.requested.isCompleted }

        // The frame that used to become a golden by luck: the source has been asked, but has no rows.
        rule.onNodeWithText("Loading teams…").assertIsDisplayed()
        rule.onAllNodes(teamRowMatcher, useUnmergedTree = true).assertCountEquals(0)
        rule.onRoot().captureToImage()

        source.release()

        // The same wait the golden tests use. A bare waitForIdle() here would still show the loading frame.
        rule.waitForTeamsRows()
        rule.onNodeWithText("Loading teams…").assertDoesNotExist()
        rule.onAllNodes(teamRowMatcher, useUnmergedTree = true).assertCountEquals(32)
        rule.onNodeWithTag("teams.row.TOR").assertExists()
    }
}

/** Emits the real fixture rows only after [release], so the pre-load frame is reproducible. */
private class DelayedTeamsSource : StandingsRepository {
    val requested = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()

    override suspend fun standings(anyGameLive: Boolean): Loadable<List<StandingRow>> {
        requested.complete(Unit)
        released.await()
        // A deliberate window after the release: if the test captures without waiting, the row list
        // cannot arrive as a side effect of the assertion settling Compose.
        delay(500)
        return Loadable(teamStandings(), Freshness.Fresh, null)
    }

    override suspend fun seasons(): Loadable<List<Season>> = error("The Teams picker does not read seasons")

    fun release() {
        released.complete(Unit)
    }
}

/** Only favourites and writability are read by [TeamsViewModel]'s init; the rest keeps the interface honest. */
private class StubPreferences : PreferencesRepository {
    override suspend fun getThemeMode() = ThemeMode.System

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun getFavourites(): Set<TeamKey> = emptySet()

    override suspend fun setFavourites(teams: Set<TeamKey>) = Unit

    override suspend fun getFavouritesOnly() = true

    override suspend fun setFavouritesOnly(enabled: Boolean) = Unit

    override suspend fun getLeagueOrder(): List<String> = net.montycasa.roux.domain.policy.LeagueOrder.canonical

    override suspend fun setLeagueOrder(order: List<String>) = Unit

    override suspend fun getTeamAlertPreferences(team: TeamKey) = TeamAlertPreferences()

    override suspend fun setTeamAlertPreferences(
        team: TeamKey,
        preferences: TeamAlertPreferences,
    ) = Unit

    override suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry? = null

    override suspend fun setNotificationLedgerEntry(
        gameId: String,
        entry: NotificationLedgerEntry,
    ) = Unit

    override suspend fun isWritable() = true

    override suspend fun claimGoal(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ) = false

    override suspend fun claimStart(
        gameId: String,
        goalBaseline: Int,
        at: Instant,
    ) = false

    override suspend fun claimBaseline(
        gameId: String,
        goalCount: Int,
        at: Instant,
    ) = false

    override suspend fun pruneNotificationLedger(
        olderThan: Instant,
        keep: Set<String>,
    ) = Unit
}
