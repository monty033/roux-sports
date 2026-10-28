package net.montycasa.roux.ui.scores

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.DateStrip
import net.montycasa.roux.domain.policy.FavoritesHint
import net.montycasa.roux.ui.reach.PrimaryControlTag
import net.montycasa.roux.ui.reach.Reach
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScoresScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val date = "2025-10-25"
    private val index = listOf(DateIndexEntry("2025-10-28", 2), DateIndexEntry(date, 0))
    private val fresh = ScoresUiState(date, today = date, freshness = Freshness.Fresh, dateIndex = index)
    private val game =
        Game(
            id = GameId("2025020123"),
            date = date,
            state = GameState.Scheduled,
            home = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs"),
            away = TeamRef(TeamKey("MTL"), name = "Montréal Canadiens"),
            startTimeUtc = Instant.parse("2025-10-25T23:00:00Z"),
        )
    private val stale =
        fresh.copy(
            rows = listOf(game),
            freshness = Freshness.Stale(Instant.parse("2025-10-25T23:42:00Z")),
        )
    private val suppliedState = mutableStateOf(fresh)
    private val fontScale = mutableStateOf(1f)

    private fun launchScreen(
        state: ScoresUiState = fresh,
        onRefresh: () -> Unit = {},
        onGameClick: (GameId) -> Unit = {},
        onSelectDate: (String) -> Unit = {},
    ) {
        suppliedState.value = state
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale.value)) {
                RouxTheme {
                    ScoresScreen(suppliedState.value, onRefresh, onGameClick, onSelectDate)
                }
            }
        }
    }

    @Test
    fun emptyDateShowsTheExactDayAndNextGamesButtonNavigatesToTheIndexedDate() {
        val selected = mutableListOf<String>()
        launchScreen(onSelectDate = { selected += it })
        rule.onNodeWithText("No games on Sat, Oct 25").assertIsDisplayed()
        rule.onNodeWithText("Next games: Tue, Oct 28").assertIsDisplayed().performClick()
        assertEquals(listOf("2025-10-28"), selected)
    }

    @Test
    fun staleShowsTheDeviceClockAndRetryIsAPrimaryInTheBottomThirtyPercent() {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            var retries = 0
            launchScreen(stale, onRefresh = { retries++ })
            rule.onNodeWithText("Updated", substring = true).assertIsDisplayed()
            // The fixture is an older day, so it carries its date; 23:42Z is 1:42 PM in Honolulu.
            rule.onNodeWithText("Oct 25, 1:42 PM", substring = true).assertIsDisplayed()
            rule.onNodeWithText("Showing saved scores. They may be out of date.").assertIsDisplayed()
            rule.onNodeWithText("Retry").assertIsDisplayed().performClick()
            assertEquals(1, retries)
            assertReach(stale, "scores.retry")
            assertRecoveryInBottomThirtyPercent()
            // A cache from another day carries its date instead of a bare clock that would read as today.
            rule.runOnIdle {
                suppliedState.value = stale.copy(freshness = Freshness.Stale(Instant.parse("2025-12-26T00:42:00Z")))
            }
            rule.onNodeWithText("Updated", substring = true).assertIsDisplayed()
            rule.onNodeWithText("Dec 25", substring = true).assertIsDisplayed()
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun missingShowsAnErrorWithAPrimaryRetryThatRefreshes() {
        var retries = 0
        val missing = ScoresUiState(date, today = date, error = LoadError.Network)
        launchScreen(missing, onRefresh = { retries++ })
        rule.onNodeWithText("Couldn’t load scores.").assertIsDisplayed()
        rule.onNodeWithText("Check your connection and try again.").assertIsDisplayed()
        rule.onNodeWithText("No games on Sat, Oct 25").assertDoesNotExist()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        assertReach(missing, "scores.retry")
        assertRecoveryInBottomThirtyPercent()
    }

    @Test
    fun swipingDownCallsOnRefresh() {
        var refreshes = 0
        launchScreen(fresh.copy(rows = listOf(game)), onRefresh = { refreshes++ })
        rule.onNodeWithTag("scores.refresh").performTouchInput {
            swipe(Offset(center.x, height * 0.1f), Offset(center.x, height * 0.95f), durationMillis = 600)
        }
        rule.waitForIdle()
        assertEquals(1, refreshes)
    }

    @Test
    fun swipingAnEmptyDateAlsoRefreshes() {
        var refreshes = 0
        launchScreen(onRefresh = { refreshes++ })
        rule.onNodeWithTag("scores.refresh").performTouchInput {
            swipe(Offset(center.x, height * 0.1f), Offset(center.x, height * 0.95f), durationMillis = 600)
        }
        rule.waitForIdle()
        assertEquals(1, refreshes)
    }

    @Test
    fun tappingAGameRowCallsOnGameClickWithTheDomainId() {
        val clicked = mutableListOf<GameId>()
        launchScreen(fresh.copy(rows = listOf(game)), onGameClick = { clicked += it })
        rule.onNodeWithTag("scores.game.${game.id.value}").performClick()
        assertEquals(listOf(game.id), clicked)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h923dp-mdpi")
    fun gameCardsAreSeventySixDpWithEightDpPageGaps() {
        val next = game.copy(id = GameId("2025020124"))
        launchScreen(fresh.copy(rows = listOf(game, next)))
        val first = rule.onNodeWithTag("scores.game.${game.id.value}")
        val second = rule.onNodeWithTag("scores.game.${next.id.value}")
        first.assertHeightIsEqualTo(76.dp)
        second.assertHeightIsEqualTo(76.dp)
        val gap = second.getUnclippedBoundsInRoot().top - first.getUnclippedBoundsInRoot().bottom
        assertEquals(8f, gap.value, 0.5f)
        first.assertWidthIsEqualTo(387.dp)
        rule.onAllNodesWithTag(STATE_RAIL_TAG, useUnmergedTree = true)[0].assertWidthIsEqualTo(92.dp)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun gameCardGapEqualsReachMinimumAndTheWholeDayStillScrolls() {
        val games = (0..11).map { game.copy(id = GameId("game-$it")) }
        launchScreen(fresh.copy(rows = games))
        val first = rule.onNodeWithTag("scores.game.${games[0].id.value}")
        val second = rule.onNodeWithTag("scores.game.${games[1].id.value}")
        first.assertHeightIsEqualTo(76.dp).assertWidthIsEqualTo(336.dp)
        rule.onAllNodesWithTag(STATE_RAIL_TAG, useUnmergedTree = true)[0].assertWidthIsEqualTo(82.dp)
        rule.onAllNodesWithTag(CREST_TAG, useUnmergedTree = true)[0].assertWidthIsEqualTo(32.dp)
        val gap = second.getUnclippedBoundsInRoot().top - first.getUnclippedBoundsInRoot().bottom
        assertEquals(Reach.MIN_TARGET_GAP_DP, gap.value, 0.01f)
        listOf(1f, 2f).forEach { scale ->
            rule.runOnIdle { fontScale.value = scale }
            rule.onNodeWithTag("scores.list").performScrollToNode(hasTestTag("scores.game.${games.last().id.value}"))
            rule.onNodeWithTag("scores.game.${games.last().id.value}").assertIsDisplayed()
            val last = rule.onNodeWithTag("scores.game.${games.last().id.value}").getUnclippedBoundsInRoot()
            val list = rule.onNodeWithTag("scores.list").getUnclippedBoundsInRoot()
            assertTrue(last.top >= list.top && last.bottom <= list.bottom)
        }
    }

    @Test
    fun dateSelectionForwardsIntentAndTheShelfHasNoFavouritesToggle() {
        val selected = mutableListOf<String>()
        launchScreen(onSelectDate = { selected += it })
        rule.onNode(SemanticsMatcher.expectValue(PrimaryControlTag, "scores.dateChip.2025-10-26")).performClick()
        assertEquals(listOf("2025-10-26"), selected)
        rule.onNodeWithContentDescription("Favourites only").assertDoesNotExist()
        rule.onNode(SemanticsMatcher.expectValue(PrimaryControlTag, "scores.favourites")).assertDoesNotExist()
    }

    @Test
    fun failedRefreshKeepsRowsAndShowsANonBlockingBannerForEveryError() {
        launchScreen(fresh.copy(rows = listOf(game)))
        val errors =
            listOf(
                LoadError.Network,
                LoadError.Http(503),
                LoadError.Parse,
                LoadError.MismatchedDay(date, "2025-10-26"),
            )
        errors.forEach { error ->
            rule.runOnIdle { suppliedState.value = stale.copy(error = error) }
            rule.onNodeWithTag("scores.game.${game.id.value}").assertIsDisplayed()
            rule.onNodeWithText("Updated", substring = true).assertIsDisplayed()
            rule.onNodeWithText("7:42 PM", substring = true).assertIsDisplayed()
            rule.onNodeWithText("Couldn’t refresh. Showing saved scores.").assertIsDisplayed()
            rule.onNodeWithTag("scores.error").assertDoesNotExist()
            rule.onNodeWithText("Retry").assertIsDisplayed()
        }
        rule.runOnIdle { suppliedState.value = fresh.copy(rows = listOf(game)) }
        rule.onNodeWithText("7:42 PM", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Retry").assertDoesNotExist()
        rule.onNodeWithTag("scores.game.${game.id.value}").assertIsDisplayed()
    }

    @Test
    fun anErrorWithFreshOrMissingMetadataStillDoesNotHideExistingRows() {
        launchScreen(fresh.copy(rows = listOf(game), error = LoadError.Network))
        listOf(Freshness.Fresh, Freshness.Missing).forEach { freshness ->
            rule.runOnIdle { suppliedState.value = fresh.copy(rows = listOf(game), freshness = freshness, error = LoadError.Network) }
            rule.onNodeWithTag("scores.game.${game.id.value}").assertIsDisplayed()
            rule.onNodeWithText("Couldn’t refresh. Showing saved scores.").assertIsDisplayed()
            rule.onNodeWithTag("scores.error").assertDoesNotExist()
        }
    }

    @Test
    fun favouriteHintsDistinguishUnpickedTeamsFromTeamsWithoutGames() {
        launchScreen(fresh.copy(rows = listOf(game), favoritesHint = FavoritesHint.PickTeams))
        rule.onNodeWithText("You haven’t picked favourite teams yet. Choose teams in the Teams tab.").assertIsDisplayed()
        rule.onNodeWithTag("scores.game.${game.id.value}").assertIsDisplayed()
        rule.runOnIdle { suppliedState.value = fresh.copy(favoritesHint = FavoritesHint.NoFavouriteGames) }
        rule
            .onNodeWithText(
                "Your favourite teams have no games this day. Choose NHL in the leagues drawer to see all games.",
            ).assertIsDisplayed()
        rule.onNodeWithText("You haven’t picked favourite teams yet. Choose teams in the Teams tab.").assertDoesNotExist()
        rule.onNodeWithText("No games on Sat, Oct 25").assertDoesNotExist()
        rule.onNodeWithText("Next games: Tue, Oct 28").assertDoesNotExist()
    }

    @Test
    fun anEmptyIndexDoesNotInventANextGamesButton() {
        launchScreen(fresh.copy(dateIndex = emptyList()))
        rule.onNodeWithText("No games on Sat, Oct 25").assertIsDisplayed()
        rule.onNodeWithText("Next games:", substring = true).assertDoesNotExist()
    }

    @Test
    fun initialLoadingDoesNotClaimAnEmptyDayOrAnErrorAndRefreshingPreservesRows() {
        launchScreen(ScoresUiState(date, today = date, isRefreshing = true))
        rule.onNodeWithText("Loading scores…").assertIsDisplayed()
        rule.onNodeWithTag("scores.error").assertDoesNotExist()
        rule.onNodeWithText("No games on Sat, Oct 25").assertDoesNotExist()
        rule.runOnIdle { suppliedState.value = stale.copy(isRefreshing = true, error = LoadError.Network) }
        rule.onNodeWithTag("scores.game.${game.id.value}").assertIsDisplayed()
        rule.onNodeWithText("Refreshing…").assertIsNotEnabled()
    }

    @Test
    fun reachPassesOnTheReferencePhoneAtNormalAndDoubleType() = auditStatesAtBothFontScales()

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-420dpi")
    fun reachPassesOnThePlanSmallPhoneAtNormalAndDoubleType() = auditStatesAtBothFontScales()

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-420dpi")
    fun reachPassesOnTheShellSmallPhoneAtNormalAndDoubleType() = auditStatesAtBothFontScales()

    private fun auditStatesAtBothFontScales() {
        launchScreen()
        listOf(1f, 2f).forEach { scale ->
            rule.runOnIdle { fontScale.value = scale }
            val states =
                listOf(
                    fresh to "scores.nextGames",
                    stale to "scores.retry",
                    stale.copy(error = LoadError.Network) to "scores.retry",
                    ScoresUiState(date, today = date, error = LoadError.Network) to "scores.retry",
                    fresh.copy(rows = listOf(game)) to null,
                    fresh.copy(favoritesHint = FavoritesHint.PickTeams) to "scores.nextGames",
                    fresh.copy(favoritesHint = FavoritesHint.NoFavouriteGames) to null,
                )
            states.forEach { (state, action) ->
                rule.runOnIdle { suppliedState.value = state }
                assertReach(state, action)
                if (action == "scores.retry") assertRecoveryInBottomThirtyPercent()
            }
        }
    }

    private fun assertReach(
        state: ScoresUiState,
        action: String? = null,
    ) {
        // Derived from the same policy call the screen makes, rather than a hand-pinned window: pinning the
        // window here let the screen be narrowed to three days to satisfy this fixture instead of the plan.
        val chips = DateStrip.items(state.dateIndex, state.selectedDate).map { "scores.dateChip.${it.date}" }
        val primaries = chips + listOfNotNull("scores.calendar", action)
        rule.assertPrimaryControlsReachable(*primaries.toTypedArray())
        val frequent = rule.reachTargets().filter { it.primary && (it.tag.startsWith("scores.dateChip.") || it.tag == "scores.calendar") }
        assertEquals(chips.size + 1, frequent.size)
        assertTrue(frequent.all { it.frequent })
        assertTrue(rule.reachTargets().any { it.tag == "scores.pullToRefresh" && !it.primary })
    }

    private fun assertRecoveryInBottomThirtyPercent() {
        val retry = rule.reachTargets().single { it.tag == "scores.retry" }
        val heightDp =
            rule
                .onRoot()
                .fetchSemanticsNode()
                .size.height / rule.density.density
        assertTrue("Retry must be primary", retry.primary)
        assertTrue("Retry starts at ${retry.rawTop} in $heightDp dp", retry.rawTop >= heightDp * Reach.FREQUENT_TOP_MAX_FRACTION)
    }
}
