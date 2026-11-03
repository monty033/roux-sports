package net.montycasa.roux.ui.drawer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.launch
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ScoreDay
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.ThemeMode
import net.montycasa.roux.domain.repo.NotificationLedgerEntry
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import net.montycasa.roux.ui.reach.findReachViolations
import net.montycasa.roux.ui.reach.reachTargets
import net.montycasa.roux.ui.scores.DrawerUi
import net.montycasa.roux.ui.scores.ScoresUiState
import net.montycasa.roux.ui.scores.ScoresViewModel
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LeagueDrawerTest {
    @get:Rule val rule = createComposeRule()
    private val state =
        mutableStateOf(
            ScoresUiState("2026-10-27", today = "2026-10-27", favouritesOnly = false, favouriteCount = 6, loadedDate = "2026-10-27"),
        )

    private lateinit var sheet: LeaguesSheetState
    private var closeSheet: () -> Unit = {}
    private var openSheet: () -> Unit = {}
    private var openToCap: () -> Unit = {}
    private val stores = mutableListOf<ViewModelStore>()
    private val navigations = mutableListOf<String>()
    private var settingsOpened = 0

    @After fun clearViewModels() {
        stores.forEach { it.clear() }
    }

    /** The bar's height in the harness: the sheet is laid out above it and never over it. */

    private fun launch(
        dark: Boolean = false,
        fontScale: Float = 1f,
        catalog: List<DrawerLeague> = drawerCatalog,
        preferences: DrawerPreferences? = null,
        atCap: Boolean = true,
    ) {
        val vm =
            preferences?.let {
                ScoresViewModel(
                    object : ScoresRepository {
                        override suspend fun loadDay(
                            date: String,
                            today: String,
                        ): Loadable<ScoreDay> = Loadable(ScoreDay(date, emptyList(), emptyList(), 0), Freshness.Fresh, null)

                        override suspend fun prefetchAdjacent(
                            date: String,
                            today: String,
                        ) = Unit
                    },
                    it,
                    object : Clock {
                        override fun now(): Instant = Instant.parse("2026-10-27T16:00:00Z")
                    },
                ).also { model -> stores += ViewModelStore().apply { put("scores", model) } }
            }
        rule.setContent {
            RouxTheme(dark) {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    sheet = remember { LeaguesSheetState() }
                    val scope = rememberCoroutineScope()
                    closeSheet = {
                        scope.launch { animate(sheet.reveal, 0f, animationSpec = tween(0)) { value, _ -> sheet.set(value) } }
                    }
                    openSheet = {
                        scope.launch { animate(sheet.reveal, sheet.capPx, animationSpec = tween(0)) { value, _ -> sheet.set(value) } }
                    }
                    openToCap = {
                        scope.launch { animate(sheet.reveal, sheet.capPx, animationSpec = tween(0)) { value, _ -> sheet.set(value) } }
                    }
                    androidx.compose.runtime.LaunchedEffect(sheet.isOpen) {
                        if (!sheet.isOpen) editing.value = false
                    }
                    val shown = vm?.uiState?.collectAsState()?.value ?: state.value
                    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF08090B) else Color(0xFFF2F2EF))) {
                        BottomLeaguesSheet(
                            state = sheet,
                            onDismissRequest = { closeSheet() },
                        ) {
                            LeaguesPanel(
                                drawer =
                                    DrawerUi(
                                        favouriteCount = shown.favouriteCount,
                                        favouritesOnly = shown.favouritesOnly,
                                        leagueOrder = shown.leagueOrder,
                                        selectedLeagueId = shown.selectedLeagueId,
                                    ),
                                seasonDate = shown.loadedDate,
                                editing = editing.value,
                                onEditing = { editing.value = it },
                                onLeagueOrder = { order ->
                                    if (vm != null) vm.setLeagueOrder(order) else state.value = state.value.copy(leagueOrder = order)
                                },
                                catalog = catalog,
                                onLeagueSelected = { id ->
                                    navigations += id
                                    state.value = state.value.copy(favouritesOnly = false, selectedLeagueId = id)
                                    closeSheet()
                                },
                                onFavouritesOnly = { only ->
                                    state.value = state.value.copy(favouritesOnly = only)
                                    closeSheet()
                                },
                                onSettings = { settingsOpened++ },
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        // The anchors are measured during layout, so the sheet is opened once they exist.
        rule.runOnIdle { if (atCap) openToCap() else openSheet() }
        rule.waitForIdle()
    }

    private val editing = mutableStateOf(false)

    @After fun resetEditing() {
        editing.value = false
    }

    @Test
    fun thePanelPaintsTheBrandSurfaceAndKeepsMeasuredTargets() {
        launch()
        rule.onNodeWithTag("leagues.sheet").assertWidthIsEqualTo(411.dp)
        val pixels = rule.onNodeWithTag("leagues.sheet").captureToImage().toPixelMap()
        assertEquals(Color(0xFF08090B), pixels[20, 20])
        rule.onNodeWithTag("leagues.myTeams").assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("leagues.nhl").assertHeightIsEqualTo(56.dp).assertIsSelected()
        rule.onNodeWithText("2026–27").assertExists()
        assertPanelReach()
    }

    @Test
    fun theSheetIsOutOrParkedAndNothingInBetween() {
        launch()
        // It rests at the cap, not at a fraction of it: there is no half-open menu to get stuck in.
        assertEquals(sheet.capPx, sheet.reveal, 1f)
        assertEquals(listOf(0f, sheet.capPx), sheet.anchors())
        // A little travel and a release fall back to parked; past halfway it opens.
        sheet.set(sheet.capPx * 0.3f)
        assertEquals(0f, sheet.target(0f), 0.5f)
        sheet.set(sheet.capPx * 0.7f)
        assertEquals(sheet.capPx, sheet.target(0f), 0.5f)
        sheet.set(sheet.capPx * 0.5f)
        assertTrue(sheet.target(0f) == 0f || sheet.target(0f) == sheet.capPx)
    }

    @Test
    fun theSheetStopsBelowTheTopOfTheWindow() {
        launch()
        val rootHeightPx =
            rule
                .onRoot()
                .fetchSemanticsNode()
                .size.height
                .toFloat()
        assertTrue(sheet.capPx < rootHeightPx * 0.6f)
        val sheetTop = rule.onNodeWithTag("leagues.sheet").getUnclippedBoundsInRoot().top
        assertTrue("sheet top was ${sheetTop.value}", sheetTop.value > 300f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun thePanelSurvivesTheSmallPhoneAtDoubleType() {
        launch(fontScale = 2f)
        rule.onNodeWithTag("leagues.myTeams").assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("leagues.heading").assertHeightIsEqualTo(48.dp)
        assertPanelReach()
    }

    private fun assertPanelReach() {
        // Audit the panel's targets. The sheet itself carries no click action, and the scrim is the dismiss area
        // rather than a panel target.
        val targets = rule.reachTargets().filter { it.tag.startsWith("leagues.") && it.tag != "leagues.scrim" }
        assertEquals(listOf("leagues.myTeams", "leagues.edit", "leagues.nhl", "leagues.settings"), targets.map { it.tag })
        assertTrue(targets.none { it.primary })
        val root = rule.onRoot().fetchSemanticsNode().size
        val violations =
            findReachViolations(
                targets,
                windowWidthDp = root.width / rule.density.density,
                windowHeightDp =
                    root.height / rule.density.density,
            )
        assertTrue(violations.joinToString { it.describe() }, violations.isEmpty())
    }

    @Test
    fun myTeamsCarriesTheCurrentCountSelectsFavouritesAndClosesTheSheet() {
        launch()
        rule.onNodeWithText("6", useUnmergedTree = true).assertExists()
        rule.runOnIdle { state.value = state.value.copy(favouriteCount = 7) }
        rule.onNodeWithText("7", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("leagues.myTeams").performClick()
        rule.waitForIdle()
        assertEquals(true, state.value.favouritesOnly)
        // Closed means gone, not merely off screen: a parked sheet composes nothing.
        rule.onNodeWithTag("leagues.sheet").assertDoesNotExist()
    }

    @Test
    fun nhlSelectsAllGamesAndClosesTheSheet() {
        state.value = state.value.copy(favouritesOnly = true)
        launch()
        rule.onNodeWithTag("leagues.nhl").performClick()
        rule.waitForIdle()
        assertEquals(false, state.value.favouritesOnly)
        rule.onNodeWithTag("leagues.sheet").assertDoesNotExist()
    }

    @Test
    fun settingsGearSitsInTheBottomRightOpensSettingsAndMeetsTheTargetFloor() {
        settingsOpened = 0
        launch()
        val sheet = rule.onNodeWithTag("leagues.sheet").getUnclippedBoundsInRoot()
        val gear = rule.onNodeWithTag("leagues.settings")
        gear.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val bounds = gear.getUnclippedBoundsInRoot()
        assertTrue(
            "gear left ${bounds.left} is not in the right half of the drawer",
            bounds.left > sheet.left + (sheet.right - sheet.left) / 2,
        )
        assertTrue(
            "gear top ${bounds.top} is not in the bottom half of the drawer",
            bounds.top > sheet.top + (sheet.bottom - sheet.top) / 2,
        )
        gear.performClick()
        assertEquals("the gear opens the Settings screen", 1, settingsOpened)
    }

    @Test
    fun settingsGearSpeaksTheSettingsTitle() {
        launch()
        rule.onNodeWithTag("leagues.settings").assertContentDescriptionEquals("Settings")
    }

    @Test
    fun theOldSettingsTextRowIsGone() {
        launch()
        // The footer text is still there, but the row that drew the word Settings is not: a gear with a
        // content description is not a Text node.
        rule.onNodeWithText("Settings").assertDoesNotExist()
        rule.onNodeWithText("NO ADS · NO TRACKERS · GPL–3.0").assertExists()
    }

    @Test
    fun pencilEntersEditModeAndDoneRestoresNavigation() {
        launch()
        rule.onNodeWithText("Edit", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithText("Tap Edit to hide or reorder leagues").assertDoesNotExist()
        assertHandles(emptyList())
        rule
            .onNodeWithContentDescription("Edit leagues")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        rule.onNodeWithContentDescription("Edit leagues").assertDoesNotExist()
        rule.onNodeWithText("DONE").assertExists()
        assertHandles(listOf("nhl", "nfl", "mlb"))
        rule.onNodeWithTag("leagues.nhl").assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("leagues.heading").assertHeightIsEqualTo(48.dp)
        rule.onNodeWithText("DONE").performClick()
        assertHandles(emptyList())
        rule.onNodeWithText("DONE").assertDoesNotExist()
        rule.onNodeWithContentDescription("Edit leagues").assertExists()
        rule.onNodeWithTag("leagues.nhl").performClick()
        assertEquals(listOf("nhl"), navigations)
        rule.waitForIdle()
        assertEquals(0f, sheet.reveal, 0.5f)
    }

    @Test
    fun editModeSuppressesLeagueSelectionAndUnavailableRowsStayInert() {
        launch(catalog = twoAvailable)
        assertHandles(emptyList())
        rule.onNodeWithTag("leagues.mlb").assertIsNotEnabled().assertHasNoClickAction()
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        // Every league is arrangeable, including the greyed ones: having no data does not mean it is not yours
        // to order, and NFL/MLB rows are the only content that makes the mode testable today.
        assertHandles(listOf("nhl", "nfl", "mlb"))
        listOf("nhl", "nfl", "mlb").forEach { id ->
            rule.onNodeWithTag("leagues.$id").assertHasNoClickAction().performTouchInput { click() }
        }
        assertEquals(emptyList<String>(), navigations)
        assertEquals("nhl", state.value.selectedLeagueId)
        assertTrue(sheet.isOpen)
        rule.onNodeWithText("DONE").performClick()
        assertHandles(emptyList())
        rule.onNodeWithTag("leagues.mlb").assertIsNotEnabled().assertHasNoClickAction()
        rule.onNodeWithTag("leagues.nfl").performClick()
        assertEquals(listOf("nfl"), navigations)
        assertEquals("nfl", state.value.selectedLeagueId)
    }

    @Test
    fun handleDragCrossesHalfARowWritesPreferencesAndReopensInSavedOrder() {
        val preferences = DrawerPreferences()
        launch(catalog = twoAvailable, preferences = preferences)
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        val handle = rule.onNodeWithTag("leagues.nfl.handle")
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -27f))
        }
        rule.runOnIdle { assertTrue(preferences.writes.isEmpty()) }
        handle.performTouchInput { moveBy(Offset(0f, -3f)) }
        rule.runOnIdle { assertEquals(listOf(listOf("nfl", "nhl", "mlb")), preferences.writes) }
        assertHandles(listOf("nhl", "nfl", "mlb"))
        handle.performTouchInput { up() }
        assertOrder("nfl", "nhl", "mlb")
        assertEquals(emptyList<String>(), navigations)
        rule.runOnIdle { closeSheet() }
        rule.waitForIdle()
        rule.runOnIdle { openToCap() }
        rule.waitForIdle()
        assertOrder("nfl", "nhl", "mlb")
        rule.onNodeWithContentDescription("Edit leagues").assertExists()
        rule.onNodeWithText("DONE").assertDoesNotExist()
        assertHandles(emptyList())
    }

    @Test
    fun heldHandleCanCrossMultipleRowsAndReverseDuringTheSameGesture() {
        val preferences = DrawerPreferences()
        launch(catalog = drawerCatalog.map { it.copy(available = true) }, preferences = preferences)
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        assertHandles(listOf("nhl", "nfl", "mlb"))
        val handle = rule.onNodeWithTag("leagues.mlb.handle")
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, -90f))
        }
        rule.runOnIdle {
            assertEquals(listOf(listOf("nhl", "mlb", "nfl"), listOf("mlb", "nhl", "nfl")), preferences.writes)
        }
        handle.performTouchInput {
            moveBy(Offset(0f, 90f))
            up()
        }
        assertOrder("nhl", "nfl", "mlb")
        rule.runOnIdle { assertEquals(listOf("nhl", "nfl", "mlb"), preferences.writes.last()) }
        assertEquals(emptyList<String>(), navigations)
    }

    @Test
    fun draggingAHandleWithoutHoldingDoesNotReorderOrNavigate() {
        val preferences = DrawerPreferences()
        launch(catalog = twoAvailable, preferences = preferences)
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        rule.onNodeWithTag("leagues.nfl.handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, -56f))
            up()
        }
        assertOrder("nhl", "nfl", "mlb")
        rule.runOnIdle { assertTrue(preferences.writes.isEmpty()) }
        assertEquals(emptyList<String>(), navigations)
    }

    @Test
    fun storedOrderLoadsInTheSavedOrderForEveryLeague() {
        // Rows follow the saved order rather than being regrouped by data availability: the panel shows one list,
        // and a league the user moved to the top stays there whether or not its feed is live yet.
        launch(catalog = twoAvailable, preferences = DrawerPreferences(listOf("mlb", "nfl", "nhl")))
        assertOrder("mlb", "nfl", "nhl")
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        assertOrder("mlb", "nfl", "nhl")
        assertHandles(listOf("nhl", "nfl", "mlb"))
    }

    @Test
    fun tappingTheScrimClosesTheSheetAndDiscardsEditMode() {
        launch()
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        rule.onNodeWithText("DONE").assertExists()
        rule.onNodeWithTag("leagues.scrim").performTouchInput { click(Offset(20f, 20f)) }
        rule.waitForIdle()
        assertEquals(0f, sheet.reveal, 0.5f)
        rule.onNodeWithTag("leagues.sheet").assertDoesNotExist()
        rule.runOnIdle { openToCap() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Edit leagues").assertExists()
        rule.onNodeWithText("DONE").assertDoesNotExist()
        assertHandles(emptyList())
    }

    @Test
    fun myTeamsHasALeadingSeventeenDpStarInItsOwnInkAndKeepsTheCount() {
        launch()
        val star = rule.onNodeWithTag("leagues.myTeams.star", useUnmergedTree = true)
        star.assertWidthIsEqualTo(17.dp).assertHeightIsEqualTo(17.dp)
        val starBounds = star.getUnclippedBoundsInRoot()
        val labelBounds = rule.onNodeWithText("My Teams", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val countBounds = rule.onNodeWithText("6", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(starBounds.right < labelBounds.left)
        assertTrue(labelBounds.right < countBounds.left)
        val pixels = star.captureToImage().toPixelMap()
        assertEquals(Color(0xFFEDEEF1), pixels[8, 8])
    }

    private fun assertHandles(ids: List<String>) {
        listOf("nhl", "nfl", "mlb").forEach { id ->
            val handle = rule.onNodeWithTag("leagues.$id.handle")
            if (id in ids) handle.assertExists() else handle.assertDoesNotExist()
        }
    }

    private fun assertOrder(vararg ids: String) {
        val bounds = ids.map { rule.onNodeWithTag("leagues.$it").getUnclippedBoundsInRoot() }
        bounds.zipWithNext().forEach { (above, below) -> assertTrue("expected ${ids.toList()}", above.top < below.top) }
        ids.forEach { rule.onNodeWithTag("leagues.$it").assertHeightIsEqualTo(56.dp) }
        rule.onNodeWithTag("leagues.heading").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun unsupportedLeaguesAreDeliberatelyAbsent() {
        launch()
        // Drawn but not offered: the file lists these leagues, the app has no data for them yet, so they
        // are greyed (.ditem.hid) and disabled rather than hidden or, worse, tappable into an empty day.
        listOf("NFL" to "FOOTBALL", "MLB" to "BASEBALL").forEach { (league, sport) ->
            rule.onNodeWithText(league).assertExists()
            rule.onNodeWithText(sport).assertExists()
            rule.onNodeWithTag("leagues.${league.lowercase()}").assertIsNotEnabled()
        }
        // Leagues the app has never mentioned stay absent.
        listOf("NBA", "WNBA", "MLS").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun seasonIsOmittedUntilADayIsLoaded() {
        state.value = state.value.copy(loadedDate = null)
        launch()
        rule.onNodeWithTag("leagues.season").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(loadedDate = "bad-date") }
        rule.onNodeWithTag("leagues.season").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(loadedDate = "2027-01-04") }
        rule.onNodeWithText("2026–27").assertExists()
    }

    @Test fun sheetLight() {
        launch()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/sheet-light.png")
    }

    @Test fun sheetDark() {
        launch(dark = true)
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/sheet-dark.png")
    }

    @Test fun sheetEditLight() {
        launch()
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/sheet-edit-light.png")
    }

    @Test fun sheetEditDark() {
        launch(dark = true)
        rule.onNodeWithContentDescription("Edit leagues").performClick()
        rule.onRoot().captureRoboImage("src/test/screenshots/drawer/sheet-edit-dark.png")
    }
}

private val twoAvailable = drawerCatalog.map { if (it.id == "nfl") it.copy(available = true) else it }

private class DrawerPreferences(
    var order: List<String> = listOf("nhl", "nfl", "mlb"),
) : PreferencesRepository {
    override suspend fun getThemeMode() = ThemeMode.System

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    val writes = mutableListOf<List<String>>()

    override suspend fun getLeagueOrder(): List<String> = order

    override suspend fun setLeagueOrder(order: List<String>) {
        this.order = order
        writes += order
    }

    override suspend fun getFavourites(): Set<TeamKey> = emptySet()

    override suspend fun setFavourites(teams: Set<TeamKey>) = Unit

    override suspend fun getFavouritesOnly(): Boolean = false

    override suspend fun setFavouritesOnly(enabled: Boolean) = Unit

    override suspend fun getTeamAlertPreferences(team: TeamKey): TeamAlertPreferences = TeamAlertPreferences()

    override suspend fun setTeamAlertPreferences(
        team: TeamKey,
        preferences: TeamAlertPreferences,
    ) = Unit

    override suspend fun getNotificationLedgerEntry(gameId: String): NotificationLedgerEntry? = null

    override suspend fun setNotificationLedgerEntry(
        gameId: String,
        entry: NotificationLedgerEntry,
    ) = Unit

    override suspend fun isWritable(): Boolean = true

    // This fake models settings, not the notification ledger: nothing here ever claims an alert.
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
