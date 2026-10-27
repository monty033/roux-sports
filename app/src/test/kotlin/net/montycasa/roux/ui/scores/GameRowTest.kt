package net.montycasa.roux.ui.scores

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.reach.assertPrimaryControlsReachable
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
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h640dp-420dpi")
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
    ) {
        rule.setContent {
            RouxTheme(dark = dark) {
                density = Density(LocalDensity.current.density, fontScale)
                CompositionLocalProvider(LocalDensity provides density) {
                    GameRow(game, Modifier.testTag("game-row"))
                }
            }
        }
    }

    @Test
    fun scheduledShowsTorontoTimeInTwelveHourFormat() {
        launch()
        rule.onNodeWithText("7:00 PM").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertDoesNotExist()
    }

    @Test
    fun scheduledUsesTorontoEvenWhenTheDeviceZoneDiffers() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        launch()
        rule.onNodeWithText("7:00 PM").assertIsDisplayed()
    }

    @Test
    fun scheduledUsesTorontoWinterOffset() {
        launch(scheduled.copy(startTimeUtc = Instant.parse("2026-12-01T00:00:00Z")))
        rule.onNodeWithText("7:00 PM").assertIsDisplayed()
    }

    @Test
    fun liveShowsPeriodClockAndLiveDescription() {
        launch(live)
        rule.onNodeWithText("2nd").assertIsDisplayed()
        rule.onNodeWithText("12:34").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertExists()
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun finalOvertimeShowsFinalOtAndNoLiveIndicatorDespiteRetainedClock() {
        launch(finalOt)
        rule.onNodeWithText("FINAL/OT").assertIsDisplayed()
        rule.onNodeWithContentDescription("Live").assertDoesNotExist()
        rule.onNodeWithText("12:34").assertDoesNotExist()
    }

    @Test
    fun finalShootoutShowsFinalSo() {
        launch(finalOt.copy(finalKind = FinalKind.SO))
        rule.onNodeWithText("FINAL/SO").assertIsDisplayed()
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
    fun rowIsAtLeastFiftySixDpTall() {
        launch()
        val heightDp = rule.onNodeWithTag("game-row").getUnclippedBoundsInRoot().height
        // The Compose helper returns Dp; round-trip with the composition's LocalDensity to also pin
        // the physical-pixel conversion used when measuring the actual rendered row.
        val heightPx = with(density) { heightDp.toPx() }
        assertTrue("row measured $heightDp", with(density) { heightPx.toDp() } >= 56.dp)
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
    fun pregameShowsItsPhaseAndStartTimeWithoutLiveIndicator() {
        launch(scheduled.copy(state = GameState.Pre))
        rule.onNodeWithText("Pregame").assertIsDisplayed()
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
        rule.onNodeWithText("OT").assertIsDisplayed()
    }

    @Test
    fun liveShootoutUsesPeriodTypeInsteadOfOrdinal() {
        launch(live.copy(period = live.period!!.copy(ordinal = 5, periodType = "SO")))
        rule.onNodeWithText("SO").assertIsDisplayed()
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
        rule.onNodeWithText("Intermission").assertIsDisplayed()
        rule.onNodeWithText("2nd").assertIsDisplayed()
        rule.onNodeWithText("12:34").assertIsDisplayed()
    }

    @Test
    fun doubleFontScaleOnSmallPhoneDoesNotOverflowText() {
        launch(live, fontScale = 2f)
        rule.onNodeWithText("Boston Bruins").assertIsDisplayed()
        rule.onNodeWithText("Toronto Maple Leafs").assertIsDisplayed()
        val textNodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        val count = textNodes.fetchSemanticsNodes().size
        assertEquals(8, count) // Two badges, names, scores, and the period/clock.
        repeat(count) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            textNodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach {
                assertFalse("height overflow in ${it.layoutInput.text}", it.didOverflowHeight)
                // A paragraph may retain a wider layout constraint than its final intrinsic width.
                // Check the actual line edges rather than counting that empty space as overflow.
                repeat(it.lineCount) { line ->
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
    fun rowHasNoClickActionsOrReachControls() {
        launch()
        assertTrue(rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isEmpty())
        rule.assertPrimaryControlsReachable()
    }

    // All snapshots deliberately use offline abbreviation fallbacks. State and theme changes remain
    // deterministic, independently of NHL logo availability; the null-logo fixture is a scored game.
    private fun snapshot(
        name: String,
        game: Game,
        dark: Boolean,
    ) {
        launch(game, dark)
        rule.onNodeWithTag("game-row").captureRoboImage("src/test/screenshots/game-row/$name.png")
    }

    @Test
    fun scheduledLight() = snapshot("scheduled-light", scheduled, dark = false)

    @Test
    fun scheduledDark() = snapshot("scheduled-dark", scheduled, dark = true)

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
