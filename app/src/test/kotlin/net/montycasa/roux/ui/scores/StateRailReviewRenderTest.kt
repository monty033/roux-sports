package net.montycasa.roux.ui.scores

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.model.FinalKind
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.PeriodInfo
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * The state rail, drawn for a person to look at.
 *
 * This is layout work on a screen Patrick looks at, and a test that asserts a number cannot say
 * whether the card looks right. So the four states that used to draw at four different widths are
 * drawn here as one column, in both themes, at 360 dp, and the pictures are written to
 * `app/build/review/` for someone to look at.
 *
 * These are not screenshot goldens: nothing is compared against anything, and nothing under
 * `src/test/screenshots/` is written. The pictures are review artefacts and they are rebuilt on
 * every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StateRailReviewRenderTest {
    @get:Rule
    val rule = createComposeRule()

    private val away = TeamRef(TeamKey("BOS"), name = "Boston Bruins")
    private val home = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs")

    private fun game(
        id: String,
        state: GameState,
        finalKind: FinalKind? = null,
        period: PeriodInfo? = null,
    ): Game =
        Game(
            id = GameId(id),
            date = "2026-10-26",
            state = state,
            away = away,
            home = home,
            awayScore = 2,
            homeScore = 3,
            finalKind = finalKind,
            period = period,
            startTimeUtc = Instant.parse("2026-10-26T23:00:00Z"),
        )

    /**
     * The states that used to draw the rail at four different widths, plus the one state the rail
     * does not have a width for.
     *
     * A live game whose period the feed does not name draws "Period unavailable" with its clock.
     * That line is wider than any state the rail is measured for, so it wraps inside the rail
     * instead of the rail growing for it - which is why it is here to be looked at.
     */
    private val cards =
        listOf(
            game("final", GameState.Final, FinalKind.REG),
            game("final-ot", GameState.Final, FinalKind.OT),
            game("final-so", GameState.Final, FinalKind.SO),
            game("live-ot", GameState.Live(), period = PeriodInfo(ordinal = 4, periodType = "OT", clock = "12:34")),
            game(
                "live-unknown-period",
                GameState.Live(),
                period = PeriodInfo(ordinal = 4, periodType = "REG", clock = "12:34"),
            ),
        )

    @Test
    fun light() = render(dark = false, name = "state-rail-light")

    @Test
    fun dark() = render(dark = true, name = "state-rail-dark")

    /**
     * The same column on a wider phone.
     *
     * The narrow card's 128 dp floor is wider than every state needs at the normal font scale, so the
     * old rail looked right there; the bug showed on a phone whose floor is 92 dp. These are the
     * pictures where the right-hand edge of the list either lines up or does not.
     */
    @Test
    @Config(qualifiers = "en-rUS-w411dp-h923dp-mdpi")
    fun lightOnAPhoneWidth() = render(dark = false, name = "state-rail-phone-light")

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h923dp-mdpi")
    fun darkOnAPhoneWidth() = render(dark = true, name = "state-rail-phone-dark")

    private fun render(
        dark: Boolean,
        name: String,
    ) {
        rule.setContent {
            RouxTheme(dark = dark) {
                BoxWithConstraints {
                    val narrow = maxWidth <= 380.dp
                    Column(Modifier.width(maxWidth).testTag(REVIEW_COLUMN_TAG)) {
                        cards.forEach { game ->
                            GameRow(
                                game,
                                narrow = narrow,
                                modifier = Modifier.testTag("review-card-${game.id.value}"),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val picture = rule.onNodeWithTag(REVIEW_COLUMN_TAG, useUnmergedTree = true).captureToImage()
        val file = File("build/review/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            picture.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("review picture: ${file.absolutePath} (${picture.width}x${picture.height})")
    }
}

private const val REVIEW_COLUMN_TAG = "state-rail-review-column"
