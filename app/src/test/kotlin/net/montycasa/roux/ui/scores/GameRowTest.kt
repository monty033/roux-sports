package net.montycasa.roux.ui.scores

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.model.Broadcast
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
import net.montycasa.roux.ui.theme.RouxDarkAccents
import net.montycasa.roux.ui.theme.RouxDarkColors
import net.montycasa.roux.ui.theme.RouxLightAccents
import net.montycasa.roux.ui.theme.RouxLightColors
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameRowTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone
    private lateinit var density: Density

    private val scheduled =
        Game(
            id = GameId("2026020001"),
            date = "2026-10-26",
            state = GameState.Scheduled,
            away = TeamRef(TeamKey("BOS"), name = "Boston Bruins"),
            home = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs"),
            startTimeUtc = Instant.parse("2026-10-26T23:00:00Z"),
        )
    private val live =
        scheduled.copy(
            state = GameState.Live(),
            awayScore = 2,
            homeScore = 3,
            period = PeriodInfo(ordinal = 2, periodType = "REG", clock = "12:34"),
        )
    private val finalOt = live.copy(state = GameState.Final, finalKind = FinalKind.OT)

    @Before
    fun setTorontoTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        // java.util.TimeZone's factory is getTimeZone (unlike kotlinx.datetime.TimeZone.of).
        TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun launch(
        game: Game = scheduled,
        dark: Boolean = false,
        fontScale: Float = 1f,
        modifier: Modifier = Modifier,
    ) {
        rule.setContent {
            RouxTheme(dark = dark) {
                density = Density(LocalDensity.current.density, fontScale)
                CompositionLocalProvider(LocalDensity provides density) {
                    BoxWithConstraints {
                        GameRow(game, narrow = maxWidth <= 380.dp, modifier = modifier.testTag("game-row"))
                    }
                }
            }
        }
    }

    @Test
    fun liveRetainsScoreRowCapsuleTag() {
        launch(live)
        rule.onNodeWithTag(STATE_CAPSULE_TAG, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("detail.hero.live.capsule", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun scheduledShowsTorontoTimeInTwelveHourFormat() {
        launch()
        rule.onNodeWithText("7:00 PM").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertDoesNotExist()
    }

    @Test
    fun scheduledUsesTheDeviceClockAndCarriesNoZoneLabel() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        launch()
        // 23:00Z is 08:00 the next morning in Tokyo. The device clock decides, and nothing appends "ET".
        rule.onNodeWithText("8:00 AM").assertIsDisplayed()
        rule.onNodeWithText("7:00 PM").assertDoesNotExist()
    }

    @Test
    fun scheduledUsesTorontoWinterOffset() {
        launch(scheduled.copy(startTimeUtc = Instant.parse("2026-12-01T00:00:00Z")))
        rule.onNodeWithText("7:00 PM").assertIsDisplayed()
    }

    @Test
    fun liveShowsPeriodClockAndLiveDescription() {
        launch(live)
        rule.onNodeWithText("2nd 12:34").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertExists()
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun finalOvertimeShowsFinalOtAndNoLiveIndicatorDespiteRetainedClock() {
        launch(finalOt)
        rule.onNodeWithText("FINAL · OT").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertDoesNotExist()
        rule.onNodeWithText("12:34").assertDoesNotExist()
    }

    @Test
    fun finalShootoutShowsFinalSo() {
        launch(finalOt.copy(finalKind = FinalKind.SO))
        rule.onNodeWithText("FINAL · SO").assertIsDisplayed()
    }

    @Test
    fun finalRegulationShowsPlainFinalWithoutOt() {
        launch(finalOt.copy(finalKind = FinalKind.REG))
        rule.onNodeWithText("FINAL").assertIsDisplayed()
        rule.onNodeWithText("OT", substring = true).assertDoesNotExist()
    }

    @Test
    fun finalWithoutKindShowsPlainFinal() {
        launch(finalOt.copy(finalKind = null))
        rule.onNodeWithText("FINAL").assertIsDisplayed()
    }

    /**
     * A 12-hour clock is a locale default, not a house style: a user who chose 24-hour time keeps it.
     * Forcing the "hma" skeleton showed 7:00 PM to everyone, which is what this pins shut.
     */
    @Test
    fun scheduledHonoursTheDeviceTwentyFourHourSetting() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        android.provider.Settings.System
            .putString(resolver, android.provider.Settings.System.TIME_12_24, "24")
        try {
            launch(scheduled)
            rule.onNodeWithText("19:00").assertIsDisplayed()
            rule.onNodeWithText("7:00 PM").assertDoesNotExist()
        } finally {
            android.provider.Settings.System
                .putString(resolver, android.provider.Settings.System.TIME_12_24, "12")
        }
    }

    @Test
    fun nullLogoShowsAbbreviationBadge() {
        launch()
        rule.onNodeWithText("BOS").assertIsDisplayed()
        rule.onNodeWithText("TOR").assertIsDisplayed()
    }

    /**
     * The badge is the placeholder and the error state, so nothing visible distinguishes "requested and
     * loading" from "never requested". Deleting the AsyncImage block entirely left every other test green;
     * this is the only assertion that the request happens.
     */
    @Test
    fun aTeamWithALogoUrlRequestsAnImage() {
        launch(live.copy(away = live.away.copy(logoUrl = "https://assets.nhle.com/logos/nhl/svg/TOR_light.svg")))
        rule.onNodeWithTag(LOGO_TAG).assertExists()
    }

    @Test
    fun aTeamWithoutALogoUrlRequestsNoImage() {
        launch(live.copy(away = live.away.copy(logoUrl = null)))
        rule.onNodeWithTag(LOGO_TAG).assertDoesNotExist()
    }

    @Test
    fun failedLogoKeepsTheAbbreviationBadge() {
        // Unsupported schemes fail locally in Coil; tests never depend on a remote logo server.
        launch(scheduled.copy(away = scheduled.away.copy(logoUrl = "unsupported://bruins")))
        rule.onNodeWithText("BOS").assertIsDisplayed()
    }

    @Test
    fun cardIsExactlySeventySixDpAtNormalType() {
        launch()
        val heightDp = rule.onNodeWithTag("game-row").getUnclippedBoundsInRoot().height
        // The Compose helper returns Dp; round-trip with the composition's LocalDensity to also pin
        // the physical-pixel conversion used when measuring the actual rendered row.
        val heightPx = with(density) { heightDp.toPx() }
        assertEquals("card height in dp", 76f, with(density) { heightPx.toDp() }.value, 0.01f)
    }

    @Test
    fun nullScoresDoNotInventZero() {
        launch(live.copy(awayScore = null, homeScore = null))
        // "null" matters as much as "0": an unconditional toString() renders the word and no zero,
        // and this test passed that mutation until the assertion below was added.
        rule.onNodeWithText("null").assertDoesNotExist()
        rule.onNodeWithText("0").assertDoesNotExist()
        rule.onNodeWithText("2").assertDoesNotExist()
        rule.onNodeWithText("3").assertDoesNotExist()
    }

    @Test
    fun suppliedZeroScoreIsDisplayed() {
        launch(live.copy(awayScore = 0))
        rule.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun pregameShowsOnlyStartTimeWithoutLiveIndicator() {
        launch(scheduled.copy(state = GameState.Pre))
        rule.onNodeWithText("Pregame").assertDoesNotExist()
        rule.onNodeWithText("7:00 PM").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertDoesNotExist()
    }

    @Test
    fun missingStartTimeShowsTimeTbd() {
        launch(scheduled.copy(startTimeUtc = null))
        rule.onNodeWithText("Time TBD").assertIsDisplayed()
        rule.onNodeWithText("7:00 PM").assertDoesNotExist()
    }

    @Test
    fun unknownStateShowsUnavailableWithoutExposingTheRawCode() {
        launch(scheduled.copy(state = GameState.Unknown("NEW_FEED_STATE")))
        rule.onNodeWithText("Status unavailable").assertIsDisplayed()
        rule.onNodeWithText("NEW_FEED_STATE").assertDoesNotExist()
        rule.onNodeWithText("7:00 PM").assertDoesNotExist()
        rule.onNodeWithText("FINAL", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Live").assertDoesNotExist()
    }

    @Test
    fun liveOvertimeUsesPeriodTypeInsteadOfOrdinal() {
        launch(live.copy(period = live.period!!.copy(ordinal = 5, periodType = "OT")))
        // P4: the extra period rides in the capsule and the clock stands alone. "OT" and "12:34" together do not
        // fit the rail on one line at 2.0 type on a 360 dp phone, and a wrapped clock is worse than the period
        // being stated once, above it.
        rule.onNodeWithText("OT").assertIsDisplayed()
        rule.onNodeWithText("12:34").assertIsDisplayed()
        rule.onNodeWithText("OT 12:34").assertDoesNotExist()
    }

    @Test
    fun liveShootoutUsesPeriodTypeInsteadOfOrdinal() {
        launch(live.copy(period = live.period!!.copy(ordinal = 5, periodType = "SO")))
        rule.onNodeWithText("SO").assertIsDisplayed()
        rule.onNodeWithText("12:34").assertIsDisplayed()
        rule.onNodeWithText("SO 12:34").assertDoesNotExist()
    }

    @Test
    fun liveWithoutPeriodStaysLiveAndShowsUnavailable() {
        launch(live.copy(period = null))
        rule.onNodeWithText("Period unavailable").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertExists()
        rule.onNodeWithText("12:34").assertDoesNotExist()
    }

    @Test
    fun intermissionKeepsPeriodAndClockAndNamesTheBreak() {
        launch(live.copy(period = live.period!!.copy(inIntermission = true)))
        rule.onNodeWithContentDescription("2nd 12:34, Intermission").assertIsDisplayed()
        rule.onNodeWithText("2nd 12:34").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun doubleFontScaleOnSmallPhoneDoesNotOverflowText() {
        launch(live, fontScale = 2f)
        rule.onNodeWithText("Boston Bruins").assertIsDisplayed()
        rule.onNodeWithText("Toronto Maple Leafs").assertIsDisplayed()
        assertTrue(rule.onNodeWithTag("game-row").getUnclippedBoundsInRoot().height > 76.dp)
        assertTextFits(expectedCount = 8)
    }

    private fun assertTextFits(expectedCount: Int) {
        val textNodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        val count = textNodes.fetchSemanticsNodes().size
        assertEquals(expectedCount, count)
        repeat(count) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            textNodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach {
                // Compose also marks a one-line ellipsis as didOverflowHeight (maxLines exceeded).
                // Ellipsis is intentional for club names; measure the visible line to detect clipping.
                if (!(it.lineCount == 1 && it.isLineEllipsized(0))) {
                    assertFalse("height overflow in ${it.layoutInput.text}", it.didOverflowHeight)
                }
                repeat(it.lineCount) { line ->
                    assertTrue("clipped line in ${it.layoutInput.text}", it.getLineBottom(line) <= it.size.height + 1f)
                    assertTrue("left overflow in ${it.layoutInput.text}", it.getLineLeft(line) >= -1f)
                    assertTrue(
                        "right overflow in ${it.layoutInput.text}: ${it.getLineRight(line)} > ${it.size.width}",
                        it.getLineRight(line) <= it.size.width + 1f,
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun doubleTypeFitsKnownShotsAndTwoDigitScores() {
        launch(live.copy(homeScore = 12, awayScore = 10, homeShots = 14, awayShots = 18), fontScale = 2f)
        assertTextFits(expectedCount = 8)
        rule.onNodeWithTag(STATE_RAIL_TAG).assertWidthIsAtLeast(128.dp)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun doubleTypeFitsIntermissionAndShots() {
        launch(live.copy(homeShots = 14, awayShots = 18, period = live.period!!.copy(inIntermission = true)), fontScale = 2f)
        assertTextFits(expectedCount = 8)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun doubleTypeFitsFinalShootoutCapsule() {
        launch(finalOt.copy(finalKind = FinalKind.SO, homeShots = 14, awayShots = 18), fontScale = 2f)
        // One fewer than the live cases: a shootout card carries the capsule alone in its rail, with no clock and
        // no channel line, so the fit check has one less text to walk.
        assertTextFits(expectedCount = 7)
    }

    @Test
    fun rowHasNoClickActionsOrReachControls() {
        launch()
        assertTrue(rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isEmpty())
        rule.assertPrimaryControlsReachable()
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Test
    fun crestsAreThirtyFourDpAndRailIsNinetyTwoDp() {
        launch(live, modifier = Modifier.padding(horizontal = 12.dp))
        rule.onNodeWithTag("game-row").assertWidthIsEqualTo(387.dp).assertHeightIsEqualTo(76.dp)
        rule.onAllNodesWithTag(TEAM_LINE_TAG).also { rows ->
            assertEquals(2, rows.fetchSemanticsNodes().size)
            repeat(2) { rows[it].assertHeightIsEqualTo(28.dp) }
        }
        rule.onAllNodesWithTag(CREST_TAG).also { crests ->
            assertEquals(2, crests.fetchSemanticsNodes().size)
            repeat(2) { crests[it].assertHeightIsEqualTo(34.dp).assertWidthIsEqualTo(34.dp) }
        }
        rule.onNodeWithTag(STATE_RAIL_TAG).assertWidthIsEqualTo(92.dp)
        val name = rule.onNodeWithText("Boston Bruins").getUnclippedBoundsInRoot()
        val score = rule.onNodeWithText("2")
        score.assertWidthIsEqualTo(30.dp)
        val crest = rule.onAllNodesWithTag(CREST_TAG)[0].getUnclippedBoundsInRoot()
        val row = rule.onAllNodesWithTag(TEAM_LINE_TAG)[0].getUnclippedBoundsInRoot()
        val card = rule.onNodeWithTag("game-row").getUnclippedBoundsInRoot()
        assertEquals(14f, (row.left - card.left).value, 0.01f)
        assertEquals(8f, (name.left - crest.right).value, 0.01f)
        assertEquals(8f, (score.getUnclippedBoundsInRoot().left - name.right).value, 0.01f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h780dp-mdpi")
    fun narrowCardHasEightyTwoDpRailThirtyTwoDpCrestsAndSeventySixDpHeight() {
        launch(live, modifier = Modifier.padding(horizontal = 12.dp))
        assertNarrowMetrics(336.dp)
    }

    private fun launchViewport(width: Dp) {
        rule.setContent {
            RouxTheme {
                density = LocalDensity.current
                ScoresScreen(
                    ScoresUiState(live.date, today = live.date, rows = listOf(live), freshness = Freshness.Fresh),
                    onRefresh = {},
                    onGameClick = {},
                    onSelectDate = {},
                    modifier = Modifier.width(width),
                )
            }
        }
        rule.onNodeWithTag("scores.list").assertWidthIsEqualTo(width)
    }

    @Test
    fun viewportAtThreeHundredEightyDpUsesNarrowMetrics() {
        launchViewport(380.dp)
        assertNarrowMetrics(356.dp, cardTag = "scores.game.${live.id.value}")
    }

    @Test
    fun viewportAtThreeHundredEightyOneDpUsesWideMetrics() {
        launchViewport(381.dp)
        assertWideViewportMetrics(357.dp)
    }

    @Test
    fun viewportAtThreeHundredNinetyDpUsesWideMetrics() {
        launchViewport(390.dp)
        assertWideViewportMetrics(366.dp)
    }

    private fun assertWideViewportMetrics(cardWidth: Dp) {
        rule.onNodeWithTag("scores.game.${live.id.value}").assertWidthIsEqualTo(cardWidth).assertHeightIsEqualTo(76.dp)
        rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).assertWidthIsEqualTo(92.dp)
        rule.onAllNodesWithTag(CREST_TAG, useUnmergedTree = true)[0].assertWidthIsEqualTo(34.dp)
        assertEquals(15.sp, textLayout(rule.onNodeWithText("Boston Bruins", useUnmergedTree = true)).layoutInput.style.fontSize)
        assertEquals(22.sp, textLayout(rule.onNodeWithText("2", useUnmergedTree = true)).layoutInput.style.fontSize)
    }

    private fun assertNarrowMetrics(
        cardWidth: Dp,
        cardTag: String = "game-row",
    ) {
        val card = rule.onNodeWithTag(cardTag)
        card.assertWidthIsEqualTo(cardWidth).assertHeightIsEqualTo(76.dp)
        rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).assertWidthIsEqualTo(128.dp)
        val rows = rule.onAllNodesWithTag(TEAM_LINE_TAG, useUnmergedTree = true)
        val crests = rule.onAllNodesWithTag(CREST_TAG, useUnmergedTree = true)
        assertEquals(2, rows.fetchSemanticsNodes().size)
        assertEquals(2, crests.fetchSemanticsNodes().size)
        repeat(2) {
            rows[it].assertHeightIsEqualTo(28.dp)
            crests[it].assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        }
        val name = rule.onNodeWithText("Boston Bruins", useUnmergedTree = true)
        val score = rule.onNodeWithText("2", useUnmergedTree = true)
        assertEquals(14.sp, textLayout(name).layoutInput.style.fontSize)
        assertEquals(21.sp, textLayout(score).layoutInput.style.fontSize)
        assertEquals(21.sp, textLayout(score).layoutInput.style.lineHeight)
        score.assertWidthIsEqualTo(28.dp)
        val crestBounds = crests[0].getUnclippedBoundsInRoot()
        val rowBounds = rows[0].getUnclippedBoundsInRoot()
        val nameBounds = name.getUnclippedBoundsInRoot()
        assertEquals(12f, (rowBounds.left - card.getUnclippedBoundsInRoot().left).value, 0.01f)
        assertEquals(7f, (nameBounds.left - crestBounds.right).value, 0.01f)
        assertEquals(7f, (score.getUnclippedBoundsInRoot().left - nameBounds.right).value, 0.01f)
        assertEquals(2f, (rows[1].getUnclippedBoundsInRoot().top - rowBounds.bottom).value, 0.01f)
    }

    @Test
    fun scoreAndClubUseTheCardScaleAndTabularFigures() {
        launch(live)
        val score = textLayout(rule.onNodeWithText("3")).layoutInput.style
        assertEquals(22.sp, score.fontSize)
        assertEquals(22.sp, score.lineHeight)
        assertEquals(FontWeight.Bold, score.fontWeight)
        assertEquals(RouxLightColors.onSurface, score.color)
        assertEquals("tnum", score.fontFeatureSettings)
        assertEquals((-0.02).em, score.letterSpacing)
        val name = textLayout(rule.onNodeWithText("Toronto Maple Leafs"))
        assertEquals(15.sp, name.layoutInput.style.fontSize)
        assertEquals(FontWeight.SemiBold, name.layoutInput.style.fontWeight)
        assertEquals((-0.005).em, name.layoutInput.style.letterSpacing)
        assertEquals(1, name.layoutInput.maxLines)
    }

    private fun assertLiveStack(dark: Boolean) {
        launch(live.copy(awayShots = 18, homeShots = 14), dark = dark)
        val accents = if (dark) RouxDarkAccents else RouxLightAccents
        val colors = if (dark) RouxDarkColors else RouxLightColors
        val capsule = rule.onNodeWithTag(STATE_CAPSULE_TAG, useUnmergedTree = true)
        val pillNode = capsule.fetchSemanticsNode()
        val labelNode = rule.onNodeWithText("LIVE", useUnmergedTree = true).fetchSemanticsNode()
        val clockNode = rule.onNodeWithText("2nd 12:34", useUnmergedTree = true).fetchSemanticsNode()
        val railNode = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("pill contains only dot and label", 2, pillNode.children.size)
        assertEquals(pillNode.id, labelNode.parent!!.id)
        assertEquals("pill is a rail child", railNode.id, pillNode.parent!!.id)
        assertEquals("clock is a sibling of the pill", railNode.id, clockNode.parent!!.id)
        assertEquals("rail holds the pill and the clock, and nothing else", 2, railNode.children.size)
        val pixels = capsule.captureToImage().toPixelMap()
        assertEquals(accents.fill, pixels[pixels.width / 2, 1])
        val dot = rule.onNodeWithContentDescription("Live")
        dot.assertWidthIsEqualTo(6.dp).assertHeightIsEqualTo(6.dp)
        assertEquals(accents.onFill, dot.captureToImage().toPixelMap()[3, 3])
        val labelBounds = rule.onNodeWithText("LIVE", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pillBounds = capsule.getUnclippedBoundsInRoot()
        val borderSpace = with(density) { 1.5.dp.roundToPx().toDp() }
        assertEquals("live pill reserves its border above the label", borderSpace + 2.dp, labelBounds.top - pillBounds.top)
        assertEquals(
            "live pill reserves its border before the dot",
            borderSpace + 6.dp,
            dot.getUnclippedBoundsInRoot().left - pillBounds.left,
        )
        val label = textLayout(rule.onNodeWithText("LIVE")).layoutInput.style
        assertEquals(accents.onFill, label.color)
        val clock = textLayout(rule.onNodeWithText("2nd 12:34")).layoutInput.style
        assertEquals(accents.signature, clock.color)
        assertEquals(12.sp, clock.fontSize)
        assertEquals(FontWeight.ExtraBold, clock.fontWeight)
        assertEquals("tnum", clock.fontFeatureSettings)
        listOf("2", "3").forEach {
            val score = textLayout(rule.onNodeWithText(it)).layoutInput.style
            assertEquals(colors.onSurface, score.color)
            assertEquals(FontWeight.Bold, score.fontWeight)
        }
        assertStartAlignedStack(listOf(capsule, rule.onNodeWithText("2nd 12:34")))
        println(
            "Measured ${if (dark) "dark" else "light"}: live pill fill=#%08X text=#%08X; clock=#%08X weight=%d; score=#%08X weight=%d"
                .format(
                    pixels[pixels.width / 2, 1].toArgb(),
                    label.color.toArgb(),
                    clock.color.toArgb(),
                    clock.fontWeight!!.weight,
                    textLayout(rule.onNodeWithText("3"))
                        .layoutInput.style.color
                        .toArgb(),
                    textLayout(rule.onNodeWithText("3"))
                        .layoutInput.style.fontWeight!!
                        .weight,
                ),
        )
    }

    private fun assertStartAlignedStack(lines: List<SemanticsNodeInteraction>) {
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG).getUnclippedBoundsInRoot()
        val bounds = lines.map { it.getUnclippedBoundsInRoot() }
        assertEquals(
            "stack centred vertically",
            (rail.top + rail.bottom).value / 2f,
            (bounds.first().top + bounds.last().bottom).value / 2f,
            0.5f,
        )
        bounds.forEach {
            assertEquals(
                "line starts 12 dp inside the rail",
                (rail.left + 12.dp).value,
                it.left.value,
                0.5f,
            )
        }
        bounds.forEach { assertTrue("line stays within the 7 dp end inset", it.right <= rail.right - 7.dp) }
        val text = lines.last()
        val layout = textLayout(text)
        assertEquals(TextAlign.Start, layout.layoutInput.style.textAlign)
        val contentWidth =
            with(density) {
                layout.layoutInput.constraints.maxWidth
                    .toDp()
            }
        // The rail has a minimum width; its lines are laid out inside the insets rather than stretched to fill them.
        // It used to be the shots line that reached the edge, so equality was the wrong claim to keep once that went.
        assertTrue(
            "rail content width stays inside both insets",
            contentWidth.value <= (rail.width - 19.dp).value + 0.01f,
        )
        assertEquals("first text line starts at the left", 0f, layout.getLineLeft(0), 0.01f)
        val leftInset = bounds.first().left - rail.left
        val rightInset = rail.width - leftInset - contentWidth
        println("Measured rail: left inset=${leftInset.value} dp, right inset=${rightInset.value} dp; start-aligned, vertically centred")
        bounds.zipWithNext().forEach { (above, below) -> assertEquals("line gap", 2f, (below.top - above.bottom).value, 0.01f) }
        rule.onNodeWithTag("game-row").assertHeightIsEqualTo(76.dp)
    }

    @Test
    fun livePillClockAndShotsFormAStartAlignedStackLight() = assertLiveStack(dark = false)

    @Test
    fun livePillClockAndShotsFormAStartAlignedStackDark() = assertLiveStack(dark = true)

    private fun assertFinalWinner(dark: Boolean) {
        launch(finalOt.copy(awayShots = 18, homeShots = 14), dark = dark)
        val accents = if (dark) RouxDarkAccents else RouxLightAccents
        val colors = if (dark) RouxDarkColors else RouxLightColors
        assertEquals(accents.signature, textLayout(rule.onNodeWithText("Toronto Maple Leafs")).layoutInput.style.color)
        assertEquals(accents.signature, textLayout(rule.onNodeWithText("3")).layoutInput.style.color)
        assertEquals(colors.onSurface, textLayout(rule.onNodeWithText("Boston Bruins")).layoutInput.style.color)
        val losingScore = textLayout(rule.onNodeWithText("2")).layoutInput.style
        val winningScore = textLayout(rule.onNodeWithText("3")).layoutInput.style
        assertEquals(colors.onSurfaceVariant, losingScore.color)
        assertEquals(FontWeight.Medium, losingScore.fontWeight)
        assertEquals(FontWeight.Medium, winningScore.fontWeight)
        val label = textLayout(rule.onNodeWithText("FINAL · OT")).layoutInput.style
        assertEquals(accents.signature, label.color)
        assertEquals(12.sp, label.fontSize)
        assertEquals(FontWeight.ExtraBold, label.fontWeight)
        assertEquals(0.09.em, label.letterSpacing)
        val pill = rule.onNodeWithTag(STATE_CAPSULE_TAG)
        val pillLayout = textLayout(pill)
        val borderSpace = with(density) { 1.5.dp.roundToPx().toDp() }
        pill.assertHeightIsEqualTo(with(density) { pillLayout.size.height.toDp() } + 4.dp + borderSpace * 2)
        val pixels = pill.captureToImage().toPixelMap()
        assertEquals(accents.stroke, pixels[pixels.width / 2, 0])
        assertEquals(colors.surfaceVariant, pixels[pixels.width / 2, pixels.height - 3])
        assertStartAlignedStack(listOf(rule.onNodeWithTag(STATE_CAPSULE_TAG)))
        println(
            "Measured ${if (dark) "dark" else "light"}: final pill text=#%08X weight=%d".format(
                label.color.toArgb(),
                label.fontWeight!!.weight,
            ),
        )
    }

    @Test
    fun finalCapsuleAndOnlyWinnerNameAndScoreAreSignatureLight() = assertFinalWinner(dark = false)

    @Test
    fun finalCapsuleAndOnlyWinnerNameAndScoreAreSignatureDark() = assertFinalWinner(dark = true)

    @Test
    fun awayWinnerIsSignatureToo() {
        launch(finalOt.copy(awayScore = 4))
        assertEquals(RouxLightAccents.signature, textLayout(rule.onNodeWithText("Boston Bruins")).layoutInput.style.color)
        assertEquals(RouxLightAccents.signature, textLayout(rule.onNodeWithText("4")).layoutInput.style.color)
        assertEquals(RouxLightColors.onSurfaceVariant, textLayout(rule.onNodeWithText("3")).layoutInput.style.color)
    }

    private fun assertUpcoming(
        dark: Boolean,
        state: GameState = GameState.Scheduled,
    ) {
        launch(scheduled.copy(state = state, homeScore = 0, awayScore = 2, homeShots = 14, awayShots = 18), dark = dark)
        val colors = if (dark) RouxDarkColors else RouxLightColors
        val accents = if (dark) RouxDarkAccents else RouxLightAccents
        rule.onNodeWithText("0").assertDoesNotExist()
        rule.onNodeWithText("2").assertDoesNotExist()
        rule.onNodeWithTag(STATE_CAPSULE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(WATCH_TAG).assertDoesNotExist()
        val name = textLayout(rule.onNodeWithText("Boston Bruins")).layoutInput.style
        assertEquals(colors.onSurfaceVariant, name.color)
        val dashes = rule.onAllNodesWithText("–")
        assertEquals("one en dash per team", 2, dashes.fetchSemanticsNodes().size)
        repeat(2) {
            dashes[it].assertIsDisplayed().assertWidthIsEqualTo(30.dp)
            val dash = textLayout(dashes[it]).layoutInput.style
            assertEquals(accents.muted, dash.color)
            assertEquals(FontWeight.Normal, dash.fontWeight)
            assertEquals((-0.02).em, dash.letterSpacing)
        }
        val time = rule.onNodeWithText("7:00 PM", useUnmergedTree = true)
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("scheduled rail contains only the time", listOf(time.fetchSemanticsNode().id), rail.children.map { it.id })
        assertEquals("tnum", textLayout(time).layoutInput.style.fontFeatureSettings)
        assertStartAlignedStack(listOf(time))
    }

    @Test
    fun upcomingShowsDashesAndSuppressesSuppliedScoresAndShotsLight() = assertUpcoming(dark = false)

    @Test
    fun upcomingShowsDashesAndSuppressesSuppliedScoresAndShotsDark() = assertUpcoming(dark = true)

    @Test
    fun pregameShowsDashesToo() = assertUpcoming(dark = false, state = GameState.Pre)

    @Test
    fun anUpcomingGameNamesItsNetworksNationalFirst() {
        launch(
            scheduled.copy(
                broadcasts =
                    listOf(
                        Broadcast("MSG", "US", "H"),
                        Broadcast("NHLN", "US", "N"),
                        Broadcast("SN", "CA", "N"),
                    ),
            ),
        )
        // National listings lead, because a local channel is meaningless to most readers. Three names fit a
        // wrapping 92 dp rail, so this list needs no count.
        rule.onNodeWithText("NHLN · SN · MSG").assertIsDisplayed()
    }

    @Test
    fun aLongListOfNetworksWrapsInsteadOfWideningTheRail() {
        // The rail's right edge is the same edge on every card in the list: a long channel list wraps down the
        // card instead of pushing the rail out. Four long names is the worst case a feed produces.
        launch(
            scheduled.copy(
                broadcasts =
                    listOf(
                        Broadcast("PRIME VIDEO", "US", "N"),
                        Broadcast("NBC SPORTS CALIFORNIA", "US", "A"),
                        Broadcast("MSG SPORTSNET", "US", "H"),
                        Broadcast("SPORTSNET ONE", "CA", "N"),
                    ),
            ),
        )
        val rail = rule.onNodeWithTag(STATE_RAIL_TAG).getUnclippedBoundsInRoot()
        val watch = rule.onNodeWithTag(WATCH_TAG).getUnclippedBoundsInRoot()
        // The rail keeps its own minimum rather than growing to fit the text...
        assertTrue(
            "the rail grew to ${rail.width} with a crowded channel list",
            rail.width.value <= 92f + 1f,
        )
        // ...and the text is laid out inside it, so it cannot be what widened the card.
        assertTrue(
            "the channel line (${watch.width}) runs past the rail's insets",
            watch.width.value <= rail.width.value - 19f + 1f,
        )
    }

    @Test
    fun oneNetworkNeedsNoCount() {
        launch(scheduled.copy(broadcasts = listOf(Broadcast("NHLN", "US", "N"))))
        rule.onNodeWithText("NHLN").assertIsDisplayed()
    }

    @Test
    fun aGameWithNoListedNetworkShowsOnlyItsTime() {
        launch(scheduled)
        rule.onNodeWithTag(WATCH_TAG).assertDoesNotExist()
    }

    @Test
    fun aLiveGameShowsNoNetworks() {
        // Where to watch is a pre-game question; once the puck drops the rail has a clock to spend that line on.
        launch(live.copy(broadcasts = listOf(Broadcast("NHLN", "US", "N"))))
        rule.onNodeWithTag(WATCH_TAG).assertDoesNotExist()
    }

    @Test
    fun shotsAreGoneFromTheRowEvenWhenTheFeedStillCarriesThem() {
        launch(live.copy(awayShots = 18, homeShots = 14))
        rule.onNodeWithText("18–14 sog").assertDoesNotExist()
    }

    @Test
    fun failedLogoKeepsThirtyFourDpCrestAndSeventySixDpCard() {
        launch(live.copy(away = live.away.copy(logoUrl = "unsupported://bruins")))
        rule.onNodeWithText("BOS").assertIsDisplayed()
        rule.onAllNodesWithTag(CREST_TAG)[0].assertWidthIsEqualTo(34.dp).assertHeightIsEqualTo(34.dp)
        rule.onNodeWithTag("game-row").assertHeightIsEqualTo(76.dp)
    }

    // All snapshots deliberately use offline abbreviation fallbacks. State and theme changes remain
    // deterministic, independently of NHL logo availability; the null-logo fixture is a scored game.
    private fun snapshot(
        name: String,
        game: Game,
        dark: Boolean,
    ) {
        val snapshotGame = if (game.state == GameState.Scheduled) game else game.copy(awayShots = 18, homeShots = 14)
        launch(snapshotGame, dark)
        rule.onNodeWithTag("game-row").captureRoboImage("src/test/screenshots/game-row/$name.png")
    }

    @Test
    fun scheduledLight() = snapshot("scheduled-light", scheduled, dark = false)

    @Test
    fun scheduledDark() = snapshot("scheduled-dark", scheduled, dark = true)

    @Test
    fun scheduledWithNetworksLight() =
        snapshot(
            "scheduled-networks-light",
            scheduled.copy(
                broadcasts =
                    listOf(
                        Broadcast("MSG", "US", "H"),
                        Broadcast("NHLN", "US", "N"),
                        Broadcast("SN", "CA", "N"),
                    ),
            ),
            dark = false,
        )

    @Test
    fun scheduledWithNetworksDark() =
        snapshot(
            "scheduled-networks-dark",
            scheduled.copy(
                broadcasts =
                    listOf(
                        Broadcast("MSG", "US", "H"),
                        Broadcast("NHLN", "US", "N"),
                        Broadcast("SN", "CA", "N"),
                    ),
            ),
            dark = true,
        )

    @Test
    fun liveLight() = snapshot("live-light", live, dark = false)

    @Test
    fun liveDark() = snapshot("live-dark", live, dark = true)

    @Test
    fun finalOtLight() = snapshot("final-ot-light", finalOt, dark = false)

    @Test
    fun finalOtDark() = snapshot("final-ot-dark", finalOt, dark = true)

    @Test
    fun nullLogoLight() = snapshot("null-logo-light", finalOt.copy(finalKind = FinalKind.REG), dark = false)

    @Test
    fun nullLogoDark() = snapshot("null-logo-dark", finalOt.copy(finalKind = FinalKind.REG), dark = true)
}
